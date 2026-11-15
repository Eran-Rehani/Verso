package app.verso.core.pdf

import android.graphics.RectF
import app.verso.reader.pdf.PdfGlyphOutlines
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M6.4: the vector-outline fallback must draw an equation from the PDF's own embedded font
 * outlines, so an unreconstructable equation is still readable and scalable rather than a
 * placeholder, a page image, or an OCR guess.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfGlyphOutlinesTest {

    private val context: android.content.Context by lazy {
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    }

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    /** A one-page PDF drawing [text] in a standard font. */
    private fun pdfWith(text: String): ByteArray {
        val doc = PDDocument()
        try {
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(72f, 700f)
                cs.showText(text)
                cs.endText()
            }
            return ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        } finally {
            doc.close()
        }
    }

    private fun glyphsOf(pdf: ByteArray): Pair<PDDocument, List<PdfGlyph>> {
        val content = app.verso.reader.pdf.PdfBoxPageSource(context).read(pdf)
        val doc = PDDocument.load(ByteArrayInputStream(pdf))
        return doc to content.pages.first().glyphs
    }

    @Test
    fun `an embedded font yields a real outline path`() {
        val (doc, glyphs) = glyphsOf(pdfWith("x"))
        doc.use {
            assertTrue("expected extracted glyphs", glyphs.isNotEmpty())
            val outlines = PdfGlyphOutlines.extract(it, glyphs)
            val first = outlines.first { o -> o.text.isNotBlank() }
            assertNotNull(
                "expected an outline for '${first.text}', reason=${first.unavailableReason}",
                first.path,
            )
        }
    }

    @Test
    fun `the outline has real geometry`() {
        val (doc, glyphs) = glyphsOf(pdfWith("x"))
        doc.use {
            val outline = PdfGlyphOutlines.extract(it, glyphs).first { o -> o.path != null }
            val bounds = RectF()
            outline.path!!.computeBounds(bounds, true)
            assertTrue("outline should have width, was ${bounds.width()}", bounds.width() > 0f)
            assertTrue("outline should have height, was ${bounds.height()}", bounds.height() > 0f)
        }
    }

    @Test
    fun `outlines are normalised to roughly one em tall`() {
        val (doc, glyphs) = glyphsOf(pdfWith("x"))
        doc.use {
            val outline = PdfGlyphOutlines.extract(it, glyphs).first { o -> o.path != null }
            val bounds = RectF()
            outline.path!!.computeBounds(bounds, true)
            // A lowercase x-height glyph in em units is well under 1 and comfortably above 0.1.
            assertTrue(
                "expected em-normalised height, was ${bounds.height()}",
                bounds.height() in 0.1f..1.5f,
            )
        }
    }

    @Test
    fun `every printed character resolves`() {
        val (doc, glyphs) = glyphsOf(pdfWith("abc"))
        doc.use {
            val outlines = PdfGlyphOutlines.extract(it, glyphs)
            assertTrue("expected all characters to resolve", PdfGlyphOutlines.isComplete(outlines))
        }
    }

    @Test
    fun `an unknown font reports unavailable instead of drawing nonsense`() {
        val (doc, glyphs) = glyphsOf(pdfWith("x"))
        doc.use {
            val bogus = glyphs.map { it.copy(fontName = "NOT-A-REAL-FONT") }
            val outlines = PdfGlyphOutlines.extract(it, bogus)
            assertTrue("must not claim completeness", !PdfGlyphOutlines.isComplete(outlines))
            assertTrue("every glyph should be marked unavailable", outlines.all { it.path == null })
            assertNotNull(
                "unavailable glyphs must explain why",
                outlines.first().unavailableReason,
            )
        }
    }

    @Test
    fun `unmapped glyphs are skipped rather than guessed`() {
        val (doc, glyphs) = glyphsOf(pdfWith("x"))
        doc.use {
            val unmapped = glyphs.map { it.copy(unmapped = true) }
            val outlines = PdfGlyphOutlines.extract(it, unmapped)
            assertTrue(outlines.all { it.path == null })
            assertEquals("unmapped glyph", outlines.first().unavailableReason)
        }
    }

    @Test
    fun `empty input yields empty output`() {
        val (doc, _) = glyphsOf(pdfWith("x"))
        doc.use {
            assertTrue(PdfGlyphOutlines.extract(it, emptyList()).isEmpty())
            assertFalse(PdfGlyphOutlines.isComplete(emptyList()))
        }
    }

    @Test
    fun `subset font prefix is stripped when matching`() {
        val (doc, glyphs) = glyphsOf(pdfWith("x"))
        doc.use {
            // Real PDFs name fonts like "ABCDEF+LMRoman10-Regular"; simulate the prefix.
            val prefixed = glyphs.map { it.copy(fontName = "ABCDEF+" + it.fontName) }
            val outlines = PdfGlyphOutlines.extract(it, prefixed)
            assertTrue(
                "subset-prefixed names must still resolve, reasons=${outlines.map { o -> o.unavailableReason }}",
                outlines.any { it.path != null },
            )
        }
    }

    @Test
    fun `missing outline path is distinguishable from an empty path`() {
        val (doc, glyphs) = glyphsOf(pdfWith("x"))
        doc.use {
            val outlines = PdfGlyphOutlines.extract(it, glyphs)
            outlines.forEach { o ->
                if (o.path == null) assertNotNull("null path must carry a reason", o.unavailableReason)
                else assertNull("a resolved glyph needs no reason", o.unavailableReason)
            }
        }
    }
}
