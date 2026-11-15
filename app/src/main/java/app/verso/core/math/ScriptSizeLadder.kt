package app.verso.core.math

import kotlin.math.abs

/**
 * TeX's script-size ladder.
 *
 * This is the second thing a hand-rolled renderer usually gets wrong. It is tempting to
 * scale a base font by the font's `scriptPercentScaleDown` (70% in Latin Modern), but TeX
 * does not multiply — it **drops to the next physical type size** and stops at a floor.
 *
 * Measured proof from real `pdflatex` output for this codebase: at a 12pt base the
 * superscript `2` of `x^2` is rendered in the **8pt face** (`LMMathSymbols8`), giving a
 * measured glyph-height ratio of **0.663**. The 70% figure would give 8.4pt (ratio 0.700)
 * and every script would sit 0.4pt too large.
 *
 * The ladder below is TeX's own size table: from a 10pt, 11pt or 12pt text size the
 * script sizes are 7/8/8 and scriptscript 5/6/6 respectively; sizes at or above 10pt always
 * land on the same 7/5 pair for script/scriptscript when set from 10pt, and the 11pt and
 * 12pt classes use 8 and 6.
 */
object ScriptSizeLadder {

    /** Absolute point sizes by (text size, script level). */
    private val ladder: Map<Int, Pair<Float, Float>> = mapOf(
        5 to (5f to 5f),
        6 to (5f to 5f),
        7 to (5f to 5f),
        8 to (6f to 6f),
        9 to (6f to 6f),
        10 to (7f to 5f),
        11 to (8f to 6f),
        12 to (8f to 6f),
        14 to (10f to 8f),
        17 to (12f to 10f),
        20 to (14f to 12f),
        25 to (18f to 15f),
    )

    /**
     * The point size to use for a script at [level] when the surrounding text is set at
     * [basePt].
     *
     * Levels beyond 2 are clamped to scriptscript, matching TeX's floor: it never gets
     * smaller than the smallest available face.
     */
    fun scriptSizePt(basePt: Float, level: MathStyleLevel): Float = when (level) {
        MathStyleLevel.Display, MathStyleLevel.Text -> basePt
        MathStyleLevel.Script -> pick(basePt).first
        MathStyleLevel.ScriptScript -> pick(basePt).second
    }

    /**
     * The ratio to scale by, for callers that scale a single face rather than swapping
     * faces. Slightly less accurate than swapping faces but matches TeX's chosen size.
     */
    fun scriptScale(basePt: Float, level: MathStyleLevel): Float {
        val size = scriptSizePt(basePt, level)
        return if (basePt <= 0f) 1f else size / basePt
    }

    /** The next style level down, as TeX applies it inside scripts. */
    fun descend(level: MathStyleLevel): MathStyleLevel = when (level) {
        MathStyleLevel.Display, MathStyleLevel.Text -> MathStyleLevel.Script
        MathStyleLevel.Script, MathStyleLevel.ScriptScript -> MathStyleLevel.ScriptScript
    }

    /**
     * Choose the ladder entry for [basePt]. Sizes between table entries take the *next
     * smaller* entry, because TeX's size rules never promote a script above its class.
     */
    private fun pick(basePt: Float): Pair<Float, Float> {
        val sizes = ladder.keys.sorted()
        val exact = sizes.firstOrNull { abs(it - basePt) < 0.01f }
        if (exact != null) return ladder.getValue(exact)
        // Largest ladder entry not exceeding basePt; fall back to the smallest.
        val below = sizes.lastOrNull { it <= basePt } ?: sizes.first()
        return ladder.getValue(below)
    }
}
