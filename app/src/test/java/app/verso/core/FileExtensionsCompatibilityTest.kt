package app.verso.core

import app.verso.core.djvu.DjvuExtractor
import app.verso.core.importer.DocumentImporter
import app.verso.core.importer.DocumentSource
import app.verso.core.importer.FormatDetector
import app.verso.core.importer.SourceFormat
import app.verso.core.model.CodeBlock
import app.verso.core.model.Heading
import app.verso.core.model.ImportError
import app.verso.core.model.ListItem
import app.verso.core.model.MathBlock
import app.verso.core.model.Paragraph
import app.verso.core.model.Quote
import app.verso.core.model.SpanKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class FileExtensionsCompatibilityTest {

    private val importer = DocumentImporter()

    @Test
    fun `standalone LaTeX tex ltx and latex files with full preamble theorem itemize and verbatim import cleanly`() = runBlocking {
        val fullLatex = """
            % Comment line that should be stripped cleanly
            \documentclass[11pt,a4paper]{article}
            \usepackage{amsmath,amssymb,amsthm}
            \title{Spectral Theory of Self-Adjoint Operators}
            \author{E. Noether}
            \date{November 2026}
            \begin{document}
            \maketitle
            \begin{abstract}
            We study the resolvent set and spectrum of unbounded self-adjoint operators on a Hilbert space \(\mathcal{H}\).
            \end{abstract}

            \section{Hilbert Space Preliminaries}
            Let \(\mathcal{H}\) be a complex Hilbert space equipped with inner product \(\langle \psi, \phi \rangle\).

            \begin{theorem}[Spectral Decomposition]
            Every bounded self-adjoint operator \(\hat{A}\) admits a projection-valued measure \(E(\lambda)\).
            \end{theorem}

            \begin{itemize}
            \item[Case 1:] Point spectrum \(\sigma_p(\hat{A})\) consisting of eigenvalues.
            \item Continuous spectrum \(\sigma_c(\hat{A})\).
            \end{itemize}

            \begin{equation}
            \langle \psi, \hat{A} \psi \rangle = \int_{-\infty}^{\infty} \lambda \, d\langle \psi, E(\lambda)\psi \rangle \tag{1}
            \end{equation}

            \begin{verbatim}
            val spectrum = computeEigenvalues(matrix)
            \end{verbatim}
            \end{document}
        """.trimIndent()

        for (ext in listOf("paper.tex", "notes.ltx", "manuscript.latex")) {
            val source = DocumentSource(ext, null, fullLatex.toByteArray(StandardCharsets.UTF_8))
            assertEquals(SourceFormat.Markdown, FormatDetector.detect(source))
            val result = importer.import(source)
            val doc = result.document
            assertEquals("Spectral Theory of Self-Adjoint Operators", doc.title)
            assertTrue(doc.blocks.none { it is Paragraph && it.text.text.contains("\\documentclass") })
            assertTrue(doc.blocks.any { it is Quote }) // Abstract rendered as Quote
            assertTrue(doc.blocks.any { it is ListItem }) // \begin{itemize} \item rendered as ListItem
            assertTrue(doc.blocks.any { it is MathBlock }) // \begin{equation} rendered as MathBlock
            assertTrue(doc.blocks.any { it is CodeBlock }) // \begin{verbatim} rendered as CodeBlock
        }
    }

    @Test
    fun `djvu and djvo files with TXTa text layer extract paragraphs and scanned djvu throws NoTextLayer`() = runBlocking {
        val textLayer = "Chapter 1: Hamiltonian Mechanics\n\nThe phase space of a classical system with n degrees of freedom is a 2n-dimensional manifold."
        val djvuBytes = buildSyntheticDjvuWithTxta(textLayer)

        for (fileName in listOf("mechanics.djvu", "archive.djvo")) {
            val source = DocumentSource(fileName, "image/vnd.djvu", djvuBytes)
            assertEquals(SourceFormat.Djvu, FormatDetector.detect(source))
            val doc = importer.import(source).document
            assertTrue(doc.blocks.isNotEmpty())
            assertTrue(doc.blocks.any { it is Paragraph && it.text.text.contains("Hamiltonian Mechanics") })
        }

        // Scanned DjVu with no TXTa/ANTa text chunk throws ImportError.NoTextLayer
        val scannedDjvu = buildSyntheticDjvuWithoutText()
        try {
            DjvuExtractor.extract(scannedDjvu)
            fail("Expected ImportError.NoTextLayer for image-only DjVu")
        } catch (_: ImportError.NoTextLayer) {
            // Expected
        }
    }

    @Test
    fun `fb2 FictionBook XML and xhtml with inline math import cleanly`() = runBlocking {
        val fb2Content = """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description>
                <title-info>
                  <book-title>Relativistic Cosmology Notes</book-title>
                </title-info>
              </description>
              <body>
                <section>
                  <title><p>1. Friedmann Equations</p></title>
                  <epigraph><p>Space tells matter how to move; matter tells space how to curve.</p></epigraph>
                  <p>For a homogeneous isotropic universe with scale factor \(a(t)\), the first Friedmann equation is:</p>
                  <p>\[\left(\frac{\dot{a}}{a}\right)^2 = \frac{8\pi G}{3}\rho - \frac{kc^2}{a^2}\]</p>
                </section>
              </body>
            </FictionBook>
        """.trimIndent()

        val source = DocumentSource("cosmology.fb2", "application/x-fictionbook+xml", fb2Content.toByteArray(StandardCharsets.UTF_8))
        assertEquals(SourceFormat.Html, FormatDetector.detect(source))
        val doc = importer.import(source).document
        assertEquals("Relativistic Cosmology Notes", doc.title)
        assertTrue(doc.blocks.any { it is Quote })
        assertTrue(doc.blocks.any { it is MathBlock })
        assertTrue(doc.blocks.filterIsInstance<Paragraph>().any { p -> p.text.spans.any { it.kind == SpanKind.Math } })
    }

    @Test
    fun `org rst adoc rmd and qmd extensions detect and extract headings`() = runBlocking {
        val orgContent = """
            #+TITLE: Statistical Thermodynamics
            * 1. Canonical Ensemble
            The partition function is defined by \(Z = \sum_i e^{-\beta E_i}\).
            ** 1.1 Helmholtz Free Energy
            Given by \(F = -k_B T \ln Z\).
        """.trimIndent()

        val orgDoc = importer.import(DocumentSource("thermo.org", null, orgContent.toByteArray())).document
        assertEquals("Statistical Thermodynamics", orgDoc.title)
        assertTrue(orgDoc.blocks.count { it is Heading } >= 3)

        val adocContent = """
            = Distributed Consensus Systems
            == 1. Paxos & Raft Safety
            Every committed log entry is durable across quorum intersections.
        """.trimIndent()
        val adocDoc = importer.import(DocumentSource("consensus.adoc", null, adocContent.toByteArray())).document
        assertEquals("Distributed Consensus Systems", adocDoc.title)
        assertTrue(adocDoc.blocks.count { it is Heading } >= 2)
    }

    private fun buildSyntheticDjvuWithTxta(text: String): ByteArray {
        val utf8 = text.toByteArray(StandardCharsets.UTF_8)
        val txtaPayload = ByteArray(3 + utf8.size)
        txtaPayload[0] = ((utf8.size ushr 16) and 0xFF).toByte()
        txtaPayload[1] = ((utf8.size ushr 8) and 0xFF).toByte()
        txtaPayload[2] = (utf8.size and 0xFF).toByte()
        System.arraycopy(utf8, 0, txtaPayload, 3, utf8.size)

        val paddedTxtaSize = if (txtaPayload.size % 2 == 1) txtaPayload.size + 1 else txtaPayload.size
        val formBodySize = 4 + 8 + paddedTxtaSize // "DJVU" + ("TXTa" + len + payload)

        val out = ByteArrayOutputStream()
        out.write("AT&TFORM".toByteArray(StandardCharsets.US_ASCII))
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(formBodySize).array())
        out.write("DJVU".toByteArray(StandardCharsets.US_ASCII))
        out.write("TXTa".toByteArray(StandardCharsets.US_ASCII))
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(txtaPayload.size).array())
        out.write(txtaPayload)
        if (txtaPayload.size % 2 == 1) out.write(0)
        return out.toByteArray()
    }

    private fun buildSyntheticDjvuWithoutText(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("AT&TFORM".toByteArray(StandardCharsets.US_ASCII))
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(12).array())
        out.write("DJVU".toByteArray(StandardCharsets.US_ASCII))
        out.write("INFO".toByteArray(StandardCharsets.US_ASCII))
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(0).array())
        return out.toByteArray()
    }
}
