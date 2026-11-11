package app.verso.core.search

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
import app.verso.core.model.TableBlock
import app.verso.core.model.plainText
import app.verso.core.toc.TocExtractor
import kotlin.math.max
import kotlin.math.min

data class DocumentSearchMatch(
    val matchId: Int,
    val blockIndex: Int,
    val charStart: Int,
    val charEnd: Int,
    val snippet: String,
    val snippetMatchStart: Int,
    val snippetMatchEnd: Int,
    val sectionTitle: String?,
    val progressFraction: Float
)

data class DocumentSearchResult(
    val query: String,
    val matches: List<DocumentSearchMatch> = emptyList(),
    val currentMatchIndex: Int = 0
) {
    val totalCount: Int get() = matches.size
    val currentMatch: DocumentSearchMatch? get() = matches.getOrNull(currentMatchIndex)
    val displayCounter: String
        get() = if (totalCount == 0) "0 of 0" else "${currentMatchIndex + 1} of $totalCount"
}

object DocumentSearcher {

    private const val SNIPPET_PADDING = 36

    fun search(
        document: Document,
        query: String,
        tocEntries: List<app.verso.core.toc.TocEntry> = TocExtractor.extract(document)
    ): List<DocumentSearchMatch> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return emptyList()

        val blocks = document.blocks
        if (blocks.isEmpty()) return emptyList()

        val totalBlocks = blocks.size
        fun progressFor(blockIdx: Int): Float =
            if (totalBlocks <= 1) 0f else (blockIdx.toFloat() / (totalBlocks - 1)).coerceIn(0f, 1f)

        fun findSectionTitle(blockIdx: Int): String? {
            if (tocEntries.isEmpty()) return null
            val idx = TocExtractor.activeEntryIndex(tocEntries, blockIdx)
            return tocEntries.getOrNull(idx)?.title
        }

        val results = mutableListOf<DocumentSearchMatch>()
        var globalMatchId = 0

        for (blockIdx in blocks.indices) {
            val block = blocks[blockIdx]
            val textToSearch = getSearchableText(block)
            if (textToSearch.isEmpty()) continue

            val matchRanges = findAllOccurrences(textToSearch, trimmedQuery)
            if (matchRanges.isEmpty()) continue

            val section = findSectionTitle(blockIdx)
            val progress = progressFor(blockIdx)

            for (range in matchRanges) {
                val start = range.first
                val end = range.last + 1

                val (snippetText, sStart, sEnd) = buildSnippet(textToSearch, start, end)

                results.add(
                    DocumentSearchMatch(
                        matchId = globalMatchId++,
                        blockIndex = blockIdx,
                        charStart = start,
                        charEnd = end,
                        snippet = snippetText,
                        snippetMatchStart = sStart,
                        snippetMatchEnd = sEnd,
                        sectionTitle = section,
                        progressFraction = progress
                    )
                )
            }
        }

        return results
    }

    fun findBlockOccurrences(block: Block, query: String): List<IntRange> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val text = getSearchableText(block)
        return findAllOccurrences(text, trimmed)
    }

    fun getSearchableText(block: Block): String = when (block) {
        is Paragraph -> block.text.text
        is Heading -> block.text.text
        is ListItem -> block.text.text
        is Quote -> block.text.text
        is CodeBlock -> block.code
        is MathBlock -> {
            val readable = LatexParser.toReadablePlainText(block.latex)
            if (readable.isNotBlank() && readable != block.latex) {
                "${block.latex} $readable"
            } else {
                block.latex
            }
        }
        is TableBlock -> block.plainText()
        is ImageBlock -> block.alt
        is Divider -> ""
    }

    private fun findAllOccurrences(source: String, query: String): List<IntRange> {
        if (query.isEmpty() || source.isEmpty()) return emptyList()
        val list = mutableListOf<IntRange>()
        var startIndex = 0
        val queryLen = query.length
        while (startIndex < source.length) {
            val found = source.indexOf(query, startIndex, ignoreCase = true)
            if (found == -1) break
            list.add(found until (found + queryLen))
            startIndex = found + max(1, queryLen)
        }
        return list
    }

    private fun buildSnippet(text: String, matchStart: Int, matchEnd: Int): Triple<String, Int, Int> {
        val len = text.length
        var sStart = max(0, matchStart - SNIPPET_PADDING)
        var sEnd = min(len, matchEnd + SNIPPET_PADDING)

        // Try to expand outwards to whitespace
        while (sStart > 0 && !text[sStart].isWhitespace() && matchStart - sStart < SNIPPET_PADDING + 8) {
            sStart--
        }
        while (sEnd < len && !text[sEnd].isWhitespace() && sEnd - matchEnd < SNIPPET_PADDING + 8) {
            sEnd++
        }

        val rawPrefix = if (sStart > 0) "…" else ""
        val rawSuffix = if (sEnd < len) "…" else ""

        val rawSub = text.substring(sStart, sEnd).replace('\n', ' ')
        val cleanSub = rawSub.trim()

        val prefixAdjust = rawPrefix.length + if (rawSub.startsWith(" ")) 0 else 0
        val relativeStart = (matchStart - sStart).coerceIn(0, cleanSub.length)
        val relativeEnd = (matchEnd - sStart).coerceIn(relativeStart, cleanSub.length)

        val finalSnippet = "$rawPrefix$cleanSub$rawSuffix"
        val finalStart = (rawPrefix.length + relativeStart).coerceIn(0, finalSnippet.length)
        val finalEnd = (rawPrefix.length + relativeEnd).coerceIn(finalStart, finalSnippet.length)

        return Triple(finalSnippet, finalStart, finalEnd)
    }

    fun nextMatchIndex(currentIndex: Int, totalMatches: Int): Int {
        if (totalMatches <= 0) return 0
        return (currentIndex + 1) % totalMatches
    }

    fun prevMatchIndex(currentIndex: Int, totalMatches: Int): Int {
        if (totalMatches <= 0) return 0
        return if (currentIndex - 1 < 0) totalMatches - 1 else currentIndex - 1
    }
}
