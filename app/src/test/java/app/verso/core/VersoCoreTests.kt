package app.verso.core

import app.verso.core.epub.EpubExtractor
import app.verso.core.html.HtmlExtractor
import app.verso.core.importer.DocumentImporter
import app.verso.core.importer.DocumentSource
import app.verso.core.importer.FormatDetector
import app.verso.core.importer.SourceFormat
import app.verso.core.importer.TextDecoding
import app.verso.core.markdown.MarkdownExtractor
import app.verso.core.markdown.MathProtector
import app.verso.core.model.CodeBlock
import app.verso.core.model.Divider
import app.verso.core.model.Document
import app.verso.core.model.DocumentCodec
import app.verso.core.model.Heading
import app.verso.core.model.ImageBlock
import app.verso.core.model.ImportError
import app.verso.core.model.InlineText
import app.verso.core.model.ListItem
import app.verso.core.model.MathBlock
import app.verso.core.model.Paragraph
import app.verso.core.model.Quote
import app.verso.core.model.Span
import app.verso.core.model.SpanKind
import app.verso.core.model.TableBlock
import app.verso.core.model.plainText
import app.verso.core.pdf.PdfAssembler
import app.verso.core.pdf.PdfContent
import app.verso.core.pdf.PdfExtractor
import app.verso.core.pdf.PdfLayout
import app.verso.core.pdf.PdfLine
import app.verso.core.pdf.PdfPage
import app.verso.core.pdf.PdfPageSource
import app.verso.core.pdf.TextRun
import app.verso.core.store.FontChoice
import app.verso.core.store.LibraryStore
import app.verso.core.store.ReadAlign
import app.verso.core.store.ReaderSettings
import app.verso.core.store.SettingsStore
import app.verso.core.store.ThemeChoice
import app.verso.core.store.coerced
import app.verso.core.store.progress
import app.verso.core.text.BlockSplitter
import app.verso.core.text.PlainTextExtractor
import app.verso.core.text.TextCleaner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VersoCoreTests {

    // --- Task 2: DocumentCodec & ImportError tests ---
    @Test
    fun `round trips every block type`() {
        val doc = Document(
            "T", listOf(
                Heading(2, InlineText("H", listOf(Span(0, 1, SpanKind.Bold)))),
                Paragraph(InlineText("a link", listOf(Span(2, 6, SpanKind.Link, "https://x.y")))),
                ListItem(1, "•", InlineText.plain("item")),
                Quote(0, InlineText.plain("q")),
                CodeBlock("kotlin", "val x = 1"),
                MathBlock("E=mc^2"),
                TableBlock(listOf(InlineText.plain("h")), listOf(listOf(InlineText.plain("c")))),
                ImageBlock("alt"),
                Divider
            )
        )
        assertEquals(doc, DocumentCodec.decode(DocumentCodec.encode(doc)))
    }

    @Test
    fun `decode of truncated json throws exception`() {
        val json = DocumentCodec.encode(Document("T", listOf(Paragraph(InlineText.plain("x")))))
        try {
            DocumentCodec.decode(json.dropLast(5))
            fail("Expected SerializationException")
        } catch (e: Exception) {
            // Success
        }
    }

    @Test
    fun `plainText covers every block kind`() {
        assertEquals("a", Paragraph(InlineText.plain("a")).plainText())
        assertEquals("x = 1", CodeBlock(null, "x = 1").plainText())
        assertEquals("E", MathBlock("E").plainText())
        assertEquals("alt", ImageBlock("alt").plainText())
        assertEquals("", Divider.plainText())
        assertEquals("h c", TableBlock(listOf(InlineText.plain("h")), listOf(listOf(InlineText.plain("c")))).plainText())
    }

    @Test
    fun `import errors carry exact user messages`() {
        assertEquals("No readable text was found in this file.", ImportError.Empty().userMessage)
        assertEquals("This book is DRM-protected and can't be opened.", ImportError.Drm().userMessage)
        assertEquals("This PDF has no selectable text (it looks scanned). OCR isn't supported.", ImportError.NoTextLayer().userMessage)
        assertEquals("This file is larger than 64 MB.", ImportError.TooLarge().userMessage)
        assertEquals("Unsupported file: ZIP archive", ImportError.Unsupported("ZIP archive").userMessage)
        assertEquals("This file couldn't be read.", ImportError.Corrupt("x").userMessage)
    }

    // --- Task 3: TextCleaner, BlockSplitter & PlainTextExtractor ---
    @Test
    fun `clean normalizes line endings and strips invisibles`() {
        assertEquals("a\nb\nc", TextCleaner.clean("\uFEFFa\r\nb\rc"))
        assertEquals("a b", TextCleaner.clean("a\u00A0b"))
        assertEquals("coopx", TextCleaner.clean("co\u00ADop\u200Bx"))
        assertEquals("a\u200Cb", TextCleaner.clean("a\u200Cb"))
        assertEquals("ab", TextCleaner.clean("a\u0007b"))
        assertEquals("a b", TextCleaner.clean("a\tb"))
    }

    @Test
    fun `long paragraph splits after sentence ends and loses nothing - Review Focus 1`() {
        val text = (1..1000).joinToString("") { "Sentence $it. " }
        val out = BlockSplitter.splitLong(listOf(Paragraph(InlineText.plain(text))), 2500)
            .map { (it as Paragraph).text.text }
        assertTrue(out.size > 1)
        assertTrue(out.all { it.length <= 2500 })
        assertEquals(text, out.joinToString(""))
        assertTrue(out.dropLast(1).all { it.endsWith(". ") })
    }

    @Test
    fun `text without whitespace hard-splits at the limit`() {
        val out = BlockSplitter.splitLong(listOf(Paragraph(InlineText.plain("x".repeat(6000)))), 2500)
            .map { (it as Paragraph).text.text.length }
        assertEquals(listOf(2500, 2500, 1000), out)
    }

    @Test
    fun `spans are clipped across chunks`() {
        val p = Paragraph(InlineText("a".repeat(3000), listOf(Span(2400, 2600, SpanKind.Bold))))
        val out = BlockSplitter.splitLong(listOf(p), 2500).map { (it as Paragraph).text }
        assertEquals(listOf(Span(2400, 2500, SpanKind.Bold)), out[0].spans)
        assertEquals(listOf(Span(0, 100, SpanKind.Bold)), out[1].spans)
    }

    @Test
    fun `code splits only at newlines`() {
        val code = (1..1000).joinToString("\n") { "line $it" }
        val out = BlockSplitter.splitLong(listOf(CodeBlock("py", code)), 2500).map { (it as CodeBlock).code }
        assertTrue(out.all { it.length <= 2500 })
        assertEquals(code, out.joinToString(""))
        assertTrue(out.dropLast(1).all { it.endsWith("\n") })
    }

    @Test
    fun `hard-wrapped prose is unwrapped`() {
        val line = "word ".repeat(14).trim()
        val doc = PlainTextExtractor.extract("$line\n$line\n$line")
        assertEquals("$line $line $line", (doc.blocks.single() as Paragraph).text.text)
    }

    // --- Task 4: HtmlExtractor ---
    @Test
    fun `headings paragraphs and inline styles`() {
        val doc = HtmlExtractor.extract("<html><head><title>T</title></head><body><h2>Hi</h2><p>a <b>b</b> <i>c</i> <a href=\"u\">d</a><br>e</p></body></html>")
        assertEquals("T", doc.title)
        assertEquals(Heading(2, InlineText.plain("Hi")), doc.blocks[0])
        val p = doc.blocks[1] as Paragraph
        assertEquals("a b c d\ne", p.text.text)
        assertTrue(p.text.spans.any { it.kind == SpanKind.Bold })
        assertTrue(p.text.spans.any { it.kind == SpanKind.Italic })
        assertTrue(p.text.spans.any { it.kind == SpanKind.Link && it.href == "u" })
    }

    // --- Task 5: MathProtector & MarkdownExtractor ---
    @Test
    fun `math protector protects inline and display math`() {
        val r = MathProtector.protect("Energy \$E=mc^2\$ here")
        assertTrue(r.math.isNotEmpty())
        assertEquals("E=mc^2", r.math.first().latex)
        assertFalse(r.math.first().display)

        // Prices are preserved
        val prices = "costs $5 and $10 total"
        assertEquals(prices, MathProtector.protect(prices).text)
    }

    @Test
    fun `markdown extraction with math and tables`() {
        val md = "# Document Title\n\nHere is **bold** and *italic* and \$E=mc^2\$ math.\n\n| A | B |\n|---|---|\n| 1 | 2 |"
        val doc = MarkdownExtractor.extract(md)
        assertTrue(doc.blocks.isNotEmpty())
        assertTrue(doc.blocks.any { it is Heading })
        assertTrue(doc.blocks.any { it is TableBlock })
    }

    // --- Task 7 & 8: PDF layout & assembly ---
    @Test
    fun `scanned pdf throws NoTextLayer - Review Focus 5`() {
        val fakeSource = object : PdfPageSource {
            override fun read(pdf: ByteArray, onPage: () -> Unit): PdfContent {
                return PdfContent(title = "Empty", pages = listOf(PdfPage(0, 600f, 800f, emptyList())))
            }
        }
        val extractor = PdfExtractor(fakeSource)
        try {
            extractor.extract(ByteArray(100))
            fail("Expected NoTextLayer")
        } catch (e: ImportError.NoTextLayer) {
            // Pass
        }
    }

    // --- Task 9: Format detection and pipeline ---
    @Test
    fun `one million characters on a single line become many small blocks - Review Focus 1`() = runBlocking {
        val bigText = "word ".repeat(200_000)
        val source = DocumentSource("big.txt", "text/plain", bigText.toByteArray())
        val result = DocumentImporter().import(source)
        assertTrue(result.document.blocks.size >= 400)
        assertTrue(result.document.blocks.all { it.plainText().length <= 2500 })
    }

    // --- Task 10: Store & Settings ---
    @Test
    fun `settings are coerced into range on load and save - Review Focus 3`() {
        val tempDir = Files.createTempDirectory("settings_test").toFile()
        try {
            val file = File(tempDir, "settings.json")
            val store = SettingsStore(file)
            store.save(ReaderSettings(fontSizeSp = 999f, lineHeight = 0.1f, paragraphSpacingEm = -3f, marginDp = 500))
            val loaded = store.load()
            assertEquals(48f, loaded.fontSizeSp)
            assertEquals(1.0f, loaded.lineHeight)
            assertEquals(0f, loaded.paragraphSpacingEm)
            assertEquals(48, loaded.marginDp)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `corrupt json falls back to default - Review Focus 3`() {
        val tempDir = Files.createTempDirectory("corrupt_test").toFile()
        try {
            val file = File(tempDir, "settings.json")
            file.writeText("{\"fontSizeSp\": 2") // Truncated
            val store = SettingsStore(file)
            assertEquals(ReaderSettings(), store.load())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `saving an existing document keeps anchor and addedAt - Review Focus 4`() {
        val tempDir = Files.createTempDirectory("library_test").toFile()
        try {
            val store = LibraryStore(tempDir)
            val doc = Document("Title", listOf(Paragraph(InlineText.plain("Hello"))))
            val bytes = "content".toByteArray()
            val id = store.idFor(bytes)

            val entry1 = store.save(id, doc, SourceFormat.PlainText)
            store.updateAnchor(id, block = 1, offsetPx = 50)

            val entry2 = store.save(id, doc.copy(title = "Updated Title"), SourceFormat.PlainText)
            assertEquals(entry1.addedAt, entry2.addedAt)
            val loadedEntry = store.entry(id)
            assertEquals(1, loadedEntry?.anchorBlock)
            assertEquals(50, loadedEntry?.anchorOffsetPx)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `book entity progress calculation and metadata`() {
        val book = app.verso.reader.data.db.BookEntity(
            id = "test-id",
            title = "Pride and Prejudice",
            format = "Epub",
            addedAt = 1000L,
            lastOpenedAt = 2000L,
            blockCount = 101,
            anchorBlock = 50,
            anchorOffsetPx = 10
        )
        assertEquals("Pride and Prejudice", book.title)
        assertEquals("Epub", book.format)
        assertEquals(0.5f, book.progress, 0.001f)
    }

    @Test
    fun `library search filters books by title case-insensitively`() {
        val books = listOf(
            app.verso.reader.data.db.BookEntity("1", "Pride and Prejudice", "Epub"),
            app.verso.reader.data.db.BookEntity("2", "Moby Dick", "Pdf"),
            app.verso.reader.data.db.BookEntity("3", "The Great Gatsby", "Markdown")
        )
        val query = "great"
        val filtered = books.filter { it.title.contains(query.trim(), ignoreCase = true) }
        assertEquals(1, filtered.size)
        assertEquals("The Great Gatsby", filtered.first().title)

        val noMatch = books.filter { it.title.contains("NonExistent", ignoreCase = true) }
        assertTrue(noMatch.isEmpty())
    }

    @Test
    fun `latex parser builds structured AST for fractions roots limits and matrices`() {
        val parsed = app.verso.core.latex.LatexParser.parseEquation(
            """x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a} \tag{1.1}"""
        )
        assertEquals("1.1", parsed.tag)
        val rootRow = parsed.root as app.verso.core.latex.MathNode.Row
        assertTrue(rootRow.children.any { it is app.verso.core.latex.MathNode.Fraction })

        val matrixNode = app.verso.core.latex.LatexParser.parse(
            """\begin{pmatrix} \cos\theta & -\sin\theta \\ \sin\theta & \cos\theta \end{pmatrix}"""
        ) as app.verso.core.latex.MathNode.Matrix
        assertEquals(app.verso.core.latex.MatrixEnvType.PAREN, matrixNode.envType)
        assertEquals(2, matrixNode.rows.size)
        assertEquals(2, matrixNode.rows[0].size)

        val readable = app.verso.core.latex.LatexParser.toReadablePlainText("""\nabla^2 \psi + \mathbb{R} = 0""")
        assertTrue(readable.contains("∇²"))
        assertTrue(readable.contains("ψ"))
        assertTrue(readable.contains("ℝ"))
    }

    @Test
    fun `markdown extractor handles latex environments and section macros`() {
        val texMd = """
            \section{Quantum Mechanics}
            The wave function $\psi(x)$ satisfies:
            \begin{equation}
            \hat{H}\psi = E\psi
            \end{equation}
            \subsection{Spin Matrices}
        """.trimIndent()
        val doc = MarkdownExtractor.extract(texMd)
        val headings = doc.blocks.filterIsInstance<Heading>()
        assertEquals(2, headings.size)
        assertEquals("Quantum Mechanics", headings[0].text.text)
        assertEquals(1, headings[0].level)
        assertEquals("Spin Matrices", headings[1].text.text)
        assertEquals(2, headings[1].level)
        assertTrue(doc.blocks.any { it is MathBlock && it.latex.contains("\\hat{H}\\psi = E\\psi") })
    }

    @Test
    fun `toc extractor builds hierarchical entries and formats inline math in headings`() {
        val md = """
            # Chapter 1: Introduction
            Introductory paragraph.
            ## 1.1 The Wave Equation $\nabla^2 u = 0$
            Second paragraph.
            ### 1.1.1 Boundary Conditions
            Third paragraph.
            # Chapter 2: Applications
            Final paragraph.
        """.trimIndent()
        val doc = MarkdownExtractor.extract(md)
        val toc = app.verso.core.toc.TocExtractor.extract(doc)
        assertEquals(4, toc.size)
        assertEquals("Chapter 1: Introduction", toc[0].title)
        assertEquals(1, toc[0].level)
        assertTrue(toc[1].title.contains("∇²"))
        assertEquals(2, toc[1].level)
        assertEquals(3, toc[2].level)
        assertEquals(1, toc[3].level)

        // Active index and prev/next navigation
        assertEquals(1, app.verso.core.toc.TocExtractor.activeEntryIndex(toc, toc[1].blockIndex))
        assertEquals(toc[0], app.verso.core.toc.TocExtractor.previousEntry(toc, toc[1].blockIndex))
        assertEquals(toc[2], app.verso.core.toc.TocExtractor.nextEntry(toc, toc[1].blockIndex))
    }

    // --- Task: Full-Text Document Searcher tests ---
    @Test
    fun `search finds keywords across headings paragraphs and code blocks`() {
        val md = """
            # Modern Architecture
            In software engineering, microservices offer modular decoupling.
            However, monoliths provide simplicity.
            ```kotlin
            fun startMicroservice() { println("microservice running") }
            ```
            ## Performance Evaluation
            We tested microservice latency under peak traffic.
        """.trimIndent()
        val doc = MarkdownExtractor.extract(md)

        val matches = app.verso.core.search.DocumentSearcher.search(doc, "microservice")
        assertEquals(4, matches.size)

        // Paragraph 1 match
        assertEquals("Modern Architecture", matches[0].sectionTitle)
        assertTrue(matches[0].snippet.contains("microservices", ignoreCase = true))

        // Code block matches
        assertEquals("Modern Architecture", matches[1].sectionTitle)
        assertEquals("Modern Architecture", matches[2].sectionTitle)

        // Section 2 paragraph match
        assertEquals("Performance Evaluation", matches[3].sectionTitle)

        // Navigation tests
        assertEquals(1, app.verso.core.search.DocumentSearcher.nextMatchIndex(0, matches.size))
        assertEquals(0, app.verso.core.search.DocumentSearcher.nextMatchIndex(3, matches.size))
        assertEquals(3, app.verso.core.search.DocumentSearcher.prevMatchIndex(0, matches.size))
        assertEquals(1, app.verso.core.search.DocumentSearcher.prevMatchIndex(2, matches.size))
    }

    @Test
    fun `search returns empty for blank query or missing terms`() {
        val doc = Document("Doc", listOf(Paragraph(InlineText.plain("Hello world"))))
        assertTrue(app.verso.core.search.DocumentSearcher.search(doc, "").isEmpty())
        assertTrue(app.verso.core.search.DocumentSearcher.search(doc, "   ").isEmpty())
        assertTrue(app.verso.core.search.DocumentSearcher.search(doc, "quantum").isEmpty())
    }

    // --- Task: BookmarkHelper tests ---
    @Test
    fun `bookmark helper computes page numbers section titles and snippets`() {
        val blocks = (0 until 12).map { idx ->
            if (idx == 0) Heading(1, InlineText.plain("Chapter 1: Foundations"))
            else if (idx == 6) Heading(2, InlineText.plain("1.2 Advanced Topics"))
            else Paragraph(InlineText.plain("Paragraph content at block $idx."))
        }
        val doc = Document("Test Book", blocks)

        val descPage1 = app.verso.core.bookmark.BookmarkHelper.describe(doc, blockIndex = 2, offsetPx = 15)
        assertEquals(1, descPage1.pageNumber)
        assertEquals(3, descPage1.totalPages)
        assertEquals("Chapter 1: Foundations", descPage1.sectionTitle)
        assertTrue(descPage1.snippet.contains("Paragraph content at block 2"))

        val descPage2 = app.verso.core.bookmark.BookmarkHelper.describe(doc, blockIndex = 7, offsetPx = 0)
        assertEquals(2, descPage2.pageNumber)
        assertEquals(3, descPage2.totalPages)
        assertEquals("1.2 Advanced Topics", descPage2.sectionTitle)
        assertTrue(descPage2.snippet.contains("Paragraph content at block 7"))
    }

    @Test
    fun `all sample documents across md tex html and txt import and extract blocks cleanly`() = kotlinx.coroutines.runBlocking {
        val importer = DocumentImporter()
        val samples = app.verso.reader.sample.SampleDocuments.all
        assertEquals(5, samples.size)

        // 1. Mathematical Physics Primer (.md)
        val mathMd = importer.import(samples[0].toDocumentSource()).document
        assertTrue(mathMd.blocks.any { it is MathBlock })
        assertTrue(app.verso.core.toc.TocExtractor.extract(mathMd).size >= 6)
        assertTrue(app.verso.core.search.DocumentSearcher.search(mathMd, "Gaussian").isNotEmpty())

        // 2. Quantum Path Integrals (.tex)
        val quantumTex = importer.import(samples[1].toDocumentSource()).document
        val texToc = app.verso.core.toc.TocExtractor.extract(quantumTex)
        assertTrue(texToc.any { it.title.contains("Feynman Path Integral") })
        assertTrue(texToc.any { it.title.contains("Harmonic Oscillator") })
        assertTrue(quantumTex.blocks.count { it is MathBlock } >= 4)

        // 3. Algorithms & Complexity (.md with GFM table containing inline math + Kotlin code block)
        val algoMd = importer.import(samples[2].toDocumentSource()).document
        val table = algoMd.blocks.filterIsInstance<TableBlock>().firstOrNull()
        assertTrue(table != null)
        val hasInlineMathInTable = table!!.rows.flatten().any { cell ->
            cell.spans.any { it.kind == SpanKind.Math }
        }
        assertTrue("Table cells should preserve inline LaTeX math spans", hasInlineMathInTable)
        assertTrue(algoMd.blocks.any { it is CodeBlock && it.language == "kotlin" })
        assertTrue(algoMd.blocks.any { it is Quote })

        // 4. Typography for Digital Reading (.html)
        val typoHtml = importer.import(samples[3].toDocumentSource()).document
        assertEquals("The Craft of Digital Typography", typoHtml.title)
        assertTrue(typoHtml.blocks.any { it is Quote })
        assertTrue(typoHtml.blocks.any { it is ListItem })

        // 5. Meditations Book IV (.txt)
        val meditationsTxt = importer.import(samples[4].toDocumentSource()).document
        assertTrue(meditationsTxt.blocks.size >= 5)
        val bm = app.verso.core.bookmark.BookmarkHelper.describe(meditationsTxt, blockIndex = 1)
        assertEquals(1, bm.pageNumber)
        assertTrue(bm.snippet.isNotBlank())
    }
}


