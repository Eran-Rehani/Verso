package app.verso.core.markdown

object MathProtector {
    const val OPEN = '\uE000'
    const val CLOSE = '\uE001'

    data class MathSpan(val latex: String, val display: Boolean)
    data class Result(val text: String, val math: List<MathSpan>)

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

            // Check for escaped char
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
                        mathList.add(MathSpan(content, display = true))
                        sb.append(ph)
                        i = closeIdx + 2
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
                    mathList.add(MathSpan(content, display = true))

                    // If display math is on its own line:
                    val isLineStart = atLineStart()
                    var afterClose = closeIdx + 2
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
                        i = afterClose
                    } else {
                        sb.append(ph)
                        i = closeIdx + 2
                    }
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
}
