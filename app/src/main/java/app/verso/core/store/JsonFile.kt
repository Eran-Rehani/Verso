package app.verso.core.store

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.io.FileOutputStream
import java.util.UUID

object AtomicFile {
    fun writeText(file: File, text: String) {
        writeBytes(file, text.toByteArray(Charsets.UTF_8))
    }

    fun writeBytes(file: File, bytes: ByteArray) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        val tmp = File(parent ?: file.absoluteFile.parentFile, "${file.name}.${UUID.randomUUID()}.tmp")
        FileOutputStream(tmp).use { out -> out.write(bytes); out.fd.sync() }

        try {
            Files.move(
                tmp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (e: AtomicMoveNotSupportedException) {
            // Fallback to non-atomic move
            Files.move(
                tmp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
}

class JsonFile<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val default: () -> T
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun read(): T {
        return try {
            if (!file.exists()) return default()
            val content = file.readText(Charsets.UTF_8)
            json.decodeFromString(serializer, content)
        } catch (e: Throwable) {
            default()
        }
    }

    fun write(value: T) {
        val text = json.encodeToString(serializer, value)
        AtomicFile.writeText(file, text)
    }
}
