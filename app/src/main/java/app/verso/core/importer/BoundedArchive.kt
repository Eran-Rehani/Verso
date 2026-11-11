package app.verso.core.importer

import app.verso.core.model.ImportError
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException

data class ArchiveLimits(val totalBytes: Long = 256L * 1024 * 1024, val entryBytes: Long = 32L * 1024 * 1024, val entries: Int = 10_000)

object BoundedArchive {
    /** Every entry is counted and drained with bounds, including ignored types and directories. */
    fun read(bytes: ByteArray, limits: ArchiveLimits = ArchiveLimits(), checkpoint: () -> Unit = {},
             include: (String) -> Boolean = { true }): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        val seen = hashSetOf<String>()
        var total = 0L
        var count = 0
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    checkpoint()
                    val entry = zip.nextEntry ?: break
                    if (++count > limits.entries) throw ImportError.ArchiveLimit("${limits.entries} entries")
                    val name = entry.name
                    if (name.startsWith('/') || name.contains('\\') || name.contains('\u0000') ||
                        name.split('/').any { it == ".." } || name.contains(':')) throw ImportError.Corrupt("Unsafe archive path")
                    val normalized = name.split('/').filter { it.isNotEmpty() && it != "." }.joinToString("/")
                    if (normalized.isEmpty() || !seen.add(normalized)) throw ImportError.Corrupt("Duplicate or empty archive path")
                    if (entry.size > limits.entryBytes) throw ImportError.ArchiveLimit("${limits.entryBytes} bytes per entry")
                    val keep = !entry.isDirectory && include(normalized)
                    val out = if (keep) ByteArrayOutputStream() else null
                    val buffer = ByteArray(16384)
                    var expanded = 0L
                    while (true) {
                        checkpoint()
                        val n = zip.read(buffer)
                        if (n < 0) break
                        expanded += n; total += n
                        if (expanded > limits.entryBytes) throw ImportError.ArchiveLimit("${limits.entryBytes} bytes per entry")
                        if (total > limits.totalBytes) throw ImportError.ArchiveLimit("${limits.totalBytes} expanded bytes")
                        out?.write(buffer, 0, n)
                    }
                    if (out != null) result[normalized] = out.toByteArray()
                    zip.closeEntry()
                }
            }
            return result
        } catch (e: CancellationException) { throw e }
        catch (e: ImportError) { throw e }
        catch (e: Exception) { throw ImportError.Corrupt("Failed to read archive", e) }
    }
}
