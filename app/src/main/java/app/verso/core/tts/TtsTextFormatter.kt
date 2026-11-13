package app.verso.core.tts

import androidx.compose.runtime.Immutable
import app.verso.core.latex.AccentKind
import app.verso.core.latex.LatexParser
import app.verso.core.latex.MathFontStyle
import app.verso.core.latex.MathNode
import app.verso.core.latex.MatrixEnvType
import app.verso.core.model.Block
import app.verso.core.model.CodeBlock
import app.verso.core.model.Divider
import app.verso.core.model.Document
import app.verso.core.model.Heading
import app.verso.core.model.ImageBlock
import app.verso.core.model.InlineText
import app.verso.core.model.ListItem
import app.verso.core.model.MathBlock
import app.verso.core.model.Paragraph
import app.verso.core.model.Quote
import app.verso.core.model.SpanKind
import app.verso.core.model.TableBlock

@Immutable
data class TtsUtteranceItem(
    val queueIndex: Int,
    val blockIndex: Int,
    val spokenText: String,
    val previewText: String
)

object TtsTextFormatter {

    private val WHITESPACE_REGEX = Regex("\\s+")

    private val SPOKEN_SYMBOL_MAP = mapOf(
        // Lowercase Greek
        "α" to "alpha",
        "β" to "beta",
        "γ" to "gamma",
        "δ" to "delta",
        "ε" to "epsilon",
        "ϵ" to "epsilon",
        "ζ" to "zeta",
        "η" to "eta",
        "θ" to "theta",
        "ϑ" to "theta",
        "ι" to "iota",
        "κ" to "kappa",
        "λ" to "lambda",
        "μ" to "mu",
        "ν" to "nu",
        "ξ" to "xi",
        "π" to "pi",
        "ϖ" to "pi",
        "ρ" to "rho",
        "ϱ" to "rho",
        "σ" to "sigma",
        "ς" to "sigma",
        "τ" to "tau",
        "υ" to "upsilon",
        "φ" to "phi",
        "ϕ" to "phi",
        "χ" to "chi",
        "ψ" to "psi",
        "ω" to "omega",
        // Uppercase Greek
        "Γ" to "Gamma",
        "Δ" to "Delta",
        "Θ" to "Theta",
        "Λ" to "Lambda",
        "Ξ" to "Xi",
        "Π" to "Pi",
        "Σ" to "Sigma",
        "Υ" to "Upsilon",
        "Φ" to "Phi",
        "Ψ" to "Psi",
        "Ω" to "Omega",
        // Physics & Calculus symbols
        "∇" to "del",
        "∂" to "partial",
        "ℏ" to "h bar",
        "ℓ" to "l",
        "ℜ" to "the real part of",
        "ℑ" to "the imaginary part of",
        "∞" to "infinity",
        "∅" to "the empty set",
        // Number sets
        "ℝ" to "the real numbers",
        "ℕ" to "the natural numbers",
        "ℤ" to "the integers",
        "ℚ" to "the rational numbers",
        "ℂ" to "the complex numbers",
        // Operators & Relations
        "+" to "plus",
        "-" to "minus",
        "−" to "minus",
        "±" to "plus or minus",
        "∓" to "minus or plus",
        "×" to "times",
        "⋅" to "times",
        "·" to "times",
        "÷" to "divided by",
        "=" to "equals",
        "≠" to "is not equal to",
        "<" to "is less than",
        ">" to "is greater than",
        "≤" to "is less than or equal to",
        "≥" to "is greater than or equal to",
        "≪" to "is much less than",
        "≫" to "is much greater than",
        "≈" to "is approximately equal to",
        "≃" to "is asymptotically equal to",
        "≅" to "is congruent to",
        "≡" to "is equivalent to",
        "∝" to "is proportional to",
        "∈" to "in",
        "∉" to "not in",
        "⊂" to "is a subset of",
        "⊆" to "is a subset of or equal to",
        "⊃" to "is a superset of",
        "⊇" to "is a superset of or equal to",
        "∪" to "union",
        "∩" to "intersection",
        "∀" to "for all",
        "∃" to "there exists",
        "∄" to "there does not exist",
        "→" to "approaches",
        "⟶" to "approaches",
        "↦" to "maps to",
        "⇒" to "implies",
        "⟹" to "implies",
        "⇔" to "if and only if",
        "⟺" to "if and only if",
        "⊥" to "perpendicular to",
        "∥" to "parallel to",
        "′" to "prime",
        "!" to "factorial"
    )

