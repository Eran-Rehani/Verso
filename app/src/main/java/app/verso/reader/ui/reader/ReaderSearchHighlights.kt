package app.verso.reader.ui.reader

import app.verso.core.model.CodeBlock
import app.verso.core.model.Document
import app.verso.core.model.Heading
import app.verso.core.model.ListItem
import app.verso.core.model.Paragraph
import app.verso.core.model.Quote
import app.verso.core.model.TableBlock

internal data class ReaderSearchHighlights(
    val blocks: Map<Int, List<IntRange>> = emptyMap(),
    val cells: Map<Int, List<List<List<IntRange>>>> = emptyMap()
)

internal fun searchHighlights(document: Document, query: String, checkCancelled: () -> Unit): ReaderSearchHighlights {
    val blocks = mutableMapOf<Int, List<IntRange>>()
    val cells = mutableMapOf<Int, List<List<List<IntRange>>>>()
    fun ranges(text: String): List<IntRange> {
        val matches = mutableListOf<IntRange>()
        var pos = 0
        while (pos < text.length) {
            checkCancelled()
            val found = text.indexOf(query, pos, ignoreCase = true)
            if (found < 0) break
            matches += found until found + query.length
            pos = found + query.length
        }
        return matches
    }
    document.blocks.forEachIndexed { index, block ->
        checkCancelled()
        val text = when (block) {
            is Paragraph -> renderedInlineTextForSearch(block.text)
            is Heading -> renderedInlineTextForSearch(block.text)
            is ListItem -> renderedInlineTextForSearch(block.text)
            is Quote -> renderedInlineTextForSearch(block.text)
            is CodeBlock -> block.code
            else -> null
        }
        if (text != null) blocks[index] = ranges(text)
        if (block is TableBlock) {
            cells[index] = block.rows.map { row -> row.map { cell ->
                checkCancelled()
                ranges(renderedInlineTextForSearch(cell))
            } }
        }
    }
    return ReaderSearchHighlights(blocks, cells)
}
