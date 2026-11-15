package app.verso.core

import app.verso.core.epub.EpubExtractor
import app.verso.core.model.ImportError
import app.verso.core.model.plainText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import app.verso.core.importer.DocumentImporter
import app.verso.core.importer.DocumentSource
import app.verso.core.importer.SourceFormat
import app.verso.core.model.Document
import app.verso.core.model.Paragraph
import app.verso.core.model.InlineText
import app.verso.reader.data.db.VersoDatabase
import app.verso.reader.data.repository.BookRepository
import app.verso.reader.intake.ImportCoordinator
import app.verso.reader.intake.ImportState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.nio.file.Files
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class EpubSpineTest {
    private fun epub(manifest: String, spine: String, chapters: Map<String, String> = emptyMap(), rootEntries: Map<String, String> = emptyMap()): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun entry(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            entry("META-INF/container.xml", "<container><rootfiles><rootfile full-path='OEBPS/book.opf'/></rootfiles></container>")
            entry("OEBPS/book.opf", "<package><metadata><title>Book</title></metadata><manifest>$manifest</manifest><spine>$spine</spine></package>")
            chapters.forEach { (name, text) -> entry("OEBPS/$name", "<html><body><p>$text</p></body></html>") }
            rootEntries.forEach { (name, text) -> entry(name, "<html><body><p>$text</p></body></html>") }
        }
        return output.toByteArray()
    }

    private val one = "<item id='one' href='one.xhtml' media-type='application/xhtml+xml'/>"
    private val two = "<item id='two' href='two.xhtml' media-type='application/xhtml+xml'/>"
    private val refs = "<itemref idref='one'/><itemref idref='two'/>"

    @Test fun `reads all required chapters in spine order`() {
        val doc = EpubExtractor.extract(epub(one + two, refs, mapOf("one.xhtml" to "First", "two.xhtml" to "Second")))
        assertEquals(listOf("First", "Second"), doc.blocks.map { it.plainText() })
    }

    @Test fun `rejects missing required chapter`() {
        assertThrows(ImportError.Corrupt::class.java) { EpubExtractor.extract(epub(one + two, refs, mapOf("one.xhtml" to "First"))) }
    }

    @Test fun `rejects missing OPF relative chapter even when root has same name`() {
        val bytes = epub(one, "<itemref idref='one'/>", rootEntries = mapOf("one.xhtml" to "Unrelated"))
        assertThrows(ImportError.Corrupt::class.java) { EpubExtractor.extract(bytes) }
    }

    @Test fun `rejects missing required id and manifest mapping`() {
        for (spine in listOf("<itemref/>", "<itemref idref='missing'/>")) {
            assertThrows(ImportError.Corrupt::class.java) { EpubExtractor.extract(epub(one, spine)) }
        }
    }

    @Test fun `rejects unsupported required chapter media`() {
        val item = "<item id='two' href='two.svg' media-type='image/svg+xml'/>"
        val error = assertThrows(ImportError.Unsupported::class.java) { EpubExtractor.extract(epub(one + item, refs, mapOf("one.xhtml" to "First"))) }
        assertTrue(error.userMessage.contains("image/svg+xml"))
    }

    @Test fun `skips non-linear and exact nav tokens but not similar properties`() {
        val manifest = one + "<item id='two' href='two.xhtml' media-type='application/xhtml+xml' properties='scripted navigation'/>" +
            "<item id='nav' href='missing.xhtml' media-type='application/xhtml+xml' properties='scripted nav'/>"
        val spine = refs + "<itemref idref='nav'/><itemref idref='absent' linear='no'/>"
        val doc = EpubExtractor.extract(epub(manifest, spine, mapOf("one.xhtml" to "First", "two.xhtml" to "Second")))
        assertEquals(listOf("First", "Second"), doc.blocks.map { it.plainText() })
    }
    @Test fun `failed import preserves existing library without creating a book`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), VersoDatabase::class.java).build()
        try {
            val repo = BookRepository(db, Files.createTempDirectory("verso-epub-test").toFile())
            val original = Document("Existing", listOf(Paragraph(InlineText.plain("Keep me"))))
            val existing = repo.importDocument(DocumentSource("existing.txt", "text/plain", "existing".toByteArray()), original, SourceFormat.PlainText)
            val broken = epub(one + two, refs, mapOf("one.xhtml" to "First"))
            val result = CompletableDeferred<ImportState>()
            val coordinator = ImportCoordinator.create(this, DocumentImporter(), repo, Dispatchers.IO) {
                if (it is ImportState.Failed || it is ImportState.Complete) result.complete(it)
            }
            coordinator.start("broken.epub") { DocumentSource("broken.epub", "application/epub+zip", broken) }
            assertTrue(withTimeout(10_000) { result.await() } is ImportState.Failed)
            assertEquals(listOf(existing), db.bookDao().getAllNow())
            assertEquals(original, repo.read(existing.id))
            assertNull(repo.getBook(BookRepository.idFor(broken)))
        } finally {
            db.close()
        }
    }

}
