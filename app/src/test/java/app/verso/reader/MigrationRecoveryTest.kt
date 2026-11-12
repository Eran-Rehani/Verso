package app.verso.reader

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import app.verso.core.model.*
import app.verso.reader.data.db.BookEntity
import app.verso.reader.data.db.LibraryMigration
import app.verso.reader.data.db.VersoDatabase
import app.verso.reader.data.repository.BookRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
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
class MigrationRecoveryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val document = Document("Lecture", listOf(Paragraph(InlineText.plain("Content"))))

    // --- helpers -------------------------------------------------------------

    private fun newDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()

    /** Creates a genuine on-disk v1 SQLite database with the pre-migration schema. */
    private fun createVersion1Database(name: String) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS books (" +
                                "id TEXT NOT NULL PRIMARY KEY, " +
                                "title TEXT NOT NULL, " +
                                "format TEXT NOT NULL, " +
                                "addedAt INTEGER NOT NULL, " +
                                "lastOpenedAt INTEGER NOT NULL, " +
                                "blockCount INTEGER NOT NULL, " +
                                "anchorBlock INTEGER NOT NULL, " +
                                "anchorOffsetPx INTEGER NOT NULL)"
                        )
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                }).build()
        )
        helper.use {
            val db = it.writableDatabase
            db.execSQL(
                "INSERT INTO books (id,title,format,addedAt,lastOpenedAt,blockCount,anchorBlock,anchorOffsetPx) " +
                    "VALUES (?,?,?,?,?,?,?,?)",
                arrayOf<Any?>("legacy-1", "Legacy Title", "Markdown", 1000L, 2000L, 10, 3, 42)
            )
            db.version = 1
        }
    }

    private fun openMigrated(name: String): VersoDatabase =
        Room.databaseBuilder(context, VersoDatabase::class.java, name)
            .addMigrations(VersoDatabase.MIGRATION_1_2, VersoDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

    private fun writeGeneration(
        dir: File,
        id: String,
        doc: Document,
        token: String = UUID.randomUUID().toString(),
        manifest: BookEntity?
    ): File {
        val generation = File(dir, "items/$id/$token").apply { mkdirs() }
        File(generation, "document.json").writeText(DocumentCodec.encode(doc))
        File(generation, "source.bin").writeBytes("source-$id".toByteArray())
        val entity = manifest ?: BookEntity(
            id = id,
            title = doc.title,
            format = "Markdown",
            addedAt = 111L,
            lastOpenedAt = 222L,
            blockCount = doc.blocks.size,
            anchorBlock = 3,
            anchorOffsetPx = 42,
            availability = "AVAILABLE",
            payloadPath = "items/$id/$token/document.json",
            sourcePath = "items/$id/$token/source.bin",
            hasOriginal = true
        )
        File(generation, "metadata.json").writeText(json.encodeToString(entity))
        return generation
    }

    // --- tests ---------------------------------------------------------------

    @Test
    fun version1DatabaseMigratesThroughRoomAndPersistsAcrossReopen() = runBlocking {
        val name = "verso-v1-${UUID.randomUUID()}.db"
        val name2 = name
        try {
            createVersion1Database(name)
            // Reopen via Room: this must run MIGRATION_1_2 and pass schema validation.
            val db = openMigrated(name)
            try {
                val row = db.bookDao().getAllNow().single()
                assertEquals("legacy-1", row.id)
                assertEquals("Legacy Title", row.title)
                assertEquals("Markdown", row.format)
                assertEquals(1000L, row.addedAt)
                assertEquals(2000L, row.lastOpenedAt)
                assertEquals(10, row.blockCount)
                assertEquals(3, row.anchorBlock)
                assertEquals(42, row.anchorOffsetPx)
                // Defaults supplied by migration.
                assertEquals("AVAILABLE", row.availability)
                assertEquals("", row.payloadPath)
                assertEquals("", row.sourcePath)
                assertFalse(row.hasOriginal)
                assertFalse(row.hasResources)
                assertEquals("REFLOW", row.readingMode)
            } finally {
                db.close()
            }
            // Reopen again and confirm data still persisted after close.
            val reopened = openMigrated(name)
            try {
                val row = reopened.bookDao().getAllNow().single()
                assertEquals("legacy-1", row.id)
                assertEquals(3, row.anchorBlock)
                assertEquals(42, row.anchorOffsetPx)
            } finally {
                reopened.close()
            }
        } finally {
            // Room/SQLite may have created journal files next to the db.
            val base = context.getDatabasePath(name2)
            base.delete()
            File("${base.path}-wal").delete()
            File("${base.path}-shm").delete()
            File("${base.path}-journal").delete()
        }
    }

    @Test
    fun interruptedImportWithoutRoomRowIsRecovered() = runBlocking {
        val dir = newDir("verso-recover-import")
        val db = Room.inMemoryDatabaseBuilder(context, VersoDatabase::class.java).build()
        try {
            db.bookDao().markMigration(LibraryMigration("legacy-v2", true))
            val id = "rec-import"
            val expectedDoc = Document("Recovered", listOf(Paragraph(InlineText.plain("Hello"))))
            writeGeneration(dir, id, expectedDoc, manifest = null)
            assertEquals(0, db.bookDao().getAllNow().size)

            val repo = BookRepository(db, dir)
            repo.initialize()

            val row = repo.getBook(id)
            assertNotNull(row)
            assertEquals(expectedDoc.title, row!!.title)
            assertEquals("AVAILABLE", row.availability)
            assertEquals(111L, row.addedAt)
            assertEquals(222L, row.lastOpenedAt)
            assertEquals(3, row.anchorBlock)
            assertEquals(42, row.anchorOffsetPx)
            assertTrue(row.hasOriginal)
            assertEquals(expectedDoc, repo.read(id))
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun interruptedRepairPreservesExistingRowMetadataAndPosition() = runBlocking {
        val dir = newDir("verso-recover-repair")
        val db = Room.inMemoryDatabaseBuilder(context, VersoDatabase::class.java).build()
        try {
            db.bookDao().markMigration(LibraryMigration("legacy-v2", true))
            val id = "rec-repair"
            val generationDoc = Document("Fresh", listOf(Paragraph(InlineText.plain("New"))))
            writeGeneration(dir, id, generationDoc, manifest = null)
            // Old row with distinct metadata and position, missing/corrupt from user's view.
            db.bookDao().insertBook(
                BookEntity(
                    id = id,
                    title = "Old Title",
                    format = "Markdown",
                    addedAt = 9000L,
                    lastOpenedAt = 9100L,
                    blockCount = 50,
                    anchorBlock = 20,
                    anchorOffsetPx = 777,
                    availability = "MISSING",
                    payloadPath = "",
                    sourcePath = "",
                    hasOriginal = false
                )
            )

            val repo = BookRepository(db, dir)
            repo.initialize()

            val row = repo.getBook(id)!!
            assertEquals(generationDoc, repo.read(id))
            assertEquals("AVAILABLE", row.availability)
            // Existing row metadata and position must be preserved.
            assertEquals("Old Title", row.title)
            assertEquals(9000L, row.addedAt)
            assertEquals(20, row.anchorBlock)
            assertEquals(777, row.anchorOffsetPx)
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun corruptPayloadStaysVisibleWithDerivedAvailability() = runBlocking {
        val dir = newDir("verso-recover-corrupt")
        val db = Room.inMemoryDatabaseBuilder(context, VersoDatabase::class.java).build()
        try {
            db.bookDao().markMigration(LibraryMigration("legacy-v2", true))
            val id = "rec-corrupt"
            val doc = Document("Corrupt", listOf(Paragraph(InlineText.plain("x"))))
            val generation = writeGeneration(dir, id, doc, manifest = null)
            // Corrupt the payload; keep metadata.
            File(generation, "document.json").writeText("{truncated")
            // Stale manifest claims AVAILABLE; delete source to force derived absence.
            File(generation, "source.bin").delete()

            val repo = BookRepository(db, dir)
            repo.initialize()

            val row = repo.getBook(id)
            assertNotNull(row)
            assertNotEquals("DELETED", row!!.availability)
            assertEquals("CORRUPT", row.availability)
            // Original presence derived from actual source file, not stale flag.
            assertFalse(row.hasOriginal)
            assertNull(repo.read(id))
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun deletedTombstoneNeverResurrectsFromCompleteGeneration() = runBlocking {
        val dir = newDir("verso-recover-deleted")
        val db = Room.inMemoryDatabaseBuilder(context, VersoDatabase::class.java).build()
        try {
            db.bookDao().markMigration(LibraryMigration("legacy-v2", true))
            val id = "rec-deleted"
            val doc = Document("Gone", listOf(Paragraph(InlineText.plain("z"))))
            writeGeneration(dir, id, doc, manifest = null)
            db.bookDao().insertBook(
                BookEntity(
                    id = id,
                    title = "Gone",
                    format = "Markdown",
                    availability = "DELETED"
                )
            )

            val repo = BookRepository(db, dir)
            repo.initialize()

            assertEquals("DELETED", repo.getBook(id)!!.availability)
            assertNull(repo.read(id))
            assertEquals(0, repo.count())
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun sourceReattachmentUsesFileRealityOnFirstRestartAndKeepsHealthyPayload() = runBlocking {
        val dir = newDir("verso-source-repair")
        val db = Room.inMemoryDatabaseBuilder(context, VersoDatabase::class.java).build()
        try {
            db.bookDao().markMigration(LibraryMigration("legacy-v2", true))
            val id = "source-repair"
            val old = writeGeneration(dir, id, document, token = "old", manifest = null)
            val row = json.decodeFromString<BookEntity>(File(old, "metadata.json").readText())
            File(old, "source.bin").delete()
            old.setLastModified(1000)
            db.bookDao().insertBook(row.copy(title = "Keep", anchorBlock = 12, hasOriginal = true))
            val fresh = writeGeneration(dir, id, document.copy(title = "New parse"), token = "new", manifest = null)
            fresh.setLastModified(2000)
            val repo = BookRepository(db, dir)
            repo.initialize()
            val repaired = repo.getBook(id)!!
            assertTrue(repaired.hasOriginal)
            assertEquals(File(fresh, "source.bin").canonicalFile, repo.original(id)!!.canonicalFile)
            assertEquals(row.payloadPath, repaired.payloadPath)
            assertEquals(document, repo.read(id))
            assertEquals(12, repaired.anchorBlock)
            BookRepository(db, dir).initialize()
            assertEquals(repaired, repo.getBook(id))
        } finally { db.close(); dir.deleteRecursively() }
    }

    @Test
    fun missingGenerationPayloadKeepsMetadataAndAvailableOriginal() = runBlocking {
        val dir = newDir("verso-missing-payload")
        val db = Room.inMemoryDatabaseBuilder(context, VersoDatabase::class.java).build()
        try {
            db.bookDao().markMigration(LibraryMigration("legacy-v2", true))
            val gen = writeGeneration(dir, "missing-payload", document, manifest = null)
            File(gen, "document.json").delete()
            val repo = BookRepository(db, dir)
            repo.initialize()
            assertEquals("MISSING", repo.getBook("missing-payload")!!.availability)
            assertTrue(repo.getBook("missing-payload")!!.hasOriginal)
            assertNotNull(repo.original("missing-payload"))
        } finally { db.close(); dir.deleteRecursively() }
    }

    @Test
    fun interruptedInventoryRetriesWithoutOverwritingBackup() = runBlocking {
        val dir = newDir("verso-retry-migration")
        val db = Room.inMemoryDatabaseBuilder(context, VersoDatabase::class.java).build()
        try {
            val docs = File(dir, "docs").apply { mkdirs() }
            File(docs, "legacy.json").writeText(DocumentCodec.encode(document))
            val raw = """[{"id":"legacy","title":"Original","format":"Markdown","addedAt":10,"lastOpenedAt":20,"blockCount":1}]"""
            File(dir, "recents.json").writeText(raw)
            val obstruction = File(dir, "migration-v2-backup/diagnostics.json").apply { mkdirs() }
            try { BookRepository(db, dir).initialize(); fail("Expected interrupted inventory") }
            catch (expected: java.io.IOException) { /* No Room transaction has committed. */ }
            assertNull(db.bookDao().migrationComplete())
            assertTrue(db.bookDao().getAllNow().isEmpty())
            assertTrue(obstruction.renameTo(File(dir, "held-obstruction")))
            val repo = BookRepository(db, dir)
            repo.initialize()
            assertEquals(10L, repo.getBook("legacy")!!.addedAt)
            assertEquals(raw, File(dir, "migration-v2-backup/recents.json").readText())
            BookRepository(db, dir).initialize()
            assertEquals(20L, repo.getBook("legacy")!!.lastOpenedAt)
        } finally { db.close(); dir.deleteRecursively() }
    }
}
