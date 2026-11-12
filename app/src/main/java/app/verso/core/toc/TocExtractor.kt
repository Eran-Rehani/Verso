package app.verso.core.toc

import androidx.compose.runtime.Immutable
import app.verso.core.latex.LatexParser
import app.verso.core.model.Block
import app.verso.core.model.Document
import app.verso.core.model.Heading
import app.verso.core.model.InlineText
import app.verso.core.model.Paragraph
import app.verso.core.model.SpanKind
import app.verso.core.model.plainText

@Immutable
data class TocEntry(
    val index: Int,
    val blockIndex: Int,
    val level: Int,
    val title: String,
    val progressFraction: Float
)

object TocExtractor {

    private val WHITESPACE_REGEX = Regex("\\s+")
    private val CHAPTER_PATTERN = Regex(
        """^(?:Chapter|CHAPTER|Part|PART|Section|SECTION|Book|BOOK|Act|ACT|Scene|SCENE|Prologue|PROLOGUE|Epilogue|EPILOGUE|Introduction|INTRODUCTION|Conclusion|CONCLUSION|Appendix|APPENDIX)\b.*"""
    )
    private val NUMBERED_HEADING_PATTERN = Regex(
        """^(?:[IVXLCDM]+\.|[0-9]{1,2}(?:\.[0-9]{1,2}){0,2}\.?)\s+[A-Z\u00C0-\u024F].{1,75}$"""
    )

    fun extract(document: Document): List<TocEntry> {
        val blocks = document.blocks
        if (blocks.isEmpty()) return emptyList()

        val totalBlocks = blocks.size

        fun progressFor(blockIdx: Int): Float {
            return if (totalBlocks <= 1) 0f
            else (blockIdx.toFloat() / (totalBlocks - 1)).coerceIn(0f, 1f)
        }

        // 1. Primary pass: explicit Heading blocks
        val rawHeadings = mutableListOf<Triple<Int, Int, String>>() // (blockIndex, rawLevel, title)
        blocks.forEachIndexed { blockIdx, block ->
            if (block is Heading) {
                val formattedTitle = formatInlineTitle(block.text)
                if (formattedTitle.isNotBlank()) {
                    rawHeadings.add(Triple(blockIdx, block.level.coerceIn(1, 6), formattedTitle))
                }
            }
        }

        if (rawHeadings.isNotEmpty()) {
            val minLevel = rawHeadings.minOf { it.second }
            return rawHeadings.mapIndexed { idx, (blockIdx, rawLevel, title) ->
                val normalizedLevel = (rawLevel - minLevel + 1).coerceIn(1, 4)
                TocEntry(
                    index = idx,
                    blockIndex = blockIdx,
                    level = normalizedLevel,
                    title = title,
                    progressFraction = progressFor(blockIdx)
                )
            }
        }

        // 2. Secondary pass: detect implicit headings in Paragraphs (e.g., Chapter titles, bold standalone lines)
        val implicitHeadings = mutableListOf<Triple<Int, Int, String>>()
        blocks.forEachIndexed { blockIdx, block ->
            if (block is Paragraph) {
                val raw = block.text.text.trim()
                if (raw.isNotEmpty() && raw.length <= 85 && !raw.contains('\n')) {
                    val isEntirelyBold = block.text.spans.any {
                        it.kind == SpanKind.Bold && it.start == 0 && it.end >= raw.length - 1
                    } && !raw.endsWith(".")
                    val isChapterLine = CHAPTER_PATTERN.matches(raw)
                    val isNumberedLine = NUMBERED_HEADING_PATTERN.matches(raw) && !raw.endsWith(".")

                    if (isChapterLine || isNumberedLine || isEntirelyBold) {
                        val level = if (isChapterLine) 1 else 2
                        implicitHeadings.add(Triple(blockIdx, level, formatInlineTitle(block.text)))
                    }
                }
            }
        }

        if (implicitHeadings.isNotEmpty()) {
            val minLevel = implicitHeadings.minOf { it.second }
            return implicitHeadings.mapIndexed { idx, (blockIdx, rawLevel, title) ->
                TocEntry(
                    index = idx,
                    blockIndex = blockIdx,
                    level = (rawLevel - minLevel + 1).coerceIn(1, 4),
                    title = title,
                    progressFraction = progressFor(blockIdx)
                )
            }
        }

        // 3. Fallback for long documents without explicit headings: generate reading landmarks
        if (totalBlocks >= 6) {
            val step = maxOf(5, totalBlocks / 10)
            val landmarks = mutableListOf<TocEntry>()
            var blockIdx = 0
            var sectionNum = 1
            while (blockIdx < totalBlocks) {
                val preview = extractSnippet(blocks[blockIdx])
                val label = if (preview.isNotBlank()) {
                    "Section $sectionNum · $preview"
                } else {
                    "Section $sectionNum"
                }
                landmarks.add(
                    TocEntry(
                        index = landmarks.size,
                        blockIndex = blockIdx,
                        level = 1,
                        title = label,
                        progressFraction = progressFor(blockIdx)
                    )
                )
                sectionNum++
                blockIdx += step
            }
            return landmarks
        }

        return emptyList()
    }

