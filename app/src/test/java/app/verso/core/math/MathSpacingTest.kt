package app.verso.core.math

import app.verso.core.latex.LatexParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TeX's inter-atom spacing, checked against the values TeX itself produces.
 *
 * At 12pt, 1mu = 12/18 = 0.6667pt, so thin = 2pt, medium = 2.667pt, thick = 3.333pt. The
 * golden generated from real `pdflatex` output confirms the medium value: the `a+b` case
 * measures 2.654pt between `a` and `+`, which is 4mu (2.667pt) less the glyph side bearing.
 */
class MathSpacingTest {

    @Test
    fun `tex mu values are the documented fractions of an em`() {
        assertEquals(1f / 18f, MathSpacing.MU_EM, 1e-7f)
    }

    @Test
    fun `Ord-Ord gets no space`() {
        assertEquals(0f, MathSpacing.gapEm(MathClass.Ord, MathClass.Ord, MathStyleLevel.Display), 0f)
    }

    @Test
    fun `binary operator gets medium space on both sides`() {
        val left = MathSpacing.gapEm(MathClass.Ord, MathClass.Bin, MathStyleLevel.Text)
        val right = MathSpacing.gapEm(MathClass.Bin, MathClass.Ord, MathStyleLevel.Text)
        assertEquals(4f / 18f, left, 1e-7f)
        assertEquals(4f / 18f, right, 1e-7f)
        // 4mu at 12pt = 2.667pt, matching the golden's measured 2.654pt.
        assertEquals(2.667f, left * 12f, 0.02f)
    }

    @Test
    fun `relation gets thick space, wider than a binary operator`() {
        val rel = MathSpacing.gapEm(MathClass.Ord, MathClass.Rel, MathStyleLevel.Text)
        val bin = MathSpacing.gapEm(MathClass.Ord, MathClass.Bin, MathStyleLevel.Text)
        assertEquals(5f / 18f, rel, 1e-7f)
        assertTrue("relations must be wider than binaries: $rel vs $bin", rel > bin)
    }

    @Test
    fun `punctuation takes a thin space AFTER it and none before`() {
        // TeXbook ch.18 / KaTeX spacingData: punct->ord is thin, ord->punct is none.
        assertEquals(
            0f,
            MathSpacing.gapEm(MathClass.Ord, MathClass.Punct, MathStyleLevel.Text),
            1e-7f,
        )
        assertEquals(
            3f / 18f,
            MathSpacing.gapEm(MathClass.Punct, MathClass.Ord, MathStyleLevel.Text),
            1e-7f,
        )
    }

    @Test
    fun `opening fence gets no space after it`() {
        assertEquals(0f, MathSpacing.gapEm(MathClass.Open, MathClass.Ord, MathStyleLevel.Text), 1e-7f)
        assertEquals(0f, MathSpacing.gapEm(MathClass.Ord, MathClass.Open, MathStyleLevel.Text), 1e-7f)
    }

    @Test
    fun `script styles suppress binary and relation spacing`() {
        for ((l, r) in listOf(
            MathClass.Ord to MathClass.Bin,
            MathClass.Ord to MathClass.Rel,
            MathClass.Bin to MathClass.Ord,
        )) {
            assertEquals(
                "script must suppress $l-$r space",
                0f,
                MathSpacing.gapEm(l, r, MathStyleLevel.Script),
                1e-7f,
            )
        }
        // A thin space does survive between an Ord and an Op even in script style.
        assertEquals(
            3f / 18f,
            MathSpacing.gapEm(MathClass.Ord, MathClass.Op, MathStyleLevel.Script),
            1e-7f,
        )
    }

    @Test
    fun `display and text levels agree`() {
        for (l in MathClass.entries) for (r in MathClass.entries) {
            assertEquals(
                MathSpacing.gapEm(l, r, MathStyleLevel.Display),
                MathSpacing.gapEm(l, r, MathStyleLevel.Text),
                1e-7f,
            )
        }
    }

    @Test
    fun `classifier separates binary operators from relations`() {
        assertEquals(
            MathClass.Bin,
            MathClassClassifier.classify(LatexParser.parse("+")),
        )
        assertEquals(
            MathClass.Rel,
            MathClassClassifier.classify(LatexParser.parse("=")),
        )
        assertEquals(
            MathClass.Rel,
            MathClassClassifier.classify(LatexParser.parse("\\le")),
        )
    }

    @Test
    fun `classifier handles fences punctuation and big operators`() {
        assertEquals(MathClass.Open, MathClassClassifier.classify(LatexParser.parse("(")))
        assertEquals(MathClass.Close, MathClassClassifier.classify(LatexParser.parse(")")))
        assertEquals(MathClass.Punct, MathClassClassifier.classify(LatexParser.parse(",")))
        assertEquals(MathClass.Op, MathClassClassifier.classify(LatexParser.parse("\\sum")))
        assertEquals(MathClass.Op, MathClassClassifier.classify(LatexParser.parse("\\int")))
    }

    @Test
    fun `leading minus is unary so it does not get binary spacing`() {
        // "-x" parses to a Row of [-, x]; the minus must resolve to Ord, not Bin.
        val classes = MathClassClassifier.resolveBinaries(
            listOf(MathClass.Bin, MathClass.Ord),
        )
        assertEquals(listOf(MathClass.Ord, MathClass.Ord), classes)
    }

    @Test
    fun `minus after an operator is still binary`() {
        // "x-y": the minus follows an Ord, so it stays Bin.
        val classes = MathClassClassifier.resolveBinaries(
            listOf(MathClass.Ord, MathClass.Bin, MathClass.Ord),
        )
        assertEquals(listOf(MathClass.Ord, MathClass.Bin, MathClass.Ord), classes)
    }

    @Test
    fun `minus after a relation becomes unary`() {
        val classes = MathClassClassifier.resolveBinaries(
            listOf(MathClass.Ord, MathClass.Rel, MathClass.Bin, MathClass.Ord),
        )
        assertEquals(
            listOf(MathClass.Ord, MathClass.Rel, MathClass.Ord, MathClass.Ord),
            classes,
        )
    }
}
