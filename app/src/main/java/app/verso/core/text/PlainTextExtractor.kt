package app.verso.core.text

import app.verso.core.model.Block
import app.verso.core.model.Document
import app.verso.core.model.InlineText
import app.verso.core.model.Paragraph

object PlainTextExtractor {
    fun extract(text: String): Document {
        val cleaned = TextCleaner.clean(text)
        if (cleaned.isBlank()) {
            return Document("", emptyList())
        }

        // Split on blank lines
        val rawChunks = cleaned.split(Regex("\n[ \t]*\n+"))
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
                blocks.add(Paragraph(InlineText.plain(formattedText)))
            }
        }

        return Document("", blocks)
    }
}
