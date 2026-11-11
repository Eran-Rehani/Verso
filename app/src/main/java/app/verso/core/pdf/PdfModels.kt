package app.verso.core.pdf

data class TextRun(
    val text: String,
    val x: Float,
    val baseline: Float,
    val width: Float,
    val fontSize: Float,
    val bold: Boolean = false
)

data class PdfPage(
    val index: Int,
    val width: Float,
    val height: Float,
    val runs: List<TextRun>
)

data class PdfContent(
    val title: String?,
    val pages: List<PdfPage>
)

interface PdfPageSource {
    fun read(pdf: ByteArray, onPage: () -> Unit = {}): PdfContent
}

data class PdfLine(
    val page: Int,
    val column: Int,
    val text: String,
    val x0: Float,
    val x1: Float,
    val baseline: Float,
    val fontSize: Float,
    val bold: Boolean
)