    private val SPOKEN_FUNCTION_MAP = mapOf(
        "sin" to "sine",
        "cos" to "cosine",
        "tan" to "tangent",
        "cot" to "cotangent",
        "sec" to "secant",
        "csc" to "cosecant",
        "arcsin" to "arc sine",
        "arccos" to "arc cosine",
        "arctan" to "arc tangent",
        "sinh" to "hyperbolic sine",
        "cosh" to "hyperbolic cosine",
        "tanh" to "hyperbolic tangent",
        "ln" to "natural log of",
        "log" to "log of",
        "exp" to "exponential of",
        "det" to "determinant of",
        "dim" to "dimension of",
        "ker" to "kernel of",
        "lim" to "limit",
        "sup" to "supremum",
        "inf" to "infimum",
        "max" to "maximum",
        "min" to "minimum",
        "gcd" to "greatest common divisor"
    )

    /**
     * Builds the ordered queue of speakable blocks for a [Document].
     */
    fun buildQueue(document: Document): List<TtsUtteranceItem> {
        if (document.blocks.isEmpty()) return emptyList()
        val items = ArrayList<TtsUtteranceItem>(document.blocks.size)
        for (i in document.blocks.indices) {
            val block = document.blocks[i]
            val spoken = formatBlock(block).trim()
            if (spoken.isNotBlank()) {
                val preview = buildPreview(block, spoken)
                items.add(
                    TtsUtteranceItem(
                        queueIndex = items.size,
                        blockIndex = i,
                        spokenText = spoken,
                        previewText = preview
                    )
                )
            }
        }
        return items
    }

    /**
     * Finds the queue index for [targetBlockIndex], or the closest subsequent speakable block.
     */
    fun findQueueIndexForBlock(queue: List<TtsUtteranceItem>, targetBlockIndex: Int): Int {
        if (queue.isEmpty()) return -1
        var low = 0
        var high = queue.lastIndex
        var result = queue.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val itemBlock = queue[mid].blockIndex
            if (itemBlock >= targetBlockIndex) {
                result = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return result
    }

    /**
     * Converts a single [Block] into natural spoken text for TextToSpeech synthesis.
     */
    fun formatBlock(block: Block): String {
        return when (block) {
            is Heading -> {
                val text = formatInlineText(block.text)
                if (text.isBlank()) "" else "$text."
            }
            is Paragraph -> formatInlineText(block.text)
            is ListItem -> {
                val body = formatInlineText(block.text)
                if (body.isBlank()) ""
                else {
                    val cleanMarker = block.marker.trim().removeSuffix(".")
                    if (cleanMarker.isNotEmpty() && cleanMarker != "•" && cleanMarker != "-") {
                        "Item $cleanMarker: $body"
                    } else {
                        body
                    }
                }
            }
            is Quote -> {
                val body = formatInlineText(block.text)
                if (body.isBlank()) "" else "Quote: $body"
            }
            is MathBlock -> {
                val spokenMath = formatLatex(block.latex)
                if (spokenMath.isBlank()) "" else "Equation: $spokenMath."
            }
            is CodeBlock -> {
                val code = block.code.trim()
                if (code.isBlank()) ""
                else {
                    val lang = block.language?.trim()?.takeIf { it.isNotEmpty() }
                    val prefix = if (lang != null) "Code in $lang: " else "Code block: "
                    prefix + code.lines().joinToString(". ") { it.trim() }
                }
            }
            is TableBlock -> {
                val rowsText = mutableListOf<String>()
                block.rows.forEachIndexed { rowIdx, row ->
                    val cellParts = row.mapIndexedNotNull { colIdx, cell ->
                        val header = block.header.getOrNull(colIdx)?.let { formatInlineText(it) }?.trim().orEmpty()
                        val value = formatInlineText(cell).trim()
                        if (value.isEmpty()) null
                        else if (header.isNotEmpty()) "$header: $value"
                        else value
                    }
                    if (cellParts.isNotEmpty()) {
                        rowsText.add("Row ${rowIdx + 1}: ${cellParts.joinToString(", ")}")
                    }
                }
                if (rowsText.isEmpty()) "" else "Table. ${rowsText.joinToString(". ")}."
            }
            is ImageBlock -> {
                if (block.alt.isBlank()) "" else "Image: ${block.alt.trim()}."
            }
            is Divider -> ""
        }
    }

    /**
     * Converts [InlineText] into natural spoken text, expanding any [SpanKind.Math] spans
     * into spoken mathematics.
     */
    fun formatInlineText(inline: InlineText): String {
        val raw = inline.text
        if (raw.isBlank()) return ""
        val mathSpans = inline.spans
            .filter { it.kind == SpanKind.Math && it.start >= 0 && it.end <= raw.length && it.start < it.end }
            .sortedBy { it.start }

        if (mathSpans.isEmpty()) {
            return raw.replace(WHITESPACE_REGEX, " ").trim()
        }

        val sb = StringBuilder(raw.length + 32)
        var cursor = 0
        for (span in mathSpans) {
            if (span.start < cursor) continue
            if (span.start > cursor) {
                sb.append(raw, cursor, span.start)
            }
            val latexSlice = raw.substring(span.start, span.end)
            val spokenMath = formatLatex(latexSlice)
            if (sb.isNotEmpty() && !sb.last().isWhitespace()) sb.append(' ')
            sb.append(spokenMath)
            cursor = span.end
            if (cursor < raw.length && !raw[cursor].isWhitespace() && raw[cursor] !in ",.;:!?)]}") {
                sb.append(' ')
            }
        }
        if (cursor < raw.length) {
            sb.append(raw, cursor, raw.length)
        }
        return sb.toString().replace(WHITESPACE_REGEX, " ").trim()
    }

    /**
     * Converts a raw LaTeX expression into natural spoken English.
     */
    fun formatLatex(latex: String): String {
        val trimmed = latex.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            val parsed = LatexParser.parseEquation(trimmed)
            val body = speakNode(parsed.root).replace(WHITESPACE_REGEX, " ").trim()
            if (parsed.tag != null && parsed.tag.isNotBlank()) {
                "$body, equation ${parsed.tag}"
            } else {
                body
            }
        } catch (_: Exception) {
            LatexParser.toReadablePlainText(trimmed)
        }
    }

