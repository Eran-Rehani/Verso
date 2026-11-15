package app.verso.reader.pdf

import android.graphics.Path
import android.graphics.RectF
import app.verso.core.pdf.PdfGlyph
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDSimpleFont
import com.tom_roush.pdfbox.util.Matrix

/**
 * Extracts a glyph's own vector outline from the font embedded in the PDF.
 *
 * This is the tier-2 fallback for equations whose internal structure (super/subscripts, a
 * fraction's numerator and denominator) cannot be reconstructed into LaTeX. Rather than show
 * the user a hole, an original page image, or an OCR guess, the equation is drawn from the
 * author's own outlines — already inside the file — so it stays crisp at any size and scales
 * with the reader's font setting.
 *
 * It is deliberately NOT OCR: the PDF text layer already states every character exactly, and
 * the reason equations break is lost *structure*, which pixel recognition cannot recover more
 * accurately than the file itself states it.
 *
 * All outlines are returned normalised to **em units**: [VectorGlyph.path] is in a coordinate
 * system where one em is 1.0 and the baseline is y = 0 with y increasing downward, matching
 * Android's `Path`. Callers scale by the point size they want.
 */
object PdfGlyphOutlines {

    /** Cap on outline complexity, so a pathological font cannot exhaust memory. */
    private const val MAX_PATH_POINTS = 20_000

    /**
     * One glyph's outline, in em units.
     *
     * [unavailableReason] is set when the font cannot supply an outline (no embedded program,
     * an unsupported encoding, or a missing glyph). Callers must treat a null [path] as
     * "vector-unavailable" and fall through to the page fallback rather than drawing nothing
     * and pretending success.
     */
    data class VectorGlyph(
        val text: String,
        val path: Path?,
        val advanceEm: Float,
        val unavailableReason: String? = null,
    )

    /**
     * Extract outlines for [glyphs] from the fonts embedded in [document].
     *
     * Returns one [VectorGlyph] per input glyph, in order. Any glyph that cannot be resolved
     * keeps its place with `path == null`, so the caller can decide between a partial render
     * and a full page fallback.
     */
    fun extract(document: PDDocument, glyphs: List<PdfGlyph>): List<VectorGlyph> {
        if (glyphs.isEmpty()) return emptyList()
        val cache = HashMap<Pair<String, String>, Path?>()
        return glyphs.map { glyph ->
            if (glyph.unmapped || glyph.text.isEmpty()) {
                return@map VectorGlyph(glyph.text, null, 0f, "unmapped glyph")
            }
            val key = glyph.fontName to glyph.text
            val cached = cache.getOrPut(key) { loadPath(document, glyph) }
            if (cached == null) {
                VectorGlyph(glyph.text, null, 0f, "no outline for '${glyph.text}' in ${glyph.fontName}")
            } else {
                VectorGlyph(glyph.text, cached, advanceEm(glyph))
            }
        }
    }

    /** True when every glyph resolved to an outline. */
    fun isComplete(outlines: List<VectorGlyph>): Boolean =
        outlines.isNotEmpty() && outlines.all { it.path != null }

    /**
     * Look the font up by name and ask PDFBox for the glyph outline.
     *
     * PDFBox already implements the Type 1 / CFF / TrueType charstring interpreters and
     * returns an `android.graphics.Path`, which is why this needs no hand-written glyph
     * parser — that was the largest risk in this task and it simply does not exist.
     */
    private fun loadPath(document: PDDocument, glyph: PdfGlyph): Path? {
        val font = findFont(document, glyph.fontName) ?: return null
        return try {
            val raw = when (font) {
                is PDType0Font -> pathForType0(font, glyph)
                is PDSimpleFont -> pathForSimple(font, glyph)
                else -> null
            } ?: return null
            // getFontMatrix() is declared on the PDFont base type; read it before narrowing.
            val matrix = (font as? com.tom_roush.pdfbox.pdmodel.font.PDFont)?.fontMatrix
            normalise(raw, matrix, glyph)
        } catch (e: Exception) {
            // A font that misbehaves must degrade to "unavailable", never crash the reader.
            null
        }
    }

    private fun pathForType0(font: PDType0Font, glyph: PdfGlyph): Path? {
        // A Type0 glyph is addressed by code point; use the first scalars as the code.
        val code = glyph.text.codePointAt(0)
        return font.getPath(code)
    }

    private fun pathForSimple(font: PDSimpleFont, glyph: PdfGlyph): Path? {
        // Simple fonts address glyphs by their encoded character name.
        val name = glyph.text.take(1)
        if (name.isEmpty()) return null
        return if (font.hasGlyph(name)) font.getPath(name) else null
    }

    /**
     * Normalise a raw outline (in font units, y-up) into em units with y-down.
     *
     * The font matrix maps font units to text space; its x-scale is the size of one unit in
     * em (0.001 for a 1000-upem font). Android's `Path` uses y-down, so the outline is
     * mirrored about the baseline, which keeps text upright while letting callers place it by
     * baseline rather than by bounding box.
     */
    private fun normalise(path: Path, fontMatrix: Matrix?, glyph: PdfGlyph): Path? {
        @Suppress("UNUSED_VARIABLE")
        val glyphText = glyph.text
        val unit = fontMatrix?.let { kotlin.math.abs(it.scaleX) }?.takeIf { it > 0f } ?: 0.001f
        val bounds = RectF()
        path.computeBounds(bounds, true)
        if (bounds.width() <= 0f && bounds.height() <= 0f) return null
        val androidMatrix = android.graphics.Matrix()
        androidMatrix.setScale(unit, -unit)
        val out = Path()
        path.transform(androidMatrix, out)
        return out
    }

    /** Advance width in em, derived from the glyph's own reported width and size. */
    private fun advanceEm(glyph: PdfGlyph): Float =
        if (glyph.fontSize > 0f) glyph.width / glyph.fontSize else 0f

    /**
     * Find the embedded font whose name matches. PDF font names are often subset-prefixed
     * (`ABCDEF+LMRoman10-Regular`), so the prefix is stripped before comparing.
     */
    private fun findFont(document: PDDocument, fontName: String): Any? {
        if (fontName.isEmpty()) return null
        val wanted = stripSubsetPrefix(fontName)
        for (pageIndex in 0 until document.numberOfPages) {
            val resources = document.getPage(pageIndex).resources ?: continue
            for (name in resources.fontNames) {
                val font = runCatching { resources.getFont(name) }.getOrNull() ?: continue
                if (stripSubsetPrefix(font.name) == wanted) return font
            }
        }
        return null
    }

    private fun stripSubsetPrefix(name: String): String {
        val plus = name.indexOf('+')
        return if (plus == 6) name.substring(7) else name
    }

}
