package app.verso.core.pdf

import app.verso.reader.pdf.PdfBoxPageSource
import app.verso.reader.pdf.PdfRuleCollector
import app.verso.reader.pdf.PdfRuleFilter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The extractor must preserve the structure that baseline flattening destroys.
 *
 * These tests build their own PDFs with the same bundled PDFBox API the app uses, so nothing
 * private is involved and the assertions are about real extraction, not a fake source.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfGlyphExtractionTest {

    private val context: android.content.Context by lazy {
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    }

    init {
        // PDType1Font resolves the standard-14 fonts through PDFBox resources, which must be
        // initialised before a document can be written in Robolectric.
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    /** A page with text at two sizes and two baselines: a base glyph and a raised script. */
    private fun pdfWithScript(): ByteArray {
        val doc = PDDocument()
        try {
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(72f, 700f)
                cs.showText("x")
                cs.endText()
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 8f)
                cs.newLineAtOffset(79f, 704f)
                cs.showText("2")
                cs.endText()
            }
            return ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        } finally {
            doc.close()
        }
    }

    /** A page with one thin stroked horizontal line: the way TeX draws a fraction bar. */
    private fun pdfWithThinRule(): ByteArray {
        val doc = PDDocument()
        try {
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.addRect(72f, 700f, 40f, 0.4f)
                cs.fill()
            }
            return ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        } finally {
            doc.close()
        }
    }

    private fun extract(bytes: ByteArray): PdfContent {
        return PdfBoxPageSource(context).read(bytes)
    }

    @Test
    fun `extraction records individual glyphs not just runs`() {
        val content = extract(pdfWithScript())
        val page = content.pages.single()
        assertTrue("expected glyphs to be captured, got ${page.glyphs.size}", page.glyphs.isNotEmpty())
        // Both the base and the script must survive as separate glyphs.
        val texts = page.glyphs.map { it.text }
        assertTrue("expected a base glyph in $texts", texts.any { it.contains("x") })
        assertTrue("expected a script glyph in $texts", texts.any { it.contains("2") })
    }

    @Test
    fun `script keeps its own smaller font size`() {
        val page = extract(pdfWithScript()).pages.single()
        val sizes = page.glyphs.map { it.fontSize }.distinct()
        assertTrue(
            "the 8pt script must not inherit the 12pt run size; sizes were $sizes",
            sizes.any { it < 10f } && sizes.any { it > 10f },
        )
    }

    @Test
    fun `script keeps its own raised baseline`() {
        val page = extract(pdfWithScript()).pages.single()
        val baselines = page.glyphs.map { it.baseline }.distinct()
        assertEquals(
            "the raised script must not share the base baseline; baselines were $baselines",
            2,
            baselines.size,
        )
    }

    @Test
    fun `a filled thin rectangle is collected as a rule`() {
        val rules = mutableListOf<PdfRule>()
        PDDocument.load(ByteArrayInputStream(pdfWithThinRule())).use { doc ->
            val page = doc.getPage(0)
            PdfRuleCollector(page) { rules += it }.processPage(page)
        }
        assertTrue("expected a painted rule, got ${rules.size}", rules.isNotEmpty())
        val rule = rules.first()
        assertTrue("rule should span horizontally, was ${rule.x0}..${rule.x1}", rule.x1 > rule.x0)
        assertTrue("rule should be thin, was ${rule.thickness}", rule.thickness in 0f..3f)
    }

    @Test
    fun `a page with no painted geometry yields no rules`() {
        val rules = mutableListOf<PdfRule>()
        val doc = PDDocument()
        try {
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(72f, 700f)
                cs.showText("plain prose")
                cs.endText()
            }
            val bytes = ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
            PDDocument.load(ByteArrayInputStream(bytes)).use { loaded ->
                val loadedPage = loaded.getPage(0)
                PdfRuleCollector(loadedPage) { rules += it }.processPage(loadedPage)
            }
        } finally {
            doc.close()
        }
        assertEquals("text alone must not produce rules", 0, rules.size)
    }

    @Test
    fun `heavy rules are filtered out so dividers are not treated as fraction bars`() {
        val pageHeight = 800f
        val thin = PdfRule(0f, 100f, 400f, 0.4f)
        val heavy = PdfRule(0f, 400f, 300f, 20f)
        val kept = PdfRuleFilter.thinRules(listOf(thin, heavy), pageHeight)
        assertEquals(listOf(thin), kept)
    }

    @Test
    fun `synthetic fixture on the classpath still extracts glyphs`() {
        val stream = javaClass.classLoader?.getResourceAsStream("pdf/rules-synthetic.pdf")
        assertNotNull("fixture missing from test resources", stream)
        val content = extract(stream!!.use { it.readBytes() })
        assertTrue("fixture should produce text runs", content.pages.isNotEmpty())
        assertTrue(
            "fixture should produce glyphs",
            content.pages.first().glyphs.isNotEmpty(),
        )
    }
}
