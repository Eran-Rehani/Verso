package app.verso.core.math

/**
 * Turns the font's raw design-unit `MathConstants` into em-relative layout values.
 *
 * `MathConstants` are in font design units (typically 1000 per em for Latin Modern), which
 * are useless to a renderer until divided by the units-per-em. Everything here returns
 * **em** or **ratio** values so layout code multiplies by its current font size and nothing
 * has to know about design units.
 *
 * Before this existed the renderer used literals such as `0.78` for the fraction child
 * scale and `-0.16` for the denominator shift; those were guesses at exactly these numbers.
 */
class MathMetrics(
    private val constants: MathConstants,
    private val unitsPerEm: Int,
) {

    private fun em(designUnits: Int): Float = designUnits.toFloat() / unitsPerEm.toFloat()

    /** Height of the math axis above the baseline (where `+`, `=` and the fraction bar sit). */
    val axisHeight: Float get() = em(constants.axisHeight)

    /** Thickness of a fraction bar / radical rule. */
    val ruleThickness: Float get() = em(constants.fractionRuleThickness)

    /** How far the numerator sits above the axis. */
    fun numeratorShiftUp(display: Boolean): Float =
        em(
            if (display) {
                constants.fractionNumeratorDisplayStyleShiftUp
            } else {
                constants.fractionNumeratorShiftUp
            }
        )

    /** How far the denominator sits below the axis. */
    fun denominatorShiftDown(display: Boolean): Float =
        em(
            if (display) {
                constants.fractionDenominatorDisplayStyleShiftDown
            } else {
                constants.fractionDenominatorShiftDown
            }
        )

    /** Minimum gap between numerator ink and the fraction bar. */
    fun numeratorGap(display: Boolean): Float =
        em(
            if (display) {
                constants.fractionNumDisplayStyleGapMin
            } else {
                constants.fractionNumeratorGapMin
            }
        )

    /** Minimum gap between the fraction bar and denominator ink. */
    fun denominatorGap(display: Boolean): Float =
        em(
            if (display) {
                constants.fractionDenomDisplayStyleGapMin
            } else {
                constants.fractionDenominatorGapMin
            }
        )

    /** Standard shift up for superscripts. */
    fun superscriptShiftUp(cramped: Boolean = false): Float =
        em(if (cramped) constants.superscriptShiftUpCramped else constants.superscriptShiftUp)

    /** Standard shift down for subscripts. */
    val subscriptShiftDown: Float get() = em(constants.subscriptShiftDown)

    /** Minimum gap between superscript and subscript ink. */
    val subSuperscriptGapMin: Float get() = em(constants.subSuperscriptGapMin)

    /** Space to add after a script, before the next atom. */
    val spaceAfterScript: Float get() = em(constants.spaceAfterScript)

    /** Gap above the radical's ink. */
    fun radicalGap(display: Boolean): Float =
        em(
            if (display) {
                constants.radicalDisplayStyleVerticalGap
            } else {
                constants.radicalVerticalGap
            }
        )

    /** Thickness of the radical rule. */
    val radicalRuleThickness: Float get() = em(constants.radicalRuleThickness)

    /** Height above which an accent must be flattened rather than raised. */
    val flattenedAccentBaseHeight: Float get() = em(constants.flattenedAccentBaseHeight)

    /** Height of the base up to which an accent needs no extra raising. */
    val accentBaseHeight: Float get() = em(constants.accentBaseHeight)

    /** Gap between an overbar and the ink below it. */
    val overbarVerticalGap: Float get() = em(constants.overbarVerticalGap)

    /** Thickness of an overbar/underbar. */
    val overbarRuleThickness: Float get() = em(constants.overbarRuleThickness)

    /** Extra ascender reserved above an overbar. */
    val overbarExtraAscender: Float get() = em(constants.overbarExtraAscender)

    /** Minimal height for a delimited expression to be treated as a sub-formula. */
    val delimitedSubFormulaMinHeight: Float get() = em(constants.delimitedSubFormulaMinHeight)

    /** Minimum height of an n-ary operator in display style. */
    val displayOperatorMinHeight: Float get() = em(constants.displayOperatorMinHeight)

    /** Scale for level-1 scripts, as a ratio (0.70 in Latin Modern). */
    val scriptPercentScaleDown: Float get() = constants.scriptPercentScaleDown / 100f

    companion object {
        /**
         * Build metrics for [font]. Uses the font's own units-per-em so a font designed at
         * 2048 upem produces the same em values as one at 1000.
         */
        fun forFont(font: ByteArray): MathMetrics {
            val table = MathTable.parse(font)
            val upem = FontHead.unitsPerEm(font)?: 1000
            return MathMetrics(table.constants, upem)
        }
    }
}

/** Reads `head.unitsPerEm`, which all math layout values are relative to. */
object FontHead {
    fun unitsPerEm(font: ByteArray): Int? {
        val offset = runCatching { BigEndianReader(font).findTableOffset("head") }.getOrNull()
            ?: return null
        // head layout, all offsets from the table start:
        //   0 version(4), 4 fontRevision(4), 8 checkSumAdjustment(4), 12 magicNumber(4),
        //   16 flags(2), 18 unitsPerEm(2).
        val reader = BigEndianReader(font).also { it.seek(offset + 18) }
        val unitsPerEm = reader.u16()
        return if (unitsPerEm > 0) unitsPerEm else null
    }
}
