package app.verso.core.math

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Metrics must be em-relative and agree with the font's own design units, because every
 * gap the renderer applies is multiplied by the current font size.
 */
class MathMetricsTest {

    private fun latinModernMetrics(): MathMetrics {
        val file = File("src/main/assets/fonts/latinmodern-math.otf")
        assumeTrue("font asset missing", file.isFile)
        return MathMetrics.forFont(file.readBytes())
    }

    @Test
    fun `units per em is read from head`() {
        val file = File("src/main/assets/fonts/latinmodern-math.otf")
        assumeTrue("font asset missing", file.isFile)
        assertEquals(1000, FontHead.unitsPerEm(file.readBytes()))
    }

    @Test
    fun `axis height is a fraction of an em`() {
        val axis = latinModernMetrics().axisHeight
        // Axis height 250/1000 = 0.25em for Latin Modern.
        assertEquals(0.25f, axis, 1e-4f)
        assertTrue("axis height should be well under an em, was $axis", axis < 1f)
    }

    @Test
    fun `rule thickness is small but non-zero`() {
        val thickness = latinModernMetrics().ruleThickness
        assertTrue("rule thickness should be positive, was $thickness", thickness > 0f)
        assertTrue("rule thickness should be under 0.15em, was $thickness", thickness < 0.15f)
    }

    @Test
    fun `display gaps are larger than text gaps`() {
        val m = latinModernMetrics()
        assertTrue(
            "display numerator gap ${m.numeratorGap(true)} should exceed text ${m.numeratorGap(false)}",
            m.numeratorGap(true) >= m.numeratorGap(false),
        )
        assertTrue(
            "display denominator gap should exceed text gap",
            m.denominatorGap(true) >= m.denominatorGap(false),
        )
    }

    @Test
    fun `script percent is exposed as a ratio`() {
        assertEquals(0.70f, latinModernMetrics().scriptPercentScaleDown, 1e-4f)
    }

    @Test
    fun `all em values are plausibly small`() {
        val m = latinModernMetrics()
        for ((name, value) in listOf(
            "axisHeight" to m.axisHeight,
            "ruleThickness" to m.ruleThickness,
            "superscriptShiftUp" to m.superscriptShiftUp(),
            "subscriptShiftDown" to m.subscriptShiftDown,
            "radicalRuleThickness" to m.radicalRuleThickness,
            "overbarRuleThickness" to m.overbarRuleThickness,
        )) {
            assertTrue("$name ($value) should be a sane fraction of an em", value > -2f && value < 2f)
        }
    }
}
