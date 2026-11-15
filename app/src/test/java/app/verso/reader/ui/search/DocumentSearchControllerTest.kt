package app.verso.reader.ui.search

import app.verso.core.model.Document
import app.verso.core.model.InlineText
import app.verso.core.model.MathBlock
import app.verso.core.model.Paragraph
import app.verso.core.search.DocumentSearcher
import app.verso.reader.ui.reader.ReaderSearchState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentSearchControllerTest {
    @Test fun `asynchronous search preserves math and text matches in document order`() = runTest {
        val state = ReaderSearchState(this, StandardTestDispatcher(testScheduler))
        val document = Document("test", listOf(
            MathBlock("\\alpha + x"),
            Paragraph(InlineText.plain("alpha in text"))
        ))
        state.update(document, emptyList(), "alpha", true)
        advanceTimeBy(180)
        runCurrent()
        val matches = state.result.matches
        assertEquals(listOf(0, 1), matches.map { it.blockIndex })
        assertTrue(matches[0].snippet.contains("\\alpha"))
        assertTrue(matches[1].snippet.contains("alpha in text"))
        assertEquals(0, DocumentSearcher.nextMatchIndex(1, matches.size))
        assertEquals(1, DocumentSearcher.prevMatchIndex(0, matches.size))
    }
}
