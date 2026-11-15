package app.verso.core

import app.verso.core.markdown.MathProtector
import app.verso.core.model.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Inline math must stay inside its sentence.
 *
 * A real defect seen on a device: a paragraph containing several inline equations was split
 * into one paragraph per equation, so "For any quadratic polynomial ax^2 + bx + c = 0" became
 * three stacked lines and ", the roots are given by" started with a stray comma.
 */
class MathParagraphFlowTest {

    private fun split(text: String, math: List<MathProtector.MathSpan>): List<Any> {
        val out = mutableListOf<Any>()
        MathProtector.splitDisplayMath(
            text = text,
            existingSpans = emptyList(),
            mathList = math,
            onParagraph = { out += it },
            onMathBlock = { out += "MATH:$it" },
        )
        return out
    }

    @Test
    fun `inline math does not split the paragraph`() {
        val math = listOf(
            MathProtector.MathSpan("x^2", display = false),
            MathProtector.MathSpan("a \\ne 0", display = false),
        )
        val text = "Let ${MathProtector.OPEN}0${MathProtector.CLOSE} and ${MathProtector.OPEN}1${MathProtector.CLOSE} hold."
        val blocks = split(text, math)
        assertEquals("one paragraph expected, got $blocks", 1, blocks.size)
        assertEquals("Let x^2 and a \\ne 0 hold.", (blocks[0] as app.verso.core.model.InlineText).text)
    }

    @Test
    fun `display math still becomes its own block with prose either side`() {
        val math = listOf(
            MathProtector.MathSpan("a+b", display = false),
            MathProtector.MathSpan("\\frac{1}{2}", display = true),
        )
        val text = "Before ${MathProtector.OPEN}0${MathProtector.CLOSE} then ${MathProtector.OPEN}1${MathProtector.CLOSE} after."
        val blocks = split(text, math)
        assertEquals("expected paragraph, math, paragraph: $blocks", 3, blocks.size)
        assertTrue("first must be prose", blocks[0] is app.verso.core.model.InlineText)
        assertEquals("MATH:\\frac{1}{2}", blocks[1])
        assertTrue("last must be prose", blocks[2] is app.verso.core.model.InlineText)
    }

    @Test
    fun `paragraph starting with inline math keeps its prose`() {
        val math = listOf(MathProtector.MathSpan("x", display = false))
        val text = "${MathProtector.OPEN}0${MathProtector.CLOSE}, the roots follow."
        val blocks = split(text, math)
        assertEquals("one paragraph expected, got $blocks", 1, blocks.size)
        assertEquals("x, the roots follow.", (blocks[0] as app.verso.core.model.InlineText).text)
    }
}
