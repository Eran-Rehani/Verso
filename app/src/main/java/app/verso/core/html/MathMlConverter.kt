package app.verso.core.html

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Converts W3C MathML elements commonly found in EPUB 3, XHTML, and HTML documents
 * into standard LaTeX strings suitable for [app.verso.core.latex.LatexParser].
 */
object MathMlConverter {

    private val OPERATOR_MAP = mapOf(
        "&PlusMinus;" to "\\pm",
        "±" to "\\pm",
        "&minus;" to "-",
        "−" to "-",
        "&times;" to "\\times",
        "×" to "\\times",
        "&divide;" to "\\div",
        "÷" to "\\div",
        "&sdot;" to "\\cdot",
        "⋅" to "\\cdot",
        "·" to "\\cdot",
        "&le;" to "\\le",
        "≤" to "\\le",
        "&ge;" to "\\ge",
        "≥" to "\\ge",
        "&ne;" to "\\ne",
        "≠" to "\\ne",
        "&asymp;" to "\\approx",
        "≈" to "\\approx",
        "&equiv;" to "\\equiv",
        "≡" to "\\equiv",
        "&in;" to "\\in",
        "∈" to "\\in",
        "&notin;" to "\\notin",
        "∉" to "\\notin",
        "&sub;" to "\\subset",
        "⊂" to "\\subset",
        "&sube;" to "\\subseteq",
        "⊆" to "\\subseteq",
        "&int;" to "\\int",
        "∫" to "\\int",
        "&sum;" to "\\sum",
        "∑" to "\\sum",
        "&prod;" to "\\prod",
        "∏" to "\\prod",
        "&radic;" to "\\sqrt",
        "√" to "\\sqrt",
        "&infin;" to "\\infty",
        "∞" to "\\infty",
        "&nabla;" to "\\nabla",
        "∇" to "\\nabla",
        "&part;" to "\\partial",
        "∂" to "\\partial",
        "&alpha;" to "\\alpha",
        "α" to "\\alpha",
        "&beta;" to "\\beta",
        "β" to "\\beta",
        "&gamma;" to "\\gamma",
        "γ" to "\\gamma",
        "&delta;" to "\\delta",
        "δ" to "\\delta",
        "&epsilon;" to "\\epsilon",
        "ε" to "\\epsilon",
        "&theta;" to "\\theta",
        "θ" to "\\theta",
        "&lambda;" to "\\lambda",
        "λ" to "\\lambda",
        "&mu;" to "\\mu",
        "μ" to "\\mu",
        "&pi;" to "\\pi",
        "π" to "\\pi",
        "&sigma;" to "\\sigma",
        "σ" to "\\sigma",
        "&omega;" to "\\omega",
        "ω" to "\\omega",
        "&Gamma;" to "\\Gamma",
        "Γ" to "\\Gamma",
        "&Delta;" to "\\Delta",
        "Δ" to "\\Delta",
        "&Theta;" to "\\Theta",
        "Θ" to "\\Theta",
        "&Lambda;" to "\\Lambda",
        "Λ" to "\\Lambda",
        "&Sigma;" to "\\Sigma",
        "Σ" to "\\Sigma",
        "&Omega;" to "\\Omega",
        "Ω" to "\\Omega",
        "&rarr;" to "\\to",
        "→" to "\\to",
        "&larr;" to "\\leftarrow",
        "←" to "\\leftarrow",
        "&harr;" to "\\leftrightarrow",
        "↔" to "\\leftrightarrow",
        "&rArr;" to "\\implies",
        "⇒" to "\\implies",
        "&hArr;" to "\\iff",
        "⇔" to "\\iff"
    )

    fun isDisplayMath(mathElement: Element): Boolean {
        val displayAttr = mathElement.attr("display").lowercase()
        val modeAttr = mathElement.attr("mode").lowercase()
        if (displayAttr == "block" || modeAttr == "display") return true

        // If math is inside a div or p that is exclusively this math element
        val parent = mathElement.parent()
        if (parent != null) {
            val parentTag = parent.tagName().lowercase()
            if ((parentTag == "div" || parentTag == "p") && parent.children().size == 1 && parent.ownText().isBlank()) {
                val parentClass = parent.className().lowercase()
                if (parentClass.contains("display") || parentClass.contains("equation")) return true
            }
        }
        return false
    }

