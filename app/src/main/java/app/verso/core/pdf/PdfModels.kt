package app.verso.core.pdf

data class TextRun(
    val text: String,
    val x: Float,
    val baseline: Float,
    val width: Float,
    val fontSize: Float,
    val bold: Boolean = false
)

data class PdfPage(
    val index: Int,
    val width: Float,
    val height: Float,
    val runs: List<TextRun>,
    /**
     * Individual positioned glyphs, before any baseline grouping.
     *
     * This is the structure that baseline flattening destroys: a superscript's smaller size
     * and raised baseline are only recoverable here, never from [runs]. Defaulted so existing
     * fake page sources and tests keep working, and never serialised into a document.
     */
    val glyphs: List<PdfGlyph> = emptyList(),
    /**
     * Thin painted horizontal rules, which are how TeX-based PDFs draw fraction bars and
     * radical rules. Defaulted for the same reason as [glyphs].
     */
    val rules: List<PdfRule> = emptyList(),
)

/**
 * One glyph as drawn on the page, in page-local top-down coordinates.
 *
 * [text] is a String, not a Char: a single PDF glyph can map to several Unicode scalars
 * (ligatures, combined accents), and a codepoint can span a surrogate pair.
 */
data class PdfGlyph(
    val text: String,
    val x: Float,
    val baseline: Float,
    val width: Float,
    val height: Float,
    val fontSize: Float,
    val fontName: String,
    /** Horizontal scale of the glyph, used to spot rotated or skewed text. */
    val direction: Float = 0f,
    /** True when the glyph could not be mapped to Unicode. */
    val unmapped: Boolean = false,
)

/**
 * A painted horizontal rule: a fraction bar, overline, or radical rule.
 *
 * Only *painted* thin horizontal segments are recorded. Paths that are constructed but never
 * stroked or filled are not rules, and neither are table rules — distinguishing those is the
 * reconstructor's job, not the extractor's.
 */
data class PdfRule(
    val x0: Float,
    val x1: Float,
    val y: Float,
    val thickness: Float,
)

data class PdfContent(
    val title: String?,
    val pages: List<PdfPage>
)

interface PdfPageSource {
    fun read(pdf: ByteArray, onPage: () -> Unit = {}): PdfContent
}

data class PdfLine(
    val page: Int,
    val column: Int,
    val text: String,
    val x0: Float,
    val x1: Float,
    val baseline: Float,
    val fontSize: Float,
    val bold: Boolean
)
