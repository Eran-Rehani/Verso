package app.verso.core.pdf

import app.verso.core.model.Document
import app.verso.core.model.ImportError

class PdfExtractor(private val source: PdfPageSource) {
    fun extract(pdf: ByteArray, onPage: () -> Unit = {}): Document {
        val content = source.read(pdf, onPage)
        val allRuns = content.pages.flatMap { it.runs }
        val totalChars = allRuns.sumOf { it.text.length }

        if (allRuns.isEmpty() || totalChars < 25) {
            throw ImportError.NoTextLayer()
        }

        val lines = PdfLayout.orderedLines(content.pages)
        val blocks = PdfAssembler.assemble(lines)

        return Document(content.title?.trim().orEmpty(), blocks)
    }
}
