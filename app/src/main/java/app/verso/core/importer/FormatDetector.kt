package app.verso.core.importer

import app.verso.core.djvu.DjvuExtractor
import app.verso.core.markdown.MathProtector
import app.verso.core.model.ImportError
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

object FormatDetector {

    private val MARKDOWN_LIKE_EXTENSIONS = setOf(
        "md", "markdown", "mdown", "mkd", "mdwn", "rmd", "qmd",
        "tex", "ltx", "latex",
        "rst", "org", "adoc", "asciidoc"
    )

    private val HTML_LIKE_EXTENSIONS = setOf(
        "html", "htm", "xhtml", "shtml", "fb2"
    )

    private val PLAIN_TEXT_EXTENSIONS = setOf(
        "txt", "text", "log", "cfg", "ini", "conf"
    )

    private val MARKDOWN_MIMES = setOf(
        "text/markdown",
        "text/x-markdown",
        "text/x-tex",
        "application/x-tex",
        "application/x-latex",
        "text/x-rst",
        "text/prs.fallenstein.rst",
        "text/org",
        "text/x-org",
        "text/asciidoc"
    )

    private val HTML_MIMES = setOf(
        "text/html",
        "application/xhtml+xml",
        "application/x-fictionbook+xml",
        "text/xml"
    )

    private val DJVU_MIMES = setOf(
        "image/vnd.djvu",
        "image/x-djvu",
        "application/x-djvu"
    )

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

        // 2. DjVu magic bytes (AT&TFORM...DJVU/DJVM or FORM...DJVU/DJVM)
        if (DjvuExtractor.isDjvuMagic(bytes)) {
            return SourceFormat.Djvu
        }

        // 3. ZIP magic bytes (PK\u0003\u0004)
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

        // 4. Name extensions
        val lowerName = source.name?.lowercase()?.trim() ?: ""
        val ext = lowerName.substringAfterLast('.', "")
        if (ext.isNotEmpty()) {
            when {
                ext in MARKDOWN_LIKE_EXTENSIONS -> return SourceFormat.Markdown
                ext in HTML_LIKE_EXTENSIONS -> return SourceFormat.Html
                ext == "epub" -> return SourceFormat.Epub
                ext == "pdf" -> return SourceFormat.Pdf
                ext == "djvu" || ext == "djvo" -> return SourceFormat.Djvu
                ext in PLAIN_TEXT_EXTENSIONS -> return SourceFormat.PlainText
            }
        }

        // 5. MIME type
        val mime = source.mimeType?.lowercase()?.substringBefore(';')?.trim() ?: ""
        when {
            mime in MARKDOWN_MIMES -> return SourceFormat.Markdown
            mime in HTML_MIMES -> return SourceFormat.Html
            mime == "application/pdf" -> return SourceFormat.Pdf
            mime == "application/epub+zip" -> return SourceFormat.Epub
            mime in DJVU_MIMES -> return SourceFormat.Djvu
        }

        // 6. Sniff first 8 KB
        val sniffLimit = minOf(bytes.size, 8192)
        val sample = TextDecoding.decode(bytes.copyOfRange(0, sniffLimit))
        val trimmedSample = sample.trimStart()

        if (trimmedSample.startsWith("<!DOCTYPE html", ignoreCase = true) ||
            trimmedSample.startsWith("<html", ignoreCase = true) ||
            trimmedSample.startsWith("<FictionBook", ignoreCase = true) ||
            (trimmedSample.startsWith("<?xml", ignoreCase = true) &&
                (trimmedSample.contains("<FictionBook", ignoreCase = true) ||
                    trimmedSample.contains("<html", ignoreCase = true)))
        ) {
            return SourceFormat.Html
        }

        // Check strong Markdown, LaTeX, Org-mode, and AsciiDoc signals
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

    private val HEADING_REGEX = Regex("(?m)^(?:#{1,6}|\\*{1,4}|={1,4})\\s+\\S")
    private val TABLE_REGEX = Regex("(?m)^\\|[-:| ]+\\|$")
    private val LATEX_SECTION_REGEX = Regex("(?m)^\\s*\\\\(?:documentclass|section|subsection|chapter|title)\\*?\\{")
    private val BOLD_REGEX = Regex("\\*\\*[^*\\n]+\\*\\*")
    private val LINK_REGEX = Regex("\\[[^\\]\\n]+\\]\\(https?://[^)\\n]+\\)")
    private val LIST_REGEX = Regex("(?m)^\\s*([-*+]|\\d+\\.)\\s+\\S")
}
