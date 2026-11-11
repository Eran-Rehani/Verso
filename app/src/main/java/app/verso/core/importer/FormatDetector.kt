package app.verso.core.importer

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
            // Check first entry
            try {
                ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                    val entry = zis.nextEntry
                    if (entry != null && entry.name == "mimetype") {
                        val content = zis.readBytes().toString(StandardCharsets.UTF_8).trim()
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
        if (lowerName.endsWith(".md") || lowerName.endsWith(".markdown") || lowerName.endsWith(".mdown")) {
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
        if (mime == "text/markdown" || mime == "text/x-markdown") {
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

        // Check strong Markdown signals
        val hasHeading = Regex("(?m)^#{1,6}\\s+\\S").containsMatchIn(sample)
        val hasFence = sample.contains("```")
        val hasTable = Regex("(?m)^\\|[-:| ]+\\|$").containsMatchIn(sample)
        val hasDisplayMath = sample.contains("$$")
        val hasBold = Regex("\\*\\*[^*\\n]+\\*\\*").containsMatchIn(sample)
        val hasLink = Regex("\\[[^\\]\\n]+\\]\\(https?://[^)\\n]+\\)").containsMatchIn(sample)

        val listMatches = Regex("(?m)^\\s*([-*+]|\\d+\\.)\\s+\\S").findAll(sample).count()

        if (hasHeading || hasFence || hasTable || hasDisplayMath || hasBold || hasLink || listMatches >= 2) {
            return SourceFormat.Markdown
        }

        return SourceFormat.PlainText
    }
}
