package app.verso.core.math

import app.verso.core.latex.MathNode
import app.verso.core.latex.SymbolCategory

/**
 * Assigns TeX atom classes to parsed nodes.
 *
 * The parser only distinguishes `VARIABLE`, `NUMBER`, `OPERATOR`, `FUNCTION`,
 * `PUNCTUATION` and `TEXT`, which is not enough for TeX spacing: `+` and `=` are both
 * `OPERATOR` but TeX puts a medium space around the first and a thick space around the
 * second. The distinction is recovered here from the characters themselves.
 *
 * Changing `LatexParser` is out of scope (see the design), so classification lives beside
 * the layout instead of inside the parser.
 */
object MathClassClassifier {

    /** Binary operators: medium space on both sides. */
    private val BINARY = setOf(
        "+", "−", "-", "±", "∓", "×", "·", "⋅", "÷", "∗", "∘", "∪", "∩", "∧", "∨",
        "⊕", "⊗", "⊖", "⊙", "†", "‡", "⊔", "⊓", "⊎", "⨯", "⨂", "⨁",
    )

    /** Relations: thick space on both sides. */
    private val RELATION = setOf(
        "=", "<", ">", "≤", "≥", "≠", "≈", "≡", "∼", "≃", "≅", "∝", "≪", "≫",
        "∈", "∉", "⊂", "⊃", "⊆", "⊇", "→", "←", "↔", "⇒", "⇐", "⇔",
        "↦", "⟹", "⟸", "⟺", "≔", "≜", "⩽", "⩾", "∣", "∤", "⊥", "∥",
    )

    /** Opening fences. */
    private val OPEN = setOf("(", "[", "{", "⟨", "⌊", "⌈", "|", "‖", "\\{")

    /** Closing fences. */
    private val CLOSE = setOf(")", "]", "}", "⟩", "⌋", "⌉", "|", "‖", "\\}")

    /** Punctuation: thin space before, none after. */
    private val PUNCT = setOf(",", ";", ":", "?", "!")

    /** Large operators that take limits. */
    private val BIG_OP = setOf("∑", "∏", "∐", "∫", "∬", "∭", "∮", "⋃", "⋂", "⋁", "⋀", "⨁", "⨂")

    fun classify(node: MathNode): MathClass = when (node) {
        is MathNode.Symbol -> classifySymbol(node)
        is MathNode.BigOp -> MathClass.Op
        // A fraction, root, delimited group or scripted expression is an inner sub-formula.
        is MathNode.Fraction, is MathNode.Sqrt, is MathNode.Delimited,
        is MathNode.SubSup, is MathNode.Matrix, is MathNode.Accent -> MathClass.Inner
        is MathNode.Row -> classifyRow(node)
        is MathNode.Styled -> classify(node.content)
        is MathNode.Boxed -> MathClass.Inner
        is MathNode.Space -> MathClass.Ord
    }

    private fun classifySymbol(node: MathNode.Symbol): MathClass {
        val text = node.text
        return when {
            text in BIG_OP -> MathClass.Op
            text in RELATION -> MathClass.Rel
            text in BINARY -> MathClass.Bin
            text in OPEN -> MathClass.Open
            text in CLOSE -> MathClass.Close
            text in PUNCT -> MathClass.Punct
            node.category == SymbolCategory.OPERATOR -> MathClass.Bin
            node.category == SymbolCategory.PUNCTUATION -> MathClass.Punct
            node.category == SymbolCategory.FUNCTION -> MathClass.Op
            else -> MathClass.Ord
        }
    }

    /**
     * A row's class is the class of its outermost non-ordinary atom, which is what TeX uses
     * when deciding how the whole group spaces against its neighbours (`{a+b}` spaces as
     * `Ord`, `\sum` as `Op`).
     */
    private fun classifyRow(node: MathNode.Row): MathClass {
        if (node.children.isEmpty()) return MathClass.Ord
        val classes = node.children.map { classify(it) }
        return classes.firstOrNull { it != MathClass.Ord } ?: MathClass.Ord
    }

    /**
     * Apply TeX's neighbour rule: a `Bin` next to the start of the list, or next to an `Op`,
     * `Bin`, `Rel`, `Open` or `Punct`, is really an `Ord`.
     *
     * This is what makes a leading `-` a unary minus, and it changes the gaps around it.
     */
    fun resolveBinaries(classes: List<MathClass>): List<MathClass> {
        val out = ArrayList<MathClass>(classes.size)
        for (i in classes.indices) {
            val previous = if (i == 0) null else out[i - 1]
            out += classes[i].atFormulaStartOrAfter(previous)
        }
        return out
    }
}
