package app.verso.reader.intake

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import app.verso.core.importer.DocumentImporter
import app.verso.core.importer.DocumentSource
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException

sealed interface IntakeResult {
    data class Source(val source: DocumentSource) : IntakeResult
    data class Failure(val message: String) : IntakeResult
    data object Ignore : IntakeResult
}

object SourceReader {
    fun readBytes(
        resolver: ContentResolver,
        uri: Uri,
        limit: Long = DocumentImporter.MAX_SOURCE_BYTES
    ): ByteArray? {
        val stream = resolver.openInputStream(uri) ?: throw FileNotFoundException("Cannot open stream for $uri")
        stream.use { input ->
            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(16384)
            var totalRead = 0L
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                totalRead += read
                if (totalRead > limit) {
                    return null
                }
                baos.write(buffer, 0, read)
            }
            return baos.toByteArray()
        }
    }
}

object IntentParser {
    fun parse(intent: Intent, resolver: ContentResolver): IntakeResult {
        val action = intent.action ?: return IntakeResult.Ignore

        when (action) {
            Intent.ACTION_SEND -> {
                val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }

                if (streamUri != null) {
                    return readUriContent(resolver, streamUri, intent.type)
                }

                val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
                if (extraText.isNullOrEmpty()) {
                    return IntakeResult.Failure("Nothing to read in the shared content.")
                }
                val bytes = extraText.toByteArray(Charsets.UTF_8)
                return IntakeResult.Source(
                    DocumentSource(name = null, mimeType = "text/plain", bytes = bytes)
                )
            }

            Intent.ACTION_VIEW -> {
                val dataUri = intent.data ?: return IntakeResult.Ignore
                return readUriContent(resolver, dataUri, intent.type)
            }

            else -> return IntakeResult.Ignore
        }
    }

    private fun readUriContent(
        resolver: ContentResolver,
        uri: Uri,
        intentType: String?
    ): IntakeResult {
        return try {
            val displayName = queryDisplayName(resolver, uri) ?: uri.lastPathSegment
            val mimeType = intentType ?: resolver.getType(uri)

            val bytes = SourceReader.readBytes(resolver, uri, DocumentImporter.MAX_SOURCE_BYTES)
                ?: return IntakeResult.Failure("This file is larger than 64 MB.")

            if (bytes.isEmpty()) {
                return IntakeResult.Failure("Nothing to read in the shared content.")
            }

            IntakeResult.Source(DocumentSource(name = displayName, mimeType = mimeType, bytes = bytes))
        } catch (e: SecurityException) {
            IntakeResult.Failure("Couldn't open the shared file.")
        } catch (e: FileNotFoundException) {
            IntakeResult.Failure("Couldn't open the shared file.")
        } catch (e: IOException) {
            IntakeResult.Failure("Couldn't open the shared file.")
        } catch (e: Exception) {
            IntakeResult.Failure("Couldn't open the shared file.")
        }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
