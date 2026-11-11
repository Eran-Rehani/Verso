package app.verso.core.importer

import app.verso.core.epub.EpubExtractor
import app.verso.core.html.HtmlExtractor
import app.verso.core.markdown.MarkdownExtractor
import app.verso.core.model.Divider
import app.verso.core.model.Document
import app.verso.core.model.Heading
import app.verso.core.model.ImportError
import app.verso.core.model.plainText
import app.verso.core.pdf.PdfExtractor
import app.verso.core.pdf.PdfPageSource
import app.verso.core.text.BlockSplitter
import app.verso.core.text.PlainTextExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class ImportResult(val document: Document, val format: SourceFormat)

interface Importer {
    suspend fun import(source: DocumentSource): ImportResult
}

class DocumentImporter(
    private val pdfSource: PdfPageSource? = null,
    private val maxSourceBytes: Long = MAX_SOURCE_BYTES
) : Importer {
    companion object {
        const val MAX_SOURCE_BYTES = 64L * 1024 * 1024
    }

    override suspend fun import(source: DocumentSource): ImportResult {
        if (source.bytes.size > maxSourceBytes) {
            throw ImportError.TooLarge()
        }

        val format = FormatDetector.detect(source)
        currentCoroutineContext().ensureActive()

        val rawDoc = try {
            when (format) {
                SourceFormat.PlainText -> {
                    val text = TextDecoding.decode(source.bytes)
                    PlainTextExtractor.extract(text)
                }
                SourceFormat.Markdown -> {
                    val text = TextDecoding.decode(source.bytes)
                    MarkdownExtractor.extract(text)
                }
                SourceFormat.Html -> {
                    val text = TextDecoding.decode(source.bytes)
                    HtmlExtractor.extract(text)
                }
                SourceFormat.Epub -> {
                    EpubExtractor.extract(source.bytes) {
                        // cancellation check
                    }
                }
                SourceFormat.Pdf -> {
                    val pdf = pdfSource ?: throw ImportError.Unsupported("PDF support is unavailable")
                    PdfExtractor(pdf).extract(source.bytes) {
                        // cancellation check
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ImportError) {
            throw e
        } catch (e: Exception) {
            throw ImportError.Corrupt(e.message ?: "Failed to read document", e)
        }

        currentCoroutineContext().ensureActive()

        // Filter out blank blocks except Divider
        val nonEmptyBlocks = rawDoc.blocks.filter { block ->
            block is Divider || block.plainText().isNotBlank()
        }

        val splitBlocks = BlockSplitter.splitLong(nonEmptyBlocks)
        if (splitBlocks.isEmpty()) {
            throw ImportError.Empty()
        }

        // Title fallback chain:
        // 1. Extractor title (if non-blank)
        // 2. First Heading text trimmed to 120 chars
        // 3. File name without extension (if name not null/blank)
        // 4. "Untitled"
        val title = when {
            rawDoc.title.isNotBlank() -> rawDoc.title.trim()
            else -> {
                val firstHeading = splitBlocks.filterIsInstance<Heading>().firstOrNull()?.text?.text?.trim()
                if (!firstHeading.isNullOrBlank()) {
                    firstHeading.take(120)
                } else if (!source.name.isNullOrBlank()) {
                    source.name.substringBeforeLast('.').trim().ifBlank { "Untitled" }
                } else {
                    "Untitled"
                }
            }
        }

        return ImportResult(Document(title, splitBlocks), format)
    }
}
