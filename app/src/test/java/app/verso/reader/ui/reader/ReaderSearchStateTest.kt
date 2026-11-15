package app.verso.reader.ui.reader

import app.verso.core.model.Document
import app.verso.core.model.InlineText
import app.verso.core.model.Paragraph
import app.verso.core.search.DocumentSearcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSearchStateTest {
    private fun document(text: String) = Document("test", listOf(Paragraph(InlineText.plain(text))))

    @Test fun debounceAndLatestQuery() = runTest {
        val state = ReaderSearchState(this, StandardTestDispatcher(testScheduler))
        val doc = document("alpha beta beta")
        state.update(doc, emptyList(), "alpha", true)
        assertTrue(state.result.searching)
        advanceTimeBy(179)
        runCurrent()
        assertTrue(state.result.matches.isEmpty())
        state.update(doc, emptyList(), "beta", true)
        advanceTimeBy(179)
        runCurrent()
        assertTrue(state.result.searching)
        advanceTimeBy(1)
        runCurrent()
        assertEquals("beta", state.result.query)
        assertEquals(2, state.result.matches.size)
        assertFalse(state.result.searching)
        state.update(doc, emptyList(), "alpha", true)
        assertTrue(state.result.searching)
        assertTrue(state.result.highlights.blocks.isEmpty())
        state.update(doc, emptyList(), " ", true)
        assertFalse(state.result.searching)
        assertTrue(state.result.matches.isEmpty())
    }

    @Test fun documentChangeAndCloseDiscardPendingWork() = runTest {
        val state = ReaderSearchState(this, StandardTestDispatcher(testScheduler))
        state.update(document("alpha"), emptyList(), "alpha", true)
        state.update(document("beta beta"), emptyList(), "beta", true)
        advanceTimeBy(180)
        runCurrent()
        assertEquals(2, state.result.matches.size)
        state.update(document("alpha"), emptyList(), "alpha", true)
        state.close()
        advanceTimeBy(180)
        runCurrent()
        assertEquals("", state.result.query)
        assertTrue(state.result.matches.isEmpty())
    }

    @Test fun checksCancellationInsideSingleBlockOccurrenceLoop() {
        var checks = 0
        try {
            DocumentSearcher.search(document("a".repeat(10000)), "a", emptyList()) {
                if (++checks == 5) throw CancellationException()
            }
            fail("scan did not cooperate with cancellation")
        } catch (_: CancellationException) {
            assertEquals(5, checks)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSearchInFlightTest {
    @Test fun runningScansAreCancelledOnQueryDocumentAndClose() = runTest {
        val started = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val state = ReaderSearchState(this, StandardTestDispatcher(testScheduler)) { document, _, query ->
            started += "${document.title}:$query"
            try { kotlinx.coroutines.awaitCancellation() }
            finally { cancelled += "${document.title}:$query" }
        }
        fun doc(title: String) = Document(title, listOf(Paragraph(InlineText.plain("alpha"))))
        state.update(doc("one"), emptyList(), "a", true)
        advanceTimeBy(180); runCurrent()
        state.update(doc("one"), emptyList(), "b", true); runCurrent()
        assertEquals(listOf("one:a"), cancelled)
        assertTrue(state.result.matches.isEmpty())
        advanceTimeBy(180); runCurrent()
        state.update(doc("two"), emptyList(), "b", true); runCurrent()
        assertEquals(listOf("one:a", "one:b"), cancelled)
        advanceTimeBy(180); runCurrent()
        state.close(); runCurrent()
        assertEquals(listOf("one:a", "one:b", "two:b"), cancelled)
        assertEquals("", state.result.query)
        assertTrue(state.result.matches.isEmpty())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSearchPublicationTest {
    @Test fun cancelledScanCannotPublishAfterLatestResult() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val state = ReaderSearchState(this, StandardTestDispatcher(testScheduler)) { document, toc, query ->
            if (query == "old") {
                started.complete(Unit)
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { gate.await() }
            }
            val matches = DocumentSearcher.search(document, query, toc)
            ReaderSearchData(matches, ReaderSearchHighlights(), matches.map { it.blockIndex }.toSet())
        }
        val doc = Document("test", listOf(Paragraph(InlineText.plain("old new"))))
        state.update(doc, emptyList(), "old", true)
        advanceTimeBy(180); runCurrent()
        assertTrue(started.isCompleted)
        state.update(doc, emptyList(), "new", true)
        advanceTimeBy(180); runCurrent()
        assertEquals("new", state.result.query)
        assertEquals(1, state.result.matches.size)
        gate.complete(Unit); runCurrent()
        assertEquals("new", state.result.query)
        assertEquals(1, state.result.matches.size)
    }

    @Test fun lateScanCannotPublishAfterDocumentChangeOrClose() = runTest {
        for (action in listOf("document", "close")) {
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val started = kotlinx.coroutines.CompletableDeferred<Unit>()
            val state = ReaderSearchState(this, StandardTestDispatcher(testScheduler)) { document, toc, query ->
                if (document.title == "old") {
                    started.complete(Unit)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { gate.await() }
                }
                val matches = DocumentSearcher.search(document, query, toc)
                ReaderSearchData(matches, ReaderSearchHighlights(), matches.map { it.blockIndex }.toSet())
            }
            state.update(Document("old", listOf(Paragraph(InlineText.plain("alpha")))), emptyList(), "alpha", true)
            advanceTimeBy(180); runCurrent()
            assertTrue(started.isCompleted)
            if (action == "document") {
                state.update(Document("new", listOf(Paragraph(InlineText.plain("alpha alpha")))), emptyList(), "alpha", true)
                advanceTimeBy(180); runCurrent()
                assertEquals(2, state.result.matches.size)
            } else {
                state.close()
            }
            gate.complete(Unit); runCurrent()
            assertEquals(if (action == "document") 2 else 0, state.result.matches.size)
            assertEquals(if (action == "document") "alpha" else "", state.result.query)
        }
    }

    @Test fun highlightRangesCoverRenderedMathCodeAndTableCells() {
        val mathText = InlineText("\\alpha", listOf(app.verso.core.model.Span(0, 6, app.verso.core.model.SpanKind.Math)))
        val doc = Document("test", listOf(
            Paragraph(mathText),
            app.verso.core.model.CodeBlock(null, "α + α"),
            app.verso.core.model.TableBlock(emptyList(), listOf(listOf(InlineText.plain("α cell"))))
        ))
        val result = searchHighlights(doc, "α") { }
        assertEquals(listOf(0..0), result.blocks[0])
        assertEquals(listOf(0..0, 4..4), result.blocks[1])
        assertEquals(listOf(0..0), result.cells[2]?.get(0)?.get(0))
    }

    @Test fun highlightRangesComeFromDisplayTextAndLargeBlocks() {
        val doc = Document("test", listOf(Paragraph(InlineText.plain("z".repeat(100000) + "needle"))))
        val result = searchHighlights(doc, "needle") { }
        assertEquals(listOf(100000..100005), result.blocks[0])
    }
}