    /**
     * Converts a `<math>` element to LaTeX.
     */
    fun toLatex(mathElement: Element): String {
        // 1. Check for TeX/LaTeX annotations inside <semantics>
        val annotations = mathElement.select("annotation")
        for (ann in annotations) {
            val encoding = ann.attr("encoding").lowercase()
            if (encoding == "application/x-tex" || encoding == "text/x-tex" || encoding == "latex" || encoding == "tex") {
                val tex = ann.text().trim()
                if (tex.isNotBlank()) return cleanLatexDelimiters(tex)
            }
        }

        // 2. Check alt / alttext attributes
        val altText = (mathElement.attr("alttext").ifBlank { mathElement.attr("alt") }).trim()
        if (altText.isNotBlank() && (altText.contains('\\') || altText.contains('^') || altText.contains('_') || altText.contains('='))) {
            return cleanLatexDelimiters(altText)
        }

        // 3. Fallback: Parse MathML DOM structure into LaTeX
        val sb = StringBuilder()
        for (child in mathElement.childNodes()) {
            convertNode(child, sb)
        }
        val result = sb.toString().trim()
        return if (result.isNotBlank()) result else altText
    }

    private fun cleanLatexDelimiters(latex: String): String {
        var trimmed = latex.trim()
        if (trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length > 4) {
            trimmed = trimmed.substring(2, trimmed.length - 2).trim()
        } else if (trimmed.startsWith("\\[") && trimmed.endsWith("\\]") && trimmed.length > 4) {
            trimmed = trimmed.substring(2, trimmed.length - 2).trim()
        } else if (trimmed.startsWith("$") && trimmed.endsWith("$") && trimmed.length > 2) {
            trimmed = trimmed.substring(1, trimmed.length - 1).trim()
        } else if (trimmed.startsWith("\\(") && trimmed.endsWith("\\)") && trimmed.length > 4) {
            trimmed = trimmed.substring(2, trimmed.length - 2).trim()
        }
        return trimmed
    }

