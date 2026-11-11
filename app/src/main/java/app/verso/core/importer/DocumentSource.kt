package app.verso.core.importer

class DocumentSource(
    val name: String?,
    val mimeType: String?,
    val bytes: ByteArray
)

enum class SourceFormat {
    PlainText,
    Markdown,
    Html,
    Epub,
    Pdf
}
