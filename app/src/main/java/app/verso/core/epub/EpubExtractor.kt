package app.verso.core.epub

import app.verso.core.html.HtmlExtractor
import app.verso.core.model.Block
import app.verso.core.model.Document
import app.verso.core.model.ImportError
import app.verso.core.text.TextCleaner
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object EpubExtractor {
    fun extract(
        bytes: ByteArray,
        maxUncompressedBytes: Long = 256L * 1024 * 1024,
        onChapter: () -> Unit = {}
    ): Document {
        val entryMap = mutableMapOf<String, ByteArray>()
        var totalUncompressed = 0L

        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val lower = name.lowercase()
                    val isCandidate = lower.endsWith(".xml") ||
                            lower.endsWith(".opf") ||
                            lower.endsWith(".xhtml") ||
                            lower.endsWith(".html") ||
                            lower.endsWith(".htm") ||
                            lower.endsWith(".ncx") ||
                            name == "mimetype"

                    if (isCandidate) {
                        val buffer = ByteArray(8192)
                        val baos = ByteArrayOutputStream()
                        var read: Int
                        while (zis.read(buffer).also { read = it } != -1) {
                            totalUncompressed += read
                            if (totalUncompressed > maxUncompressedBytes) {
                                throw ImportError.Corrupt("Exceeded maximum uncompressed size cap")
                            }
                            baos.write(buffer, 0, read)
                        }
                        entryMap[name] = baos.toByteArray()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: ImportError) {
            throw e
        } catch (e: Exception) {
            throw ImportError.Corrupt("Failed to read EPUB ZIP entries", e)
        }

        if (entryMap.isEmpty()) {
            throw ImportError.Corrupt("ZIP archive is empty or invalid")
        }

        // Check DRM in META-INF/encryption.xml
        val encryptionXmlBytes = entryMap["META-INF/encryption.xml"]
        if (encryptionXmlBytes != null) {
            val encDoc = Jsoup.parse(String(encryptionXmlBytes, StandardCharsets.UTF_8), "", Parser.xmlParser())
            val encMethods = encDoc.select("EncryptionMethod, enc|EncryptionMethod")
            for (method in encMethods) {
                val algorithm = method.attr("Algorithm")
                val isFontObfuscation = algorithm == "http://www.idpf.org/2008/embedding" ||
                        algorithm == "http://ns.adobe.com/pdf/enc#RC"
                if (!isFontObfuscation && algorithm.isNotBlank()) {
                    throw ImportError.Drm()
                }
            }
        }

        // Find OPF path from META-INF/container.xml
        val containerBytes = entryMap["META-INF/container.xml"]
            ?: throw ImportError.Corrupt("META-INF/container.xml not found")
        val containerDoc = Jsoup.parse(String(containerBytes, StandardCharsets.UTF_8), "", Parser.xmlParser())
        val rootfile = containerDoc.select("rootfile[full-path], rootfiles > rootfile").firstOrNull()
            ?: throw ImportError.Corrupt("rootfile not defined in container.xml")
        val opfPath = rootfile.attr("full-path").trim()
        if (opfPath.isBlank()) {
            throw ImportError.Corrupt("Empty OPF path in container.xml")
        }

        val opfBytes = entryMap[opfPath]
            ?: throw ImportError.Corrupt("OPF file '$opfPath' not found in EPUB")
        val opfDoc = Jsoup.parse(String(opfBytes, StandardCharsets.UTF_8), "", Parser.xmlParser())

        // Extract title
        val titleElement = opfDoc.select("metadata > dc|title, metadata > title").firstOrNull()
        val bookTitle = titleElement?.text()?.trim() ?: ""

        // Map manifest items
        data class ManifestItem(val id: String, val href: String, val mediaType: String, val properties: String)
        val manifest = mutableMapOf<String, ManifestItem>()
        for (item in opfDoc.select("manifest > item")) {
            val id = item.attr("id")
            val href = item.attr("href")
            val mediaType = item.attr("media-type")
            val properties = item.attr("properties")
            manifest[id] = ManifestItem(id, href, mediaType, properties)
        }

        // Resolve spine items
        val opfDir = if (opfPath.contains('/')) opfPath.substringBeforeLast('/') + "/" else ""
        val allBlocks = mutableListOf<Block>()

        for (itemref in opfDoc.select("spine > itemref")) {
            val idref = itemref.attr("idref")
            val linear = itemref.attr("linear")
            if (linear.equals("no", ignoreCase = true)) continue

            val manifestItem = manifest[idref] ?: continue
            val mediaType = manifestItem.mediaType.lowercase()
            if (mediaType != "application/xhtml+xml" && mediaType != "text/html") continue
            if (manifestItem.properties.contains("nav")) continue

            // Resolve href relative to OPF directory
            val cleanHref = manifestItem.href.substringBefore('#')
            val decodedPath = try {
                URI(cleanHref).path ?: cleanHref
            } catch (e: Exception) {
                cleanHref
            }

            val resolvedPath = normalizePath(opfDir + decodedPath)
            val chapterBytes = entryMap[resolvedPath] ?: entryMap[cleanHref]
            if (chapterBytes == null) {
                // Skip missing chapter gracefully
                continue
            }

            val chapterHtml = decodeUtf8(chapterBytes)
            val cleanedHtml = TextCleaner.clean(chapterHtml)
            val chapterDoc = Jsoup.parse(cleanedHtml)
            val chapterBlocks = HtmlExtractor.blocksOf(chapterDoc.body())
            allBlocks.addAll(chapterBlocks)
            onChapter()
        }

        return Document(bookTitle, allBlocks)
    }

    private fun decodeUtf8(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun normalizePath(path: String): String {
        val parts = path.split('/')
        val stack = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                "", "." -> {}
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                else -> stack.add(part)
            }
        }
        return stack.joinToString("/")
    }
}
