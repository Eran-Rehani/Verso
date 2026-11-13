package app.verso.core.markdown

import app.verso.core.model.InlineText
import app.verso.core.model.Span
import app.verso.core.model.SpanKind

object MathProtector {
    const val OPEN = '\uE000'
    const val CLOSE = '\uE001'

    data class MathSpan(val latex: String, val display: Boolean)
    data class Result(val text: String, val math: List<MathSpan>)

    private val DISPLAY_ENVS = setOf(
        "equation", "equation*",
        "align", "align*", "aligned",
        "gather", "gather*", "gathered",
        "multline", "multline*",
        "flalign", "flalign*",
        "alignat", "alignat*",
        "eqnarray", "eqnarray*",
        "displaymath",
        "matrix", "pmatrix", "bmatrix", "Bmatrix", "vmatrix", "Vmatrix", "smallmatrix",
        "cases", "dcases", "split", "array"
    )

    private val INLINE_ENVS = setOf(
        "math"
    )

    private val TRAILING_EQ_TAG_REGEX = Regex("""^\s*\(([0-9]+[a-zA-Z]?(?:\.[0-9]+[a-zA-Z]?)*|[ivxlcdmIVXLCDM]+|[A-Z]\.?[0-9]*)\)""")

    fun protect(markdown: String): Result {
        val mathList = mutableListOf<MathSpan>()
        val sb = StringBuilder()
        var i = 0
        val len = markdown.length

        // Track code blocks
        var inFencedCode = false
        var fenceChar = ' '
        var fenceLen = 0

        fun atLineStart(): Boolean {
            var j = sb.length - 1
            while (j >= 0 && sb[j] != '\n') {
                if (!sb[j].isWhitespace()) return false
                j--
            }
            return true
        }

        fun appendDisplayPlaceholder(ph: String, isLineStart: Boolean, afterCloseIdx: Int): Int {
            var afterClose = afterCloseIdx
            while (afterClose < len && (markdown[afterClose] == ' ' || markdown[afterClose] == '\t')) afterClose++
            val isLineEnd = afterClose >= len || markdown[afterClose] == '\n' || markdown[afterClose] == '\r'

            if (isLineStart && isLineEnd) {
                if (!sb.endsWith("\n\n") && sb.isNotEmpty()) {
                    if (sb.endsWith("\n")) sb.append("\n") else sb.append("\n\n")
                }
                sb.append(ph)
                if (afterClose < len) {
                    if (markdown.startsWith("\r\n", afterClose)) {
                        afterClose += 2
                    } else if (markdown[afterClose] == '\n' || markdown[afterClose] == '\r') {
                        afterClose++
                    }
                }
                sb.append("\n\n")
                return afterClose
            } else {
                sb.append(ph)
                return afterCloseIdx
            }
        }

        while (i < len) {
            // Check for fenced code block ``` or ~~~
            if ((markdown[i] == '`' || markdown[i] == '~') && atLineStart()) {
                val ch = markdown[i]
                var count = 0
                while (i + count < len && markdown[i + count] == ch) count++
                if (count >= 3) {
                    if (!inFencedCode) {
                        inFencedCode = true
                        fenceChar = ch
                        fenceLen = count
                    } else if (ch == fenceChar && count >= fenceLen) {
                        inFencedCode = false
                    }
                    sb.append(markdown.substring(i, i + count))
                    i += count
                    continue
                }
            }

            if (inFencedCode) {
                sb.append(markdown[i])
                i++
                continue
            }

            // Check for escaped char or LaTeX delimiter/environment
            if (markdown[i] == '\\' && i + 1 < len) {
                val next = markdown[i + 1]
                if (next == '$') {
                    // Escaped dollar: append both as literal
                    sb.append("\\$")
                    i += 2
                    continue
                }
                // Check \[ ... \]
                if (next == '[') {
                    val closeIdx = markdown.indexOf("\\]", i + 2)
                    if (closeIdx != -1) {
                        var content = markdown.substring(i + 2, closeIdx).trim()
                        var afterClose = closeIdx + 2
                        val tagMatch = TRAILING_EQ_TAG_REGEX.find(markdown.substring(afterClose))
                        if (tagMatch != null) {
                            val tagVal = tagMatch.groupValues[1]
                            content = "$content \\tag{$tagVal}"
                            afterClose += tagMatch.range.last + 1
                        }
                        val ph = "$OPEN${mathList.size}$CLOSE"
                        val isLineStart = atLineStart()
                        mathList.add(MathSpan(content, display = true))
                        i = appendDisplayPlaceholder(ph, isLineStart, afterClose)
                        continue
                    }
                }
                // Check \( ... \)
                if (next == '(') {
                    val closeIdx = markdown.indexOf("\\)", i + 2)
                    if (closeIdx != -1) {
                        val content = markdown.substring(i + 2, closeIdx).trim()
                        val ph = "$OPEN${mathList.size}$CLOSE"
                        mathList.add(MathSpan(content, display = false))
                        sb.append(ph)
                        i = closeIdx + 2
                        continue
                    }
                }
                // Check \begin{env} ... \end{env}
                if (markdown.startsWith("\\begin{", i)) {
                    val envCloseBrace = markdown.indexOf('}', i + 7)
                    if (envCloseBrace != -1) {
                        val envName = markdown.substring(i + 7, envCloseBrace).trim()
                        if (envName in DISPLAY_ENVS || envName in INLINE_ENVS) {
                            val endTag = "\\end{$envName}"
                            val endIdx = markdown.indexOf(endTag, envCloseBrace + 1)
                            if (endIdx != -1) {
                                val isDisplay = envName in DISPLAY_ENVS
                                val innerBody = markdown.substring(envCloseBrace + 1, endIdx).trim()
                                // Preserve matrix/cases/align wrapper so LatexParser knows the environment type
                                var fullLatex = if (envName in setOf("equation", "equation*", "displaymath", "math")) {
                                    innerBody
                                } else {
                                    "\\begin{$envName}$innerBody\\end{$envName}"
                                }
                                var afterClose = endIdx + endTag.length
                                if (isDisplay) {
                                    val tagMatch = TRAILING_EQ_TAG_REGEX.find(markdown.substring(afterClose))
                                    if (tagMatch != null) {
                                        val tagVal = tagMatch.groupValues[1]
                                        fullLatex = "$fullLatex \\tag{$tagVal}"
                                        afterClose += tagMatch.range.last + 1
                                    }
                                }
                                val ph = "$OPEN${mathList.size}$CLOSE"
                                val isLineStart = atLineStart()
                                mathList.add(MathSpan(fullLatex, display = isDisplay))
                                i = if (isDisplay) {
                                    appendDisplayPlaceholder(ph, isLineStart, afterClose)
                                } else {
                                    sb.append(ph)
                                    afterClose
                                }
                                continue
                            }
                        }
                    }
                }
                sb.append('\\')
                i++
                continue
            }

            // Check for inline code `...`
            if (markdown[i] == '`') {
                var tickCount = 0
                while (i + tickCount < len && markdown[i + tickCount] == '`') tickCount++
                val ticks = markdown.substring(i, i + tickCount)
                val endTicks = markdown.indexOf(ticks, i + tickCount)
                if (endTicks != -1) {
                    sb.append(markdown.substring(i, endTicks + tickCount))
                    i = endTicks + tickCount
                    continue
                } else {
                    sb.append(ticks)
                    i += tickCount
                    continue
                }
            }

            // Check for $$ display math
            if (i + 1 < len && markdown[i] == '$' && markdown[i + 1] == '$') {
                val closeIdx = markdown.indexOf("$$", i + 2)
                if (closeIdx != -1) {
                    var content = markdown.substring(i + 2, closeIdx).trim()
                    var afterClose = closeIdx + 2
                    val tagMatch = TRAILING_EQ_TAG_REGEX.find(markdown.substring(afterClose))
                    if (tagMatch != null) {
                        val tagVal = tagMatch.groupValues[1]
                        content = "$content \\tag{$tagVal}"
                        afterClose += tagMatch.range.last + 1
                    }
                    val ph = "$OPEN${mathList.size}$CLOSE"
                    val isLineStart = atLineStart()
                    mathList.add(MathSpan(content, display = true))
                    i = appendDisplayPlaceholder(ph, isLineStart, afterClose)
                    continue
                }
            }

            // Check for single $ inline math
            if (markdown[i] == '$') {
                val nextChar = if (i + 1 < len) markdown[i + 1] else ' '
                if (!nextChar.isWhitespace() && nextChar != '$') {
                    // Look for matching closing $ (allowing soft single newlines, but stopping at double newlines)
                    var closeIdx = -1
                    var k = i + 1
                    var consecutiveNewlines = 0
                    while (k < len) {
                        if (markdown[k] == '\\') {
                            k += 2
                            continue
                        }
                        if (markdown[k] == '$') {
                            val prevChar = markdown[k - 1]
                            val afterChar = if (k + 1 < len) markdown[k + 1] else ' '
                            if (!prevChar.isWhitespace() && !afterChar.isDigit()) {
                                closeIdx = k
                                break
                            }
                        }
                        if (markdown[k] == '\n') {
                            consecutiveNewlines++
                            if (consecutiveNewlines >= 2 || (k - i) > 500) {
                                // Inline $ cannot cross paragraph boundaries or exceed reasonable length
                                break
                            }
                        } else if (!markdown[k].isWhitespace()) {
                            consecutiveNewlines = 0
                        }
                        k++
                    }

                    if (closeIdx != -1) {
                        val content = markdown.substring(i + 1, closeIdx).trim()
                        val ph = "$OPEN${mathList.size}$CLOSE"
                        mathList.add(MathSpan(content, display = false))
                        sb.append(ph)
                        i = closeIdx + 1
                        continue
                    }
                }
            }

            sb.append(markdown[i])
            i++
        }

        return Result(sb.toString(), mathList)
    }

