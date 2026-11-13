package app.verso.core.pdf

import app.verso.core.markdown.MathProtector
import app.verso.core.model.Block
import app.verso.core.model.Heading
import app.verso.core.model.InlineText
import app.verso.core.model.ListItem
import app.verso.core.model.MathBlock
import app.verso.core.model.Paragraph
import app.verso.core.text.TextCleaner
import kotlin.math.roundToInt

object PdfAssembler {
    private val SENTENCE_END_REGEX = Regex("[.!?:;][\"'”’)x\\]]*$")
    private val BULLET_START_REGEX = Regex("^[•▪◦●]\\s+")

    fun assemble(lines: List<PdfLine>): List<Block> {
        if (lines.isEmpty()) return emptyList()

        // Clean lines & expand ligatures
        val cleanedLines = lines.map { line ->
            var text = TextCleaner.clean(line.text)
            text = text.replace("ﬁ", "fi")
                .replace("ﬂ", "fl")
                .replace("ﬀ", "ff")
                .replace("ﬃ", "ffi")
                .replace("ﬄ", "ffl")
            line.copy(text = text.trim())
        }.filter { it.text.isNotBlank() }

        if (cleanedLines.isEmpty()) return emptyList()

        // 1. Compute dominant body size (rounded to 0.5pt)
        val sizeCharCounts = mutableMapOf<Float, Int>()
        for (line in cleanedLines) {
            val roundedSize = (line.fontSize * 2f).roundToInt() / 2f
            sizeCharCounts[roundedSize] = (sizeCharCounts[roundedSize] ?: 0) + line.text.length
        }
        val bodySize = sizeCharCounts.maxByOrNull { it.value }?.key ?: 10f

        // 2. Compute pitch: median of positive baseline deltas (< 3 * bodySize)
        val deltas = mutableListOf<Float>()
        for (i in 0 until cleanedLines.size - 1) {
            val cur = cleanedLines[i]
            val next = cleanedLines[i + 1]
            if (cur.page == next.page && cur.column == next.column) {
                val delta = next.baseline - cur.baseline
                if (delta > 0.5f && delta < (3f * bodySize)) {
                    deltas.add(delta)
                }
            }
        }
        val pitch = if (deltas.isNotEmpty()) {
            deltas.sorted()[deltas.size / 2]
        } else {
            1.2f * bodySize
        }

        // 3. Compute left margin per (page, column)
        val marginMap = mutableMapOf<Pair<Int, Int>, Float>()
        val pageColGroups = cleanedLines.groupBy { it.page to it.column }
        for ((key, colLines) in pageColGroups) {
            val x0Counts = mutableMapOf<Int, Int>()
            for (line in colLines) {
                val roundedX0 = line.x0.roundToInt()
                x0Counts[roundedX0] = (x0Counts[roundedX0] ?: 0) + 1
            }
            val dominantX0 = x0Counts.maxByOrNull { it.value }?.key?.toFloat() ?: 0f
            marginMap[key] = dominantX0
        }

        val blocks = mutableListOf<Block>()

        var currentType: CurrentType = CurrentType.None
        var currentText = StringBuilder()
        var currentLevel = 0
        var currentBulletMarker = ""
        var currentBulletX0 = 0f
        var lastLine: PdfLine? = null

        var inDisplayMath = false
        var displayMathClosingTag: String? = null
        val displayMathLines = mutableListOf<String>()

        fun flush() {
            if (inDisplayMath && displayMathLines.isNotEmpty()) {
                val fullMath = displayMathLines.joinToString("\n")
                blocks.add(MathBlock(fullMath))
                inDisplayMath = false
                displayMathClosingTag = null
                displayMathLines.clear()
            }
            val text = currentText.toString().trim()
            if (text.isNotEmpty()) {
                val hasMath = text.contains('$') || text.contains('\\')
                val protected = if (hasMath) MathProtector.protect(text) else null

                when (currentType) {
                    CurrentType.Heading -> {
                        val inline = if (protected != null && protected.math.isNotEmpty()) {
                            MathProtector.expandPlaceholders(protected.text, emptyList(), protected.math)
                        } else {
                            InlineText.plain(text)
                        }
                        blocks.add(Heading(currentLevel, MathProtector.detectUndelimitedInlineMath(inline)))
                    }
                    CurrentType.ListItem -> {
                        val inline = if (protected != null && protected.math.isNotEmpty()) {
                            MathProtector.expandPlaceholders(protected.text, emptyList(), protected.math)
                        } else {
                            InlineText.plain(text)
                        }
                        blocks.add(ListItem(0, currentBulletMarker, MathProtector.detectUndelimitedInlineMath(inline)))
                    }
                    CurrentType.Paragraph -> {
                        if (protected != null && protected.math.any { it.display }) {
                            MathProtector.splitDisplayMath(
                                text = protected.text,
                                existingSpans = emptyList(),
                                mathList = protected.math,
                                onParagraph = { blocks.add(Paragraph(MathProtector.detectUndelimitedInlineMath(it))) },
                                onMathBlock = { blocks.add(MathBlock(it)) }
                            )
                        } else {
                            val inline = if (protected != null && protected.math.isNotEmpty()) {
                                MathProtector.expandPlaceholders(protected.text, emptyList(), protected.math)
                            } else {
                                InlineText.plain(text)
                            }
                            blocks.add(Paragraph(MathProtector.detectUndelimitedInlineMath(inline)))
                        }
                    }
                    CurrentType.None -> {}
                }
            }
            currentText = StringBuilder()
            currentType = CurrentType.None
        }

        for (line in cleanedLines) {
            val leftMargin = marginMap[line.page to line.column] ?: 0f
            val trimmedLine = line.text.trim()

            // If we are currently buffering a multi-line display math block
            if (inDisplayMath) {
                val closing = displayMathClosingTag
                if (closing != null && trimmedLine.contains(closing)) {
                    val closeIdx = trimmedLine.indexOf(closing)
                    val beforeClose = trimmedLine.substring(0, closeIdx).trim()
                    if (beforeClose.isNotEmpty()) {
                        displayMathLines.add(beforeClose)
                    }
                    val after = trimmedLine.substring(closeIdx + closing.length).trim()
                    val tagMatch = Regex("""^\(([0-9]+[a-zA-Z]?(?:\.[0-9]+[a-zA-Z]?)*|[ivxlcdmIVXLCDM]+|[A-Z]\.?[0-9]*)\)""").find(after)
                    var fullMath = displayMathLines.joinToString("\n").trim()
                    if (tagMatch != null) {
                        fullMath = "$fullMath \\tag{${tagMatch.groupValues[1]}}"
                    }
                    blocks.add(MathBlock(fullMath))
                    inDisplayMath = false
                    displayMathClosingTag = null
                    displayMathLines.clear()
                } else {
                    displayMathLines.add(line.text)
                }
                lastLine = line
                continue
            }

            // Check if line is an isolated equation number tag following a MathBlock: e.g. "(1)" or "(2.3)"
            if (blocks.isNotEmpty() && blocks.last() is MathBlock && Regex("""^\(([0-9]+[a-zA-Z]?(?:\.[0-9]+[a-zA-Z]?)*|[ivxlcdmIVXLCDM]+|[A-Z]\.?[0-9]*)\)$""").matches(trimmedLine)) {
                val lastBlock = blocks.removeAt(blocks.size - 1) as MathBlock
                val tagStr = trimmedLine.removePrefix("(").removeSuffix(")")
                blocks.add(MathBlock("${lastBlock.latex} \\tag{$tagStr}"))
                lastLine = line
                continue
            }

            // Heading check
            val isBigHeading = line.fontSize >= (1.15f * bodySize)
            val isBoldShortHeading = line.bold && line.text.length < 100 &&
                    !line.text.endsWith(".") && !line.text.endsWith(",") && !line.text.endsWith(";") &&
                    line.fontSize in (0.9f * bodySize)..(1.15f * bodySize)

            if (isBigHeading || isBoldShortHeading) {
                val level = when {
                    line.fontSize >= 1.6f * bodySize -> 1
                    line.fontSize >= 1.3f * bodySize -> 2
                    else -> 3
                }

                // Check if can merge consecutive heading lines of equal size on one page
                if (currentType == CurrentType.Heading &&
                    lastLine != null &&
                    lastLine.page == line.page &&
                    lastLine.fontSize == line.fontSize &&
                    (line.baseline - lastLine.baseline) <= (1.6f * line.fontSize)
                ) {
                    currentText.append(" ").append(line.text)
                    lastLine = line
                    continue
                } else {
                    flush()
                    currentType = CurrentType.Heading
                    currentLevel = level
                    currentText.append(line.text)
                    lastLine = line
                    continue
                }
            }

            // Bullet list check
            if (BULLET_START_REGEX.containsMatchIn(line.text)) {
                flush()
                currentType = CurrentType.ListItem
                currentBulletMarker = "•"
                currentBulletX0 = line.x0
                val stripped = line.text.replace(BULLET_START_REGEX, "").trim()
                currentText.append(stripped)
                lastLine = line
                continue
            }

            // Continuation of bullet item
            if (currentType == CurrentType.ListItem &&
                lastLine != null &&
                line.x0 > (currentBulletX0 + 1f) &&
                !BULLET_START_REGEX.containsMatchIn(line.text)
            ) {
                appendJoinedText(currentText, line.text)
                lastLine = line
                continue
            }

            // Display math checks: $$, \[, or \begin{env}
            if (trimmedLine.startsWith("$$")) {
                flush()
                val closeIdx = trimmedLine.indexOf("$$", 2)
                if (closeIdx != -1) {
                    val raw = trimmedLine.substring(2, closeIdx).trim()
                    val after = trimmedLine.substring(closeIdx + 2).trim()
                    val tagMatch = Regex("""^\(([0-9]+[a-zA-Z]?(?:\.[0-9]+[a-zA-Z]?)*|[ivxlcdmIVXLCDM]+|[A-Z]\.?[0-9]*)\)""").find(after)
                    val content = if (tagMatch != null) "$raw \\tag{${tagMatch.groupValues[1]}}" else raw
                    blocks.add(MathBlock(content))
                } else {
                    inDisplayMath = true
                    displayMathClosingTag = "$$"
                    val rem = trimmedLine.removePrefix("$$").trim()
                    if (rem.isNotEmpty()) displayMathLines.add(rem)
                }
                lastLine = line
                continue
            }

            if (trimmedLine.startsWith("\\[")) {
                flush()
                val closeIdx = trimmedLine.indexOf("\\]", 2)
                if (closeIdx != -1) {
                    val raw = trimmedLine.substring(2, closeIdx).trim()
                    val after = trimmedLine.substring(closeIdx + 2).trim()
                    val tagMatch = Regex("""^\(([0-9]+[a-zA-Z]?(?:\.[0-9]+[a-zA-Z]?)*|[ivxlcdmIVXLCDM]+|[A-Z]\.?[0-9]*)\)""").find(after)
                    val content = if (tagMatch != null) "$raw \\tag{${tagMatch.groupValues[1]}}" else raw
                    blocks.add(MathBlock(content))
                } else {
                    inDisplayMath = true
                    displayMathClosingTag = "\\]"
                    val rem = trimmedLine.removePrefix("\\[").trim()
                    if (rem.isNotEmpty()) displayMathLines.add(rem)
                }
                lastLine = line
                continue
            }

            if (trimmedLine.startsWith("\\begin{")) {
                val closeBrace = trimmedLine.indexOf('}', 7)
                if (closeBrace != -1) {
                    val envName = trimmedLine.substring(7, closeBrace).trim()
                    val endTag = "\\end{$envName}"
                    flush()
                    if (trimmedLine.contains(endTag)) {
                        val closeIdx = trimmedLine.indexOf(endTag)
                        val after = trimmedLine.substring(closeIdx + endTag.length).trim()
                        val tagMatch = Regex("""^\(([0-9]+[a-zA-Z]?(?:\.[0-9]+[a-zA-Z]?)*|[ivxlcdmIVXLCDM]+|[A-Z]\.?[0-9]*)\)""").find(after)
                        val baseContent = trimmedLine.substring(0, closeIdx + endTag.length).trim()
                        val content = if (tagMatch != null) "$baseContent \\tag{${tagMatch.groupValues[1]}}" else baseContent
                        blocks.add(MathBlock(content))
                    } else {
                        inDisplayMath = true
                        displayMathClosingTag = endTag
                        displayMathLines.add(line.text)
                    }
                    lastLine = line
                    continue
                }
            }

            // Standalone formula line in PDF text
            val isStandaloneFormulaLine = isDisplayFormulaLine(trimmedLine, line.fontSize, bodySize)
            if (isStandaloneFormulaLine) {
                flush()
                blocks.add(MathBlock(trimmedLine))
                lastLine = line
                continue
            }

            // Paragraph handling: check if new paragraph starts
            val prevEndedSentence = lastLine != null && SENTENCE_END_REGEX.containsMatchIn(currentText.toString().trim())
            val isNewParagraph = when {
                currentType != CurrentType.Paragraph -> true
                lastLine == null -> true
                line.page != lastLine.page || line.column != lastLine.column -> prevEndedSentence
                (line.baseline - lastLine.baseline) > (1.45f * pitch) -> true
                (line.x0 - leftMargin) >= (0.8f * bodySize) && prevEndedSentence -> true
                else -> false
            }

            if (isNewParagraph) {
                flush()
                currentType = CurrentType.Paragraph
                currentText.append(line.text)
            } else {
                appendJoinedText(currentText, line.text)
            }

            lastLine = line
        }

        flush()
        return blocks
    }

