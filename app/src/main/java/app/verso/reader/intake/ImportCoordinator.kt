package app.verso.reader.intake

import app.verso.core.importer.*
import app.verso.core.model.*
import app.verso.reader.data.db.BookEntity
import app.verso.reader.data.repository.BookRepository
import kotlinx.coroutines.*

data class ImportedDocument(val book: BookEntity, val document: Document)
sealed interface ImportState {
    data object Idle : ImportState
    data class Running(val name: String?, val phase: String) : ImportState
    data class Complete(val result: ImportedDocument) : ImportState
    data class Failed(val message: String) : ImportState
}

/** [scope] owns UI state (Main in the app); acquisition and processing run on [work]. */
class ImportCoordinator(
    private val scope: CoroutineScope,
    private val work: CoroutineDispatcher,
    private val process: suspend (DocumentSource, (String) -> Unit) -> ImportedDocument,
    private val onState: (ImportState) -> Unit,
    private val maxSourceBytes: Long = DocumentImporter.MAX_SOURCE_BYTES
) {
    private var generation = 0L
    private var job: Job? = null

    fun start(name: String?, acquire: suspend () -> DocumentSource) {
        scope.launch {
            job?.cancel()
            val request = ++generation
            onState(ImportState.Running(name, "Reading source"))
            job = scope.launch {
                var finished = false
                try {
                    val result = withContext(work) {
                        val source = acquire()
                        ensureActive()
                        if (source.bytes.size > maxSourceBytes) throw ImportError.TooLarge()
                        process(source) { phase ->
                            scope.launch {
                                if (request == generation && !finished) onState(ImportState.Running(source.name ?: name, phase))
                            }
                        }
                    }
                    if (request == generation) onState(ImportState.Complete(result))
                } catch (e: CancellationException) { throw e }
                catch (e: ImportError) { if (request == generation) onState(ImportState.Failed(e.userMessage)) }
                catch (e: Exception) {
                    if (request == generation) onState(ImportState.Failed("This document could not be imported. Existing books have been retained."))
                } finally { finished = true }
            }
        }
    }

    fun cancel() {
        scope.launch { ++generation; job?.cancel(); job = null; onState(ImportState.Idle) }
    }

    companion object {
        fun create(scope: CoroutineScope, importer: Importer, repository: BookRepository,
                   work: CoroutineDispatcher = Dispatchers.IO, onState: (ImportState) -> Unit): ImportCoordinator =
            ImportCoordinator(scope, work, { source, phase ->
                repository.initialize()
                val id = BookRepository.idFor(source.bytes)
                val existing = repository.read(id)
                val book = if (existing != null) {
                    if (repository.original(id) == null) {
                        phase("Attaching original source")
                        val old = repository.getBook(id)!!
                        val format = SourceFormat.entries.firstOrNull { it.name == old.format } ?: FormatDetector.detect(source)
                        repository.importDocument(source, existing, format)
                    } else { repository.touch(id); repository.getBook(id)!! }
                } else {
                    phase("Extracting document")
                    val result = importer.import(source)
                    currentCoroutineContext().ensureActive()
                    phase("Saving document")
                    repository.importDocument(source, result.document, result.format)
                }
                val document = existing ?: repository.read(id) ?: error("Committed payload missing")
                ImportedDocument(book, document)
            }, onState)
    }
}
