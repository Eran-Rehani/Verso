package app.verso.reader

import app.verso.core.importer.DocumentSource
import app.verso.core.model.Document
import app.verso.core.model.InlineText
import app.verso.core.model.Paragraph
import app.verso.reader.data.db.BookEntity
import app.verso.reader.intake.ImportCoordinator
import app.verso.reader.intake.ImportState
import app.verso.reader.intake.ImportedDocument
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ImportCoordinatorTest {

    private fun source(name: String): DocumentSource =
        DocumentSource(name, "text/markdown", "hello".toByteArray())

    private fun imported(id: String, title: String): ImportedDocument {
        val book = BookEntity(id = id, title = title, format = "Markdown")
        val document = Document(title = title, blocks = listOf(Paragraph(InlineText.plain("body"))))
        return ImportedDocument(book, document)
    }

    private class Recorder {
        val events = mutableListOf<ImportState>()
        val sink: (ImportState) -> Unit = { events += it }
    }

    @Test
    fun `stale canceled request never replaces newer complete request`() = runTest {
        val rec = Recorder()
        val work = StandardTestDispatcher(testScheduler)
        val gateA = CompletableDeferred<Unit>()
        val processCalls = AtomicInteger(0)

        lateinit var coordinator: ImportCoordinator
        coordinator = ImportCoordinator(
            scope = this,
            work = work,
            process = { src, _ ->
                processCalls.incrementAndGet()
                if (src.name == "A") gateA.await()
                imported(src.name ?: error("Missing fixture name"), "Title ${src.name}")
            },
            onState = rec.sink
        )

        coordinator.start("A") { source("A") }
        runCurrent()
        // A has started and is gated inside its processor.
        assertTrue(rec.events.any { it is ImportState.Running })

        coordinator.start("B") { source("B") }
        advanceUntilIdle()

        val completes = rec.events.filterIsInstance<ImportState.Complete>()
        assertEquals(1, completes.size)
        assertEquals("B", completes.single().result.book.id)
        assertFalse(rec.events.any { it is ImportState.Complete && it.result.book.id == "A" })
    }

    @Test
    fun `cancel stops suspending processor and emits Idle without Complete`() = runTest {
        val rec = Recorder()
        val work = StandardTestDispatcher(testScheduler)
        val loopGate = CompletableDeferred<Unit>()
        val processedAfterCancel = AtomicInteger(0)
        val loopIterations = AtomicInteger(0)

        val coordinator = ImportCoordinator(
            scope = this,
            work = work,
            process = { _, _ ->
                // Suspending loop; only proceeds past a single iteration when the gate fires.
                while (true) {
                    loopIterations.incrementAndGet()
                    loopGate.await()
                    processedAfterCancel.incrementAndGet()
                }
                @Suppress("UNREACHABLE_CODE") imported("loop", "Loop")
            },
            onState = rec.sink
        )

        coordinator.start("Loop") { source("Loop") }
        runCurrent()
        assertTrue(loopIterations.get() >= 1)

        coordinator.cancel()
        runCurrent()

        // Completing the gate after cancellation must not resume the canceled processor.
        loopGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(0, processedAfterCancel.get())
        assertTrue(rec.events.any { it is ImportState.Idle })
        assertFalse(rec.events.any { it is ImportState.Complete })
    }

    @Test
    fun `oversized source fails without invoking processor`() = runTest {
        val rec = Recorder()
        val work = StandardTestDispatcher(testScheduler)
        val processCalls = AtomicInteger(0)

        val coordinator = ImportCoordinator(
            scope = this,
            work = work,
            process = { src, _ ->
                processCalls.incrementAndGet()
                imported(src.name ?: error("Missing fixture name"), "Title")
            },
            onState = rec.sink,
            maxSourceBytes = 8
        )

        coordinator.start("Big") {
            DocumentSource("Big", "text/markdown", ByteArray(9))
        }
        advanceUntilIdle()

        assertEquals(0, processCalls.get())
        val failed = rec.events.filterIsInstance<ImportState.Failed>()
        assertEquals(1, failed.size)
        assertTrue(failed.single().message.isNotBlank())
        assertFalse(rec.events.any { it is ImportState.Complete })
    }

    @Test
    fun `late phase and error from canceled request cannot overwrite newer complete`() = runTest {
        val rec = Recorder()
        val work = StandardTestDispatcher(testScheduler)
        val gateA = CompletableDeferred<Unit>()
        val pendingPhase = CompletableDeferred<(String) -> Unit>()

        val coordinator = ImportCoordinator(
            scope = this,
            work = work,
            process = { src, phase ->
                if (src.name == "A") {
                    pendingPhase.complete(phase)
                    try { gateA.await() }
                    finally { throw app.verso.core.model.ImportError.Corrupt("stale failure") }
                }
                imported(src.name ?: error("Missing fixture name"), "Title ${src.name}")
            },
            onState = rec.sink
        )

        coordinator.start("A") { source("A") }
        runCurrent()

        coordinator.start("B") { source("B") }
        advanceUntilIdle()

        val completesBefore = rec.events.filterIsInstance<ImportState.Complete>()
        assertEquals(1, completesBefore.size)
        assertEquals("B", completesBefore.single().result.book.id)
        val eventCountBefore = rec.events.size

        // Fire the stale phase callback captured from the canceled A request.
        advanceUntilIdle()
        pendingPhase.await().invoke("Stale phase A")
        advanceUntilIdle()

        // No new Complete replaced B, and no stale Running phase leaked in.
        val completesAfter = rec.events.filterIsInstance<ImportState.Complete>()
        assertEquals(1, completesAfter.size)
        assertEquals("B", completesAfter.single().result.book.id)
        assertFalse(rec.events.drop(eventCountBefore).any { it is ImportState.Running })

        // Complete the A gate so no coroutine is left suspended at test end.
        gateA.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, rec.events.filterIsInstance<ImportState.Complete>().size)
    }
}