    private fun appendJoinedText(sb: StringBuilder, nextLine: String) {
        val s = sb.toString()
        if (s.isEmpty()) {
            sb.append(nextLine)
            return
        }

        val lastChar = s.last()
        val secondLast = if (s.length >= 2) s[s.length - 2] else ' '

        if (lastChar == '-' && secondLast.isLetter()) {
            val firstNext = nextLine.firstOrNull() ?: ' '
            if (firstNext.isLowerCase()) {
                // Drop hyphen and join without space
                sb.deleteCharAt(sb.length - 1)
                sb.append(nextLine)
                return
            } else if (firstNext.isUpperCase()) {
                // Keep hyphen and add space
                sb.append(" ").append(nextLine)
                return
            }
        }

        sb.append(" ").append(nextLine)
    }

    private val MATH_OPERATOR_CHARS = setOf(
        '=', '≤', '≥', '≠', '≈', '≡', '∈', '∉', '⊂', '⊆', '⊃', '⊇', '±', '∓',
        '∑', '∏', '∫', '∂', '∇', '→', '⇒', '⇔', '↦', '≔', '≕', '∝', '⊥', '⊤',
        '∠', '∀', '∃', '∄', '·', '×'
    )

    private fun isDisplayFormulaLine(text: String, fontSize: Float, bodySize: Float): Boolean {
        if (text.length < 3 || text.length > 250) return false
        // Must contain an equation relation or major math operator
        val hasOp = text.any { it in MATH_OPERATOR_CHARS }
        if (!hasOp) return false

        // Check if line ends with equation number like (1), (2.3), [4]
        val clean = text.replace(Regex("\\s*\\([0-9.]+\\)\\s*$"), "").trim()
        if (clean.isEmpty()) return false

        // Check density of math/symbol characters
        var mathCharCount = 0
        var proseLetterCount = 0
        for (c in clean) {
            when {
                c in MATH_OPERATOR_CHARS || c == '+' || c == '-' || c == '−' || c == '/' || c == '*' || c == '^' || c == '_' -> mathCharCount++
                c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}' || c == '|' -> mathCharCount++
                c in '0'..'9' -> mathCharCount++
                c in '\u0370'..'\u03FF' -> mathCharCount++ // Greek
                c.isLetter() -> proseLetterCount++
            }
        }

