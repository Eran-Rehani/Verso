package app.verso.core.importer

import app.verso.core.markdown.MathProtector
import app.verso.core.model.ImportError
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

object FormatDetector {

    fun detect(source: DocumentSource): SourceFormat {
        val bytes = source.bytes

        // 1. PDF magic bytes within first 1024 bytes
        val searchLimit = minOf(bytes.size, 1024)
        if (searchLimit >= 5) {
            val prefix = String(bytes, 0, searchLimit, StandardCharsets.ISO_8859_1)
            if (prefix.contains("%PDF-")) {
                return SourceFormat.Pdf
            }
        }

        // 2. ZIP magic bytes (PK\u0003\u0004)
        if (bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()
        ) {
            val name = source.name?.lowercase() ?: ""
            if (name.endsWith(".epub") || source.mimeType == "application/epub+zip") {
                return SourceFormat.Epub
            }
            // Check first entry with bounded 128-byte buffer
            try {
                ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                    val entry = zis.nextEntry
                    if (entry != null && entry.name == "mimetype") {
                        val buffer = ByteArray(128)
                        var size = 0
                        while (size < buffer.size) {
                            val n = zis.read(buffer, size, buffer.size - size)
                            if (n < 0) break
                            size += n
                        }
                        val content = String(buffer, 0, size, StandardCharsets.UTF_8).trim()
                        if (content == "application/epub+zip") {
                            return SourceFormat.Epub
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore zip read errors here
            }
            throw ImportError.Unsupported("ZIP archive")
        }

        // 3. Name extensions
        val lowerName = source.name?.lowercase() ?: ""
        if (lowerName.endsWith(".md") ||
            lowerName.endsWith(".markdown") ||
            lowerName.endsWith(".mdown") ||
            lowerName.endsWith(".tex") ||
            lowerName.endsWith(".latex")
        ) {
            return SourceFormat.Markdown
        }
        if (lowerName.endsWith(".html") || lowerName.endsWith(".htm") || lowerName.endsWith(".xhtml")) {
            return SourceFormat.Html
        }
        if (lowerName.endsWith(".epub")) {
            return SourceFormat.Epub
        }
        if (lowerName.endsWith(".pdf")) {
            return SourceFormat.Pdf
        }
        if (lowerName.endsWith(".txt")) {
            return SourceFormat.PlainText
        }

        // 4. MIME type
        val mime = source.mimeType?.lowercase() ?: ""
        if (mime == "text/markdown" ||
            mime == "text/x-markdown" ||
            mime == "text/x-tex" ||
            mime == "application/x-tex" ||
            mime == "application/x-latex"
        ) {
            return SourceFormat.Markdown
        }
        if (mime == "text/html") {
            return SourceFormat.Html
        }
        if (mime == "application/pdf") {
            return SourceFormat.Pdf
        }
        if (mime == "application/epub+zip") {
            return SourceFormat.Epub
        }

        // 5. Sniff first 8 KB
        val sniffLimit = minOf(bytes.size, 8192)
        val sample = TextDecoding.decode(bytes.copyOfRange(0, sniffLimit))
        val trimmedSample = sample.trimStart()

        if (trimmedSample.startsWith("<!DOCTYPE html", ignoreCase = true) ||
            trimmedSample.startsWith("<html", ignoreCase = true)
        ) {
            return SourceFormat.Html
        }

        // Check strong Markdown & LaTeX signals
        val hasHeading = HEADING_REGEX.containsMatchIn(sample)
        val hasFence = sample.contains("```")
        val hasTable = TABLE_REGEX.containsMatchIn(sample)
        val hasDisplayMath = sample.contains("$$") || sample.contains("\\[") || sample.contains("\\begin{")
        val hasLatexSection = LATEX_SECTION_REGEX.containsMatchIn(sample)
        val hasInlineMath = MathProtector.protect(sample).math.isNotEmpty()
        val hasBold = BOLD_REGEX.containsMatchIn(sample)
        val hasLink = LINK_REGEX.containsMatchIn(sample)

        val listMatches = LIST_REGEX.findAll(sample).take(2).count()

        if (hasHeading || hasFence || hasTable || hasDisplayMath || hasLatexSection || hasInlineMath || hasBold || hasLink || listMatches >= 2) {
            return SourceFormat.Markdown
        }

        return SourceFormat.PlainText
    }

    private val HEADING_REGEX = Regex("(?m)^#{1,6}\\s+\\S")
    private val TABLE_REGEX = Regex("(?m)^\\|[-:| ]+\\|$")
    private val LATEX_SECTION_REGEX = Regex("(?m)^\\s*\\\\(?:section|subsection|chapter)\\*?\\{")
    private val BOLD_REGEX = Regex("\\*\\*[^*\\n]+\\*\\*")
    private val LINK_REGEX = Regex("\\[[^\\]\\n]+\\]\\(https?://[^)\\n]+\\)")
    private val LIST_REGEX = Regex("(?m)^\\s*([-*+]|\\d+\\.)\\s+\\S")
}
