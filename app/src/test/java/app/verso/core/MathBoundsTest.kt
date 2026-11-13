package app.verso.core

import app.verso.core.latex.LatexParser
import app.verso.core.latex.MathNode
import app.verso.core.model.*
import app.verso.core.text.BlockSplitter
import org.junit.Assert.*
import org.junit.Test

class MathBoundsTest {
    private fun rejected(node: MathNode, source: String) {
        assertTrue(node is MathNode.Symbol)
        node as MathNode.Symbol
        assertEquals(source, node.rejectedSource)
        assertTrue(node.text.startsWith("Unsupported equation:"))
        assertTrue(node.text.length < 200)
    }

    @Test fun deeplyNestedEquationDoesNotOverflow() {
        val source = "{".repeat(10_000) + "x" + "}".repeat(10_000)
        var failure: Throwable? = null
        val worker = Thread(null, { try { rejected(LatexParser.parse(source), source) } catch (t: Throwable) { failure = t } }, "math-repro", 1024 * 1024)
        worker.start()
        worker.join()
        assertNull("parser failed on deeply nested input", failure)
    }

    @Test fun limitsApplyToEveryEntryPointAndPreserveSource() {
        val cases = listOf(
            "{".repeat(65) + "x" + "}".repeat(65),
            "\\sqrt".repeat(70) + "x",
            "\\sqrt".repeat(10_000) + "x",
            "\\text{" + "{".repeat(64) + "x" + "}".repeat(65),
            "x".repeat(4097),
            " ".repeat(16385),
            "x" + "^x".repeat(100)
        )
        for (source in cases) {
            rejected(LatexParser.parse(source), source)
            rejected(LatexParser.parseEquation(source).root, source)
            val text = LatexParser.toReadablePlainText(source)
            assertTrue(text.startsWith("Unsupported equation:"))
            assertTrue(text.length < 200)
        }
        assertFalse((LatexParser.parse("{".repeat(64) + "x" + "}".repeat(64)) as? MathNode.Symbol)?.rejectedSource != null)
        assertFalse((LatexParser.parse("x".repeat(4096)) as? MathNode.Symbol)?.rejectedSource != null)
        assertEquals("x² + y² = z²", LatexParser.toReadablePlainText("x^2 + y^2 = z^2"))
    }

    @Test fun rejectedInputsAreNotCached() {
        val source = "x".repeat(4097)
        LatexParser.parse(source)
        LatexParser.parseEquation(source)
        LatexParser.toReadablePlainText(source)
        for (name in listOf("parseCache", "equationCache", "plainTextCache")) {
            val field = LatexParser::class.java.getDeclaredField(name).apply { isAccessible = true }
            assertFalse((field.get(LatexParser) as Map<*, *>).containsKey(source))
        }
    }

    @Test fun mathSpansStayWholeAcrossChunkBoundaries() {
        val math = "x".repeat(45)
        val text = "abc " + math + " end"
        val span = Span(4, 49, SpanKind.Math)
        val blocks = BlockSplitter.splitLong(listOf(Paragraph(InlineText(text, listOf(span)))), 10)
        val parts = blocks.map { (it as Paragraph).text }
        assertEquals(text, parts.joinToString("") { it.text })
        val mathParts = parts.filter { part -> part.spans.any { it.kind == SpanKind.Math } }
        assertEquals(1, mathParts.size)
        assertEquals(math, mathParts.single().text.substring(mathParts.single().spans.single().start, mathParts.single().spans.single().end))
        assertTrue(mathParts.single().text.length > 10)
    }

    @Test fun overlappingMathSpansStillAdvance() {
        val text = "x".repeat(50)
        val spans = listOf(Span(0, 15, SpanKind.Math), Span(10, 30, SpanKind.Math), Span(25, 45, SpanKind.Math))
        val parts = BlockSplitter.splitLong(listOf(Paragraph(InlineText(text, spans))), 10).map { (it as Paragraph).text }
        assertEquals(text, parts.joinToString("") { it.text })
        assertEquals(3, parts.sumOf { it.spans.size })
    }
}