        // Standalone equation line check: if the line has 4+ plain prose words (not starting with '\'),
        // it is a prose sentence with inline math rather than a standalone display equation.
        val words = clean.split(Regex("\\s+")).filter {
            it.length > 3 && !it.startsWith("\\") && it.all { ch -> ch.isLetter() }
        }
        val isSentence = words.size >= 4
        if (isSentence) return false

        // If it contains backslash commands like \frac, \sum, \int, and is not a prose sentence, it's display math
        if (clean.contains("\\frac") || clean.contains("\\sum") || clean.contains("\\int") ||
            clean.contains("\\sqrt") || clean.contains("\\partial") || clean.contains("\\nabla") ||
            clean.contains("\\cdot") || clean.contains("\\times") || clean.contains("\\mathbf") ||
            clean.contains("\\mathbb") || clean.contains("\\mathcal") || clean.contains("\\alpha") ||
            clean.contains("\\beta") || clean.contains("\\theta") || clean.contains("\\lambda") ||
            clean.contains("\\sigma") || clean.contains("\\omega")
        ) {
            return true
        }

        val hasRelation = clean.any { it in RELATION_CHARS }
        return (mathCharCount >= 3) && hasRelation
    }

    private val RELATION_CHARS = setOf('=', '≈', '≤', '≥', '≠', '≡', '≔', '≕', '∼', '∝', '∈', '∉', '⊂', '⊆', '⊃', '⊇')

    private enum class CurrentType {
        None, Heading, ListItem, Paragraph
    }
}
