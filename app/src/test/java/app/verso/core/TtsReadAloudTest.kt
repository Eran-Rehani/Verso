package app.verso.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.verso.core.importer.DocumentImporter
import app.verso.core.importer.DocumentSource
import app.verso.core.model.CodeBlock
import app.verso.core.model.Divider
import app.verso.core.model.Document
import app.verso.core.model.Heading
import app.verso.core.model.InlineText
import app.verso.core.model.MathBlock
import app.verso.core.model.Paragraph
import app.verso.core.model.Span
import app.verso.core.model.SpanKind
import app.verso.core.tts.TtsTextFormatter
import app.verso.reader.tts.ReaderTtsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TtsReadAloudTest {

    @Test
    fun `formatLatex converts fractions roots integrals and Greek symbols into spoken math`() {
        val quadratic = TtsTextFormatter.formatLatex("""x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a} \tag{1.1}""")
        assertTrue(quadratic.contains("equals"))
        assertTrue(quadratic.contains("plus or minus"))
        assertTrue(quadratic.contains("square root of"))
        assertTrue(quadratic.contains("b squared"))
        assertTrue(quadratic.contains("over"))
        assertTrue(quadratic.contains("equation 1.1"))

        val gaussian = TtsTextFormatter.formatLatex("""\int_{-\infty}^{\infty} e^{-x^2} dx = \sqrt{\pi}""")
        assertTrue(gaussian.contains("the integral from"))
        assertTrue(gaussian.contains("infinity"))
        assertTrue(gaussian.contains("the square root of pi"))

        val simpleFrac = TtsTextFormatter.formatLatex("""\frac{1}{2} + \binom{n}{k}""")
        assertTrue(simpleFrac.contains("1 over 2"))
        assertTrue(simpleFrac.contains("n choose k"))
    }

    @Test
    fun `formatInlineText expands inline math spans into spoken math seamlessly`() {
        val raw = "For x in \\mathbb{R}, the energy is E = mc^2."
        val rStart = raw.indexOf("\\mathbb{R}")
        val rEnd = rStart + "\\mathbb{R}".length
        val eStart = raw.indexOf("E = mc^2")
        val eEnd = eStart + "E = mc^2".length

        val inline = InlineText(
            text = raw,
            spans = listOf(
                Span(rStart, rEnd, SpanKind.Math),
                Span(eStart, eEnd, SpanKind.Math)
            )
        )
        val spoken = TtsTextFormatter.formatInlineText(inline)
        assertTrue(spoken.contains("the real numbers"))
        assertTrue(spoken.contains("E equals m c squared"))
    }

    @Test
    fun `buildQueue skips dividers and maps block indices accurately`() = runBlocking {
        val doc = Document(
            title = "Lecture Notes",
            blocks = listOf(
                Heading(1, InlineText.plain("Chapter 1: Waves")),
                Divider,
                Paragraph(InlineText.plain("A wave propagates at phase velocity v.")),
                MathBlock("""\nabla^2 \psi = 0"""),
                CodeBlock("kotlin", "val c = 299792458")
            )
        )

        val queue = TtsTextFormatter.buildQueue(doc)
        // Divider at blockIndex 1 is skipped, leaving 4 speakable items
        assertEquals(4, queue.size)
        assertEquals(0, queue[0].blockIndex)
        assertEquals(2, queue[1].blockIndex)
        assertEquals(3, queue[2].blockIndex)
        assertEquals(4, queue[3].blockIndex)

        // Binary search for blockIndex 1 (Divider) resolves to queueIndex 1 (blockIndex 2)
        assertEquals(1, TtsTextFormatter.findQueueIndexForBlock(queue, 1))
        assertEquals(2, TtsTextFormatter.findQueueIndexForBlock(queue, 3))
    }

    @Test
    fun `ReaderTtsController manages play pause next previous speed and stop state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val doc = Document(
            title = "Test Doc",
            blocks = listOf(
                Heading(1, InlineText.plain("Introduction")),
                Paragraph(InlineText.plain("First paragraph of the document.")),
                Paragraph(InlineText.plain("Second paragraph of the document."))
            )
        )

        val controller = ReaderTtsController(context, scope, doc)
        try {
            assertFalse(controller.state.value.isActive)
            assertEquals(3, controller.state.value.queue.size)

            // Start from block 1
            controller.startFromBlock(1)
            assertTrue(controller.state.value.isActive)
            assertTrue(controller.state.value.isPlaying)
            assertEquals(1, controller.state.value.activeBlockIndex)

            // Advance to next block
            controller.nextBlock()
            assertEquals(2, controller.state.value.activeBlockIndex)

            // Step back to previous block
            controller.previousBlock()
            assertEquals(1, controller.state.value.activeBlockIndex)

            // Pause and resume
            controller.pause()
            assertFalse(controller.state.value.isPlaying)
            assertTrue(controller.state.value.isActive)

            controller.resume()
            assertTrue(controller.state.value.isPlaying)

            // Cycle speech rate: 1.0 -> 1.25 -> 1.5 -> 2.0 -> 0.75
            assertEquals(1.0f, controller.state.value.speechRate, 0.01f)
            controller.cycleSpeed()
            assertEquals(1.25f, controller.state.value.speechRate, 0.01f)
            controller.cycleSpeed()
            assertEquals(1.5f, controller.state.value.speechRate, 0.01f)

            // Toggle auto-scroll
            assertTrue(controller.state.value.autoScroll)
            controller.toggleAutoScroll()
            assertFalse(controller.state.value.autoScroll)

            // Stop
            controller.stop()
            assertFalse(controller.state.value.isActive)
            assertFalse(controller.state.value.isPlaying)
        } finally {
            controller.shutdown()
        }
    }
}
