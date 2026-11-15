package app.verso.mathgolden

import app.verso.core.latex.LatexParser
import app.verso.core.latex.MathNode
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Proves our side of the comparison can actually be measured.
 *
 * Without this, a "passing" fidelity test could mean the renderer produced nothing at all
 * and the comparison had no boxes to disagree about.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OursRasterTest {

    private fun measure(latex: String, display: Boolean = true) =
        OursRaster.measure(LatexParser.parseEquation(latex).root, baseFontPt = 12.0, isDisplay = display)

    private fun latinModern(): android.graphics.Typeface =
        android.graphics.Typeface.createFromFile(
            File("src/main/assets/fonts/latinmodern-math.otf")
        )

    @Test
    fun `single symbol produces one box with sane size`() {
        val boxes = measure("x")
        assertTrue("expected ink for 'x', got ${boxes.size} boxes", boxes.isNotEmpty())
        val total = boxes.sumOf { it.width }
        assertTrue("glyph width should be positive, was $total", total > 0.5)
        assertTrue("glyph width should be under 2 em, was $total", total < 24.0)
    }

    @Test
    fun `text with several symbols produces ink wider than one glyph`() {
        val one = measure("x").sumOf { it.width }
        val many = measure("x+y+z").sumOf { it.width }
        assertTrue("'x+y+z' ($many) should be wider than 'x' ($one)", many > one)
    }

    @Test
    fun `fraction is wider and taller than a single symbol`() {
        val flat = measure("a")
        val frac = measure("\\frac{a}{b}")
        val flatH = flat.maxOf { it.yMax } - flat.minOf { it.yMin }
        val fracH = frac.maxOf { it.yMax } - frac.minOf { it.yMin }
        assertTrue("fraction ($fracH) should be taller than 'a' ($flatH)", fracH > flatH)
    }

    @Test
    fun `empty-ish node does not crash segmentation`() {
        val boxes = OursRaster.measure(MathNode.Row(emptyList()), baseFontPt = 12.0, isDisplay = true)
        assertTrue("row with no children has no ink", boxes.isEmpty())
    }

    @Test
    fun `measurement is deterministic`() {
        val a = measure("\\frac{a+b}{c}")
        val b = measure("\\frac{a+b}{c}")
        assertTrue(
            "same input must measure identically",
            a.map { it.xMin to it.xMax } == b.map { it.xMin to it.xMax },
        )
    }

    /**
     * M2 gate: with the bundled Latin Modern face the measured advance for 'x' must move
     * toward TeX's 6.65pt, away from the system serif's 7.88pt. If the font were not being
     * applied, this delta would stay at zero.
     */
    @Test
    fun `bundled latin modern face measures narrower than the system serif`() {
        val node = LatexParser.parseEquation("x").root
        val system = OursRaster.measure(node, baseFontPt = 12.0, isDisplay = true)
        val bundled = OursRaster.measure(
            node,
            baseFontPt = 12.0,
            isDisplay = true,
            typeface = latinModern(),
        )
        val systemWidth = system.sumOf { it.width }
        val bundledWidth = bundled.sumOf { it.width }
        println("system serif 'x' = %.3fpt, bundled LM = %.3fpt".format(systemWidth, bundledWidth))
        assertTrue("bundled face must produce ink", bundled.isNotEmpty())
        assertTrue(
            "bundled LM ($bundledWidth) should be narrower than system serif ($systemWidth)",
            bundledWidth < systemWidth,
        )
    }
}
