package app.verso.core

import app.verso.core.model.Paragraph
import app.verso.core.text.PlainTextExtractor
import app.verso.core.fixtures.SampleFixtures
import org.junit.Test

/** The .tex sample goes through PlainTextExtractor; check whether prose stays whole. */
class SampleLatexFlowTest {
    @Test
    fun `latex sample paragraph flow`() {
        val spec = SampleFixtures.all.first { it.fileName.endsWith(".tex") }
        val doc = PlainTextExtractor.extract(spec.content)
        println("TEXTSIZE=" + spec.content.length)
        println("BLOCKS=" + doc.blocks.map { it::class.simpleName })
        doc.blocks.filterIsInstance<Paragraph>().take(10).forEach { p ->
            println("  PARA='" + p.text.text.take(150) + "'")
        }
    }
}
