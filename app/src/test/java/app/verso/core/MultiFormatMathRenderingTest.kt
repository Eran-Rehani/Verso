package app.verso.core

import app.verso.core.html.HtmlExtractor
import app.verso.core.latex.LatexParser
import app.verso.core.latex.LatexSymbolMap
import app.verso.core.latex.MathNode
import app.verso.core.markdown.MarkdownExtractor
import app.verso.core.model.MathBlock
import app.verso.core.model.Paragraph
import app.verso.core.model.SpanKind
import app.verso.core.pdf.PdfAssembler
import app.verso.core.pdf.PdfLine
import app.verso.core.text.PlainTextExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiFormatMathRenderingTest {

    @Test
    fun testLatexSymbolMapArrowsAndShortcuts() {
        assertEquals("↙", LatexSymbolMap.OPERATORS_AND_RELATIONS["swarrow"])
        assertEquals("ℝ", LatexSymbolMap.MISC_SYMBOLS["R"])
        assertEquals("ℕ", LatexSymbolMap.MISC_SYMBOLS["N"])
        assertEquals("ℤ", LatexSymbolMap.MISC_SYMBOLS["Z"])
        assertEquals("ℚ", LatexSymbolMap.MISC_SYMBOLS["Q"])
        assertEquals("ℂ", LatexSymbolMap.MISC_SYMBOLS["C"])
        assertEquals("≔", LatexSymbolMap.OPERATORS_AND_RELATIONS["coloneq"])
        assertEquals("ε", LatexSymbolMap.GREEK_LOWER["eps"])
    }

    @Test
    fun testLatexParserAbsNormAndMacros() {
        val parsed = LatexParser.parse("\\abs{x} + \\norm{v}")
        assertTrue(parsed is MathNode.Row)
        val children = (parsed as MathNode.Row).children
        assertTrue(children.any { it is MathNode.Delimited && it.left == "|" && it.right == "|" })
        assertTrue(children.any { it is MathNode.Delimited && it.left == "‖" && it.right == "‖" })
    }

    @Test
    fun testUnicodeMathToRenderedLatex() {
        // Test Unicode math formula: x² + y² = z² with superscripts
        val parsedPythagoras = LatexParser.parse("x² + y² = z²")
        assertTrue(parsedPythagoras is MathNode.Row)
        val nodes = (parsedPythagoras as MathNode.Row).children
        assertTrue(nodes.any { it is MathNode.SubSup && (it.superscript as? MathNode.Symbol)?.text == "2" })

        // Test Unicode root: √(x + 1)
        val parsedRoot = LatexParser.parse("√(x + 1)")
        assertTrue(parsedRoot is MathNode.Row)
        val rootChildren = (parsedRoot as MathNode.Row).children
        assertTrue(rootChildren.any { it is MathNode.Sqrt })

        // Test Unicode calculus and greek: ∫ (α·x + β) dx
        val parsedCalc = LatexParser.parse("∫ (α·x + β) dx")
        assertTrue(parsedCalc is MathNode.Row)
        val calcChildren = (parsedCalc as MathNode.Row).children
        assertTrue(calcChildren.any { it is MathNode.BigOp && it.isIntegral })
        assertTrue(calcChildren.any { (it as? MathNode.Symbol)?.text == "α" })
    }

    @Test
    fun testLatexFileExtraction() {
        val latexDoc = """
            \documentclass{article}
            \newcommand{\R}{\mathbb{R}}
            \begin{document}
            \section{Calculus}
            Let ${'$'}f: \R \to \R${'$'} be continuous.
            \begin{equation}
            \int_{a}^{b} f(x) dx = F(b) - F(a)
            \end{equation}
            \end{document}
        """.trimIndent()

        val doc = MarkdownExtractor.extract(latexDoc)
        assertTrue(doc.blocks.isNotEmpty())
        val mathBlocks = doc.blocks.filterIsInstance<MathBlock>()
        assertEquals(1, mathBlocks.size)
        assertTrue(mathBlocks[0].latex.contains("\\int"))

        val paragraphs = doc.blocks.filterIsInstance<Paragraph>()
        val mathSpanPara = paragraphs.firstOrNull { p -> p.text.spans.any { it.kind == SpanKind.Math } }
        assertNotNull("Should contain inline math span for f: \\R -> \\R", mathSpanPara)
    }

    @Test
    fun testEpubHtmlMathMLConversion() {
        val epubChapter = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Math EPUB</title></head>
            <body>
                <p>The quadratic formula is:</p>
                <div class="display-equation">
                    <math display="block">
                        <mi>x</mi>
                        <mo>=</mo>
                        <mfrac>
                            <mrow>
                                <mo>-</mo>
                                <mi>b</mi>
                                <mo>±</mo>
                                <msqrt>
                                    <msup><mi>b</mi><mn>2</mn></msup>
                                    <mo>-</mo>
                                    <mn>4</mn><mi>a</mi><mi>c</mi>
                                </msqrt>
                            </mrow>
                            <mrow>
                                <mn>2</mn><mi>a</mi>
                            </mrow>
                        </mfrac>
                    </math>
                </div>
                <p>where <math><mi>a</mi><mo>≠</mo><mn>0</mn></math>.</p>
            </body>
            </html>
        """.trimIndent()

        val doc = HtmlExtractor.extract(epubChapter)
        val mathBlocks = doc.blocks.filterIsInstance<MathBlock>()
        assertEquals(1, mathBlocks.size)
        assertTrue(mathBlocks[0].latex.contains("\\frac"))
        assertTrue(mathBlocks[0].latex.contains("\\sqrt"))

        val inlineMathPara = doc.blocks.filterIsInstance<Paragraph>().firstOrNull { p ->
            p.text.spans.any { it.kind == SpanKind.Math }
        }
        assertNotNull(inlineMathPara)
        assertTrue(inlineMathPara!!.text.text.contains("a") && inlineMathPara.text.text.contains("0"))
    }

    @Test
    fun testPdfAssemblerMathRecognition() {
        val lines = listOf(
            PdfLine(page = 1, column = 0, text = "Euler formula:", x0 = 10f, x1 = 100f, baseline = 100f, fontSize = 12f, bold = false),
            PdfLine(page = 1, column = 0, text = "$$ e^{i\\pi} + 1 = 0 $$", x0 = 10f, x1 = 100f, baseline = 120f, fontSize = 12f, bold = false),
            PdfLine(page = 1, column = 0, text = "where ${'$'}e${'$'} is Euler's constant.", x0 = 10f, x1 = 100f, baseline = 140f, fontSize = 12f, bold = false)
        )

        val blocks = PdfAssembler.assemble(lines)
        val mathBlocks = blocks.filterIsInstance<MathBlock>()
        assertEquals(1, mathBlocks.size)
        assertEquals("e^{i\\pi} + 1 = 0", mathBlocks[0].latex)

        val parasWithInline = blocks.filterIsInstance<Paragraph>().filter { p ->
            p.text.spans.any { it.kind == SpanKind.Math }
        }
        assertTrue(parasWithInline.isNotEmpty())
    }

    @Test
    fun testDjVuPlainTextMathExtraction() {
        val djvuText = """
            According to the theorem, we have:
            
            $$ \lim_{n \to \infty} \left(1 + \frac{1}{n}\right)^n = e $$
            
            Hence the limit converges.
        """.trimIndent()

        val doc = PlainTextExtractor.extract(djvuText)
        val mathBlocks = doc.blocks.filterIsInstance<MathBlock>()
        assertEquals(1, mathBlocks.size)
        assertTrue(mathBlocks[0].latex.contains("\\lim"))
        assertTrue(mathBlocks[0].latex.contains("\\frac"))

        val paras = doc.blocks.filterIsInstance<Paragraph>()
        assertEquals(2, paras.size)
    }
}