    /**
     * Splits a text that may contain display math placeholders (`OPEN idx CLOSE`)
     * into alternating paragraphs and [MathBlock]s. If no display math placeholders are found,
     * invokes [onParagraph] with the expanded inline text.
     */
    fun splitDisplayMath(
        text: String,
        existingSpans: List<Span>,
        mathList: List<MathSpan>,
        onParagraph: (InlineText) -> Unit,
        onMathBlock: (String) -> Unit
    ) {
        if (!text.contains(OPEN) || mathList.none { it.display }) {
            val expanded = expandPlaceholders(text, existingSpans, mathList)
            if (expanded.text.isNotBlank()) {
                onParagraph(expanded)
            }
            return
        }

        var currentIndex = 0
        while (currentIndex < text.length) {
            val openPos = text.indexOf(OPEN, currentIndex)
            if (openPos == -1) {
                val remText = text.substring(currentIndex)
                val exp = expandPlaceholders(remText, emptyList(), mathList)
                if (exp.text.isNotBlank()) {
                    onParagraph(exp)
                }
                break
            }

            val closePos = text.indexOf(CLOSE, openPos + 1)
            if (closePos == -1) {
                val remText = text.substring(currentIndex)
                val exp = expandPlaceholders(remText, emptyList(), mathList)
                if (exp.text.isNotBlank()) {
                    onParagraph(exp)
                }
                break
            }

            val numStr = text.substring(openPos + 1, closePos)
            val mathIdx = numStr.toIntOrNull()
            if (mathIdx != null && mathIdx in mathList.indices && mathList[mathIdx].display) {
                // Emit preceding text
                if (openPos > currentIndex) {
                    val preText = text.substring(currentIndex, openPos)
                    val exp = expandPlaceholders(preText, emptyList(), mathList)
                    if (exp.text.isNotBlank()) {
                        onParagraph(exp)
                    }
                }
                // Emit display MathBlock
                onMathBlock(mathList[mathIdx].latex)
                currentIndex = closePos + 1
            } else {
                // Regular inline math or non-display math placeholder, advance to next
                val nextChunkEnd = closePos + 1
                val chunkText = text.substring(currentIndex, nextChunkEnd)
                val exp = expandPlaceholders(chunkText, emptyList(), mathList)
                if (exp.text.isNotBlank()) {
                    onParagraph(exp)
                }
                currentIndex = nextChunkEnd
            }
        }
    }

