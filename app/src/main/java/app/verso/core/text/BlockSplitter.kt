package app.verso.core.text

import app.verso.core.model.Block
import app.verso.core.model.CodeBlock
import app.verso.core.model.InlineText
import app.verso.core.model.ListItem
import app.verso.core.model.Paragraph
import app.verso.core.model.Quote
import app.verso.core.model.Span

object BlockSplitter {
    const val MAX_BLOCK_CHARS = 2500

    fun splitLong(blocks: List<Block>, maxChars: Int = MAX_BLOCK_CHARS): List<Block> {
        require(maxChars > 0)
        val result = mutableListOf<Block>()
        for (block in blocks) {
            when (block) {
                is Paragraph -> {
                    if (block.text.text.length <= maxChars) {
                        result.add(block)
                    } else {
                        val chunks = splitProse(block.text, maxChars)
                        for (chunk in chunks) {
                            result.add(Paragraph(chunk))
                        }
                    }
                }
                is Quote -> {
                    if (block.text.text.length <= maxChars) {
                        result.add(block)
                    } else {
                        val chunks = splitProse(block.text, maxChars)
                        for (chunk in chunks) {
                            result.add(Quote(block.depth, chunk))
                        }
                    }
                }
                is ListItem -> {
                    if (block.text.text.length <= maxChars) {
                        result.add(block)
                    } else {
                        val chunks = splitProse(block.text, maxChars)
                        chunks.forEachIndexed { index, chunk ->
                            val marker = if (index == 0) block.marker else ""
                            result.add(ListItem(block.depth, marker, chunk))
                        }
                    }
                }
                is CodeBlock -> {
                    if (block.code.length <= maxChars) {
                        result.add(block)
                    } else {
                        val codeChunks = splitCode(block.code, maxChars)
                        for (chunk in codeChunks) {
                            result.add(CodeBlock(block.language, chunk))
                        }
                    }
                }
                else -> {
                    result.add(block)
                }
            }
        }
        return result
    }

    private fun splitProse(inline: InlineText, maxChars: Int): List<InlineText> {
        val text = inline.text
        val chunks = mutableListOf<InlineText>()
        var offset = 0
        val len = text.length

        while (offset < len) {
            val remaining = len - offset
            if (remaining <= maxChars) {
                chunks.add(sliceInline(inline, offset, len))
                break
            }

            val windowEnd = offset + maxChars
            var splitIdx = -1

            // 1. Look for sentence endings followed by space (". ", "? ", "! ") or newline ("\n")
            val window = text.substring(offset, windowEnd)
            val sentenceDelims = listOf(". ", "? ", "! ", "\n")
            var lastDelimPos = -1
            for (delim in sentenceDelims) {
                var pos = window.lastIndexOf(delim)
                if (pos != -1) {
                    val endOfDelim = pos + delim.length
                    if (endOfDelim > lastDelimPos) {
                        lastDelimPos = endOfDelim
                    }
                }
            }

            if (lastDelimPos > 0) {
                splitIdx = offset + lastDelimPos
            } else {
                // 2. Look for any whitespace
                var lastWs = -1
                for (i in windowEnd - 1 downTo offset + 1) {
                    if (text[i].isWhitespace()) {
                        lastWs = i + 1
                        break
                    }
                }
                if (lastWs != -1) {
                    splitIdx = lastWs
                } else {
                    // 3. Hard split at maxChars
                    splitIdx = windowEnd
                }
            }

            // Math spans are atomic, even when they exceed the target chunk size.
            var moveForward = false
            while (true) {
                val crossing = inline.spans.filter { it.kind == app.verso.core.model.SpanKind.Math && it.start < splitIdx && splitIdx < it.end }
                if (crossing.isEmpty()) break
                val start = crossing.minOf { it.start }
                if (start <= offset) moveForward = true
                splitIdx = if (moveForward) crossing.maxOf { it.end }.coerceAtMost(len) else start
            }
            chunks.add(sliceInline(inline, offset, splitIdx))
            offset = splitIdx
        }

        return chunks
    }

    private fun sliceInline(inline: InlineText, start: Int, end: Int): InlineText {
        val chunkText = inline.text.substring(start, end)
        val chunkSpans = mutableListOf<Span>()
        for (span in inline.spans) {
            val spanStart = maxOf(start, span.start)
            val spanEnd = minOf(end, span.end)
            if (spanEnd > spanStart) {
                chunkSpans.add(
                    Span(
                        start = spanStart - start,
                        end = spanEnd - start,
                        kind = span.kind,
                        href = span.href
                    )
                )
            }
        }
        return InlineText(chunkText, chunkSpans)
    }

    private fun splitCode(code: String, maxChars: Int): List<String> {
        val chunks = mutableListOf<String>()
        var offset = 0
        val len = code.length

        while (offset < len) {
            val remaining = len - offset
            if (remaining <= maxChars) {
                chunks.add(code.substring(offset, len))
                break
            }

            val windowEnd = offset + maxChars
            val lastNewline = code.lastIndexOf('\n', windowEnd - 1)
            val splitIdx = if (lastNewline >= offset) {
                lastNewline + 1
            } else {
                windowEnd
            }

            chunks.add(code.substring(offset, splitIdx))
            offset = splitIdx
        }

        return chunks
    }
}
