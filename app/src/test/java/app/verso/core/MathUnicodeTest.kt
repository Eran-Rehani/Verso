package app.verso.core

import app.verso.core.latex.LatexParser
import app.verso.core.latex.MathNode
import app.verso.core.latex.SymbolCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathUnicodeTest {
    @Test fun supplementaryLetterIsOneVariable() {
        val source = "𝑥"
        val expected = MathNode.Symbol(source, SymbolCategory.VARIABLE)
        assertEquals(expected, LatexParser.parse(source))
        assertEquals(expected, LatexParser.parseEquation(source).root)
        assertEquals(source, LatexParser.toReadablePlainText(source))
    }
    @Test fun supplementaryScalarsSurviveMixedNestedMath() {
        val cases = listOf(
            "a𝑥+𝟙2.𝟚" to "a𝑥 + 𝟙2.𝟚",
            "\\mathbf{𝑥_𝟙}^𝑦" to "𝑥_(𝟙)^(𝑦)",
            "\\begin{matrix}𝑥&𝟙\\\\a&𝑦\\end{matrix}" to "𝑥, 𝟙; a, 𝑦"
        )
        fun symbols(node: MathNode): List<String> = when (node) {
            is MathNode.Symbol -> listOf(node.text)
            is MathNode.Row -> node.children.flatMap(::symbols)
            is MathNode.SubSup -> symbols(node.base) + listOfNotNull(node.subscript, node.superscript).flatMap(::symbols)
            is MathNode.Styled -> symbols(node.content)
            is MathNode.Matrix -> node.rows.flatten().flatMap(::symbols)
            else -> emptyList()
        }
        for ((source, readable) in cases) {
            val root = LatexParser.parse(source)
            assertEquals(root, LatexParser.parseEquation(source).root)
            assertEquals(readable, LatexParser.toReadablePlainText(source))
            for (text in symbols(root)) {
                assertTrue("isolated surrogate in $text", text.codePoints().noneMatch { it in 0xD800..0xDFFF })
            }
        }
        val number = LatexParser.parse("𝟙2.𝟚") as MathNode.Symbol
        assertEquals(SymbolCategory.NUMBER, number.category)
        assertEquals("𝟙2.𝟚", number.text)
    }
}