    fun expandPlaceholders(
        text: String,
        existingSpans: List<Span>,
        mathList: List<MathSpan>
    ): InlineText {
        if (!text.contains(OPEN)) {
            return InlineText(text, existingSpans)
        }

        val sb = StringBuilder()
        val indexMap = IntArray(text.length + 1)
        val mathSpans = mutableListOf<Span>()
        var i = 0

        while (i < text.length) {
            indexMap[i] = sb.length
            if (text[i] == OPEN) {
                val closePos = text.indexOf(CLOSE, i + 1)
                if (closePos != -1) {
                    val numStr = text.substring(i + 1, closePos)
                    val idx = numStr.toIntOrNull()
                    if (idx != null && idx in mathList.indices) {
                        val mathContent = mathList[idx].latex
                        val start = sb.length
                        sb.append(mathContent)
                        val end = sb.length
                        mathSpans.add(Span(start, end, SpanKind.Math))
                        for (k in i until closePos + 1) {
                            indexMap[k] = start
                        }
                        i = closePos + 1
                        continue
                    }
                }
            }
            sb.append(text[i])
            i++
        }
        indexMap[text.length] = sb.length

        val newText = sb.toString()
        val finalSpans = mutableListOf<Span>()
        for (span in existingSpans) {
            val s = indexMap[span.start.coerceIn(0, text.length)]
            val e = indexMap[span.end.coerceIn(0, text.length)]
            if (e > s) {
                finalSpans.add(Span(s, e, span.kind, span.href))
            }
        }
        finalSpans.addAll(mathSpans)

        return InlineText(newText, finalSpans)
    }

