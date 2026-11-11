package app.verso.core.store

import app.verso.core.importer.SourceFormat
import app.verso.core.model.Document
import app.verso.core.model.DocumentCodec
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.security.MessageDigest

@Serializable
data class RecentEntry(
    val id: String,
    val title: String,
    val format: String,
    val addedAt: Long,
    val lastOpenedAt: Long,
    val blockCount: Int,
    val anchorBlock: Int = 0,
    val anchorOffsetPx: Int = 0
)

val RecentEntry.progress: Float
    get() = if (blockCount <= 1) {
        0f
    } else {
        (anchorBlock.toFloat() / (blockCount - 1)).coerceIn(0f, 1f)
    }

class LibraryStore(
    private val dir: File,
    private val maxEntries: Int = 20,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val recentsFile = File(dir, "recents.json")
    private val docsDir = File(dir, "docs").apply { if (!exists()) mkdirs() }
    private val recentsJson = JsonFile(recentsFile, ListSerializer(RecentEntry.serializer())) { emptyList() }

    fun idFor(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    @Synchronized
    fun list(): List<RecentEntry> {
        val entries = recentsJson.read()
        // Clean up entries with missing document files
        val valid = entries.filter { entry ->
            File(docsDir, "${entry.id}.json").exists()
        }.sortedByDescending { it.lastOpenedAt }

        if (valid.size != entries.size) {
            recentsJson.write(valid)
        }
        return valid
    }

    @Synchronized
    fun contains(id: String): Boolean {
        return File(docsDir, "$id.json").exists() && list().any { it.id == id }
    }

    @Synchronized
    fun save(id: String, document: Document, format: SourceFormat): RecentEntry {
        // Write document file first
        val docFile = File(docsDir, "$id.json")
        val json = DocumentCodec.encode(document)
        AtomicFile.writeText(docFile, json)

        val now = clock()
        val currentList = list().toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == id }

        val entry = if (existingIndex >= 0) {
            val existing = currentList[existingIndex]
            existing.copy(
                title = document.title,
                format = format.name,
                lastOpenedAt = now,
                blockCount = document.blocks.size
            )
        } else {
            RecentEntry(
                id = id,
                title = document.title,
                format = format.name,
                addedAt = now,
                lastOpenedAt = now,
                blockCount = document.blocks.size,
                anchorBlock = 0,
                anchorOffsetPx = 0
            )
        }

        if (existingIndex >= 0) {
            currentList[existingIndex] = entry
        } else {
            currentList.add(0, entry)
        }

        // Evict beyond maxEntries
        currentList.sortByDescending { it.lastOpenedAt }
        while (currentList.size > maxEntries) {
            val evicted = currentList.removeAt(currentList.size - 1)
            val evictedFile = File(docsDir, "${evicted.id}.json")
            if (evictedFile.exists()) {
                evictedFile.delete()
            }
        }

        recentsJson.write(currentList)
        return entry
    }

    @Synchronized
    fun load(id: String): Document? {
        val docFile = File(docsDir, "$id.json")
        if (!docFile.exists()) return null
        return try {
            val doc = DocumentCodec.decode(docFile.readText(Charsets.UTF_8))
            doc
        } catch (e: Exception) {
            // Corrupt document, delete dead file and entry
            docFile.delete()
            delete(id)
            null
        }
    }

    @Synchronized
    fun entry(id: String): RecentEntry? {
        return list().firstOrNull { it.id == id }
    }

    @Synchronized
    fun touch(id: String) {
        val currentList = list().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) {
            currentList[index] = currentList[index].copy(lastOpenedAt = clock())
            currentList.sortByDescending { it.lastOpenedAt }
            recentsJson.write(currentList)
        }
    }

    @Synchronized
    fun updateAnchor(id: String, block: Int, offsetPx: Int) {
        val currentList = list().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) {
            currentList[index] = currentList[index].copy(
                anchorBlock = block,
                anchorOffsetPx = offsetPx
            )
            recentsJson.write(currentList)
        }
    }

    @Synchronized
    fun delete(id: String) {
        val currentList = list().toMutableList()
        currentList.removeAll { it.id == id }
        recentsJson.write(currentList)

        val docFile = File(docsDir, "$id.json")
        if (docFile.exists()) {
            docFile.delete()
        }
    }
}
