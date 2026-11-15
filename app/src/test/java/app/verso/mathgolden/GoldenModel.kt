package app.verso.mathgolden

/**
 * A glyph box read from a TeX golden, in TeX points.
 *
 * `char` may be more than one character, because `pdftotext -bbox` groups glyph runs
 * into words. A multi-character entry is still meaningful for *gap* comparison, which is
 * what the spacing work depends on.
 */
data class GoldenGlyph(
    val char: String,
    val xMin: Double,
    val yMin: Double,
    val xMax: Double,
    val yMax: Double,
) {
    val width: Double get() = xMax - xMin
    val height: Double get() = yMax - yMin

    /** Bottom edge is closer to the baseline than the top for TeX output. */
    val baselineProxy: Double get() = yMax
}

/**
 * One compiled reference case: either display (`$\displaystyle …$`) or inline (`$…$`).
 *
 * All coordinates are TeX points relative to the cropped page produced by the `preview`
 * package, so they are directly comparable between cases and independent of screen
 * density.
 */
data class GoldenCase(
    val case: String,
    val mode: String,
    val latex: String,
    val baseFontPt: Double,
    val rasterDpi: Int,
    val pageWidth: Double,
    val pageHeight: Double,
    val glyphs: List<GoldenGlyph>,
) {
    /** Gap between each glyph and the next, in TeX points. TeX's inter-atom spacing. */
    fun gaps(): List<Double> = glyphs.zipWithNext { a, b -> b.xMin - a.xMax }

    val text: String get() = glyphs.joinToString("") { it.char }
}

/** Failure modes that should stop the harness loudly instead of silently passing. */
class GoldenLoadException(message: String) : Exception(message)
