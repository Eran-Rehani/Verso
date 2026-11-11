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
                val pdPage = doc.getPage(pageIndex)
                val cropBox = pdPage.cropBox ?: pdPage.mediaBox
                val rotation = pdPage.rotation
                val isRotated = rotation == 90 || rotation == 270
                val pageWidth = if (isRotated) cropBox.height else cropBox.width
                val pageHeight = if (isRotated) cropBox.width else cropBox.height

                val pageRuns = mutableListOf<TextRun>()
                val stripper = object : PDFTextStripper() {
                    init {
                        sortByPosition = false
                    }

                    override fun writeString(text: String, textPositions: List<TextPosition>) {
                        if (textPositions.isEmpty()) return
                        val first = textPositions.first()
                        if (first.dir != 0f) {
                            // Rotated text skipped
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
                pages.add(
                    PdfPage(
                        index = pageIndex,
                        width = pageWidth,
                        height = pageHeight,
                        runs = pageRuns
                    )
                )
                onPage()
            }

            return PdfContent(title, pages)
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