    private val UNDELIMITED_LATEX_CMD_REGEX = Regex(
        """\\(?:frac|dfrac|tfrac|cfrac|sqrt|sum|prod|int|iint|iiint|oint|lim|binom|vec|hat|widehat|bar|overline|underline|tilde|widetilde|dot|ddot|mathbb|mathcal|mathbf|mathrm|alpha|beta|gamma|delta|epsilon|varepsilon|zeta|eta|theta|vartheta|iota|kappa|lambda|mu|nu|xi|pi|rho|sigma|tau|upsilon|phi|varphi|chi|psi|omega|Gamma|Delta|Theta|Lambda|Xi|Pi|Sigma|Phi|Psi|Omega|nabla|partial|infty|hbar|pm|mp|times|cdot|leq|geq|neq|approx|equiv|in|notin|subset|subseteq|cup|cap|to|rightarrow|Rightarrow|iff)\b(?:\s*(?:\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}|\[[^\[\]]*\]|[_^](?:\{[^{}]*\}|\\[a-zA-Z]+|[a-zA-Z0-9])))*"""
    )

    private const val UNICODE_SCRIPT_CHARS = "⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ⁿⁱ₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎ₐₑₕᵢⱼₖₗₘₙₒₚᵣₛₜᵤᵥₓ"
    private const val MATH_ATOM_PATTERN =
        """(?:[√∛∜∫∬∭∮∑∏∂∇]\s*(?:\([^()]+\)|[a-zA-Zα-ωΑ-Ω0-9$UNICODE_SCRIPT_CHARS]+)|[a-zA-Zα-ωΑ-Ω0-9]+[$UNICODE_SCRIPT_CHARS]+|[a-zA-Zα-ωΑ-Ω0-9]+[_^](?:\{[^{}]*\}|[a-zA-Z0-9]+)|[a-zA-Zα-ωΑ-Ω]\([a-zA-Zα-ωΑ-Ω0-9,\s+\-−]+\)|(?<![a-zA-Z])[a-zA-Zα-ωΑ-Ω](?![a-zA-Z])|[0-9]+(?:\.[0-9]+)?)"""

    private val UNDELIMITED_UNICODE_EQ_REGEX = Regex(
        """$MATH_ATOM_PATTERN(?:\s*[+\-−·×*/±∓]\s*$MATH_ATOM_PATTERN)*\s*[=≠≤≥≈≡≔≕∈∉⊂⊆⊃⊇→⇒⇔]\s*$MATH_ATOM_PATTERN(?:\s*[+\-−·×*/±∓=≠≤≥≈≡]\s*$MATH_ATOM_PATTERN)*"""
    )

    /**
     * Scans uncovered text segments in [inlineText] for undelimited LaTeX command formulas
     * or inline Unicode mathematical equations and marks them with [SpanKind.Math].
     */
    fun detectUndelimitedInlineMath(inlineText: InlineText): InlineText {
        val raw = inlineText.text
        if (raw.length < 3) return inlineText

        val hasPotentialMath = raw.contains('\\') || raw.contains('^') || raw.contains('_') ||
            raw.any {
                it in "=≠≤≥≈≡≔≕∈∉⊂⊆±∓√∛∜∫∑∏∂∇" ||
                    it in '\u0370'..'\u03FF' ||
                    it in "⁰¹²³⁴⁵⁶⁷⁸⁹₀₁₂₃₄₅₆₇₈₉"
            }
        if (!hasPotentialMath) return inlineText

        val existingProtected = inlineText.spans.filter { it.kind == SpanKind.Math || it.kind == SpanKind.Code }
        fun isOverlapping(start: Int, end: Int): Boolean {
            return existingProtected.any { start < it.end && end > it.start }
        }

        val addedSpans = mutableListOf<Span>()

        for (match in UNDELIMITED_LATEX_CMD_REGEX.findAll(raw)) {
            var s = match.range.first
            var e = match.range.last + 1
            // Trim trailing punctuation like '.', ',', ';' that belongs to the sentence
            while (e > s && (raw[e - 1] == '.' || raw[e - 1] == ',' || raw[e - 1] == ';' || raw[e - 1].isWhitespace())) {
                e--
            }
            if (e - s >= 2 && !isOverlapping(s, e)) {
                addedSpans.add(Span(s, e, SpanKind.Math))
            }
        }

        for (match in UNDELIMITED_UNICODE_EQ_REGEX.findAll(raw)) {
            val s = match.range.first
            var e = match.range.last + 1
            while (e > s && (raw[e - 1] == '.' || raw[e - 1] == ',' || raw[e - 1] == ';' || raw[e - 1].isWhitespace())) {
                e--
            }
            val overlapsAdded = addedSpans.any { s < it.end && e > it.start }
            if (e - s >= 3 && !isOverlapping(s, e) && !overlapsAdded) {
                addedSpans.add(Span(s, e, SpanKind.Math))
            }
        }

        if (addedSpans.isEmpty()) return inlineText
        return InlineText(raw, inlineText.spans + addedSpans)
    }
}