    private fun speakNode(node: MathNode): String {
        return when (node) {
            is MathNode.Row -> {
                if (node.children.isEmpty()) return ""
                val parts = ArrayList<String>(node.children.size)
                for (child in node.children) {
                    val s = speakNode(child).trim()
                    if (s.isNotEmpty()) parts.add(s)
                }
                parts.joinToString(" ")
            }
            is MathNode.Symbol -> {
                val t = node.text.trim()
                SPOKEN_SYMBOL_MAP[t]
                    ?: SPOKEN_FUNCTION_MAP[t]
                    ?: t
            }
            is MathNode.BigOp -> {
                when (node.symbol) {
                    "∑" -> "the sum"
                    "∏" -> "the product"
                    "∐" -> "the coproduct"
                    "∫" -> "the integral"
                    "∬" -> "the double integral"
                    "∭" -> "the triple integral"
                    "∮" -> "the contour integral"
                    "⋃" -> "the union"
                    "⋂" -> "the intersection"
                    "bigoplus" -> "the direct sum"
                    "bigotimes" -> "the tensor product"
                    else -> SPOKEN_FUNCTION_MAP[node.symbol] ?: node.symbol
                }
            }
            is MathNode.Fraction -> {
                val num = speakNode(node.numerator).trim()
                val den = speakNode(node.denominator).trim()
                if (!node.hasBar) {
                    "$num choose $den"
                } else if (isSimpleSpokenToken(node.numerator) && isSimpleSpokenToken(node.denominator)) {
                    "$num over $den"
                } else {
                    "fraction, $num, over, $den, end fraction"
                }
            }
            is MathNode.Sqrt -> {
                val rad = speakNode(node.radicand).trim()
                val deg = node.degree?.let { speakNode(it).trim() }.orEmpty()
                when (deg) {
                    "" -> "the square root of $rad"
                    "3" -> "the cube root of $rad"
                    else -> "the $deg root of $rad"
                }
            }
            is MathNode.SubSup -> {
                val baseNode = node.base
                val sub = node.subscript?.let { speakNode(it).trim() }.orEmpty()
                val sup = node.superscript?.let { speakNode(it).trim() }.orEmpty()

                if (baseNode is MathNode.BigOp) {
                    val opSpoken = speakNode(baseNode)
                    return buildString {
                        append(opSpoken)
                        if (sub.isNotEmpty() && sup.isNotEmpty()) {
                            append(" from ").append(sub).append(" to ").append(sup).append(" of")
                        } else if (sub.isNotEmpty()) {
                            if (baseNode.isLimitWord) {
                                append(" as ").append(sub).append(" of")
                            } else {
                                append(" over ").append(sub).append(" of")
                            }
                        } else if (sup.isNotEmpty()) {
                            append(" to ").append(sup).append(" of")
                        }
                    }
                }

                val baseSpoken = speakNode(baseNode).trim()
                buildString {
                    append(baseSpoken)
                    if (sub.isNotEmpty()) {
                        append(" sub ").append(sub)
                    }
                    if (sup.isNotEmpty()) {
                        when (sup) {
                            "2" -> append(" squared")
                            "3" -> append(" cubed")
                            "′", "'" -> append(" prime")
                            "⊤", "T" -> append(" transpose")
                            else -> append(" to the power of ").append(sup)
                        }
                    }
                }
            }
            is MathNode.Delimited -> {
                val inner = speakNode(node.content).trim()
                when {
                    node.left == "|" && node.right == "|" -> "the absolute value of $inner"
                    node.left == "‖" && node.right == "‖" -> "the norm of $inner"
                    node.left == "⟨" && node.right == "⟩" -> "inner product of $inner"
                    else -> inner
                }
            }
            is MathNode.Accent -> {
                val base = speakNode(node.base).trim()
                when (node.kind) {
                    AccentKind.HAT, AccentKind.WIDE_HAT -> "$base hat"
                    AccentKind.BAR, AccentKind.OVERLINE -> "$base bar"
                    AccentKind.UNDERLINE -> "$base underlined"
                    AccentKind.VEC, AccentKind.OVER_RIGHT_ARROW, AccentKind.OVER_LEFT_ARROW -> "vector $base"
                    AccentKind.TILDE, AccentKind.WIDE_TILDE -> "$base tilde"
                    AccentKind.DOT -> "$base dot"
                    AccentKind.DDOT -> "$base double dot"
                    AccentKind.CHECK -> "$base check"
                    AccentKind.BREVE -> "$base breve"
                    AccentKind.OVERBRACE -> "$base overbrace"
                    AccentKind.UNDERBRACE -> "$base underbrace"
                }
            }
            is MathNode.Matrix -> {
                when (node.envType) {
                    MatrixEnvType.CASES -> {
                        val cases = node.rows.mapIndexedNotNull { idx, row ->
                            val cells = row.map { speakNode(it).trim() }.filter { it.isNotEmpty() }
                            if (cells.isEmpty()) null
                            else "case ${idx + 1}: ${cells.joinToString(", ")}"
                        }
                        "piecewise: ${cases.joinToString("; ")}"
                    }
                    MatrixEnvType.ALIGNED -> {
                        node.rows.mapNotNull { row ->
                            val line = row.map { speakNode(it).trim() }.filter { it.isNotEmpty() }.joinToString(" ")
                            line.ifEmpty { null }
                        }.joinToString(". ")
                    }
                    else -> {
                        val rows = node.rows.mapIndexedNotNull { idx, row ->
                            val cells = row.map { speakNode(it).trim() }.filter { it.isNotEmpty() }
                            if (cells.isEmpty()) null
                            else "row ${idx + 1}: ${cells.joinToString(", ")}"
                        }
                        "matrix, ${rows.joinToString("; ")}"
                    }
                }
            }
            is MathNode.Styled -> {
                val inner = speakNode(node.content).trim()
                if (node.style == MathFontStyle.BLACKBOARD) {
                    when (inner) {
                        "R" -> "the real numbers"
                        "N" -> "the natural numbers"
                        "Z" -> "the integers"
                        "Q" -> "the rational numbers"
                        "C" -> "the complex numbers"
                        else -> "blackboard bold $inner"
                    }
                } else {
                    inner
                }
            }
            is MathNode.Boxed -> speakNode(node.content)
            is MathNode.Space -> ""
        }
    }

    private fun isSimpleSpokenToken(node: MathNode): Boolean = when (node) {
        is MathNode.Symbol -> node.text.length <= 3
        is MathNode.Row -> node.children.size == 1 && isSimpleSpokenToken(node.children[0])
        is MathNode.Styled -> isSimpleSpokenToken(node.content)
        else -> false
    }

    private fun buildPreview(block: Block, fallbackSpoken: String): String {
        val raw = when (block) {
            is Heading -> block.text.text
            is Paragraph -> block.text.text
            is ListItem -> "${block.marker} ${block.text.text}".trim()
            is Quote -> block.text.text
            is MathBlock -> LatexParser.toReadablePlainText(block.latex)
            is CodeBlock -> block.code.lines().firstOrNull().orEmpty()
            is TableBlock -> fallbackSpoken
            is ImageBlock -> block.alt
            is Divider -> ""
        }.replace(WHITESPACE_REGEX, " ").trim()
        val text = raw.ifBlank { fallbackSpoken }
        return if (text.length <= 120) text else text.take(117).trimEnd() + "…"
    }
}
