package app.verso.reader.data.repository

import androidx.room.withTransaction
import app.verso.core.importer.DocumentSource
import app.verso.core.importer.SourceFormat
import app.verso.core.model.Document
import app.verso.core.model.DocumentCodec
import app.verso.core.store.AtomicFile
import app.verso.core.store.RecentEntry
import app.verso.reader.data.db.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** The only application writer of library metadata and positions. */
class BookRepository(private val database: VersoDatabase, private val dir: File) {
    private val dao = database.bookDao()
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var initialized = false

    val allBooks: Flow<List<BookEntity>> = dao.getAllBooks()

    private suspend fun <T> locked(action: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { initializeLocked(); action() }
    }

    suspend fun initialize() = locked { Unit }

    private suspend fun initializeLocked() {
        if (initialized) return
        dir.mkdirs()
        if (dao.migrationComplete() != true) {
            // Do not use LibraryStore/JsonFile helpers here: inventory must not mutate evidence.
            val oldRows = dao.getAllNow()
            val recents = File(dir, "recents.json")
            val backup = File(dir, "migration-v2-backup").apply { mkdirs() }
            if (recents.exists() && !File(backup, "recents.json").exists())
                AtomicFile.writeBytes(File(backup, "recents.json"), recents.readBytes())
            if (!File(backup, "room.json").exists())
                AtomicFile.writeText(File(backup, "room.json"), json.encodeToString(oldRows))
            val diagnostics = mutableListOf<String>()
            val legacy = try {
                if (!recents.exists()) emptyList() else {
                    val array = json.parseToJsonElement(recents.readText()).jsonArray
                    array.mapNotNull { item ->
                        try {
                            json.decodeFromJsonElement<RecentEntry>(item).takeIf {
                                it.id.isNotBlank() && it.title.isNotBlank() && it.addedAt >= 0 &&
                                    it.lastOpenedAt >= 0 && it.blockCount >= 0 && it.anchorBlock >= 0 && it.anchorOffsetPx >= 0
                            }.also { if (it == null) diagnostics += "Invalid legacy record retained in backup" }
                        } catch (e: Exception) { diagnostics += "Invalid legacy record retained in backup"; null }
                    }
                }
            } catch (e: Exception) { diagnostics += "Unreadable recents.json retained in backup"; emptyList() }
            val byJson = legacy.associateBy { it.id }
            val byRoom = oldRows.associateBy { it.id }
            val files = File(dir, "docs").listFiles().orEmpty().filter { it.isFile && it.extension == "json" }
                .associateBy { it.nameWithoutExtension }
            val ids = byRoom.keys + byJson.keys + files.keys
            val inventory = ids.map { id ->
                currentCoroutineContext().ensureActive()
                val entry = byJson[id]
                val old = byRoom[id]
                val file = legacyFile(id)
                val doc = file?.takeIf { it.isFile }?.let { decodeOrNull(it) }
                val base = old ?: BookEntity(id, doc?.title ?: id, "Unknown", file?.lastModified() ?: 0L, file?.lastModified() ?: 0L)
                val merged = if (entry == null) base else base.copy(
                    title = entry.title, format = entry.format, addedAt = entry.addedAt,
                    lastOpenedAt = entry.lastOpenedAt, blockCount = entry.blockCount,
                    anchorBlock = entry.anchorBlock, anchorOffsetPx = entry.anchorOffsetPx
                )
                merged.copy(
                    blockCount = if (entry == null && old == null) doc?.blocks?.size ?: 0 else merged.blockCount,
                    availability = when { file?.isFile != true -> "MISSING"; doc == null -> "CORRUPT"; else -> "AVAILABLE" },
                    hasOriginal = false, hasResources = false
                )
            }
            AtomicFile.writeText(File(backup, "diagnostics.json"), json.encodeToString(diagnostics))
            // A single transaction makes restart either retry all inventory or observe completion.
            database.withTransaction {
                inventory.forEach { dao.insertBook(it) }
                dao.markMigration(LibraryMigration("legacy-v2", true))
            }
        }
        // Reconcile complete generations after interrupted commits, including repairs.
        File(dir, "items").listFiles().orEmpty().filter { it.isDirectory }.forEach { bookDir ->
            bookDir.listFiles().orEmpty().filter { it.isDirectory }.sortedByDescending { it.lastModified() }.forEach { generation ->
                currentCoroutineContext().ensureActive()
                val manifest = File(generation, "metadata.json")
                if (!manifest.isFile) return@forEach
                val snapshot = try { json.decodeFromString<BookEntity>(manifest.readText()) } catch (e: Exception) { null }
                if (snapshot == null || snapshot.id != bookDir.name) return@forEach
                val relative = generation.relativeTo(dir).invariantSeparatorsPath
                // Trust only paths inside this generation, never paths supplied by a manifest.
                val payload = safeFile("$relative/document.json")
                val original = safeFile("$relative/source.bin")
                val doc = payload?.takeIf { it.isFile }?.let { decodeOrNull(it) }
                val candidate = snapshot.copy(
                    payloadPath = "$relative/document.json", sourcePath = "$relative/source.bin",
                    hasOriginal = original?.isFile == true,
                    availability = when { payload?.isFile != true -> "MISSING"; doc == null -> "CORRUPT"; else -> "AVAILABLE" }
                )
                val current = dao.getBookById(candidate.id)
                if (current == null) {
                    dao.insertBook(candidate)
                } else if (current.availability != "DELETED") {
                    val currentOriginalExists = safeFile(current.sourcePath)?.isFile == true
                    if (current.payloadPath == candidate.payloadPath) {
                        dao.insertBook(current.copy(availability = candidate.availability, hasOriginal = currentOriginalExists))
                    } else {
                        val currentPayloadUsable = payloadFile(current)?.let { it.isFile && decodeOrNull(it) != null } == true
                        if (!currentPayloadUsable && doc != null) {
                            // Preserve live Room user data, even if the snapshot is older.
                            dao.insertBook(current.copy(
                                payloadPath = candidate.payloadPath, sourcePath = candidate.sourcePath,
                                availability = "AVAILABLE", hasOriginal = candidate.hasOriginal,
                                hasResources = candidate.hasResources, blockCount = doc.blocks.size
                            ))
                        } else if (currentPayloadUsable && !currentOriginalExists && candidate.hasOriginal) {
                            // Reattach source independently: a healthy parsed revision stays authoritative.
                            dao.insertBook(current.copy(sourcePath = candidate.sourcePath, hasOriginal = true))
                        }
                    }
                }
            }
        }
        initialized = true
    }

