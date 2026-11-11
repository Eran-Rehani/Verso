package app.verso.core.pdf

import app.verso.core.model.Block
import app.verso.core.model.Heading
import app.verso.core.model.InlineText
import app.verso.core.model.ListItem
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

        fun flush() {
            val text = currentText.toString().trim()
            if (text.isNotEmpty()) {
                when (currentType) {
                    CurrentType.Heading -> blocks.add(Heading(currentLevel, InlineText.plain(text)))
                    CurrentType.ListItem -> blocks.add(ListItem(0, currentBulletMarker, InlineText.plain(text)))
                    CurrentType.Paragraph -> blocks.add(Paragraph(InlineText.plain(text)))
                    CurrentType.None -> {}
                }
            }
            currentText = StringBuilder()
            currentType = CurrentType.None
        }

        for (line in cleanedLines) {
            val leftMargin = marginMap[line.page to line.column] ?: 0f

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

    private enum class CurrentType {
        None, Heading, ListItem, Paragraph
    }
}
