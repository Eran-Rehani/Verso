package app.verso.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.verso.core.importer.DocumentSource
import app.verso.core.importer.Importer
import app.verso.core.model.Document
import app.verso.core.model.ImportError
import app.verso.core.store.LibraryStore
import app.verso.core.store.RecentEntry
import app.verso.core.store.ReaderSettings
import app.verso.core.store.SettingsStore
import app.verso.core.store.coerced
import app.verso.reader.data.db.BookEntity
import app.verso.reader.data.repository.BookRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppEnv(
    val library: LibraryStore,
    val settings: SettingsStore,
    val importer: Importer,
    val bookRepository: BookRepository
)

sealed interface Screen {
    data object Home : Screen
    data class Importing(val name: String?) : Screen
    data class Reading(
        val id: String,
        val title: String,
        val document: Document,
        val anchorBlock: Int,
        val anchorOffsetPx: Int
    ) : Screen
    data class Failed(val message: String) : Screen
}

enum class MainTab {
    Home,
    Library
}

class AppViewModel(
    private val env: AppEnv,
    private val work: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _settings = MutableStateFlow(env.settings.load())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    private val _recents = MutableStateFlow(env.library.list())
    val recents: StateFlow<List<RecentEntry>> = _recents.asStateFlow()

    private val _currentTab = MutableStateFlow(MainTab.Home)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    val books: StateFlow<List<BookEntity>> = env.bookRepository.allBooks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var currentImportJob: Job? = null

    init {
        // Sync any pre-existing recents from disk to Room on launch
        viewModelScope.launch(work) {
            val recentsList = env.library.list()
            for (item in recentsList) {
                env.bookRepository.saveOrUpdate(
                    id = item.id,
                    title = item.title,
                    format = item.format,
                    blockCount = item.blockCount,
                    anchorBlock = item.anchorBlock,
                    anchorOffsetPx = item.anchorOffsetPx
                )
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _currentTab.value = tab
    }

    fun import(source: DocumentSource) {
        currentImportJob?.cancel()
        _screen.value = Screen.Importing(source.name)

        currentImportJob = viewModelScope.launch(work) {
            try {
                val id = env.library.idFor(source.bytes)
                if (env.library.contains(id)) {
                    val doc = env.library.load(id)
                    if (doc != null) {
                        env.library.touch(id)
                        env.bookRepository.touch(id)
                        val entry = env.library.entry(id)
                        val updatedRecents = env.library.list()
                        withContext(Dispatchers.Main) {
                            _recents.value = updatedRecents
                            _screen.value = Screen.Reading(
                                id = id,
                                title = doc.title,
                                document = doc,
                                anchorBlock = entry?.anchorBlock ?: 0,
                                anchorOffsetPx = entry?.anchorOffsetPx ?: 0
                            )
                        }
                        return@launch
                    }
                }

                val result = env.importer.import(source)
                env.library.save(id, result.document, result.format)
                env.bookRepository.saveOrUpdate(
                    id = id,
                    title = result.document.title,
                    format = result.format.name,
                    blockCount = result.document.blocks.size,
                    anchorBlock = 0,
                    anchorOffsetPx = 0
                )
                val updatedRecents = env.library.list()

                withContext(Dispatchers.Main) {
                    _recents.value = updatedRecents
                    _screen.value = Screen.Reading(
                        id = id,
                        title = result.document.title,
                        document = result.document,
                        anchorBlock = 0,
                        anchorOffsetPx = 0
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportError) {
                withContext(Dispatchers.Main) {
                    _screen.value = Screen.Failed(e.userMessage)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    _screen.value = Screen.Failed(ImportError.Corrupt("unexpected", t).userMessage)
                }
            }
        }
    }

    fun pasteText(text: String) {
        if (text.isBlank()) {
            _screen.value = Screen.Failed("Clipboard is empty.")
            return
        }
        val bytes = text.toByteArray(Charsets.UTF_8)
        import(DocumentSource(name = null, mimeType = "text/plain", bytes = bytes))
    }

    fun open(id: String) {
        val doc = env.library.load(id)
        if (doc == null) {
            _recents.value = env.library.list()
            _screen.value = Screen.Failed("That document is no longer available.")
            return
        }

        env.library.touch(id)
        viewModelScope.launch(work) {
            env.bookRepository.touch(id)
        }
        val entry = env.library.entry(id)
        _recents.value = env.library.list()
        _screen.value = Screen.Reading(
            id = id,
            title = doc.title,
            document = doc,
            anchorBlock = entry?.anchorBlock ?: 0,
            anchorOffsetPx = entry?.anchorOffsetPx ?: 0
        )
    }

    fun closeReader() {
        _screen.value = Screen.Home
    }

    fun dismissError() {
        _screen.value = Screen.Home
    }

    fun cancelImport() {
        currentImportJob?.cancel()
        currentImportJob = null
        _screen.value = Screen.Home
    }

    fun showFailure(message: String) {
        _screen.value = Screen.Failed(message)
    }

    fun updateSettings(newSettings: ReaderSettings) {
        val coerced = newSettings.coerced()
        env.settings.save(coerced)
        _settings.value = coerced
    }

    fun onAnchor(block: Int, offsetPx: Int) {
        val current = _screen.value
        if (current is Screen.Reading) {
            env.library.updateAnchor(current.id, block, offsetPx)
            viewModelScope.launch(work) {
                env.bookRepository.updateAnchor(current.id, block, offsetPx)
            }
            _recents.value = env.library.list()
        }
    }

    fun delete(id: String) {
        env.library.delete(id)
        viewModelScope.launch(work) {
            env.bookRepository.delete(id)
        }
        _recents.value = env.library.list()
    }

    companion object {
        fun factory(env: AppEnv): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AppViewModel(env) as T
            }
        }
    }
}
