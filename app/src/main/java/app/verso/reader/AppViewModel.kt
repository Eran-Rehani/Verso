package app.verso.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.verso.core.importer.*
import app.verso.reader.intake.ImportCoordinator
import app.verso.reader.intake.ImportState
import app.verso.core.model.*
import app.verso.core.store.*
import app.verso.reader.data.db.BookEntity
import app.verso.reader.data.repository.BookRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

class AppEnv(val library: LibraryStore, val settings: SettingsStore, val importer: Importer, val bookRepository: BookRepository)
sealed interface Screen {
    data object Home : Screen
    data class Importing(val name: String?, val phase: String = "Reading source") : Screen
    data class Reading(val id: String, val title: String, val document: Document, val anchorBlock: Int, val anchorOffsetPx: Int) : Screen
    data class Failed(val message: String) : Screen
}
enum class MainTab { Home, Library }

class AppViewModel(private val env: AppEnv, private val work: CoroutineDispatcher = Dispatchers.IO) : ViewModel() {
    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen = _screen.asStateFlow()
    private val _settings = MutableStateFlow(ReaderSettings())
    val settings = _settings.asStateFlow()
    private val _currentTab = MutableStateFlow(MainTab.Home)
    val currentTab = _currentTab.asStateFlow()
    private val _books = MutableStateFlow<List<BookEntity>>(emptyList())
    val books = _books.asStateFlow()
    val recents: StateFlow<List<RecentEntry>> = books.map { list -> list.take(20).map {
        RecentEntry(it.id, it.title, it.format, it.addedAt, it.lastOpenedAt, it.blockCount, it.anchorBlock, it.anchorOffsetPx)
    } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private var generation = 0L
    private val ready = viewModelScope.async(work) { env.bookRepository.initialize() }
    private data class Position(val id: String?, val block: Int = 0, val offset: Int = 0, val barrier: CompletableDeferred<Unit>? = null)
    private val positions = Channel<Position>(Channel.UNLIMITED)
    private val settingsWrites = Channel<ReaderSettings>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            try {
                ready.await()
                _settings.value = withContext(work) { env.settings.load() }
                env.bookRepository.allBooks.collect { _books.value = it }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _screen.value = Screen.Failed("Library recovery failed. Your source files have been retained.") }
        }
        viewModelScope.launch(work) {
            var writeError: Exception? = null
            for (event in positions) {
                try {
                    ready.await()
                    if (event.id != null) env.bookRepository.updateAnchor(event.id, event.block, event.offset)
                    if (event.barrier != null) {
                        val error = writeError
                        if (error == null) event.barrier.complete(Unit) else event.barrier.completeExceptionally(error)
                        writeError = null
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { writeError = e; event.barrier?.completeExceptionally(e) }
            }
        }
        viewModelScope.launch(work) { for (value in settingsWrites) env.settings.save(value) }
    }

    fun selectTab(tab: MainTab) { _currentTab.value = tab }

    private val coordinator = ImportCoordinator.create(viewModelScope, env.importer, env.bookRepository, work) { state ->
        when (state) {
            ImportState.Idle -> _screen.value = Screen.Home
            is ImportState.Running -> _screen.value = Screen.Importing(state.name, state.phase)
            is ImportState.Complete -> state.result.let {
                _screen.value = Screen.Reading(it.book.id, it.book.title, it.document, it.book.anchorBlock, it.book.anchorOffsetPx)
            }
            is ImportState.Failed -> _screen.value = Screen.Failed(state.message)
        }
    }

    fun acquire(name: String?, loader: suspend () -> DocumentSource) {
        ++generation
        coordinator.start(name) { ready.await(); flushPositions(); loader() }
    }
    fun import(source: DocumentSource) = acquire(source.name) { source }

    fun pasteText(text: String) {
        acquire(null) {
            if (text.isBlank()) throw ImportError.Intake("Clipboard is empty.")
            val context = currentCoroutineContext()
            DocumentSource.fromText(text) { context.ensureActive() }
        }
    }

    fun open(id: String) {
        coordinator.cancel()
        val request = ++generation
        viewModelScope.launch {
            try {
                ready.await(); flushPositions()
                val doc = withContext(work) { env.bookRepository.read(id) }
                val book = env.bookRepository.getBook(id)
                if (request != generation) return@launch
                if (doc == null || book == null) {
                    _screen.value = Screen.Failed("This document is missing or damaged. Re-import the same original file to repair it and keep its reading position.")
                } else {
                    env.bookRepository.touch(id)
                    if (request != generation) return@launch
                    _screen.value = Screen.Reading(id, book.title, doc, book.anchorBlock, book.anchorOffsetPx)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (request == generation) _screen.value = Screen.Failed("This document could not be opened. Its files have been retained.") }
        }
    }

    suspend fun flushPositions() {
        val barrier = CompletableDeferred<Unit>()
        positions.send(Position(null, barrier = barrier))
        barrier.await()
    }
    fun onAnchor(id: String, block: Int, offsetPx: Int) { positions.trySend(Position(id, block, offsetPx)) }
    fun onAnchor(block: Int, offsetPx: Int) { (screen.value as? Screen.Reading)?.let { onAnchor(it.id, block, offsetPx) } }
    fun closeReader() {
        val request = ++generation
        viewModelScope.launch {
            try { flushPositions(); if (request == generation) _screen.value = Screen.Home }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (request == generation) _screen.value = Screen.Failed("Your reading position could not be saved.") }
        }
    }
    fun dismissError() { _screen.value = Screen.Home }
    fun cancelImport() { ++generation; coordinator.cancel() }
    fun showFailure(message: String) { viewModelScope.launch(Dispatchers.Main.immediate) { _screen.value = Screen.Failed(message) } }
    fun updateSettings(value: ReaderSettings) { val coerced = value.coerced(); _settings.value = coerced; settingsWrites.trySend(coerced) }
    fun delete(id: String) { viewModelScope.launch { ready.await(); env.bookRepository.delete(id) } }
    companion object {
        fun factory(env: AppEnv): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(env) as T
        }
    }
}
