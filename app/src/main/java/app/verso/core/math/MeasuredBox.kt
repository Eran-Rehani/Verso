package app.verso.core.math

/**
 * A measured math box, in em units.
 *
 * This is the geometry a TeX layout engine needs: width, and the height/depth split at the
 * baseline (TeX's `height` and `depth`). Compose's `Layout` computes equivalent values but
 * only as pixels inside a `MeasureScope`, which cannot be inspected by tests and — critically
 * — made the fidelity harness measure *ink extents* rather than *advances*.
 *
 * Measuring advances rather than ink is what lets a box be compared to TeX fairly: TeX's
 * inter-atom space is inserted between advance boxes, so a glyph's side bearing belongs to
 * the glyph, not to the gap after it.
 */
data class MeasuredBox(
    val widthEm: Float,
    /** Extent above the baseline. */
    val heightEm: Float,
    /** Extent below the baseline. */
    val depthEm: Float,
    /**
     * Positioned glyphs relative to this box's left edge and the baseline, for comparison
     * against a golden's per-glyph boxes and for hit-testing.
     */
    val glyphs: List<PositionedGlyph> = emptyList(),
) {
    /** Total vertical extent, the value delimiter sizing and line breaking care about. */
    val totalHeightEm: Float get() = heightEm + depthEm

    companion object {
        val Empty = MeasuredBox(0f, 0f, 0f)
    }
}

/**
 * One drawn glyph. [xEm] is the advance-box origin, not the ink origin, so consecutive
 * glyphs' origins differ by exactly the advance.
 */
data class PositionedGlyph(
    val char: String,
    /** Left edge of the advance box, relative to the parent box's left edge. */
    val xEm: Float,
    /** Advance width of this glyph. */
    val advanceEm: Float,
    /** Baseline offset from the parent's baseline (positive is up); scripts differ. */
    val baselineShiftEm: Float = 0f,
    /** Ink extents relative to the advance origin, for rendering and hit-testing. */
    val inkLeftEm: Float = 0f,
    val inkRightEm: Float = 0f,
    val inkAscentEm: Float = 0f,
    val inkDescentEm: Float = 0f,
    /** Point size relative to the enclosing style's size. */
    val sizeScale: Float = 1f,
)

/** Glyph metrics for a single character at a given size, in em of that size. */
data class GlyphMetrics(
    val advanceEm: Float,
    val inkLeftEm: Float,
    val inkRightEm: Float,
    val inkAscentEm: Float,
    val inkDescentEm: Float,
)

/**
 * Supplies glyph metrics. Implemented against `Paint` on Android and against fixed values in
 * tests, so the layout engine itself stays free of platform dependencies and can be tested
 * on the JVM.
 */
interface GlyphMetricsProvider {
    /**
     * Metrics for [char] at [sizeScale] × the base style size.
     *
     * Return `null` when the character has no glyph, so callers can fall back rather than
     * silently lay out a missing glyph as zero-width.
     */
    fun metrics(char: String, sizeScale: Float): GlyphMetrics?
}
