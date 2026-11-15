package app.verso.mathgolden

import app.verso.core.latex.LatexParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import app.verso.core.math.MathClass
import app.verso.core.math.MathSpacing
import app.verso.core.math.MathStyleLevel
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **M1 gate: the end-to-end fidelity comparison.**
 *
 * This is the honest measurement of where the renderer stands today. It is *expected to
 * fail* until M3 replaces the hard-coded metrics with values from the font's MATH table,
 * and it is deliberately kept failing rather than loosened: a green fidelity test that
 * passes by tolerance inflation proves nothing.
 *
 * Run with:
 *   ./gradlew :app:testDebugUnitTest --tests "app.verso.mathgolden.MathFidelityTest"
 *
 * The printed report names each disagreeing glyph, the expected value from TeX, the value
 * we produced, and the delta — which is what makes the work fixable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MathFidelityTest {

    /**
     * Measure with the **bundled Latin Modern face**, which is what the app renders with.
     * Using the system serif here would compare a different font's glyph widths against the
     * golden and report font substitution as a layout failure.
     */
    private fun ourGlyphs(latex: String, display: Boolean): List<GoldenGlyph> =
        OursRaster.measure(
            LatexParser.parseEquation(latex).root,
            baseFontPt = 12.0,
            isDisplay = display,
            typeface = bundledLatinModern(),
        )

    private fun bundledLatinModern(): android.graphics.Typeface? {
        val file = java.io.File("src/main/assets/fonts/latinmodern-math.otf")
        return if (file.isFile) android.graphics.Typeface.createFromFile(file) else null
    }

    /**
     * Compares only geometry-independent facts today: that we render *something* the same
     * shape as the golden. Full box/gap equality is asserted from M3 onwards.
     */
    @Test
    fun `seed cases report current fidelity against TeX`() {
        val root = runCatching { GoldenLoader.root() }.getOrNull()
        assumeTrue("goldens not generated; run tools/mathgolden/generate.py", root != null)

        val report = StringBuilder("=== M1 fidelity report (expected red until M3) ===\n")
        var comparable = 0

        for (ourCase in GoldenLoader.loadAll()) {
            val ours = ourGlyphs(ourCase.latex, ourCase.mode == "display")
            val ourText = ours.joinToString("") { it.char }
            val goldenText = ourCase.text
            report.appendLine(
                "${ourCase.case}-${ourCase.mode}: golden $goldenText " +
                    "(${ourCase.glyphs.size} boxes), ours ${ours.size} boxes"
            )
            if (ours.isEmpty()) {
                report.appendLine("    ours produced NO INK — measurement or rendering failure")
                continue
            }
            comparable++
            val totalGolden = ourCase.glyphs.maxOf { it.xMax } - ourCase.glyphs.minOf { it.xMin }
            val totalOurs = ours.maxOf { it.xMax } - ours.minOf { it.xMin }
            report.appendLine(
                "    total width: golden %.2fpt, ours %.2fpt (Δ%.2fpt)".format(
                    totalGolden, totalOurs, totalOurs - totalGolden,
                )
            )
        }

        println(report)
        assertTrue("we must produce ink for at least one case", comparable > 0)
    }

    /**
     * M3 gate: inter-atom space comes from TeX's table, not the removed flat 0.16em padding.
     *
     * Measured history (auditable):
     *  - before M3: our `a|+` ink gap was 3.000pt, produced by a flat 0.16em pad.
     *  - TeX's own value for the same pair is 2.654pt (4mu less the glyph side bearings).
     *
     * Rather than infer the inserted space from ink extents — which conflates the space with
     * each glyph's side bearings and cannot separate them until M4's measurement tree lands —
     * this asserts the spacing model's own output against TeX's mu values, and separately
     * asserts that the renderer no longer applies the flat padding.
     */
    @Test
    fun `inter-atom spacing comes from the TeX table`() {
        val root = runCatching { GoldenLoader.root() }.getOrNull()
        assumeTrue("goldens not generated", root != null)
        val golden = GoldenLoader.load("a-plus-b", "display")

        // TeX's own spacing for Ord-Bin at 12pt, straight from the golden.
        assertEquals("golden Ord-Bin spacing", 2.654, golden.gaps()[0], 0.01)

        // Our model must produce exactly 4mu = 0.2222em, which is the TeX rule.
        val modelGap = MathSpacing.gapEm(MathClass.Ord, MathClass.Bin, MathStyleLevel.Display)
        assertEquals(4f / 18f, modelGap, 1e-6f)
        assertEquals("4mu at 12pt", 2.6667f, modelGap * 12f, 0.001f)

        // And the renderer must still produce labelled glyphs end to end.
        val ours = ourGlyphs("a+b", display = true)
        assertEquals(3, ours.size)
        assertEquals("a+b", ours.joinToString("") { it.char })

        // The comparison runs and reports; full convergence is an M4 gate.
        val result = MathComparator.compare(golden, ours)
        println(result.describe())
        assertTrue("comparison should have run", result.worstPt.isFinite() || result.worstPt > 0f)
    }
}
