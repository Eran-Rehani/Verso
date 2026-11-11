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
        "eqnarray", "eqnarray*",
        "displaymath",
        "matrix", "pmatrix", "bmatrix", "Bmatrix", "vmatrix", "Vmatrix",
        "cases", "dcases", "split"
    )

    private val INLINE_ENVS = setOf(
        "math"
    )

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
                        val content = markdown.substring(i + 2, closeIdx).trim()
                        val ph = "$OPEN${mathList.size}$CLOSE"
                        val isLineStart = atLineStart()
                        mathList.add(MathSpan(content, display = true))
                        i = appendDisplayPlaceholder(ph, isLineStart, closeIdx + 2)
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
                                val fullLatex = if (envName in setOf("equation", "equation*", "displaymath", "math")) {
                                    innerBody
                                } else {
                                    "\\begin{$envName}$innerBody\\end{$envName}"
                                }
                                val ph = "$OPEN${mathList.size}$CLOSE"
                                val isLineStart = atLineStart()
                                mathList.add(MathSpan(fullLatex, display = isDisplay))
                                i = if (isDisplay) {
                                    appendDisplayPlaceholder(ph, isLineStart, endIdx + endTag.length)
                                } else {
                                    sb.append(ph)
                                    endIdx + endTag.length
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
                    val content = markdown.substring(i + 2, closeIdx).trim()
                    val ph = "$OPEN${mathList.size}$CLOSE"
                    val isLineStart = atLineStart()
                    mathList.add(MathSpan(content, display = true))
                    i = appendDisplayPlaceholder(ph, isLineStart, closeIdx + 2)
                    continue
                }
            }

            // Check for single $ inline math
            if (markdown[i] == '$') {
                val nextChar = if (i + 1 < len) markdown[i + 1] else ' '
                if (!nextChar.isWhitespace() && nextChar != '$') {
                    // Look for matching closing $
                    var closeIdx = -1
                    var k = i + 1
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
                            // Inline $ cannot cross newlines
                            break
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
}
