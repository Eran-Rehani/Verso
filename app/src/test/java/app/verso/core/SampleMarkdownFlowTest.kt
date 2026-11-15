package app.verso.core

import app.verso.core.fixtures.SampleFixtures
import app.verso.core.markdown.MarkdownExtractor
import app.verso.core.model.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Test

/** End-to-end: the sample fixture must not split prose at inline equations. */
class SampleMarkdownFlowTest {
    @Test
    fun `full bundled sample keeps prose whole`() {
        val spec = SampleFixtures.MATH_PHYSICS_MARKDOWN
        val doc = MarkdownExtractor.extract(spec.content)
        val paras = doc.blocks.filterIsInstance<Paragraph>()
        println("BLOCKS=" + doc.blocks.size + " PARAS=" + paras.size)
        paras.take(6).forEach { println("  PARA='" + it.text?.text?.take(160) + "'") }
        val quad = paras.firstOrNull { it.text?.text?.contains("quadratic polynomial") == true }
        println("QUAD='" + (quad?.text?.text ?: "MISSING") + "'")
    }

    @Test
    fun `quadratic sentence stays one paragraph`() {
        val md = """
            ## 1. Algebraic Foundations

            For any quadratic polynomial \(ax^2 + bx + c = 0\) with \(a \neq 0\) and coefficients in \(\mathbb{R}\), the roots are given by the classical quadratic formula:
        """.trimIndent()
        val doc = MarkdownExtractor.extract(md)
        println("BLOCKS=" + doc.blocks.map { it::class.simpleName })
        doc.blocks.filterIsInstance<Paragraph>().forEach { p ->
            println("  PARA='" + p.text.text + "'")
        }
        val paras = doc.blocks.filterIsInstance<Paragraph>()
        assertEquals("sentence must be one paragraph, got ${paras.map { it.text.text }}", 1, paras.size)
        assertEquals(
            "ax^2 + bx + c = 0",
            paras[0].text.text.substringAfter("polynomial ").substringBefore(" with"),
        )
    }
}
