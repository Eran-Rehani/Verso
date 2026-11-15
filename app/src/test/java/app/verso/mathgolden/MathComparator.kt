package app.verso.mathgolden

/**
 * Tolerances for the fidelity comparison, in TeX points.
 *
 * Boxes and gaps are the primary signal (see the design): pixel diffs alone mask
 * systematic sub-pixel spacing errors and cannot say *which* gap was wrong.
 *
 * These start deliberately loose so the harness can be proven working, then get
 * tightened once we know how much of the gap is rendering-pipeline noise on our side.
 */
data class MathTolerance(
    /** How far a glyph's left edge may differ from the golden. */
    val edgesPt: Double = 0.30,
    /** How far a glyph's width may differ. */
    val widthPt: Double = 0.30,
    /** How far forward-advance (inter-glyph gap) may differ. */
    val gapPt: Double = 0.30,
)

/** One measured disagreement, phrased so it can be fixed without opening an image. */
data class Discrepancy(val message: String, val deltaPt: Double)

/**
 * Result of comparing our render against a TeX golden.
 *
 * `worst` is the largest absolute deviation found, which is the number to report when a
 * milestone claims "within tolerance".
 */
data class MatchReport(
    val case: String,
    val mode: String,
    val passed: Boolean,
    val worstPt: Double,
    val discrepancies: List<Discrepancy>,
) {
    /** Readable, deterministic text used in assertion messages. */
    fun describe(): String = buildString {
        appendLine("Golden mismatch: ${case}-$mode (worst ${"%.3f".format(worstPt)}pt)")
        if (discrepancies.isEmpty()) {
            appendLine("  no discrepancies")
        } else {
            discrepancies.take(12).forEach { appendLine("  - ${it.message}") }
            if (discrepancies.size > 12) {
                appendLine("  … ${discrepancies.size - 12} more")
            }
        }
    }
}

object MathComparator {

    /**
     * Compare a golden against our side's glyph boxes, both in TeX points.
     *
     * Both sides are normalized against their own first glyph and its baseline before
     * comparison. Absolute positions are meaningless across the two pipelines — the
     * golden is cropped by `preview`, ours is drawn into a buffer with margin — and what
     * actually matters for typesetting is *relative* placement: how each glyph is
     * positioned and spaced with respect to the previous one.
     */
    fun compare(
        golden: GoldenCase,
        oursRaw: List<GoldenGlyph>,
        tolerance: MathTolerance = MathTolerance(),
    ): MatchReport {
        val discrepancies = mutableListOf<Discrepancy>()
        var worst = 0.0

        fun record(delta: Double, message: String) {
            val abs = kotlin.math.abs(delta)
            if (abs > worst) worst = abs
            discrepancies += Discrepancy(message, abs)
        }

        // Text agreement first: if we rendered different characters, box deltas are noise.
        val goldenText = golden.text
        val ourText = oursRaw.joinToString("") { it.char }
        if (goldenText != ourText) {
            discrepancies += Discrepancy(
                "text differs: golden '${escape(goldenText)}' vs ours '${escape(ourText)}'",
                Double.POSITIVE_INFINITY,
            )
            return MatchReport(
                case = golden.case,
                mode = golden.mode,
                passed = false,
                worstPt = Double.POSITIVE_INFINITY,
                discrepancies = discrepancies,
            )
        }

        if (oursRaw.size != golden.glyphs.size) {
            discrepancies += Discrepancy(
                "glyph count differs: golden ${golden.glyphs.size} vs ours ${oursRaw.size}",
                Double.POSITIVE_INFINITY,
            )
            return MatchReport(golden.case, golden.mode, false, Double.POSITIVE_INFINITY, discrepancies)
        }

        val ours = normalize(oursRaw)
        val gold = normalize(golden.glyphs, baselineFrom = golden.glyphs.firstOrNull()?.baselineProxy ?: 0.0)

        gold.forEachIndexed { index, g ->
            val o = ours[index]
            val edge = o.xMin - g.xMin
            if (kotlin.math.abs(edge) > tolerance.edgesPt) {
                record(edge, "glyph[${index}] '${escape(g.char)}' xMin expected " +
                    "${fmt(g.xMin)}pt, got ${fmt(o.xMin)}pt (Δ${fmt(edge)}pt > ${fmt(tolerance.edgesPt)}pt)")
            }
            val width = o.width - g.width
            if (kotlin.math.abs(width) > tolerance.widthPt) {
                record(width, "glyph[${index}] '${escape(g.char)}' width expected " +
                    "${fmt(g.width)}pt, got ${fmt(o.width)}pt (Δ${fmt(width)}pt > ${fmt(tolerance.widthPt)}pt)")
            }
            val height = o.height - g.height
            if (kotlin.math.abs(height) > tolerance.widthPt) {
                record(height, "glyph[${index}] '${escape(g.char)}' height expected " +
                    "${fmt(g.height)}pt, got ${fmt(o.height)}pt (Δ${fmt(height)}pt > ${fmt(tolerance.widthPt)}pt)")
            }
            val baseline = o.baselineProxy - g.baselineProxy
            if (kotlin.math.abs(baseline) > tolerance.edgesPt) {
                record(baseline, "glyph[${index}] '${escape(g.char)}' baseline shift expected " +
                    "${fmt(g.baselineProxy)}pt, got ${fmt(o.baselineProxy)}pt (Δ${fmt(baseline)}pt)")
            }
        }

        val goldenGaps = gold.zipWithNext { a, b -> b.xMin - a.xMax }
        val ourGaps = ours.zipWithNext { a, b -> b.xMin - a.xMax }
        goldenGaps.forEachIndexed { index, expected ->
            val actual = ourGaps[index]
            val delta = actual - expected
            if (kotlin.math.abs(delta) > tolerance.gapPt) {
                val left = escape(gold[index].char)
                val right = escape(gold[index + 1].char)
                record(delta, "gap after '$left' before '$right' expected ${fmt(expected)}pt, " +
                    "got ${fmt(actual)}pt (Δ${fmt(delta)}pt > ${fmt(tolerance.gapPt)}pt)")
            }
        }

        return MatchReport(
            case = golden.case,
            mode = golden.mode,
            passed = discrepancies.isEmpty(),
            worstPt = worst,
            discrepancies = discrepancies,
        )
    }

    /**
     * Re-express boxes relative to the first glyph's left edge and the common baseline.
     *
     * The baseline is taken from the glyph with the largest bottom edge, which for TeX
     * output is a full-height glyph sitting on the baseline; superscripts and numerators
     * then carry a meaningful positive/negative offset.
     */
    private fun normalize(
        glyphs: List<GoldenGlyph>,
        baselineFrom: Double? = null,
    ): List<GoldenGlyph> {
        if (glyphs.isEmpty()) return glyphs
        val originX = glyphs.first().xMin
        val baseline = baselineFrom ?: glyphs.maxOf { it.baselineProxy }
        return glyphs.map {
            it.copy(
                xMin = it.xMin - originX,
                xMax = it.xMax - originX,
                yMin = it.yMin - baseline,
                yMax = it.yMax - baseline,
            )
        }
    }

    private fun fmt(v: Double): String = "%.3f".format(v)

    private fun escape(s: String): String = s.replace("\\", "\\\\")
}
