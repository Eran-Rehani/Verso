package app.verso.reader.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.verso.core.model.Document
import app.verso.core.search.DocumentSearchMatch
import app.verso.core.search.DocumentSearcher
import app.verso.core.toc.TocEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ReaderSearchResult(
    val query: String = "",
    val searching: Boolean = false,
    val matches: List<DocumentSearchMatch> = emptyList(),
    val highlights: ReaderSearchHighlights = ReaderSearchHighlights(),
    val matchedBlocks: Set<Int> = emptySet()
)

internal data class ReaderSearchData(val matches: List<DocumentSearchMatch>, val highlights: ReaderSearchHighlights, val matchedBlocks: Set<Int>)

internal class ReaderSearchState(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scan: suspend (Document, List<TocEntry>, String) -> ReaderSearchData = { document, toc, query ->
        val context = currentCoroutineContext()
        val check = { context.ensureActive() }
        val matches = DocumentSearcher.search(document, query, toc, check)
        ReaderSearchData(matches, searchHighlights(document, query.trim(), check), matches.map { it.blockIndex }.toSet())
    }
) {
    var result by mutableStateOf(ReaderSearchResult())
        private set
    private var job: Job? = null

    fun update(document: Document, toc: List<TocEntry>, query: String, open: Boolean) {
        job?.cancel()
        result = ReaderSearchResult(query, open && query.isNotBlank())
        if (!open || query.isBlank()) return
        job = scope.launch {
            delay(180)
            val data = withContext(dispatcher) { scan(document, toc, query) }
            result = ReaderSearchResult(query, matches = data.matches, highlights = data.highlights, matchedBlocks = data.matchedBlocks)
        }
    }

    fun close() {
        job?.cancel()
        result = ReaderSearchResult()
    }
}
