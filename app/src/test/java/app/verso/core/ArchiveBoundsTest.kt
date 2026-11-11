package app.verso.core

import app.verso.core.importer.*
import app.verso.core.model.ImportError
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveBoundsTest {
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { out -> entries.forEach { (name, body) ->
            out.putNextEntry(ZipEntry(name)); out.write(body); out.closeEntry()
        } }
    }.toByteArray()
    private fun expectLimit(action: () -> Unit) {
        try { action(); fail("Expected a specific archive limit") }
        catch (error: ImportError.ArchiveLimit) { assertTrue(error.userMessage.startsWith("Archive limit exceeded:")) }
    }
    @Test fun ignoredEntriesCountTowardExpandedAndPerEntryLimits() {
        val bytes = zip("ignored.dat" to ByteArray(24), "chapter.html" to "hi".toByteArray())
        expectLimit { BoundedArchive.read(bytes, ArchiveLimits(totalBytes = 20, entryBytes = 100), include = { it.endsWith("html") }) }
        expectLimit { BoundedArchive.read(bytes, ArchiveLimits(entryBytes = 20), include = { false }) }
    }
    @Test fun directoriesAndIgnoredEntriesCountTowardEntryCount() {
        val bytes = zip("directory/" to byteArrayOf(), "ignored.bin" to byteArrayOf(), "chapter.html" to byteArrayOf())
        expectLimit { BoundedArchive.read(bytes, ArchiveLimits(entries = 2), include = { false }) }
    }
    @Test fun traversalAndDuplicateNormalizedNamesAreRejected() {
        for (name in listOf("../escape", "/absolute", "a/../../b", "a\\b", "C:foo")) {
            try { BoundedArchive.read(zip(name to byteArrayOf())); fail("Unsafe name $name") }
            catch (expected: ImportError.Corrupt) { assertTrue(expected.message!!.contains("Unsafe archive path")) }
        }
        try { BoundedArchive.read(zip("a/b" to byteArrayOf(), "a/./b" to byteArrayOf())); fail("Expected duplicate") }
        catch (expected: ImportError.Corrupt) { assertTrue(expected.message!!.contains("Duplicate")) }
    }
    @Test fun cancellationIsNotWrappedAsCorruptionAndStopsReads() {
        val bytes = zip("ignored.bin" to ByteArray(100_000))
        var checks = 0
        val cancelled = CancellationException("stop now")
        try {
            BoundedArchive.read(bytes, checkpoint = { if (++checks == 4) throw cancelled }, include = { false })
            fail("Expected cancellation")
        } catch (actual: CancellationException) { assertSame(cancelled, actual) }
        assertEquals(4, checks)
    }
    @Test fun boundedDetectionDoesNotAcceptAnOverlongMimetype() {
        val bytes = zip("mimetype" to ("application/epub+zip" + "x".repeat(100_000)).toByteArray())
        try { FormatDetector.detect(DocumentSource("book.zip", "application/zip", bytes)); fail("Expected unsupported") }
        catch (expected: ImportError.Unsupported) { /* First entry is read only up to detection bound. */ }
    }
    @Test fun exactByteLimitsAndPreservedSharedWhitespace() {
        val bytes = zip("a.txt" to ByteArray(20))
        assertEquals(20, BoundedArchive.read(bytes, ArchiveLimits(totalBytes = 20, entryBytes = 20))["a.txt"]!!.size)
        val text = "  Hebrew שלום\n    code\n"
        assertEquals(text, DocumentSource.fromText(text).bytes.toString(Charsets.UTF_8))
    }
}
