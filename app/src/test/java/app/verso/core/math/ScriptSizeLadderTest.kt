package app.verso.core.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The script ladder is verified against what TeX actually emits.
 *
 * Real `pdflatex` output for `$x^2$` at 12pt embeds `LMRoman8-Regular` for the superscript —
 * an 8pt face, i.e. the ladder's script size — not a 12pt face scaled to 70%.
 */
class ScriptSizeLadderTest {

    @Test
    fun `12pt base uses 8pt scripts and 6pt scriptscripts`() {
        assertEquals(8f, ScriptSizeLadder.scriptSizePt(12f, MathStyleLevel.Script), 1e-4f)
        assertEquals(6f, ScriptSizeLadder.scriptSizePt(12f, MathStyleLevel.ScriptScript), 1e-4f)
    }

    @Test
    fun `12pt script ratio matches the measured 0_663 not the fonts 0_70`() {
        val ratio = ScriptSizeLadder.scriptScale(12f, MathStyleLevel.Script)
        assertEquals(8f / 12f, ratio, 1e-4f)
        assertTrue(
            "ratio $ratio should be near the measured 0.663, not the font's 0.70",
            kotlin.math.abs(ratio - 0.663f) < 0.01f,
        )
    }

    @Test
    fun `10pt base uses the classic 7pt and 5pt`() {
        assertEquals(7f, ScriptSizeLadder.scriptSizePt(10f, MathStyleLevel.Script), 1e-4f)
        assertEquals(5f, ScriptSizeLadder.scriptSizePt(10f, MathStyleLevel.ScriptScript), 1e-4f)
    }

    @Test
    fun `display and text keep the base size`() {
        assertEquals(12f, ScriptSizeLadder.scriptSizePt(12f, MathStyleLevel.Display), 1e-4f)
        assertEquals(12f, ScriptSizeLadder.scriptSizePt(12f, MathStyleLevel.Text), 1e-4f)
    }

    @Test
    fun `scripts never grow as the base shrinks below the ladder`() {
        val small = ScriptSizeLadder.scriptSizePt(5f, MathStyleLevel.Script)
        assertTrue("small bases must not produce larger scripts, got $small", small <= 5f)
    }

    @Test
    fun `script sizes are monotonic in base size`() {
        var previous = 0f
        for (base in listOf(5f, 7f, 8f, 9f, 10f, 11f, 12f, 14f, 17f, 20f, 25f)) {
            val script = ScriptSizeLadder.scriptSizePt(base, MathStyleLevel.Script)
            assertTrue("script size must not decrease as base grows ($base -> $script)", script >= previous)
            previous = script
        }
    }

    @Test
    fun `interpolated base takes the next smaller entry`() {
        // 13pt is not a TeX size; it must not borrow the 14pt class's 10pt scripts.
        assertEquals(8f, ScriptSizeLadder.scriptSizePt(13f, MathStyleLevel.Script), 1e-4f)
    }

    @Test
    fun `descend walks to the floor`() {
        assertEquals(MathStyleLevel.Script, ScriptSizeLadder.descend(MathStyleLevel.Text))
        assertEquals(MathStyleLevel.Script, ScriptSizeLadder.descend(MathStyleLevel.Display))
        assertEquals(MathStyleLevel.ScriptScript, ScriptSizeLadder.descend(MathStyleLevel.Script))
        assertEquals(MathStyleLevel.ScriptScript, ScriptSizeLadder.descend(MathStyleLevel.ScriptScript))
    }
}
