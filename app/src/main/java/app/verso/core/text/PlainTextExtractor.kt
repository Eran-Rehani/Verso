package app.verso.core.text

import app.verso.core.markdown.MathProtector
import app.verso.core.model.Block
import app.verso.core.model.Document
import app.verso.core.model.InlineText
import app.verso.core.model.MathBlock
import app.verso.core.model.Paragraph

object PlainTextExtractor {
    private val BLANK_LINE_SPLIT_REGEX = Regex("\n[ \t]*\n+")

    fun extract(text: String): Document {
        val cleaned = TextCleaner.clean(text)
        if (cleaned.isBlank()) {
            return Document("", emptyList())
        }

        val protected = MathProtector.protect(cleaned)
        val mathList = protected.math

        // Split on blank lines
        val rawChunks = protected.text.split(BLANK_LINE_SPLIT_REGEX)
        val blocks = mutableListOf<Block>()

        for (rawChunk in rawChunks) {
            val lines = rawChunk.lines().map { it.trimEnd() }
            val nonEmptyLines = lines.filter { it.isNotBlank() }
            if (nonEmptyLines.isEmpty()) continue

            val formattedText = if (nonEmptyLines.size >= 2) {
                val longLinesCount = nonEmptyLines.count { it.length >= 45 }
                val ratio = longLinesCount.toDouble() / nonEmptyLines.size
                if (ratio >= 0.6) {
                    // Hard-wrapped prose: join with space
                    nonEmptyLines.joinToString(" ")
                } else {
                    // Keep line breaks
                    lines.dropWhile { it.isBlank() }
                        .dropLastWhile { it.isBlank() }
                        .joinToString("\n")
                }
            } else {
                nonEmptyLines.single()
            }

            if (formattedText.isNotBlank()) {
                val trimmed = formattedText.trim()
                if (mathList.isNotEmpty() &&
                    trimmed.startsWith(MathProtector.OPEN) &&
                    trimmed.endsWith(MathProtector.CLOSE)
                ) {
                    val idx = trimmed.substring(1, trimmed.length - 1).toIntOrNull()
                    if (idx != null && idx in mathList.indices && mathList[idx].display) {
                        blocks.add(MathBlock(mathList[idx].latex))
                        continue
                    }
                }

                val inline = if (mathList.isNotEmpty() && formattedText.contains(MathProtector.OPEN)) {
                    MathProtector.expandPlaceholders(formattedText, emptyList(), mathList)
                } else {
                    InlineText.plain(formattedText)
                }
                blocks.add(Paragraph(inline))
            }
        }

        return Document("", blocks)
    }
}
