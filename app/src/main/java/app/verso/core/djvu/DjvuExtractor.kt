package app.verso.core.djvu

import app.verso.core.model.Document
import app.verso.core.model.ImportError
import app.verso.core.text.PlainTextExtractor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Extracts embedded text layers from DjVu (.djvu / .djvo) IFF85 container files.
 *
 * DjVu files start with optional "AT&T" magic bytes followed by an IFF "FORM" header
 * with form type "DJVU" (single-page) or "DJVM" (multi-page document).
 * Uncompressed text layers are stored in "TXTa" and "ANTa" chunks.
 * If a DjVu file only contains compressed wavelet/JB2 image bitmaps ("BG44", "Sjbz")
 * or BZZ-compressed text ("TXTz") without an accessible text layer, [ImportError.NoTextLayer]
 * is thrown so the user receives a clear explanation.
 */
object DjvuExtractor {

    fun isDjvuMagic(bytes: ByteArray): Boolean {
        if (bytes.size >= 16) {
            if (bytes[0] == 'A'.code.toByte() &&
                bytes[1] == 'T'.code.toByte() &&
                bytes[2] == '&'.code.toByte() &&
                bytes[3] == 'T'.code.toByte() &&
                bytes[4] == 'F'.code.toByte() &&
                bytes[5] == 'O'.code.toByte() &&
                bytes[6] == 'R'.code.toByte() &&
                bytes[7] == 'M'.code.toByte()
            ) {
                val formType = String(bytes, 12, 4, StandardCharsets.US_ASCII)
                return formType == "DJVU" || formType == "DJVM" || formType == "BM44" || formType == "PM44"
            }
        }
        if (bytes.size >= 12) {
            if (bytes[0] == 'F'.code.toByte() &&
                bytes[1] == 'O'.code.toByte() &&
                bytes[2] == 'R'.code.toByte() &&
                bytes[3] == 'M'.code.toByte()
            ) {
                val formType = String(bytes, 8, 4, StandardCharsets.US_ASCII)
                return formType == "DJVU" || formType == "DJVM" || formType == "BM44" || formType == "PM44"
            }
        }
        return false
    }

    fun extract(bytes: ByteArray, checkpoint: () -> Unit = {}): Document {
        if (!isDjvuMagic(bytes)) {
            throw ImportError.Corrupt("Invalid DjVu header")
        }

        val startOffset = if (bytes[0] == 'A'.code.toByte() && bytes[1] == 'T'.code.toByte()) 4 else 0
        val extractedPages = mutableListOf<String>()

        parseIffContainer(bytes, startOffset, bytes.size, extractedPages, checkpoint)

        val combinedText = extractedPages
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")

        if (combinedText.isBlank()) {
            throw ImportError.NoTextLayer()
        }

        return PlainTextExtractor.extract(combinedText)
    }

    private fun parseIffContainer(
        bytes: ByteArray,
        offset: Int,
        limit: Int,
        outPages: MutableList<String>,
        checkpoint: () -> Unit
    ) {
        if (offset + 12 > limit) return
        val chunkId = String(bytes, offset, 4, StandardCharsets.US_ASCII)
        val chunkSize = readUint32BE(bytes, offset + 4).coerceAtLeast(0)
        val containerEnd = (offset + 8 + chunkSize).coerceAtMost(limit)

        if (chunkId != "FORM") return
        val formType = String(bytes, offset + 8, 4, StandardCharsets.US_ASCII)
        if (formType != "DJVU" && formType != "DJVM" && formType != "BM44" && formType != "PM44") return

        var cursor = offset + 12
        while (cursor + 8 <= containerEnd) {
            checkpoint()
            val subId = String(bytes, cursor, 4, StandardCharsets.US_ASCII)
            val subLen = readUint32BE(bytes, cursor + 4).coerceAtLeast(0)
            val payloadStart = cursor + 8
            val payloadEnd = (payloadStart + subLen).coerceAtMost(containerEnd)

            when (subId) {
                "FORM" -> {
                    parseIffContainer(bytes, cursor, payloadEnd, outPages, checkpoint)
                }
                "TXTa" -> {
                    val text = parseTxtaPayload(bytes, payloadStart, payloadEnd)
                    if (text.isNotBlank()) {
                        outPages.add(text)
                    }
                }
                "ANTa" -> {
                    val text = parseAntaPayload(bytes, payloadStart, payloadEnd)
                    if (text.isNotBlank()) {
                        outPages.add(text)
                    }
                }
            }

            // IFF chunks are padded to even byte boundaries
            val paddedLen = if (subLen % 2 == 1) subLen + 1 else subLen
            val nextCursor = payloadStart + paddedLen
            if (nextCursor <= cursor) break
            cursor = nextCursor
        }
    }

    /**
     * Parses a DjVu TXTa chunk:
     * Standard TXTa starts with a 24-bit big-endian text length followed by UTF-8 text,
     * or an S-expression containing quoted text strings.
     */
    private fun parseTxtaPayload(bytes: ByteArray, start: Int, end: Int): String {
        val length = end - start
        if (length <= 0) return ""

        // Check if it starts with S-expression '(page' or similar ASCII
        val rawStr = String(bytes, start, length, StandardCharsets.UTF_8)
        if (rawStr.trimStart().startsWith("(")) {
            return extractSExpressionStrings(rawStr)
        }

        // Standard TXTa binary header: 3-byte length (24-bit BE) + UTF-8 text + zone hierarchy
        if (length >= 4) {
            val strLen = ((bytes[start].toInt() and 0xFF) shl 16) or
                ((bytes[start + 1].toInt() and 0xFF) shl 8) or
                (bytes[start + 2].toInt() and 0xFF)
            if (strLen in 1..(length - 3)) {
                return String(bytes, start + 3, strLen, StandardCharsets.UTF_8)
            }
        }

        // Fallback: readable UTF-8 text
        return rawStr.filter { !it.isISOControl() || it == '\n' || it == '\r' || it == '\t' }.trim()
    }

    private fun parseAntaPayload(bytes: ByteArray, start: Int, end: Int): String {
        val length = end - start
        if (length <= 0) return ""
        val raw = String(bytes, start, length, StandardCharsets.UTF_8)
        return if (raw.contains('"')) extractSExpressionStrings(raw) else ""
    }

    private fun extractSExpressionStrings(sexpr: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = sexpr.length
        while (i < len) {
            if (sexpr[i] == '"') {
                i++
                val token = StringBuilder()
                while (i < len && sexpr[i] != '"') {
                    if (sexpr[i] == '\\' && i + 1 < len) {
                        i++
                        when (val esc = sexpr[i]) {
                            'n' -> token.append('\n')
                            't' -> token.append(' ')
                            'r' -> {}
                            else -> token.append(esc)
                        }
                    } else {
                        token.append(sexpr[i])
                    }
                    i++
                }
                if (token.isNotEmpty()) {
                    if (sb.isNotEmpty() && !sb.endsWith("\n") && !sb.endsWith(" ")) {
                        sb.append(' ')
                    }
                    sb.append(token)
                }
            }
            i++
        }
        return sb.toString().trim()
    }

    private fun readUint32BE(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int
    }
}
