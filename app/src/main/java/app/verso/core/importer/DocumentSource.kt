package app.verso.core.importer

class DocumentSource(
    val name: String?,
    val mimeType: String?,
    val bytes: ByteArray
) {
    companion object {
        fun fromText(text: String, checkpoint: () -> Unit = {}): DocumentSource {
            var bytes = 0L
            var index = 0
            while (index < text.length) {
                if (index % 1024 == 0) checkpoint()
                val c = text[index]
                bytes += when {
                    c.code < 128 -> 1
                    c.code < 2048 -> 2
                    c.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate() -> { index++; 4 }
                    c.isSurrogate() -> 1 // UTF-8 encoder replacement for an unpaired surrogate.
                    else -> 3
                }
                if (bytes > DocumentImporter.MAX_SOURCE_BYTES) throw app.verso.core.model.ImportError.TooLarge()
                index++
            }
            checkpoint()
            return DocumentSource(null, "text/plain", text.toByteArray(Charsets.UTF_8))
        }
    }
}

enum class SourceFormat {
    PlainText,
    Markdown,
    Html,
    Epub,
    Pdf
}
