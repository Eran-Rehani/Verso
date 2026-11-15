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

    @Test
    fun twentyFiveDocumentsSurviveRepositoryRecreation() = runBlocking {
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
        } finally {
            db.close()
            context.deleteDatabase(name)
            dir.deleteRecursively()
        }
    }

    @Test
    fun removalUndoPreservesDataAndRejectsReplacement() = runBlocking {
        val dir = Files.createTempDirectory("verso-undo").toFile()
        val db = database()
        try {
            val repo = BookRepository(db, dir)
            val source = DocumentSource("book.md", "text/markdown", "book".toByteArray())
            val book = repo.importDocument(source, document, SourceFormat.Markdown)
            repo.updateAnchor(book.id, 4, 12)
            repo.saveBookmark(book.id, document, 0, customLabel = "Saved")
            val before = repo.getBook(book.id)!!
            val bookmarks = repo.getBookmarks(book.id)
            val token = repo.delete(book.id)!!
            assertEquals(0, repo.count())
            BookRepository(db, dir).initialize()
            assertEquals("DELETED", repo.getBook(book.id)!!.availability)
            assertTrue(repo.undoRemoval(token))
            assertEquals(before, repo.getBook(book.id))
            assertEquals(bookmarks, repo.getBookmarks(book.id))
            assertEquals(document, repo.read(book.id))
            val stale = repo.delete(book.id)!!
            val replacement = repo.importDocument(source, document.copy(title = "Replacement"), SourceFormat.Markdown)
            assertFalse(repo.undoRemoval(stale))
            assertEquals(replacement, repo.getBook(book.id))
            val newerRemoval = repo.delete(book.id)!!
            assertFalse(repo.undoRemoval(stale))
            assertEquals("DELETED", repo.getBook(book.id)!!.availability)
            assertTrue(repo.undoRemoval(newerRemoval))
            assertEquals(replacement, repo.getBook(book.id))
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun undoKeepsPositionSavedAfterRemoval() = runBlocking {
        val dir = Files.createTempDirectory("verso-late-position").toFile()
        val db = database()
        try {
            val repo = BookRepository(db, dir)
            val book = repo.importDocument(
                DocumentSource("book.md", "text/markdown", "book".toByteArray()),
                document, SourceFormat.Markdown
            )
            val token = repo.delete(book.id)!!
            repo.updateAnchor(book.id, 7, 24)
            assertTrue(repo.undoRemoval(token))
            val restored = repo.getBook(book.id)!!
            assertEquals("AVAILABLE", restored.availability)
            assertEquals(7, restored.anchorBlock)
            assertEquals(24, restored.anchorOffsetPx)
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun oldUndoCannotRestoreReimportRemovedByAnotherRepository() = runBlocking {
        val dir = Files.createTempDirectory("verso-reimport-undo").toFile()
        val db = database()
        try {
            val first = BookRepository(db, dir)
            val source = DocumentSource("book.md", "text/markdown", "book".toByteArray())
            val book = first.importDocument(source, document, SourceFormat.Markdown)
            val stale = first.delete(book.id)!!
            val second = BookRepository(db, dir)
            val replacement = second.importDocument(source, document.copy(title = "Replacement"), SourceFormat.Markdown)
            val newToken = second.delete(book.id)!!
            assertFalse(first.undoRemoval(stale))
            assertEquals("DELETED", first.getBook(book.id)!!.availability)
            assertTrue(second.undoRemoval(newToken))
            assertEquals(replacement, second.getBook(book.id))
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun migrationPrefersValidJsonAndKeepsBackupsAndMissingRows() = runBlocking {
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
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun corruptRecentsAndPayloadReadsNeverDeleteEvidence() = runBlocking {
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
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun legacyStoreDoesNotEvictOrDeleteCorruption() {
        val dir = Files.createTempDirectory("verso-old-api").toFile()
        try {
            val store = LibraryStore(dir)
            repeat(25) { store.save("$it", document, SourceFormat.Markdown) }
            assertEquals(25, store.list().size)
            File(dir, "docs/1.json").writeText("bad")
            assertNull(store.load("1"))
            assertTrue(File(dir, "docs/1.json").exists())
            assertEquals(25, store.list().size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun bookmarksPersistToggleUpdateLabelAndDeleteAcrossRepository() = runBlocking {
        val dir = Files.createTempDirectory("verso-bookmarks").toFile()
        val db = database()
        try {
            val repo = BookRepository(db, dir)
            val richDoc = Document(
                "Physics Notes",
                listOf(
                    Heading(1, InlineText.plain("1. Kinematics")),
                    Paragraph(InlineText.plain("Velocity and acceleration equations.")),
                    Heading(2, InlineText.plain("2. Dynamics")),
                    Paragraph(InlineText.plain("Newton's second law of motion."))
                )
            )
            val book = repo.importDocument(
                DocumentSource("physics.md", "text/markdown", "# Physics".toByteArray()),
                richDoc,
                SourceFormat.Markdown
            )

            // Add bookmark at block 1
            val added1 = repo.toggleBookmark(book.id, richDoc, blockIndex = 1, offsetPx = 24)
            assertTrue(added1)

            // Add bookmark at block 3 with custom label
            val added2 = repo.toggleBookmark(book.id, richDoc, blockIndex = 3, offsetPx = 0, customLabel = "Key Law")
            assertTrue(added2)

            val saved = repo.getBookmarks(book.id)
            assertEquals(2, saved.size)
            assertEquals(1, saved[0].blockIndex)
            assertEquals("1. Kinematics", saved[0].sectionTitle)
            assertEquals(3, saved[1].blockIndex)
            assertEquals("Key Law", saved[1].label)
            assertEquals("2. Dynamics", saved[1].sectionTitle)

            // Update bookmark label
            repo.updateBookmarkLabel(saved[0].id, "Review for exam")
            assertEquals("Review for exam", repo.getBookmarks(book.id)[0].label)

            // Toggle again at block 1 removes it
            val toggledOff = repo.toggleBookmark(book.id, richDoc, blockIndex = 1, offsetPx = 0)
            assertFalse(toggledOff)
            assertEquals(1, repo.getBookmarks(book.id).size)

            // Remove remaining bookmark
            repo.removeBookmark(saved[1].id)
            assertTrue(repo.getBookmarks(book.id).isEmpty())
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }
}

