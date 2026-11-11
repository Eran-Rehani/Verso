package app.verso.reader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.verso.core.importer.DocumentSource
import app.verso.core.importer.SourceFormat
import app.verso.core.model.*
import app.verso.core.store.LibraryStore
import app.verso.core.store.RecentEntry
import app.verso.reader.data.db.BookEntity
import app.verso.reader.data.db.VersoDatabase
import app.verso.reader.data.repository.BookRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LibraryPersistenceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun database() = Room.inMemoryDatabaseBuilder(context, VersoDatabase::class.java).build()
    private val document = Document("Lecture", listOf(Paragraph(InlineText.plain("Content"))))

    @Test fun twentyFiveDocumentsSurviveRepositoryRecreation() = runBlocking {
        val dir = Files.createTempDirectory("verso-library").toFile()
        val name = "verso-25-${UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, VersoDatabase::class.java, name).build()
        try {
            val repo = BookRepository(db, dir)
            val ids = (1..25).map { n ->
                repo.importDocument(DocumentSource("$n.md", "text/markdown", "$n".toByteArray()), document, SourceFormat.Markdown).id
            }
            db.close()
            db = Room.databaseBuilder(context, VersoDatabase::class.java, name).build()
            val reopened = BookRepository(db, dir)
            reopened.initialize()
            assertEquals(25, db.bookDao().getAllNow().size)
            ids.forEach { assertEquals(document, reopened.read(it)) }
        } finally { db.close(); context.deleteDatabase(name); dir.deleteRecursively() }
    }

    @Test fun migrationPrefersValidJsonAndKeepsBackupsAndMissingRows() = runBlocking {
        val dir = Files.createTempDirectory("verso-legacy").toFile()
        val docs = File(dir, "docs").apply { mkdirs() }
        val db = database()
        try {
            File(docs, "valid.json").writeText(DocumentCodec.encode(document))
            File(docs, "orphan.json").writeText(DocumentCodec.encode(document.copy(title = "Orphan")))
            File(docs, "corrupt.json").writeText("{truncated")
            val entries = listOf(
                RecentEntry("valid", "JSON title", "Markdown", 11, 22, 1, 7, 35),
                RecentEntry("missing", "Missing", "Pdf", 33, 44, 20),
                RecentEntry("corrupt", "Corrupt", "Html", 55, 66, 10)
            )
            val raw = Json.encodeToString(entries)
            File(dir, "recents.json").writeText(raw)
            db.bookDao().insertBook(BookEntity("valid", "Room title", "PlainText", 111, 222, 1, 0, 99))
            val repo = BookRepository(db, dir)
            repo.initialize()
            val migrated = repo.getBook("valid")!!
            assertEquals("JSON title", migrated.title)
            assertEquals(11L, migrated.addedAt)
            assertEquals(22L, migrated.lastOpenedAt)
            assertEquals(7, migrated.anchorBlock)
            assertEquals(35, migrated.anchorOffsetPx)
            assertFalse(migrated.hasOriginal)
            assertEquals("MISSING", repo.getBook("missing")!!.availability)
            assertEquals("CORRUPT", repo.getBook("corrupt")!!.availability)
            assertNotNull(repo.read("orphan"))
            assertEquals("{truncated", File(docs, "corrupt.json").readText())
            assertEquals(raw, File(dir, "migration-v2-backup/recents.json").readText())
            assertTrue(File(dir, "migration-v2-backup/room.json").readText().contains("Room title"))
            repo.updateAnchor("valid", 8, 99)
            BookRepository(db, dir).initialize()
            assertEquals(8, repo.getBook("valid")!!.anchorBlock)
            assertEquals(22L, repo.getBook("valid")!!.lastOpenedAt)
        } finally { db.close(); dir.deleteRecursively() }
    }

    @Test fun corruptRecentsAndPayloadReadsNeverDeleteEvidence() = runBlocking {
        val dir = Files.createTempDirectory("verso-corrupt").toFile()
        File(dir, "docs").mkdirs()
        val corrupt = File(dir, "docs/broken.json").apply { writeText("bad") }
        val raw = File(dir, "recents.json").apply { writeText("[broken") }
        val db = database()
        try {
            val repo = BookRepository(db, dir)
            repo.initialize()
            assertNull(repo.read("broken"))
            assertEquals("bad", corrupt.readText())
            assertEquals("[broken", raw.readText())
            assertNotNull(repo.getBook("broken"))
        } finally { db.close(); dir.deleteRecursively() }
    }

    @Test fun legacyStoreDoesNotEvictOrDeleteCorruption() {
        val dir = Files.createTempDirectory("verso-old-api").toFile()
        try {
            val store = LibraryStore(dir)
            repeat(25) { store.save("$it", document, SourceFormat.Markdown) }
            assertEquals(25, store.list().size)
            File(dir, "docs/1.json").writeText("bad")
            assertNull(store.load("1"))
            assertTrue(File(dir, "docs/1.json").exists())
            assertEquals(25, store.list().size)
        } finally { dir.deleteRecursively() }
    }
}