    suspend fun getBook(id: String): BookEntity? = locked { dao.getBookById(id) }

    suspend fun read(id: String): Document? = locked {
        val book = dao.getBookById(id) ?: return@locked null
        if (book.availability == "DELETED") return@locked null
        val file = payloadFile(book)
        val doc = file?.takeIf { it.isFile }?.let { decodeOrNull(it) }
        val availability = if (doc != null) "AVAILABLE" else if (file?.isFile == true) "CORRUPT" else "MISSING"
        if (book.availability != availability) dao.setAvailability(id, availability)
        doc
    }

    suspend fun importDocument(source: DocumentSource, document: Document, format: SourceFormat): BookEntity = locked {
        val id = idFor(source.bytes)
        val old = dao.getBookById(id)
        val token = UUID.randomUUID().toString()
        val stage = File(dir, "staging/$token").apply { check(mkdirs()) }
        try {
            AtomicFile.writeText(File(stage, "document.json"), DocumentCodec.encode(document))
            currentCoroutineContext().ensureActive()
            AtomicFile.writeBytes(File(stage, "source.bin"), source.bytes)
            currentCoroutineContext().ensureActive()
            val path = "items/$id/$token"
            val now = System.currentTimeMillis()
            val entity = (old ?: BookEntity(id, document.title, format.name, now, now)).copy(
                title = document.title, format = format.name, blockCount = document.blocks.size,
                lastOpenedAt = now, availability = "AVAILABLE", payloadPath = "$path/document.json",
                sourcePath = "$path/source.bin", hasOriginal = true
            )
            AtomicFile.writeText(File(stage, "metadata.json"), json.encodeToString(entity))
            currentCoroutineContext().ensureActive()
            // Cancellation after this boundary must preserve the fully committed book.
            withContext(NonCancellable) {
                val destination = File(dir, path)
                destination.parentFile!!.mkdirs()
                Files.move(stage.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
                database.withTransaction { dao.insertBook(entity) }
            }
            entity
        } finally {
            // This directory is ours; complete generations have already moved out.
            if (stage.exists()) stage.deleteRecursively()
        }
    }

    suspend fun touch(id: String) = locked { dao.updateLastOpened(id, System.currentTimeMillis()) }

    suspend fun updateAnchor(id: String, block: Int, offsetPx: Int) = locked {
        dao.updateReadingPosition(id, block.coerceAtLeast(0), offsetPx.coerceAtLeast(0))
    }

    // Retaining files plus a tombstone prevents crash reconciliation resurrecting a deleted book.
    suspend fun delete(id: String) = locked { dao.setAvailability(id, "DELETED") }

    suspend fun count(): Int = locked { dao.getAllNow().count { it.availability != "DELETED" } }

    suspend fun original(id: String): File? = locked {
        dao.getBookById(id)?.takeIf { it.hasOriginal }?.let { safeFile(it.sourcePath) }?.takeIf { it.isFile }
    }

    // --- Bookmarks ---
    fun bookmarksFor(bookId: String): Flow<List<BookmarkEntity>> = dao.getBookmarksForBook(bookId)

    suspend fun getBookmarks(bookId: String): List<BookmarkEntity> = locked {
        dao.getBookmarksForBookNow(bookId)
    }

    suspend fun toggleBookmark(
        bookId: String,
        document: Document,
        blockIndex: Int,
        offsetPx: Int = 0,
        customLabel: String = ""
    ): Boolean = locked {
        val safeBlockIndex = if (document.blocks.isNotEmpty()) {
            blockIndex.coerceIn(0, document.blocks.size - 1)
        } else 0
        val existing = dao.getBookmarkAtBlock(bookId, safeBlockIndex)
        if (existing != null) {
            dao.deleteBookmarkById(existing.id)
            false
        } else {
            val desc = app.verso.core.bookmark.BookmarkHelper.describe(document, safeBlockIndex, offsetPx)
            val entity = BookmarkEntity(
                id = BookmarkEntity.idFor(bookId, safeBlockIndex),
                bookId = bookId,
                blockIndex = safeBlockIndex,
                offsetPx = desc.offsetPx,
                pageNumber = desc.pageNumber,
                totalPages = desc.totalPages,
                sectionTitle = desc.sectionTitle ?: "",
                snippet = desc.snippet,
                label = customLabel.trim(),
                progressFraction = desc.progressFraction,
                createdAt = System.currentTimeMillis()
            )
            dao.insertBookmark(entity)
            true
        }
    }

    suspend fun saveBookmark(
        bookId: String,
        document: Document,
        blockIndex: Int,
        offsetPx: Int = 0,
        customLabel: String = ""
    ): BookmarkEntity = locked {
        val safeBlockIndex = if (document.blocks.isNotEmpty()) {
            blockIndex.coerceIn(0, document.blocks.size - 1)
        } else 0
        val desc = app.verso.core.bookmark.BookmarkHelper.describe(document, safeBlockIndex, offsetPx)
        val existing = dao.getBookmarkAtBlock(bookId, safeBlockIndex)
        val entity = BookmarkEntity(
            id = BookmarkEntity.idFor(bookId, safeBlockIndex),
            bookId = bookId,
            blockIndex = safeBlockIndex,
            offsetPx = desc.offsetPx,
            pageNumber = desc.pageNumber,
            totalPages = desc.totalPages,
            sectionTitle = desc.sectionTitle ?: "",
            snippet = desc.snippet,
            label = customLabel.trim().ifEmpty { existing?.label ?: "" },
            progressFraction = desc.progressFraction,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        dao.insertBookmark(entity)
        entity
    }

    suspend fun removeBookmark(bookmarkId: String) = locked {
        dao.deleteBookmarkById(bookmarkId)
    }

    suspend fun updateBookmarkLabel(bookmarkId: String, label: String) = locked {
        dao.updateBookmarkLabel(bookmarkId, label.trim())
    }

    private fun payloadFile(book: BookEntity): File? =
        if (book.payloadPath.isEmpty()) legacyFile(book.id) else safeFile(book.payloadPath)

    private fun legacyFile(id: String): File? =
        if (id.matches(Regex("[A-Za-z0-9_-][A-Za-z0-9._-]{0,127}"))) safeFile("docs/$id.json") else null

    private fun safeFile(path: String): File? {
        if (path.isBlank() || File(path).isAbsolute) return null
        val file = File(dir, path).canonicalFile
        return file.takeIf { it.toPath().startsWith(dir.canonicalFile.toPath()) }
    }

    private fun decodeOrNull(file: File): Document? = try { DocumentCodec.decode(file.readText()) } catch (e: Exception) { null }

    companion object {
        fun idFor(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .take(16).joinToString("") { "%02x".format(it) }
    }
}
