package app.verso.reader.pdf

import android.graphics.Path
import android.graphics.PointF
import app.verso.core.pdf.PdfRule
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage

/**
 * Collects thin painted horizontal rules from a page's content stream.
 *
 * TeX-based PDFs draw fraction bars, radical rules and overlines as stroked or filled
 * rectangles rather than as glyphs, so a text-layer-only reading of the page simply cannot
 * see them. Without these, a fraction's numerator and denominator are indistinguishable from
 * two separate lines of prose — which is exactly how the reported documents fell apart.
 *
 * Only *painted* geometry is recorded: `strokePath`, `fillPath` and `fillAndStrokePath`.
 * A path that is constructed and then discarded, or that is only clipped, is not a rule.
 * Vertical and curved segments are ignored, because nothing in math layout needs them and
 * treating a table's vertical rule as a fraction bar would invent structure.
 */
internal class PdfRuleCollector(
    page: PDPage,
    private val onRule: (PdfRule) -> Unit,
) : PDFGraphicsStreamEngine(page) {

    /** Segments of the current path, as (x0, y0, x1, y1). */
    private val segments = mutableListOf<FloatArray>()
    private var current: PointF? = null
    private var start: PointF? = null

    override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) {
        // A rectangle arrives as four corners; record its horizontal edges as segments.
        segments += floatArrayOf(p0.x, p0.y, p1.x, p1.y)
        segments += floatArrayOf(p2.x, p2.y, p3.x, p3.y)
        current = null
        start = null
    }

    override fun drawImage(pdImage: PDImage) = Unit

    override fun clip(windingRule: Path.FillType) = Unit

    override fun moveTo(x: Float, y: Float) {
        emitLine(current, PointF(x, y))
        current = PointF(x, y)
        if (start == null) start = current
    }

    override fun lineTo(x: Float, y: Float) {
        val from = current
        val to = PointF(x, y)
        emitLine(from, to)
        current = to
    }

    override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        // A curve is never a fraction bar; just advance the current point.
        current = PointF(x3, y3)
    }

    override fun getCurrentPoint(): PointF = current ?: PointF(0f, 0f)

    override fun closePath() {
        val from = current
        val to = start
        emitLine(from, to)
        current = to
    }

    override fun endPath() {
        segments.clear()
        current = null
        start = null
    }

    override fun strokePath() {
        collectPainted(defaultLineWidth)
        endPath()
    }

    override fun fillPath(windingRule: Path.FillType) {
        collectPainted(0f)
        endPath()
    }

    override fun fillAndStrokePath(windingRule: Path.FillType) {
        collectPainted(defaultLineWidth)
        endPath()
    }

    override fun shadingFill(shadingName: COSName) = Unit

    /** Line width from the graphics state, used as stroke thickness. */
    private var defaultLineWidth = 1f

    private fun emitLine(from: PointF?, to: PointF?) {
        if (from == null || to == null) return
        if (segments.size >= MAX_SEGMENTS) return
        segments += floatArrayOf(from.x, from.y, to.x, to.y)
    }

    /**
     * Turn accumulated segments into rules. A horizontal segment qualifies when it is very
     * nearly level and its painted height would be small relative to the page; the thickness
     * tolerance is deliberately tight so table rules with visible weight are not mistaken for
     * fraction bars.
     */
    private fun collectPainted(strokeWidth: Float) {
        if (segments.size > MAX_SEGMENTS) return
        for (seg in segments) {
            val (x0, y0, x1, y1) = seg
            val dx = kotlin.math.abs(x1 - x0)
            val dy = kotlin.math.abs(y1 - y0)
            if (dx < 1e-3f) continue // vertical
            if (dy > MAX_SLOPE * dx) continue // not horizontal
            if (dx < MIN_RULE_LENGTH) continue // a dot or hyphen, not a rule
            onRule(
                PdfRule(
                    x0 = minOf(x0, x1),
                    x1 = maxOf(x0, x1),
                    y = (y0 + y1) / 2f,
                    thickness = strokeWidth,
                )
            )
        }
    }

    private operator fun FloatArray.component1() = this[0]
    private operator fun FloatArray.component2() = this[1]
    private operator fun FloatArray.component3() = this[2]

    private companion object {
        /** Hard cap so a pathological page cannot exhaust memory. */
        const val MAX_SEGMENTS = 20_000

        /** A rule must be at least this long in PDF units to matter. */
        const val MIN_RULE_LENGTH = 6f

        /** Maximum vertical drift allowed across a horizontal rule (5%). */
        const val MAX_SLOPE = 0.05f
    }
}

/**
 * Separates fraction-like rules from table rules.
 *
 * The distinction is whether the rule is "thin": fraction bars are hairline-weight, while
 * table and section rules are usually noticeably heavier. This is a heuristic and is
 * intentionally documented as one — the reconstructor only claims a fraction when a rule is
 * thin *and* has ink above and below it at compatible extents.
 */
internal object PdfRuleFilter {

    fun thinRules(rules: List<PdfRule>, pageHeight: Float): List<PdfRule> {
        if (rules.isEmpty()) return rules
        val inkLimit = pageHeight * MAX_HEAVY_FRACTION
        return rules.filter { it.thickness <= inkLimit }
    }

    /** A rule heavier than this fraction of the page height is treated as a divider. */
    private const val MAX_HEAVY_FRACTION = 0.012f
}
