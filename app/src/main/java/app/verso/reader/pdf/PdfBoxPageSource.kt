package app.verso.reader.pdf

import android.content.Context
import app.verso.core.model.ImportError
import app.verso.core.pdf.PdfContent
import app.verso.core.pdf.PdfPage
import app.verso.core.pdf.PdfPageSource
import app.verso.core.pdf.TextRun
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.Writer
import java.util.concurrent.atomic.AtomicBoolean

class PdfBoxPageSource(private val context: Context) : PdfPageSource {
    companion object {
        private val initialized = AtomicBoolean(false)
    }

    override fun read(pdf: ByteArray, onPage: () -> Unit): PdfContent {
        if (!initialized.getAndSet(true)) {
            PDFBoxResourceLoader.init(context.applicationContext)
        }

        var document: PDDocument? = null
        try {
            val doc = PDDocument.load(java.io.ByteArrayInputStream(pdf), MemoryUsageSetting.setupMixed(32L * 1024 * 1024))
            document = doc
            val title = doc.documentInformation?.title?.trim()?.ifBlank { null }
            val pageCount = doc.numberOfPages
            val pages = mutableListOf<PdfPage>()

            for (pageIndex in 0 until pageCount) {
                onPage()
                val pdPage = doc.getPage(pageIndex)
                val cropBox = pdPage.cropBox ?: pdPage.mediaBox
                val rotation = pdPage.rotation
                val isRotated = rotation == 90 || rotation == 270
                val pageWidth = if (isRotated) cropBox.height else cropBox.width
                val pageHeight = if (isRotated) cropBox.width else cropBox.height

                val pageRuns = mutableListOf<TextRun>()
                val pageGlyphs = mutableListOf<app.verso.core.pdf.PdfGlyph>()
                val stripper = object : PDFTextStripper() {
                    init {
                        sortByPosition = false
                    }

                    override fun writeString(text: String, textPositions: List<TextPosition>) {
                        if (textPositions.isEmpty()) return
                        val first = textPositions.first()
                        // Record every glyph BEFORE any early return or trimming. The old code
                        // only ever kept the run's first position, which is exactly how
                        // superscript sizes and baselines were being destroyed.
                        for (tp in textPositions) {
                            val raw = tp.unicode ?: ""
                            pageGlyphs += app.verso.core.pdf.PdfGlyph(
                                text = raw,
                                x = tp.xDirAdj,
                                baseline = tp.yDirAdj,
                                width = tp.widthDirAdj,
                                height = tp.heightDir,
                                fontSize = tp.fontSizeInPt,
                                fontName = tp.font?.name ?: "",
                                direction = tp.dir,
                                unmapped = raw.isEmpty() || raw.any { it.code in 0..8 || it.code in 14..31 },
                            )
                        }
                        if (first.dir != 0f) {
                            // Rotated text skipped from the prose stream (glyphs kept above)
                            return
                        }

                        val trimmed = text.trim()
                        if (trimmed.isEmpty()) return

                        val last = textPositions.last()
                        val fontName = first.font?.name ?: ""
                        val isBold = fontName.contains("Bold", ignoreCase = true) ||
                                fontName.contains("Black", ignoreCase = true) ||
                                fontName.contains("Heavy", ignoreCase = true)

                        val runWidth = (last.xDirAdj + last.widthDirAdj) - first.xDirAdj
                        val run = TextRun(
                            text = trimmed,
                            x = first.xDirAdj,
                            baseline = first.yDirAdj,
                            width = runWidth,
                            fontSize = first.fontSizeInPt,
                            bold = isBold
                        )
                        pageRuns.add(run)
                    }
                }

                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1

                val noopWriter = object : Writer() {
                    override fun write(cbuf: CharArray, off: Int, len: Int) {}
                    override fun flush() {}
                    override fun close() {}
                }

                stripper.writeText(document, noopWriter)
                // Second pass: painted horizontal rules. TeX draws fraction bars as stroked
                // or filled geometry, so a text-only reading can never see them.
                val pageRules = mutableListOf<app.verso.core.pdf.PdfRule>()
                try {
                    PdfRuleCollector(pdPage) { pageRules += it }.processPage(pdPage)
                } catch (ignored: Exception) {
                    // A malformed content stream must not fail the whole import: prose is
                    // still readable, and the reconstructor simply sees no rules on this page.
                }
                pages.add(
                    PdfPage(
                        index = pageIndex,
                        width = pageWidth,
                        height = pageHeight,
                        runs = pageRuns,
                        glyphs = pageGlyphs,
                        rules = PdfRuleFilter.thinRules(pageRules, pageHeight)
                    )
                )
                onPage()
            }

            return PdfContent(title, pages)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: InvalidPasswordException) {
            throw ImportError.Unsupported("password-protected PDF")
        } catch (e: ImportError) {
            throw e
        } catch (e: Exception) {
            throw ImportError.Corrupt("Failed to read PDF document", e)
        } finally {
            try {
                document?.close()
            } catch (ignored: Exception) {}
        }
    }
}
