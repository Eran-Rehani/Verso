package app.verso.mathgolden

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Loads TeX goldens produced by `tools/mathgolden/generate.py`.
 *
 * The harness must fail loudly when goldens are absent rather than silently skipping:
 * a skipped fidelity test is indistinguishable from a passing one in CI, and the whole
 * point of this project is that fidelity is proven rather than assumed.
 */
object GoldenLoader {

    private val json = Json { ignoreUnknownKeys = true }

    /** Repo-relative location of the generated goldens. */
    private val defaultRoot: File? by lazy { locateRoot() }

    private fun locateRoot(): File? {
        // Test working directory is the app module; walk up to the repository root.
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "tools/mathgolden/build")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        return null
    }

    fun root(): File =
        defaultRoot ?: throw GoldenLoadException(
            "No golden directory found. Expected tools/mathgolden/build relative to the " +
                "repository root. Run: python3 tools/mathgolden/generate.py"
        )

    /** Load one case by name, e.g. `load("frac", "display")`. */
    fun load(case: String, mode: String): GoldenCase {
        val file = File(root(), "$case-$mode.glyphs.json")
        if (!file.isFile) {
            throw GoldenLoadException(
                "Missing golden '${file.name}'. Run: python3 tools/mathgolden/generate.py " +
                    "--only $case"
            )
        }
        return parse(file.readText(), file.name)
    }

    fun loadAll(): List<GoldenCase> {
        val files = root().listFiles { f -> f.name.endsWith(".glyphs.json") }
            ?: throw GoldenLoadException("Golden directory exists but could not be listed.")
        if (files.isEmpty()) {
            throw GoldenLoadException(
                "No goldens present in ${root()}. Run: python3 tools/mathgolden/generate.py"
            )
        }
        return files.sortedBy { it.name }.map { parse(it.readText(), it.name) }
    }

    fun parse(text: String, sourceName: String = "<inline>"): GoldenCase {
        val rootObj = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw GoldenLoadException("Golden '$sourceName' is not valid JSON: ${e.message}")
        }
        return GoldenCase(
            case = rootObj.reqString("case", sourceName),
            mode = rootObj.reqString("mode", sourceName),
            latex = rootObj.reqString("latex", sourceName),
            baseFontPt = rootObj.reqDouble("baseFontPt", sourceName),
            rasterDpi = rootObj.reqInt("rasterDpi", sourceName),
            pageWidth = rootObj.reqDouble("pageWidth", sourceName),
            pageHeight = rootObj.reqDouble("pageHeight", sourceName),
            glyphs = rootObj.reqArray("glyphs", sourceName).map { element ->
                val g = element.jsonObject
                GoldenGlyph(
                    char = g.reqString("char", sourceName),
                    xMin = g.reqDouble("xMin", sourceName),
                    yMin = g.reqDouble("yMin", sourceName),
                    xMax = g.reqDouble("xMax", sourceName),
                    yMax = g.reqDouble("yMax", sourceName),
                )
            },
        )
    }

    private fun JsonObject.reqString(key: String, src: String): String =
        (this[key] ?: throw GoldenLoadException("Golden '$src' missing '$key'"))
            .jsonPrimitive.content

    private fun JsonObject.reqDouble(key: String, src: String): Double =
        (this[key] ?: throw GoldenLoadException("Golden '$src' missing '$key'"))
            .jsonPrimitive.double

    private fun JsonObject.reqInt(key: String, src: String): Int =
        (this[key] ?: throw GoldenLoadException("Golden '$src' missing '$key'"))
            .jsonPrimitive.int

    private fun JsonObject.reqArray(key: String, src: String): JsonArray =
        (this[key] ?: throw GoldenLoadException("Golden '$src' missing '$key'")) as? JsonArray
            ?: throw GoldenLoadException("Golden '$src' field '$key' is not an array")
}
