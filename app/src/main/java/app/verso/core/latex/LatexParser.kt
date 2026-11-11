package app.verso.core.latex

enum class SymbolCategory {
    VARIABLE,
    NUMBER,
    OPERATOR,
    FUNCTION,
    PUNCTUATION,
    TEXT
}

enum class MathFontStyle {
    NORMAL,
    BOLD,
    ITALIC,
    ROMAN,
    BLACKBOARD,
    CALLIGRAPHIC,
    FRAKTUR,
    MONOSPACE,
    SANS_SERIF
}

enum class AccentKind {
    HAT,
    WIDE_HAT,
    BAR,
    OVERLINE,
    UNDERLINE,
    VEC,
    OVER_RIGHT_ARROW,
    OVER_LEFT_ARROW,
    TILDE,
    WIDE_TILDE,
    DOT,
    DDOT,
    CHECK,
    BREVE,
    OVERBRACE,
    UNDERBRACE
}

enum class MatrixEnvType {
    PLAIN,      // matrix
    PAREN,      // pmatrix -> ( )
    BRACKET,    // bmatrix -> [ ]
    BRACE,      // Bmatrix -> { }
    VBAR,       // vmatrix -> | |
    DOUBLE_VBAR,// Vmatrix -> ‖ ‖
    CASES,      // cases   -> { on left
    ALIGNED     // align, aligned, gather, split, equation
}

sealed interface MathNode {
    data class Row(val children: List<MathNode>) : MathNode
    data class Symbol(val text: String, val category: SymbolCategory) : MathNode
    data class BigOp(val symbol: String, val isIntegral: Boolean, val isLimitWord: Boolean = false) : MathNode
    data class Fraction(
        val numerator: MathNode,
        val denominator: MathNode,
        val hasBar: Boolean = true,
        val compact: Boolean = false
    ) : MathNode
    data class Sqrt(val radicand: MathNode, val degree: MathNode? = null) : MathNode
    data class SubSup(
        val base: MathNode,
        val subscript: MathNode? = null,
        val superscript: MathNode? = null,
        val limitsUnderOver: Boolean = false
    ) : MathNode
    data class Delimited(val left: String, val content: MathNode, val right: String) : MathNode
    data class Accent(val base: MathNode, val kind: AccentKind) : MathNode
    data class Matrix(val rows: List<List<MathNode>>, val envType: MatrixEnvType) : MathNode
    data class Styled(val content: MathNode, val style: MathFontStyle) : MathNode
    data class Boxed(val content: MathNode) : MathNode
    data class Space(val em: Float) : MathNode
}

data class ParsedEquation(
    val root: MathNode,
    val tag: String? = null
)

object LatexParser {

    private const val CACHE_CAPACITY = 256
    private val TAG_REGEX = Regex("""\\tag\*?\{([^{}]*)\}""")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val DELIMITER_SIZING_COMMANDS = setOf(
        "big", "Big", "bigg", "Bigg",
        "bigl", "Bigl", "biggl", "Biggl",
        "bigr", "Bigr", "biggr", "Biggr",
        "middle"
    )

