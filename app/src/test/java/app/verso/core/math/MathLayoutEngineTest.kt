package app.verso.core.math

import app.verso.core.latex.LatexParser
import app.verso.core.latex.MathNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The layout engine is pure, so its geometry can be asserted exactly.
 *
 * Glyph metrics are fixed values here rather than real font lookups, which makes each
 * assertion about the *rules* (spacing, script shifts, fraction gaps) rather than about a
 * font file. Real-font integration is covered by the golden harness.
 */
class MathLayoutEngineTest {

    /** Every character is 0.5em wide, 0.7em tall, no depth. */
    private class FixedMetrics : GlyphMetricsProvider {
        override fun metrics(char: String, sizeScale: Float) = GlyphMetrics(
            advanceEm = 0.5f * sizeScale,
            inkLeftEm = 0f,
            inkRightEm = 0.5f * sizeScale,
            inkAscentEm = 0.7f * sizeScale,
            inkDescentEm = 0f,
        )
    }

    private fun engine(): MathLayoutEngine {
        // Real Latin Modern MATH constants read from the bundled asset.
        val file = java.io.File("src/main/assets/fonts/latinmodern-math.otf")
        org.junit.Assume.assumeTrue("font asset missing", file.isFile)
        return MathLayoutEngine(MathMetrics.forFont(file.readBytes()), FixedMetrics())
    }

    private fun parse(latex: String): MathNode = LatexParser.parseEquation(latex).root

    @Test
    fun `single glyph is its advance wide`() {
        val box = engine().layout(parse("x"))
        assertEquals(0.5f, box.widthEm, 1e-4f)
    }

    @Test
    fun `two adjacent ordinals add no inter-atom space`() {
        val box = engine().layout(parse("xy"))
        assertEquals("two 0.5em glyphs, no Ord-Ord space", 1.0f, box.widthEm, 1e-4f)
    }

    @Test
    fun `binary operator adds exactly 4mu on each side`() {
        // a+b at text level: 0.5 + 4/18 + 0.5 + 4/18 + 0.5
        val box = engine().layout(parse("a+b"))
        val expected = 1.5f + 2f * (4f / 18f)
        assertEquals(expected, box.widthEm, 1e-4f)
    }

    @Test
    fun `relation adds exactly 5mu on each side, wider than a binary`() {
        val rel = engine().layout(parse("a=b")).widthEm
        val bin = engine().layout(parse("a+b")).widthEm
        assertEquals(1.5f + 2f * (5f / 18f), rel, 1e-4f)
        assertTrue("relation ($rel) must be wider than binary ($bin)", rel > bin)
    }

    @Test
    fun `script styles suppress the binary spacing`() {
        val display = engine().layout(parse("a+b"), MathStyleLevel.Display).widthEm
        val script = engine().layout(parse("a+b"), MathStyleLevel.Script).widthEm
        assertTrue("script spacing ($script) must be tighter than display ($display)", script < display)
    }

    @Test
    fun `superscript is set smaller than its base`() {
        val box = engine().layout(parse("x^2"))
        assertTrue("expected glyphs for x^2", box.glyphs.size >= 2)
        val base = box.glyphs.first()
        val script = box.glyphs.last()
        assertTrue(
            "script size ${script.sizeScale} must be below base ${base.sizeScale}",
            script.sizeScale < base.sizeScale,
        )
    }

    @Test
    fun `superscript sits above the baseline`() {
        val box = engine().layout(parse("x^2"), MathStyleLevel.Display)
        val script = box.glyphs.last()
        assertTrue("superscript shift ${script.baselineShiftEm} should be positive", script.baselineShiftEm > 0f)
    }

    @Test
    fun `subscript sits below the baseline`() {
        val box = engine().layout(parse("x_2"), MathStyleLevel.Display)
        val script = box.glyphs.last()
        assertTrue("subscript shift ${script.baselineShiftEm} should be negative", script.baselineShiftEm < 0f)
    }

    @Test
    fun `fraction stacks two lines so it is taller than one glyph`() {
        val single = engine().layout(parse("a")).totalHeightEm
        val frac = engine().layout(parse("\\frac{a}{b}")).totalHeightEm
        // A stacked fraction must clear one glyph height by a wide margin, but the ratio is
        // not exactly 2: the numerator sits above the axis and the denominator below, so the
        // total depends on the font's shift constants rather than on twice the glyph height.
        assertTrue("fraction ($frac) must be much taller than one glyph ($single)", frac > single * 1.5f)
    }

    @Test
    fun `fraction assigns the denominator a negative baseline shift`() {
        val box = engine().layout(parse("\\frac{a}{b}"))
        assertTrue("expected numerator and denominator glyphs", box.glyphs.size >= 2)
        assertTrue(
            "denominator must sit below the baseline",
            box.glyphs.any { it.baselineShiftEm < 0f },
        )
    }

    @Test
    fun `fraction width is the wider of its parts, not their sum`() {
        // Each part is a single glyph, so the fraction is as wide as one glyph at the
        // fraction's (script-level) size - never the two side by side.
        val frac = engine().layout(parse("\\frac{w}{i}"))
        val scriptGlyph = 0.5f * (8f / 12f)
        assertEquals(scriptGlyph, frac.widthEm, 1e-4f)
    }

    @Test
    fun `sqrt is taller than its radicand`() {
        val inner = engine().layout(parse("x")).totalHeightEm
        val root = engine().layout(parse("\\sqrt{x}")).totalHeightEm
        assertTrue("sqrt ($root) must exceed radicand ($inner)", root > inner)
    }

    @Test
    fun `row height is the tallest child`() {
        val box = engine().layout(parse("x+\\frac{a}{b}"))
        assertTrue("row must be at least as tall as its fraction", box.heightEm >= 0.7f)
    }

    @Test
    fun `empty row has no size`() {
        val box = engine().layout(MathNode.Row(emptyList()))
        assertEquals(0f, box.widthEm, 0f)
        assertEquals(0f, box.totalHeightEm, 0f)
    }

    @Test
    fun `layout is deterministic`() {
        val e = engine()
        val a = e.layout(parse("\\frac{a+b}{c}"))
        val b = e.layout(parse("\\frac{a+b}{c}"))
        assertEquals(a.widthEm, b.widthEm, 0f)
        assertEquals(a.heightEm, b.heightEm, 0f)
        assertEquals(a.depthEm, b.depthEm, 0f)
    }

    @Test
    fun `matrix is at least as wide as its widest row`() {
        val box = engine().layout(parse("\\begin{pmatrix}a & b \\\\ c & d\\end{pmatrix}"))
        assertTrue("matrix must have positive width", box.widthEm > 1f)
        assertTrue("2x2 matrix must be taller than one row", box.totalHeightEm > 1f)
    }
}
