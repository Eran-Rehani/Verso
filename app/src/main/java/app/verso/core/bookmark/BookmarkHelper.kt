package app.verso.core.bookmark

import app.verso.core.latex.LatexParser
import app.verso.core.model.Block
import app.verso.core.model.CodeBlock
import app.verso.core.model.Divider
import app.verso.core.model.Document
import app.verso.core.model.Heading
import app.verso.core.model.ImageBlock
import app.verso.core.model.ListItem
import app.verso.core.model.MathBlock
import app.verso.core.model.Paragraph
import app.verso.core.model.Quote
import app.verso.core.model.SpanKind
import app.verso.core.model.TableBlock
import app.verso.core.model.plainText
import app.verso.core.toc.TocExtractor
import kotlin.math.max

data class BookmarkDescriptor(
    val blockIndex: Int,
    val offsetPx: Int,
    val pageNumber: Int,
    val totalPages: Int,
    val sectionTitle: String?,
    val snippet: String,
    val progressFraction: Float
)

object BookmarkHelper {

    const val BLOCKS_PER_PAGE = 5
    private const val MAX_SNIPPET_LENGTH = 140

    fun pageForBlock(blockIndex: Int): Int {
        return (blockIndex.coerceAtLeast(0) / BLOCKS_PER_PAGE) + 1
    }

    fun totalPages(totalBlocks: Int): Int {
        if (totalBlocks <= 0) return 1
        return max(1, (totalBlocks + BLOCKS_PER_PAGE - 1) / BLOCKS_PER_PAGE)
    }

    fun progressForBlock(blockIndex: Int, totalBlocks: Int): Float {
        if (totalBlocks <= 1) return 0f
        return (blockIndex.toFloat() / (totalBlocks - 1)).coerceIn(0f, 1f)
    }

    fun describe(
        document: Document,
        blockIndex: Int,
        offsetPx: Int = 0
    ): BookmarkDescriptor {
        val blocks = document.blocks
        val totalBlocks = blocks.size
        val safeBlockIndex = if (totalBlocks > 0) blockIndex.coerceIn(0, totalBlocks - 1) else 0
        val page = pageForBlock(safeBlockIndex)
        val totalPages = totalPages(totalBlocks)
        val progress = progressForBlock(safeBlockIndex, totalBlocks)

        val tocEntries = TocExtractor.extract(document)
        val activeTocIdx = TocExtractor.activeEntryIndex(tocEntries, safeBlockIndex)
        val sectionTitle = tocEntries.getOrNull(activeTocIdx)?.title
            ?: document.title.takeIf { it.isNotBlank() }

        val snippet = extractSnippet(blocks, safeBlockIndex)

        return BookmarkDescriptor(
            blockIndex = safeBlockIndex,
            offsetPx = offsetPx.coerceAtLeast(0),
            pageNumber = page,
            totalPages = totalPages,
            sectionTitle = sectionTitle,
            snippet = snippet,
            progressFraction = progress
        )
    }

    private fun extractSnippet(blocks: List<Block>, startIndex: Int): String {
        if (blocks.isEmpty()) return "Empty document"

        // Try the target block first; if it's a Divider or empty, look at adjacent blocks
        for (idx in startIndex until minOf(blocks.size, startIndex + 3)) {
            val text = readableBlockText(blocks[idx]).trim()
            if (text.isNotEmpty()) {
                return truncateSnippet(text)
            }
        }
        for (idx in (startIndex - 1) downTo maxOf(0, startIndex - 2)) {
            val text = readableBlockText(blocks[idx]).trim()
            if (text.isNotEmpty()) {
                return truncateSnippet(text)
            }
        }
        return "Page ${pageForBlock(startIndex)}"
    }

    fun readableBlockText(block: Block): String = when (block) {
        is Heading -> formatInlineMath(block.text.text, block.text.spans.filter { it.kind == SpanKind.Math }.map { it.start until it.end })
        is Paragraph -> formatInlineMath(block.text.text, block.text.spans.filter { it.kind == SpanKind.Math }.map { it.start until it.end })
        is ListItem -> "${block.marker} " + formatInlineMath(block.text.text, block.text.spans.filter { it.kind == SpanKind.Math }.map { it.start until it.end })
        is Quote -> "“" + formatInlineMath(block.text.text, block.text.spans.filter { it.kind == SpanKind.Math }.map { it.start until it.end }) + "”"
        is CodeBlock -> block.code.lineSequence().firstOrNull()?.trim() ?: block.code.trim()
        is MathBlock -> LatexParser.toReadablePlainText(block.latex).ifBlank { block.latex }
        is TableBlock -> block.plainText()
        is ImageBlock -> block.alt.ifBlank { "Image" }
        is Divider -> ""
    }

    private val MULTI_SPACE_REGEX = Regex("\\s+")

    private fun formatInlineMath(raw: String, mathRanges: List<IntRange>): String {
        if (mathRanges.isEmpty()) return raw.replace('\n', ' ').replace(MULTI_SPACE_REGEX, " ")
        val sb = StringBuilder()
        var cursor = 0
        for (range in mathRanges.sortedBy { it.first }) {
            val start = range.first.coerceIn(0, raw.length)
            val end = (range.last + 1).coerceIn(start, raw.length)
            if (start > cursor) {
                sb.append(raw.substring(cursor, start))
            }
            if (end > start) {
                val latex = raw.substring(start, end)
                sb.append(LatexParser.toReadablePlainText(latex))
            }
            cursor = end
        }
        if (cursor < raw.length) {
            sb.append(raw.substring(cursor))
        }
        return sb.toString().replace('\n', ' ').replace(MULTI_SPACE_REGEX, " ")
    }

    private fun truncateSnippet(text: String): String {
        val singleLine = text.replace('\n', ' ').replace(MULTI_SPACE_REGEX, " ").trim()
        if (singleLine.length <= MAX_SNIPPET_LENGTH) return singleLine
        return singleLine.take(MAX_SNIPPET_LENGTH - 1).trimEnd() + "…"
    }
}
