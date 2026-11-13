package app.verso.core

import app.verso.core.latex.LatexParser
import app.verso.core.latex.MathNode
import app.verso.core.latex.SymbolCategory
import app.verso.core.model.Heading
import app.verso.core.model.MathBlock
import app.verso.core.model.Paragraph
import app.verso.core.pdf.PdfAssembler
import app.verso.core.pdf.PdfContent
import app.verso.core.pdf.PdfExtractor
import app.verso.core.pdf.PdfLine
import app.verso.core.pdf.PdfPage
import app.verso.core.pdf.PdfPageSource
import app.verso.core.pdf.TextRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfLatexMathRenderingTest {

    @Test
    fun testPdfMultiLineBeginEndEquation() {
        val lines = listOf(
            PdfLine(page = 0, column = 0, text = "We formulate the field equation as", x0 = 10f, x1 = 200f, baseline = 100f, fontSize = 10f, bold = false),
            PdfLine(page = 0, column = 0, text = "\\begin{equation}", x0 = 10f, x1 = 100f, baseline = 120f, fontSize = 10f, bold = false),
            PdfLine(page = 0, column = 0, text = "\\nabla \\times \\mathbf{B} = \\mu_0 \\mathbf{J} + \\mu_0 \\epsilon_0 \\frac{\\partial \\mathbf{E}}{\\partial t}", x0 = 10f, x1 = 300f, baseline = 140f, fontSize = 10f, bold = false),
            PdfLine(page = 0, column = 0, text = "\\label{eq:maxwell}", x0 = 10f, x1 = 100f, baseline = 160f, fontSize = 10f, bold = false),
            PdfLine(page = 0, column = 0, text = "\\end{equation}", x0 = 10f, x1 = 100f, baseline = 180f, fontSize = 10f, bold = false),
            PdfLine(page = 0, column = 0, text = "which governs electrodynamics across all media.", x0 = 10f, x1 = 250f, baseline = 205f, fontSize = 10f, bold = false)
        )

        val blocks = PdfAssembler.assemble(lines)
        val mathBlocks = blocks.filterIsInstance<MathBlock>()
        assertEquals(1, mathBlocks.size)

        val equation = mathBlocks[0].latex
        assertTrue("Math block must contain Maxwell formula", equation.contains("\\nabla \\times \\mathbf{B}"))

        // Ensure no paragraph contains leaked delimiters
        val paras = blocks.filterIsInstance<Paragraph>()
        assertEquals(2, paras.size)
        assertTrue(paras.none { it.text.text.contains("\\begin{equation}") })
        assertTrue(paras.none { it.text.text.contains("\\end{equation}") })
        assertTrue(paras.none { it.text.text.contains("\\label") })

        // Ensure LatexParser parses this equation cleanly without error
        val parsed = LatexParser.parseEquation(equation)
        assertNotNull(parsed.root)
        val plainText = LatexParser.toReadablePlainText(equation)
        assertTrue(plainText.contains("∇") || plainText.contains("B"))
        assertFalse("Should not render literal word label", plainText.contains("label"))
    }

    @Test
    fun testPdfMultiLineBracketDisplayMath() {
        val lines = listOf(
            PdfLine(page = 0, column = 0, text = "The energy-momentum invariant is:", x0 = 10f, x1 = 200f, baseline = 100f, fontSize = 10f, bold = false),
            PdfLine(page = 0, column = 0, text = "\\[", x0 = 10f, x1 = 20f, baseline = 125f, fontSize = 10f, bold = false),
            PdfLine(page = 0, column = 0, text = "E^2 = (pc)^2 + (m_0 c^2)^2", x0 = 10f, x1 = 180f, baseline = 145f, fontSize = 10f, bold = false),
            PdfLine(page = 0, column = 0, text = "\\]", x0 = 10f, x1 = 20f, baseline = 165f, fontSize = 10f, bold = false),
            PdfLine(page = 0, column = 0, text = "for any massive relativistic particle.", x0 = 10f, x1 = 200f, baseline = 190f, fontSize = 10f, bold = false)
        )

        val blocks = PdfAssembler.assemble(lines)
        val mathBlocks = blocks.filterIsInstance<MathBlock>()
        assertEquals(1, mathBlocks.size)

        val parsed = LatexParser.parseEquation(mathBlocks[0].latex)
        assertTrue(parsed.root is MathNode.Row)
        val children = (parsed.root as MathNode.Row).children
        assertTrue(children.any { it is MathNode.SubSup && (it.superscript as? MathNode.Symbol)?.text == "2" })

        // Check that paragraphs don't leak \[ or \]
        val paras = blocks.filterIsInstance<Paragraph>()
        assertTrue(paras.none { it.text.text.contains("\\[") || it.text.text.contains("\\]") })
    }

    @Test
    fun testPdfEquationWithTagOnSameLine() {
        val line = PdfLine(
            page = 0, column = 0,
            text = "\\[ \\int_0^1 f(x) dx = 1 \\] (2.3)",
            x0 = 10f, x1 = 200f, baseline = 100f, fontSize = 10f, bold = false
        )
        val blocks = PdfAssembler.assemble(listOf(line))
        val mathBlocks = blocks.filterIsInstance<MathBlock>()
        assertEquals(1, mathBlocks.size)

        val parsed = LatexParser.parseEquation(mathBlocks[0].latex)
        assertEquals("2.3", parsed.tag)
        val plain = LatexParser.toReadablePlainText(mathBlocks[0].latex)
        assertTrue(plain.contains("∫"))
        assertFalse("Tag (2.3) should not be inside plain text formula", plain.contains("(2.3)"))
    }

    @Test
    fun testPdfEquationWithTagOnNextLine() {
        val lines = listOf(
            PdfLine(page = 0, column = 0, text = "f(x) = \\sum_{n=1}^\\infty \\frac{1}{n^s}", x0 = 10f, x1 = 200f, baseline = 100f, fontSize = 10f, bold = false),
            PdfLine(page = 0, column = 0, text = "(1.4)", x0 = 190f, x1 = 210f, baseline = 100f, fontSize = 10f, bold = false)
        )
        val blocks = PdfAssembler.assemble(lines)
        val mathBlocks = blocks.filterIsInstance<MathBlock>()
        assertEquals(1, mathBlocks.size)

        val parsed = LatexParser.parseEquation(mathBlocks[0].latex)
        assertEquals("1.4", parsed.tag)
        // No orphaned paragraph with (1.4)
        val paras = blocks.filterIsInstance<Paragraph>()
        assertTrue(paras.isEmpty())
    }

    @Test
    fun testPdfUnicodeMathEquationExtraction() {
        val line = PdfLine(
            page = 0, column = 0,
            text = "y ≔ α·x + β ± √(x² + 1) (3)",
            x0 = 10f, x1 = 250f, baseline = 100f, fontSize = 10f, bold = false
        )
        val blocks = PdfAssembler.assemble(listOf(line))
        val mathBlocks = blocks.filterIsInstance<MathBlock>()
        assertEquals(1, mathBlocks.size)

        val parsed = LatexParser.parseEquation(mathBlocks[0].latex)
        assertEquals("3", parsed.tag)
        assertTrue(parsed.root is MathNode.Row)
        val nodes = (parsed.root as MathNode.Row).children

        // Check for ≔ operator
        assertTrue(nodes.any { it is MathNode.Symbol && it.text == "≔" && it.category == SymbolCategory.OPERATOR })
        // Check for α variable
        assertTrue(nodes.any { it is MathNode.Symbol && it.text == "α" && it.category == SymbolCategory.VARIABLE })
        // Check for Sqrt node
        assertTrue(nodes.any { it is MathNode.Sqrt })
    }

    @Test
    fun testPdfExtractorFullDocumentWithLatexMath() {
        val fakeSource = object : PdfPageSource {
            override fun read(pdf: ByteArray, onPage: () -> Unit): PdfContent {
                val runs = listOf(
                    // Title
                    TextRun(text = "Quantum Mechanics", x = 50f, baseline = 40f, width = 200f, fontSize = 18f, bold = true),
                    // Section
                    TextRun(text = "1. Schrödinger Equation", x = 50f, baseline = 70f, width = 150f, fontSize = 13f, bold = true),
                    // Paragraph
                    TextRun(text = "The time-dependent state evolution is described by", x = 50f, baseline = 95f, width = 250f, fontSize = 10f),
                    // Multi-line display equation:
                    TextRun(text = "\\[", x = 50f, baseline = 120f, width = 20f, fontSize = 10f),
                    TextRun(text = "i \\hbar \\frac{\\partial \\psi}{\\partial t} = \\hat{H} \\psi", x = 50f, baseline = 140f, width = 180f, fontSize = 10f),
                    TextRun(text = "\\]", x = 50f, baseline = 160f, width = 20f, fontSize = 10f),
                    TextRun(text = "(1)", x = 250f, baseline = 160f, width = 20f, fontSize = 10f),
                    // Subsequent paragraph
                    TextRun(text = "where \\(\\psi\\) is the wavefunction and \\(\\hat{H}\\) is Hamiltonian.", x = 50f, baseline = 185f, width = 280f, fontSize = 10f)
                )
                return PdfContent(
                    title = "Quantum Mechanics",
                    pages = listOf(
                        PdfPage(index = 0, width = 400f, height = 600f, runs = runs)
                    )
                )
            }
        }

        val extractor = PdfExtractor(fakeSource)
        val doc = extractor.extract(ByteArray(50))

        assertEquals("Quantum Mechanics", doc.title)
        assertTrue(doc.blocks.isNotEmpty())

        val heading = doc.blocks.filterIsInstance<Heading>()
        assertTrue(heading.isNotEmpty())

        val mathBlocks = doc.blocks.filterIsInstance<MathBlock>()
        assertEquals(1, mathBlocks.size)
        val parsed = LatexParser.parseEquation(mathBlocks[0].latex)
        assertEquals("1", parsed.tag)

        // Check inline math
        val paras = doc.blocks.filterIsInstance<Paragraph>()
        val inlineMathPara = paras.firstOrNull { it.text.spans.any { span -> span.kind == app.verso.core.model.SpanKind.Math } }
        assertNotNull("Subsequent paragraph should contain inline math for \\psi and \\hat{H}", inlineMathPara)
    }

    @Test
    fun testPdfUndelimitedInlineMathDetectionForInspector() {
        val lines = listOf(
            PdfLine(
                page = 0,
                column = 0,
                text = "In this section we consider the ratio \\frac{a+b}{c} and the relation x² + y² = r² within the text.",
                x0 = 10f,
                x1 = 300f,
                baseline = 100f,
                fontSize = 10f,
                bold = false
            )
        )

        val blocks = PdfAssembler.assemble(lines)
        val paras = blocks.filterIsInstance<Paragraph>()
        assertEquals(1, paras.size)

        val para = paras[0]
        val mathSpans = para.text.spans.filter { it.kind == app.verso.core.model.SpanKind.Math }
        assertTrue("Should detect both \\frac{a+b}{c} and x² + y² = r² as inline math spans", mathSpans.size >= 2)

        val extractedEquations = mathSpans.map { para.text.text.substring(it.start, it.end) }
        assertTrue(extractedEquations.any { it.contains("\\frac{a+b}{c}") })
        assertTrue(extractedEquations.any { it.contains("x² + y² = r²") })

        // Verify each detected inline equation parses cleanly for the Rendered LaTeX Inspector
        for (eq in extractedEquations) {
            val parsed = LatexParser.parseEquation(eq)
            assertNotNull(parsed.root)
        }
    }
}
