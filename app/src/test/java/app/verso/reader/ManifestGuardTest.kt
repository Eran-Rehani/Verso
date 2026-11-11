package app.verso.reader

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class ManifestGuardTest {
    private val manifest: String by lazy {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: File(".").walk().firstOrNull { it.name == "AndroidManifest.xml" }
            ?: error("AndroidManifest.xml not found")
        file.readText()
    }

    @Test
    fun `manifest declares no permissions`() {
        assertFalse(manifest.contains("uses-permission"))
    }

    @Test
    fun `manifest does not reference webview or internet`() {
        assertFalse(manifest.contains("INTERNET"))
        assertFalse(manifest.contains("WebView"))
    }
}