    private fun convertNode(node: Node, sb: StringBuilder) {
        when (node) {
            is TextNode -> {
                val t = node.text().trim()
                if (t.isNotEmpty()) {
                    val mapped = OPERATOR_MAP[t] ?: t
                    sb.append(mapped)
                }
            }
            is Element -> {
                val tag = node.tagName().lowercase().substringAfterLast(':')
                when (tag) {
                    "mrow", "mstyle", "semantics", "mpadded", "phantom", "mphantom" -> {
                        for (child in node.childNodes()) {
                            convertNode(child, sb)
                        }
                    }
                    "mi" -> {
                        val text = node.text().trim()
                        val mapped = OPERATOR_MAP[text] ?: text
                        if (mapped.startsWith("\\")) {
                            sb.append(mapped).append(" ")
                        } else {
                            sb.append(mapped)
                        }
                    }
                    "mn" -> {
                        sb.append(node.text().trim())
                    }
                    "mo" -> {
                        val text = node.text().trim()
                        val mapped = OPERATOR_MAP[text] ?: text
                        if (mapped.startsWith("\\")) {
                            sb.append(" ").append(mapped).append(" ")
                        } else {
                            sb.append(mapped)
                        }
                    }
                    "mtext" -> {
                        val t = node.text().trim()
                        if (t.isNotEmpty()) {
                            sb.append("\\text{").append(t).append("}")
                        }
                    }
                    "mspace" -> {
                        sb.append("\\quad ")
                    }
                    "mfrac" -> {
                        val children = node.children()
                        val num = if (children.size > 0) nodeToLatex(children[0]) else ""
                        val den = if (children.size > 1) nodeToLatex(children[1]) else ""
                        sb.append("\\frac{").append(num).append("}{").append(den).append("}")
                    }
                    "msqrt" -> {
                        val inner = childrenToLatex(node)
                        sb.append("\\sqrt{").append(inner).append("}")
                    }
                    "mroot" -> {
                        val children = node.children()
                        val base = if (children.size > 0) nodeToLatex(children[0]) else ""
                        val deg = if (children.size > 1) nodeToLatex(children[1]) else ""
                        if (deg.isNotEmpty()) {
                            sb.append("\\sqrt[").append(deg).append("]{").append(base).append("}")
                        } else {
                            sb.append("\\sqrt{").append(base).append("}")
                        }
                    }
                    "msup" -> {
                        val children = node.children()
                        val base = if (children.size > 0) nodeToLatex(children[0]) else ""
                        val sup = if (children.size > 1) nodeToLatex(children[1]) else ""
                        sb.append("{").append(base).append("}^{").append(sup).append("}")
                    }
                    "msub" -> {
                        val children = node.children()
                        val base = if (children.size > 0) nodeToLatex(children[0]) else ""
                        val sub = if (children.size > 1) nodeToLatex(children[1]) else ""
                        sb.append("{").append(base).append("}_{").append(sub).append("}")
                    }
                    "msubsup" -> {
                        val children = node.children()
                        val base = if (children.size > 0) nodeToLatex(children[0]) else ""
                        val sub = if (children.size > 1) nodeToLatex(children[1]) else ""
                        val sup = if (children.size > 2) nodeToLatex(children[2]) else ""
                        sb.append("{").append(base).append("}_{").append(sub).append("}^{").append(sup).append("}")
                    }
                    "munder" -> {
                        val children = node.children()
                        val base = if (children.size > 0) nodeToLatex(children[0]) else ""
                        val under = if (children.size > 1) nodeToLatex(children[1]) else ""
                        sb.append("\\underset{").append(under).append("}{").append(base).append("}")
                    }
                    "mover" -> {
                        val children = node.children()
                        val base = if (children.size > 0) nodeToLatex(children[0]) else ""
                        val over = if (children.size > 1) nodeToLatex(children[1]) else ""
                        when (over) {
                            "^", "^", "\u0302" -> sb.append("\\hat{").append(base).append("}")
                            "¯", "―", "\u0304" -> sb.append("\\bar{").append(base).append("}")
                            "→", "\\to", "\u20D7" -> sb.append("\\vec{").append(base).append("}")
                            "~", "\u0303" -> sb.append("\\tilde{").append(base).append("}")
                            ".", "\u0307" -> sb.append("\\dot{").append(base).append("}")
                            "..", "\u0308" -> sb.append("\\ddot{").append(base).append("}")
                            else -> sb.append("\\overset{").append(over).append("}{").append(base).append("}")
                        }
                    }
                    "munderover" -> {
                        val children = node.children()
                        val base = if (children.size > 0) nodeToLatex(children[0]) else ""
                        val under = if (children.size > 1) nodeToLatex(children[1]) else ""
                        val over = if (children.size > 2) nodeToLatex(children[2]) else ""
                        sb.append("{").append(base).append("}_{").append(under).append("}^{").append(over).append("}")
                    }
                    "mfenced" -> {
                        val open = node.attr("open").ifBlank { "(" }
                        val close = node.attr("close").ifBlank { ")" }
                        val inner = childrenToLatex(node)
                        sb.append("\\left").append(open).append(" ").append(inner).append(" \\right").append(close)
                    }
                    "mtable" -> {
                        sb.append("\\begin{pmatrix}")
                        val rows = node.select("mtr, tr")
                        for (i in rows.indices) {
                            val cells = rows[i].select("mtd, td")
                            for (j in cells.indices) {
                                sb.append(childrenToLatex(cells[j]))
                                if (j < cells.size - 1) sb.append(" & ")
                            }
                            if (i < rows.size - 1) sb.append(" \\\\ ")
                        }
                        sb.append("\\end{pmatrix}")
                    }
                    "menclose" -> {
                        val inner = childrenToLatex(node)
                        sb.append("\\boxed{").append(inner).append("}")
                    }
                    "annotation", "annotation-xml" -> {
                        // Handled above in toLatex()
                    }
                    else -> {
                        for (child in node.childNodes()) {
                            convertNode(child, sb)
                        }
                    }
                }
            }
        }
    }

    private fun nodeToLatex(element: Element): String {
        val sb = StringBuilder()
        convertNode(element, sb)
        return sb.toString().trim()
    }

    private fun childrenToLatex(element: Element): String {
        val sb = StringBuilder()
        for (child in element.childNodes()) {
            convertNode(child, sb)
        }
        return sb.toString().trim()
    }
}
