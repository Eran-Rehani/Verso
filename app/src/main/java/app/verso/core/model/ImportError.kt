package app.verso.core.model

sealed class ImportError(
    message: String,
    val userMessage: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    class Empty : ImportError("empty", "No readable text was found in this file.")
    class Drm : ImportError("drm", "This book is DRM-protected and can't be opened.")
    class NoTextLayer : ImportError("no text layer", "This PDF has no selectable text (it looks scanned). OCR isn't supported.")
    class TooLarge : ImportError("too large", "This file is larger than 64 MB.")
    class Unsupported(reason: String) : ImportError("unsupported: $reason", "Unsupported file: $reason")
    class Corrupt(detail: String, cause: Throwable? = null) : ImportError("corrupt: $detail", "This file couldn't be read.", cause)
}
