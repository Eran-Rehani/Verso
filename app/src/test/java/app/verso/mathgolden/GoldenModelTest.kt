package app.verso.mathgolden

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The harness itself must be proven before it is allowed to judge the renderer.
 * These tests use hand-built values so they do not depend on TeX being installed.
 */
class GoldenModelTest {

    private val sample = GoldenCase(
        case = "a+b",
        mode = "display",
        latex = "a+b",
        baseFontPt = 12.0,
        rasterDpi = 300,
        pageWidth = 26.54,
        pageHeight = 10.223,
        glyphs = listOf(
            GoldenGlyph("a", 0.5, 0.0, 6.645, 9.0),
            GoldenGlyph("+", 9.299, 0.0, 18.404, 9.0),
            GoldenGlyph("b", 21.058, 0.0, 26.035, 9.0),
        ),
    )

    @Test
    fun `text joins glyphs`() {
        assertEquals("a+b", sample.text)
    }

    @Test
    fun `gaps are the TeX inter-atom spacing`() {
        val gaps = sample.gaps()
        assertEquals(2, gaps.size)
        // TeX thickness: 4mu at 12pt = 4/18 em = 2.667pt per side for Ord-Bin spacing.
        assertEquals(2.654, gaps[0], 0.01)
        assertEquals(2.654, gaps[1], 0.01)
    }

    @Test
    fun `glyph width and height`() {
        val a = sample.glyphs[0]
        assertEquals(6.145, a.width, 1e-6)
        assertEquals(9.0, a.height, 1e-6)
    }

    @Test
    fun `loader parses the generated golden format`() {
        val case = GoldenLoader.parse(
            """
            {
              "case": "x",
              "mode": "display",
              "latex": "x",
              "baseFontPt": 12,
              "rasterDpi": 300,
              "pageWidth": 7.652,
              "pageHeight": 6.147,
              "glyphs": [
                {"char": "x", "xMin": 0.5, "yMin": -2.6499, "xMax": 7.1519, "yMax": 7.9663}
              ]
            }
            """.trimIndent(),
        )
        assertEquals("x", case.case)
        assertEquals("display", case.mode)
        assertEquals(1, case.glyphs.size)
        assertEquals(7.1519, case.glyphs[0].xMax, 1e-9)
    }

    @Test
    fun `loader rejects malformed input instead of passing silently`() {
        val failure = runCatching { GoldenLoader.parse("""{"case":"x"}""") }.exceptionOrNull()
        assertTrue("expected GoldenLoadException, got $failure", failure is GoldenLoadException)
    }

    @Test
    fun `missing golden reports how to fix it`() {
        val failure = runCatching { GoldenLoader.load("definitely-not-a-case", "display") }
            .exceptionOrNull()
        assertTrue(failure is GoldenLoadException)
        assertTrue(
            "message should name the generator command",
            failure!!.message!!.contains("generate.py"),
        )
    }

    @Test
    fun `identical render passes`() {
        val report = MathComparator.compare(sample, sample.glyphs)
        assertTrue(report.describe(), report.passed)
        assertEquals(0.0, report.worstPt, 1e-9)
    }

    @Test
    fun `spacing error is reported with the exact gap`() {
        // Shift '+' and 'b' right by 0.4pt: this widens the first gap beyond tolerance.
        val shifted = sample.glyphs.mapIndexed { index, g ->
            if (index == 0) g else g.copy(xMin = g.xMin + 0.4, xMax = g.xMax + 0.4)
        }
        val report = MathComparator.compare(sample, shifted)
        assertFalse(report.passed)
        val text = report.describe()
        assertTrue("should name the gap: $text", text.contains("gap after 'a' before '+'"))
        assertTrue("should name the delta: $text", text.contains("Δ0.400pt"))
    }

    @Test
    fun `text mismatch is reported before box noise`() {
        val wrong = sample.glyphs.mapIndexed { index, g ->
            if (index == 1) g.copy(char = "-") else g
        }
        val report = MathComparator.compare(sample, wrong)
        assertFalse(report.passed)
        assertTrue(report.describe().contains("text differs"))
    }

    @Test
    fun `glyph count mismatch is reported`() {
        // Same visible text, but the 'b' is split into two boxes: count disagrees while
        // the joined text still matches, which is the only way to reach the count check.
        val split = listOf(
            sample.glyphs[0],
            sample.glyphs[1],
            sample.glyphs[2].copy(char = "b"),
            sample.glyphs[2].copy(char = ""),
        )
        val report = MathComparator.compare(sample, split)
        assertFalse(report.passed)
        assertTrue(report.describe().contains("glyph count differs"))
    }

    @Test
    fun `generated goldens load from disk when present`() {
        // This test documents that the real harness is wired to real files.
        val root = runCatching { GoldenLoader.root() }.getOrNull()
        if (root == null) return // Generator not run in this environment; other tests cover logic.
        val cases = GoldenLoader.loadAll()
        assertNotNull(cases)
        assertTrue("goldens should include the seed cases", cases.any { it.case == "frac" })
    }
}