    private fun <V> createLruCache(): MutableMap<String, V> =
        java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, V>(CACHE_CAPACITY, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean =
                    size > CACHE_CAPACITY
            }
        )

    private val parseCache = createLruCache<MathNode>()
    private val equationCache = createLruCache<ParsedEquation>()
    private val plainTextCache = createLruCache<String>()

    fun parseEquation(latex: String): ParsedEquation {
        val rawTrimmed = latex.trim()
        equationCache[rawTrimmed]?.let { return it }

        var cleaned = rawTrimmed
        var tag: String? = null

        // Extract \tag{...} or \tag*{...} if present
        val tagMatch = TAG_REGEX.find(cleaned)
        if (tagMatch != null) {
            tag = tagMatch.groupValues[1].trim()
            cleaned = cleaned.removeRange(tagMatch.range).trim()
        }

        // Remove surrounding display delimiters if still present
        if (cleaned.startsWith("$$") && cleaned.endsWith("$$") && cleaned.length >= 4) {
            cleaned = cleaned.substring(2, cleaned.length - 2).trim()
        } else if (cleaned.startsWith("\\[") && cleaned.endsWith("\\]") && cleaned.length >= 4) {
            cleaned = cleaned.substring(2, cleaned.length - 2).trim()
        } else if (cleaned.startsWith("\\(") && cleaned.endsWith("\\)") && cleaned.length >= 4) {
            cleaned = cleaned.substring(2, cleaned.length - 2).trim()
        } else if (cleaned.startsWith("$") && cleaned.endsWith("$") && cleaned.length >= 2) {
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        }

        val node = parse(cleaned)
        return ParsedEquation(node, tag).also { equationCache[rawTrimmed] = it }
    }

    fun parse(latex: String): MathNode {
        val trimmed = latex.trim()
        if (trimmed.isEmpty()) return MathNode.Row(emptyList())
        parseCache[trimmed]?.let { return it }

        val parser = ParserState(trimmed)
        return parser.parseTopLevel().also { parseCache[trimmed] = it }
    }

    /**
     * Converts a LaTeX expression into a clean, readable Unicode string suitable for
     * Table of Contents titles, search previews, and accessibility labels.
     */
    fun toReadablePlainText(latex: String): String {
        val trimmed = latex.trim()
        if (trimmed.isEmpty()) return ""
        plainTextCache[trimmed]?.let { return it }

        val node = parse(trimmed)
        return nodeToPlainText(node).replace(WHITESPACE_REGEX, " ").trim().also {
            plainTextCache[trimmed] = it
        }
    }

    private fun nodeToPlainText(node: MathNode): String = when (node) {
        is MathNode.Row -> node.children.joinToString("") { nodeToPlainText(it) }
        is MathNode.Symbol -> when (node.category) {
            SymbolCategory.OPERATOR -> " ${node.text} "
            SymbolCategory.FUNCTION -> "${node.text} "
            else -> node.text
        }
        is MathNode.BigOp -> node.symbol
        is MathNode.Fraction -> {
            val num = nodeToPlainText(node.numerator).trim()
            val den = nodeToPlainText(node.denominator).trim()
            if (node.hasBar) {
                val n = if (num.length > 1) "($num)" else num
                val d = if (den.length > 1) "($den)" else den
                "$n/$d"
            } else {
                "($num; $den)"
            }
        }
        is MathNode.Sqrt -> {
            val rad = nodeToPlainText(node.radicand).trim()
            val deg = node.degree?.let { nodeToPlainText(it).trim() }
            val prefix = when (deg) {
                null, "", "2" -> "√"
                "3" -> "∛"
                "4" -> "∜"
                else -> "${toSuperscriptChars(deg)}√"
            }
            if (rad.length == 1) "$prefix$rad" else "$prefix($rad)"
        }
        is MathNode.SubSup -> {
            val b = nodeToPlainText(node.base)
            val sub = node.subscript?.let { s ->
                val raw = nodeToPlainText(s).trim()
                toSubscriptChars(raw)
            }.orEmpty()
            val sup = node.superscript?.let { p ->
                val raw = nodeToPlainText(p).trim()
                toSuperscriptChars(raw)
            }.orEmpty()
            "$b$sub$sup"
        }
        is MathNode.Delimited -> {
            val l = if (node.left == ".") "" else node.left
            val r = if (node.right == ".") "" else node.right
            "$l${nodeToPlainText(node.content)}$r"
        }
        is MathNode.Accent -> {
            val baseStr = nodeToPlainText(node.base)
            when (node.kind) {
                AccentKind.HAT, AccentKind.WIDE_HAT -> "$baseStr\u0302"
                AccentKind.BAR, AccentKind.OVERLINE -> "$baseStr\u0304"
                AccentKind.UNDERLINE -> "$baseStr\u0332"
                AccentKind.VEC, AccentKind.OVER_RIGHT_ARROW -> "$baseStr\u20D7"
                AccentKind.OVER_LEFT_ARROW -> "$baseStr\u20D6"
                AccentKind.TILDE, AccentKind.WIDE_TILDE -> "$baseStr\u0303"
                AccentKind.DOT -> "$baseStr\u0307"
                AccentKind.DDOT -> "$baseStr\u0308"
                AccentKind.CHECK -> "$baseStr\u030C"
                AccentKind.BREVE -> "$baseStr\u0306"
                AccentKind.OVERBRACE, AccentKind.UNDERBRACE -> baseStr
            }
        }
        is MathNode.Matrix -> {
            node.rows.joinToString("; ") { row ->
                row.joinToString(", ") { cell -> nodeToPlainText(cell).trim() }
            }
        }
        is MathNode.Styled -> {
            val raw = nodeToPlainText(node.content)
            when (node.style) {
                MathFontStyle.BLACKBOARD -> LatexSymbolMap.mapBlackboard(raw)
                MathFontStyle.CALLIGRAPHIC -> LatexSymbolMap.mapCalligraphic(raw)
                MathFontStyle.FRAKTUR -> LatexSymbolMap.mapFraktur(raw)
                else -> raw
            }
        }
        is MathNode.Boxed -> "[${nodeToPlainText(node.content)}]"
        is MathNode.Space -> if (node.em >= 0.15f) " " else ""
    }

    private fun toSuperscriptChars(text: String): String {
        val allMappable = text.all { it in LatexSymbolMap.SUPERSCRIPT_MAP }
        return if (allMappable && text.isNotEmpty()) {
            text.map { LatexSymbolMap.SUPERSCRIPT_MAP[it]!! }.joinToString("")
        } else {
            "^($text)"
        }
    }

    private fun toSubscriptChars(text: String): String {
        val allMappable = text.all { it in LatexSymbolMap.SUBSCRIPT_MAP }
        return if (allMappable && text.isNotEmpty()) {
            text.map { LatexSymbolMap.SUBSCRIPT_MAP[it]!! }.joinToString("")
        } else {
            "_($text)"
        }
    }

    private class ParserState(private val src: String) {
        private var pos = 0
        private val len = src.length

        fun parseTopLevel(): MathNode {
            // Check if top-level has \\ line breaks outside environments
            val rows = parseRowsUntil(endCommand = null)
            return if (rows.size == 1 && rows[0].size == 1) {
                rows[0][0]
            } else {
                MathNode.Matrix(rows, MatrixEnvType.ALIGNED)
            }
        }

        private fun parseRowsUntil(endCommand: String?): List<List<MathNode>> {
            val rows = mutableListOf<List<MathNode>>()
            var currentCells = mutableListOf<MathNode>()
            var currentNodes = mutableListOf<MathNode>()

            fun flushCell() {
                currentCells.add(simplifyRow(currentNodes))
                currentNodes = mutableListOf()
            }

            fun flushRow() {
                flushCell()
                // Ignore trailing completely empty row after \\
                val isAllEmpty = currentCells.all { it is MathNode.Row && it.children.isEmpty() }
                if (!isAllEmpty || rows.isEmpty()) {
                    rows.add(currentCells)
                }
                currentCells = mutableListOf()
            }

            while (pos < len) {
                skipWhitespace()
                if (pos >= len) break

                if (endCommand != null && src.startsWith(endCommand, pos)) {
                    pos += endCommand.length
                    break
                }

                // Check for \right when parsing inside \left ... \right
                if (endCommand == "\\right" && src.startsWith("\\right", pos) && !isLetterAt(pos + 6)) {
                    break
                }

                // Check for '}' when parsing inside a group
                if (endCommand == "}" && src[pos] == '}') {
                    pos++
                    break
                }

                // Check for row break \\
                if (pos + 1 < len && src[pos] == '\\' && src[pos + 1] == '\\') {
                    pos += 2
                    skipWhitespace()
                    // Optional [spacing] after \\
                    if (pos < len && src[pos] == '[') {
                        val closeBracket = src.indexOf(']', pos + 1)
                        if (closeBracket != -1) {
                            pos = closeBracket + 1
                        }
                    }
                    flushRow()
                    continue
                }

                // Check for column separator &
                if (src[pos] == '&') {
                    pos++
                    flushCell()
                    continue
                }

                // Check for \over or \choose inside current cell
                if (src.startsWith("\\over", pos) && !isLetterAt(pos + 5)) {
                    pos += 5
                    val num = simplifyRow(currentNodes)
                    currentNodes = mutableListOf()
                    val restNodes = mutableListOf<MathNode>()
                    while (pos < len) {
                        skipWhitespace()
                        if (pos >= len) break
                        if (endCommand != null && src.startsWith(endCommand, pos)) break
                        if (src[pos] == '}' || src[pos] == '&' || (pos + 1 < len && src[pos] == '\\' && src[pos + 1] == '\\')) break
                        val n = parseNextAtomWithSubSup() ?: break
                        restNodes.add(n)
                    }
                    val den = simplifyRow(restNodes)
                    currentNodes.add(MathNode.Fraction(num, den, hasBar = true))
                    continue
                }

                if (src.startsWith("\\choose", pos) && !isLetterAt(pos + 7)) {
                    pos += 7
                    val num = simplifyRow(currentNodes)
                    currentNodes = mutableListOf()
                    val restNodes = mutableListOf<MathNode>()
                    while (pos < len) {
                        skipWhitespace()
                        if (pos >= len) break
                        if (endCommand != null && src.startsWith(endCommand, pos)) break
                        if (src[pos] == '}' || src[pos] == '&' || (pos + 1 < len && src[pos] == '\\' && src[pos + 1] == '\\')) break
                        val n = parseNextAtomWithSubSup() ?: break
                        restNodes.add(n)
                    }
                    val den = simplifyRow(restNodes)
                    currentNodes.add(
                        MathNode.Delimited("(", MathNode.Fraction(num, den, hasBar = false), ")")
                    )
                    continue
                }

                val node = parseNextAtomWithSubSup()
                if (node != null) {
                    currentNodes.add(node)
                } else if (pos < len && src[pos] == '}') {
                    // Stray closing brace
                    if (endCommand == "}") {
                        pos++
                        break
                    } else {
                        pos++
                    }
                }
            }

            if (currentNodes.isNotEmpty() || currentCells.isNotEmpty() || rows.isEmpty()) {
                flushRow()
            }

            return rows
        }

        private fun parseGroupOrSingleNode(): MathNode {
            skipWhitespace()
            if (pos >= len) return MathNode.Row(emptyList())
            if (src[pos] == '{') {
                pos++
                val rows = parseRowsUntil(endCommand = "}")
                return if (rows.size == 1 && rows[0].size == 1) {
                    rows[0][0]
                } else {
                    MathNode.Matrix(rows, MatrixEnvType.PLAIN)
                }
            }
            return parseSingleAtom() ?: MathNode.Row(emptyList())
        }

        private fun parseOptionalBracketArg(): MathNode? {
            skipWhitespace()
            if (pos < len && src[pos] == '[') {
                pos++
                val nodes = mutableListOf<MathNode>()
                while (pos < len && src[pos] != ']') {
                    val n = parseNextAtomWithSubSup()
                    if (n != null) nodes.add(n) else pos++
                }
                if (pos < len && src[pos] == ']') pos++
                return simplifyRow(nodes)
            }
            return null
        }

        private fun parseRawGroupText(): String {
            skipWhitespace()
            if (pos >= len) return ""
            if (src[pos] == '{') {
                pos++
                var depth = 1
                val start = pos
                while (pos < len && depth > 0) {
                    if (src[pos] == '\\' && pos + 1 < len) {
                        pos += 2
                        continue
                    }
                    if (src[pos] == '{') depth++
                    else if (src[pos] == '}') depth--
                    pos++
                }
                return src.substring(start, (pos - 1).coerceAtLeast(start))
            }
            return src[pos++].toString()
        }

        private fun parseNextAtomWithSubSup(): MathNode? {
            var base = parseSingleAtom() ?: return null

            var subscript: MathNode? = null
            var superscript: MathNode? = null
            var limitsOverride: Boolean? = null

            // Check for primes: f', f''
            var primeCount = 0
            while (pos < len && src[pos] == '\'') {
                primeCount++
                pos++
            }
            if (primeCount > 0) {
                val primes = "′".repeat(primeCount)
                superscript = MathNode.Symbol(primes, SymbolCategory.OPERATOR)
            }

            while (pos < len) {
                skipWhitespace()
                if (pos >= len) break

                if (src.startsWith("\\limits", pos) && !isLetterAt(pos + 7)) {
                    pos += 7
                    limitsOverride = true
                    continue
                }
                if (src.startsWith("\\nolimits", pos) && !isLetterAt(pos + 9)) {
                    pos += 9
                    limitsOverride = false
                    continue
                }

                val ch = src[pos]
                if (ch == '_') {
                    pos++
                    val subNode = parseGroupOrSingleNode()
                    subscript = if (subscript == null) subNode else simplifyRow(listOf(subscript, subNode))
                } else if (ch == '^') {
                    pos++
                    val supNode = parseGroupOrSingleNode()
                    superscript = if (superscript == null) supNode else simplifyRow(listOf(superscript, supNode))
                } else {
                    break
                }
            }

            if (subscript != null || superscript != null) {
                val defaultLimits = when (base) {
                    is MathNode.BigOp -> !base.isIntegral
                    is MathNode.Symbol -> base.category == SymbolCategory.FUNCTION && base.text in LatexSymbolMap.LIMIT_FUNCTIONS
                    else -> false
                }
                val useUnderOver = limitsOverride ?: defaultLimits
                base = MathNode.SubSup(
                    base = base,
                    subscript = subscript,
                    superscript = superscript,
                    limitsUnderOver = useUnderOver
                )
            }

            return base
        }

        private fun parseSingleAtom(): MathNode? {
            skipWhitespace()
            if (pos >= len) return null

            val ch = src[pos]
            if (ch == '}' || ch == '&') return null

            if (ch == '{') {
                return parseGroupOrSingleNode()
            }

            if (ch == '\\') {
                return parseCommand()
            }

            // Number run (digits and decimal point inside numbers)
            if (ch.isDigit()) {
                val start = pos
                while (pos < len && (src[pos].isDigit() || (src[pos] == '.' && pos + 1 < len && src[pos + 1].isDigit()))) {
                    pos++
                }
                return MathNode.Symbol(src.substring(start, pos), SymbolCategory.NUMBER)
            }

            // Single ASCII letter -> italic math variable
            if (ch.isLetter()) {
                pos++
                return MathNode.Symbol(ch.toString(), SymbolCategory.VARIABLE)
            }

            // Basic operators & relations
            pos++
            return when (ch) {
                '+' -> MathNode.Symbol("+", SymbolCategory.OPERATOR)
                '-' -> MathNode.Symbol("−", SymbolCategory.OPERATOR)
                '=' -> MathNode.Symbol("=", SymbolCategory.OPERATOR)
                '<' -> MathNode.Symbol("<", SymbolCategory.OPERATOR)
                '>' -> MathNode.Symbol(">", SymbolCategory.OPERATOR)
                '*' -> MathNode.Symbol("∗", SymbolCategory.OPERATOR)
                '/' -> MathNode.Symbol("/", SymbolCategory.PUNCTUATION)
                ':' -> MathNode.Symbol(":", SymbolCategory.OPERATOR)
                '~' -> MathNode.Space(0.33f)
                ',', ';', '!', '?', '.', '(', ')', '[', ']', '|' ->
                    MathNode.Symbol(ch.toString(), SymbolCategory.PUNCTUATION)
                else -> MathNode.Symbol(ch.toString(), SymbolCategory.TEXT)
            }
        }

        private fun parseCommand(): MathNode {
            pos++ // consume '\'
            if (pos >= len) return MathNode.Symbol("", SymbolCategory.TEXT)

            // Check single non-letter escaped characters: \, \; \: \! \{ \} \$ \% \& \# \_ \| \
            val first = src[pos]
            if (!first.isLetter()) {
                pos++
                return when (first) {
                    ',' -> MathNode.Space(0.17f)
                    ':' -> MathNode.Space(0.22f)
                    ';' -> MathNode.Space(0.28f)
                    '!' -> MathNode.Space(-0.08f)
                    ' ' -> MathNode.Space(0.33f)
                    '|' -> MathNode.Symbol("‖", SymbolCategory.PUNCTUATION)
                    '{', '}', '$', '%', '&', '#', '_' -> MathNode.Symbol(first.toString(), SymbolCategory.PUNCTUATION)
                    else -> MathNode.Symbol(first.toString(), SymbolCategory.TEXT)
                }
            }

            // Read alphabetic command name
            val start = pos
            while (pos < len && src[pos].isLetter()) {
                pos++
            }
            var cmd = src.substring(start, pos)
            // Support starred commands like \operatorname*
            if (pos < len && src[pos] == '*') {
                pos++
            }

            // 1. Environments: \begin{env} ... \end{env}
            if (cmd == "begin") {
                val envName = parseRawGroupText().trim()
                val endMarker = "\\end{$envName}"
                // Skip optional column alignment spec in \begin{array}{ccc} or \begin{alignedat}{2}
                skipWhitespace()
                if ((envName == "array" || envName.startsWith("alignedat") || envName == "subarray") && pos < len && src[pos] == '{') {
                    parseRawGroupText()
                }
                val rows = parseRowsUntil(endCommand = endMarker)
                val envType = when (envName.removeSuffix("*")) {
                    "pmatrix" -> MatrixEnvType.PAREN
                    "bmatrix" -> MatrixEnvType.BRACKET
                    "Bmatrix" -> MatrixEnvType.BRACE
                    "vmatrix" -> MatrixEnvType.VBAR
                    "Vmatrix" -> MatrixEnvType.DOUBLE_VBAR
                    "cases", "dcases", "rcases" -> MatrixEnvType.CASES
                    "align", "aligned", "gather", "gathered", "split", "equation", "multiline", "multline", "eqnarray" ->
                        MatrixEnvType.ALIGNED
                    else -> MatrixEnvType.PLAIN
                }
                return MathNode.Matrix(rows, envType)
            }

            // 2. Fractions and Binomials
            when (cmd) {
                "frac", "dfrac", "cfrac" -> {
                    val num = parseGroupOrSingleNode()
                    val den = parseGroupOrSingleNode()
                    return MathNode.Fraction(num, den, hasBar = true, compact = false)
                }
                "tfrac", "sfrac" -> {
                    val num = parseGroupOrSingleNode()
                    val den = parseGroupOrSingleNode()
                    return MathNode.Fraction(num, den, hasBar = true, compact = true)
                }
                "binom", "dbinom", "tbinom" -> {
                    val top = parseGroupOrSingleNode()
                    val bot = parseGroupOrSingleNode()
                    return MathNode.Delimited(
                        left = "(",
                        content = MathNode.Fraction(top, bot, hasBar = false, compact = cmd == "tbinom"),
                        right = ")"
                    )
                }
            }

            // 3. Roots
            if (cmd == "sqrt") {
                val degree = parseOptionalBracketArg()
                val radicand = parseGroupOrSingleNode()
                return MathNode.Sqrt(radicand = radicand, degree = degree)
            }

            // 4. Delimiters: \left ... \right and \bigl, \Bigl, etc.
            if (cmd == "left") {
                val leftDelim = readDelimiterToken()
                val innerRows = parseRowsUntil(endCommand = "\\right")
                val rightDelim = if (src.startsWith("\\right", pos)) {
                    pos += 6
                    readDelimiterToken()
                } else {
                    ""
                }
                val innerContent = if (innerRows.size == 1 && innerRows[0].size == 1) {
                    innerRows[0][0]
                } else {
                    MathNode.Matrix(innerRows, MatrixEnvType.PLAIN)
                }
                return MathNode.Delimited(leftDelim, innerContent, rightDelim)
            }

            if (cmd in DELIMITER_SIZING_COMMANDS) {
                val d = readDelimiterToken()
                return MathNode.Symbol(d, SymbolCategory.PUNCTUATION)
            }

            // 5. Overset, Underset, Stackrel
            when (cmd) {
                "overset", "stackrel" -> {
                    val top = parseGroupOrSingleNode()
                    val base = parseGroupOrSingleNode()
                    return MathNode.SubSup(base = base, superscript = top, limitsUnderOver = true)
                }
                "underset" -> {
                    val bot = parseGroupOrSingleNode()
                    val base = parseGroupOrSingleNode()
                    return MathNode.SubSup(base = base, subscript = bot, limitsUnderOver = true)
                }
            }

            // 6. Accents & Over/Under bars/arrows/braces
            val accentKind = when (cmd) {
                "hat" -> AccentKind.HAT
                "widehat" -> AccentKind.WIDE_HAT
                "bar" -> AccentKind.BAR
                "overline" -> AccentKind.OVERLINE
                "underline" -> AccentKind.UNDERLINE
                "vec" -> AccentKind.VEC
                "overrightarrow", "overleftrightarrow" -> AccentKind.OVER_RIGHT_ARROW
                "overleftarrow" -> AccentKind.OVER_LEFT_ARROW
                "tilde" -> AccentKind.TILDE
                "widetilde" -> AccentKind.WIDE_TILDE
                "dot" -> AccentKind.DOT
                "ddot", "dddot" -> AccentKind.DDOT
                "check" -> AccentKind.CHECK
                "breve" -> AccentKind.BREVE
                "overbrace" -> AccentKind.OVERBRACE
                "underbrace" -> AccentKind.UNDERBRACE
                else -> null
            }
            if (accentKind != null) {
                val base = parseGroupOrSingleNode()
                return MathNode.Accent(base, accentKind)
            }

            // 7. Boxed
            if (cmd == "boxed") {
                return MathNode.Boxed(parseGroupOrSingleNode())
            }

            // 8. Text and Font Styling
            when (cmd) {
                "text", "textrm", "textup", "textnormal", "mbox", "hbox" -> {
                    val raw = parseRawGroupText()
                    return MathNode.Symbol(raw, SymbolCategory.TEXT)
                }
                "textbf" -> {
                    val raw = parseRawGroupText()
                    return MathNode.Styled(MathNode.Symbol(raw, SymbolCategory.TEXT), MathFontStyle.BOLD)
                }
                "textit", "emph" -> {
                    val raw = parseRawGroupText()
                    return MathNode.Styled(MathNode.Symbol(raw, SymbolCategory.TEXT), MathFontStyle.ITALIC)
                }
                "operatorname" -> {
                    val raw = parseRawGroupText()
                    return MathNode.Symbol(raw, SymbolCategory.FUNCTION)
                }
                "mathbf", "boldsymbol", "bm" -> {
                    return MathNode.Styled(parseGroupOrSingleNode(), MathFontStyle.BOLD)
                }
                "mathit" -> {
                    return MathNode.Styled(parseGroupOrSingleNode(), MathFontStyle.ITALIC)
                }
                "mathrm", "rm" -> {
                    return MathNode.Styled(parseGroupOrSingleNode(), MathFontStyle.ROMAN)
                }
                "mathbb", "Bbb" -> {
                    val raw = parseRawGroupText()
                    return MathNode.Symbol(LatexSymbolMap.mapBlackboard(raw), SymbolCategory.VARIABLE)
                }
                "mathcal", "mathscr", "cal" -> {
                    val raw = parseRawGroupText()
                    return MathNode.Symbol(LatexSymbolMap.mapCalligraphic(raw), SymbolCategory.VARIABLE)
                }
                "mathfrak", "frak" -> {
                    val raw = parseRawGroupText()
                    return MathNode.Symbol(LatexSymbolMap.mapFraktur(raw), SymbolCategory.VARIABLE)
                }
                "mathtt", "texttt" -> {
                    return MathNode.Styled(parseGroupOrSingleNode(), MathFontStyle.MONOSPACE)
                }
                "mathsf", "textsf" -> {
                    return MathNode.Styled(parseGroupOrSingleNode(), MathFontStyle.SANS_SERIF)
                }
                "displaystyle", "textstyle", "scriptstyle", "scriptscriptstyle", "noindent", "relax" -> {
                    return MathNode.Row(emptyList())
                }
                "phantom", "hphantom", "vphantom" -> {
                    parseRawGroupText()
                    return MathNode.Space(0.5f)
                }
                "color", "textcolor" -> {
                    parseRawGroupText() // discard color name
                    return parseGroupOrSingleNode()
                }
                "cancel", "bcancel", "xcancel" -> {
                    return parseGroupOrSingleNode()
                }
            }

            // 9. Spacing commands
            when (cmd) {
                "quad" -> return MathNode.Space(1.0f)
                "qquad" -> return MathNode.Space(2.0f)
                "enspace", "thinspace" -> return MathNode.Space(0.25f)
                "medspace" -> return MathNode.Space(0.22f)
                "thickspace" -> return MathNode.Space(0.28f)
                "negthinspace" -> return MathNode.Space(-0.08f)
                "hspace", "kern", "mkern", "mskip" -> {
                    parseRawGroupText()
                    return MathNode.Space(0.4f)
                }
            }

            // 10. Negated relations: \not= \not\in \not\subset
            if (cmd == "not") {
                skipWhitespace()
                val next = parseSingleAtom()
                if (next is MathNode.Symbol) {
                    val combined = when (next.text) {
                        "=" -> "≠"
                        "∈" -> "∉"
                        "⊂" -> "⊄"
                        "⊃" -> "⊅"
                        "⊆" -> "⊈"
                        "⊇" -> "⊉"
                        "≡" -> "≢"
                        "≈" -> "≉"
                        "∼" -> "≁"
                        "∣", "|" -> "∤"
                        "∥" -> "∦"
                        else -> "${next.text}\u0338"
                    }
                    return MathNode.Symbol(combined, SymbolCategory.OPERATOR)
                }
                return MathNode.Symbol("¬", SymbolCategory.OPERATOR)
            }

            // 11. extensible arrows with text: \xrightarrow{...}, \xleftarrow{...}
            if (cmd == "xrightarrow" || cmd == "xleftarrow") {
                val sub = parseOptionalBracketArg()
                val sup = parseGroupOrSingleNode()
                val arrow = if (cmd == "xrightarrow") "⟶" else "⟵"
                return MathNode.SubSup(
                    base = MathNode.Symbol(arrow, SymbolCategory.OPERATOR),
                    subscript = sub,
                    superscript = sup,
                    limitsUnderOver = true
                )
            }

            if (cmd == "pmod") {
                val arg = parseGroupOrSingleNode()
                return MathNode.Row(
                    listOf(
                        MathNode.Space(0.35f),
                        MathNode.Symbol("(mod ", SymbolCategory.FUNCTION),
                        arg,
                        MathNode.Symbol(")", SymbolCategory.PUNCTUATION)
                    )
                )
            }

            // 12. Big Operators (\sum, \prod, \int, etc.)
            LatexSymbolMap.BIG_OPERATORS[cmd]?.let { sym ->
                val isIntegral = cmd in LatexSymbolMap.INTEGRAL_COMMANDS
                return MathNode.BigOp(symbol = sym, isIntegral = isIntegral)
            }

            // 13. Standard math functions (\sin, \cos, \lim, \log, \max, etc.)
            if (cmd in LatexSymbolMap.FUNCTION_NAMES) {
                val isLim = cmd in LatexSymbolMap.LIMIT_FUNCTIONS
                return if (isLim) {
                    MathNode.BigOp(symbol = cmd, isIntegral = false, isLimitWord = true)
                } else {
                    MathNode.Symbol(cmd, SymbolCategory.FUNCTION)
                }
            }

            // 14. Lowercase Greek (italic variable)
            LatexSymbolMap.GREEK_LOWER[cmd]?.let { greek ->
                return MathNode.Symbol(greek, SymbolCategory.VARIABLE)
            }

            // 15. Uppercase Greek (upright variable/symbol)
            LatexSymbolMap.GREEK_UPPER[cmd]?.let { greek ->
                return MathNode.Symbol(greek, SymbolCategory.NUMBER)
            }

            // 16. Operators & Relations
            LatexSymbolMap.OPERATORS_AND_RELATIONS[cmd]?.let { op ->
                return MathNode.Symbol(op, SymbolCategory.OPERATOR)
            }

            // 17. Misc symbols
            LatexSymbolMap.MISC_SYMBOLS[cmd]?.let { sym ->
                return MathNode.Symbol(sym, SymbolCategory.NUMBER)
            }

            // Fallback: unknown command rendered cleanly without backslash
            return MathNode.Symbol(cmd, SymbolCategory.FUNCTION)
        }

        private fun readDelimiterToken(): String {
            skipWhitespace()
            if (pos >= len) return ""
            val ch = src[pos]
            if (ch == '\\') {
                pos++
                if (pos >= len) return ""
                if (!src[pos].isLetter()) {
                    val c = src[pos++]
                    return when (c) {
                        '{' -> "{"
                        '}' -> "}"
                        '|' -> "‖"
                        else -> c.toString()
                    }
                }
                val start = pos
                while (pos < len && src[pos].isLetter()) pos++
                val name = src.substring(start, pos)
                return when (name) {
                    "langle" -> "⟨"
                    "rangle" -> "⟩"
                    "lfloor" -> "⌊"
                    "rfloor" -> "⌋"
                    "lceil" -> "⌈"
                    "rceil" -> "⌉"
                    "vert" -> "|"
                    "Vert" -> "‖"
                    "lbrace" -> "{"
                    "rbrace" -> "}"
                    "lbrack" -> "["
                    "rbrack" -> "]"
                    "ulcorner" -> "⌜"
                    "urcorner" -> "⌝"
                    "llcorner" -> "⌞"
                    "lrcorner" -> "⌟"
                    "uparrow" -> "↑"
                    "downarrow" -> "↓"
                    else -> LatexSymbolMap.MISC_SYMBOLS[name] ?: ""
                }
            }
            pos++
            return if (ch == '.') "" else ch.toString()
        }

        private fun simplifyRow(nodes: List<MathNode>): MathNode {
            val nonEmpty = nodes.filterNot { it is MathNode.Row && it.children.isEmpty() }
            return when (nonEmpty.size) {
                0 -> MathNode.Row(emptyList())
                1 -> nonEmpty[0]
                else -> MathNode.Row(nonEmpty)
            }
        }

        private fun skipWhitespace() {
            while (pos < len && src[pos].isWhitespace()) {
                pos++
            }
        }

        private fun isLetterAt(index: Int): Boolean {
            return index in 0 until len && src[index].isLetter()
        }
    }
}
