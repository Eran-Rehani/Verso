package app.verso.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object DocumentCodec {
    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    fun encode(doc: Document): String = json.encodeToString(doc)

    fun decode(jsonString: String): Document = json.decodeFromString(jsonString)
}