    /**
     * O(log N) binary search for the active TOC entry at [currentBlockIndex].
     */
    fun activeEntryIndex(entries: List<TocEntry>, currentBlockIndex: Int): Int {
        if (entries.isEmpty()) return -1
        var low = 0
        var high = entries.lastIndex
        var best = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (entries[mid].blockIndex <= currentBlockIndex) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return best
    }

    fun previousEntry(entries: List<TocEntry>, currentBlockIndex: Int): TocEntry? {
        if (entries.isEmpty()) return null
        val activeIdx = activeEntryIndex(entries, currentBlockIndex)
        return when {
            activeIdx < 0 -> null
            // If we are scrolled past the active heading by more than 1 block, jump to the start of the active heading
            currentBlockIndex > entries[activeIdx].blockIndex + 1 -> entries[activeIdx]
            activeIdx > 0 -> entries[activeIdx - 1]
            else -> entries.first()
        }
    }

    /**
     * O(log N) binary search for the first TOC entry strictly after [currentBlockIndex].
     */
    fun nextEntry(entries: List<TocEntry>, currentBlockIndex: Int): TocEntry? {
        if (entries.isEmpty()) return null
        var low = 0
        var high = entries.lastIndex
        var candidate: TocEntry? = null
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (entries[mid].blockIndex > currentBlockIndex) {
                candidate = entries[mid]
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return candidate
    }

    fun formatInlineTitle(inlineText: InlineText): String {
        val raw = inlineText.text
        val mathSpans = inlineText.spans
            .filter { it.kind == SpanKind.Math }
            .sortedBy { it.start }

        if (mathSpans.isEmpty()) {
            return raw.replace(WHITESPACE_REGEX, " ").trim()
        }

        val sb = StringBuilder()
        var cursor = 0
        for (span in mathSpans) {
            val s = span.start.coerceIn(0, raw.length)
            val e = span.end.coerceIn(0, raw.length)
            if (s > cursor) {
                sb.append(raw.substring(cursor, s))
            }
            if (e > s) {
                val latex = raw.substring(s, e)
                sb.append(LatexParser.toReadablePlainText(latex))
            }
            cursor = maxOf(cursor, e)
        }
        if (cursor < raw.length) {
            sb.append(raw.substring(cursor))
        }
        return sb.toString().replace(WHITESPACE_REGEX, " ").trim()
    }

    private fun extractSnippet(block: Block): String {
        val text = block.plainText().replace(WHITESPACE_REGEX, " ").trim()
        if (text.isEmpty()) return ""
        return if (text.length <= 42) text else text.take(40).trimEnd() + "…"
    }
}
