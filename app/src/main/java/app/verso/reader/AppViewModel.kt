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

class AppEnv(
    val library: LibraryStore,
    val settings: SettingsStore,
    val importer: Importer,
    val bookRepository: BookRepository
)

sealed interface Screen {
    data object Home : Screen
    data class Importing(val name: String?, val phase: String = "Reading source") : Screen
    data class Reading(
        val id: String,
        val title: String,
        val document: Document,
        val anchorBlock: Int,
        val anchorOffsetPx: Int
    ) : Screen
    data class Failed(val message: String) : Screen
}

enum class MainTab { Home, Library }

class AppViewModel(
    private val env: AppEnv,
    private val work: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen = _screen.asStateFlow()
    private val _settings = MutableStateFlow(ReaderSettings())
    val settings = _settings.asStateFlow()
    private val _settingsSaveError = MutableStateFlow(false)
    val settingsSaveError = _settingsSaveError.asStateFlow()
    private val _currentTab = MutableStateFlow(MainTab.Home)
    val currentTab = _currentTab.asStateFlow()
    private val _books = MutableStateFlow<List<BookEntity>>(emptyList())
    val books = _books.asStateFlow()
    val recents: StateFlow<List<RecentEntry>> = books.map { list ->
        list.take(20).map {
            RecentEntry(it.id, it.title, it.format, it.addedAt, it.lastOpenedAt, it.blockCount, it.anchorBlock, it.anchorOffsetPx)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val bookmarks: StateFlow<List<app.verso.reader.data.db.BookmarkEntity>> = screen
        .map { (it as? Screen.Reading)?.id }
        .distinctUntilChanged()
        .flatMapLatest { bookId ->
            if (bookId == null) flowOf(emptyList())
            else env.bookRepository.bookmarksFor(bookId)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var generation = 0L
    private val ready = viewModelScope.async(work) { env.bookRepository.initialize() }

    private data class Position(
        val id: String?,
        val block: Int = 0,
        val offset: Int = 0,
        val barrier: CompletableDeferred<Unit>? = null
    )

    private val positions = Channel<Position>(Channel.UNLIMITED)
    private val settingsWrites = Channel<ReaderSettings>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            try {
                ready.await()
                _settings.value = withContext(work) { env.settings.load() }
                env.bookRepository.allBooks.collect { _books.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _screen.value = Screen.Failed("Library recovery failed. Your source files have been retained.")
            }
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    writeError = e
                    event.barrier?.completeExceptionally(e)
                }
            }
        }
        viewModelScope.launch(work) {
            for (value in settingsWrites) {
                try {
                    env.settings.save(value)
                    _settingsSaveError.value = false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _settingsSaveError.value = true
                }
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _currentTab.value = tab
    }

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

    fun loadSample(spec: app.verso.reader.sample.SampleDocumentSpec) {
        import(spec.toDocumentSource())
    }

    fun loadSampleDocument() {
        loadSample(app.verso.reader.sample.SampleDocuments.MATH_PHYSICS_MARKDOWN)
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request == generation) _screen.value = Screen.Failed("This document could not be opened. Its files have been retained.")
            }
        }
    }

    suspend fun flushPositions() {
        val barrier = CompletableDeferred<Unit>()
        positions.send(Position(null, barrier = barrier))
        barrier.await()
    }

    fun onAnchor(id: String, block: Int, offsetPx: Int) {
        positions.trySend(Position(id, block, offsetPx))
    }

    fun onAnchor(block: Int, offsetPx: Int) {
        (screen.value as? Screen.Reading)?.let { onAnchor(it.id, block, offsetPx) }
    }

    fun closeReader() {
        val request = ++generation
        viewModelScope.launch {
            try {
                flushPositions()
                if (request == generation) _screen.value = Screen.Home
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request == generation) _screen.value = Screen.Failed("Your reading position could not be saved.")
            }
        }
    }

    fun dismissError() {
        _screen.value = Screen.Home
    }

    fun cancelImport() {
        ++generation
        coordinator.cancel()
    }

    fun showFailure(message: String) {
        viewModelScope.launch(Dispatchers.Main.immediate) { _screen.value = Screen.Failed(message) }
    }

    fun updateSettings(value: ReaderSettings) {
        val coerced = value.coerced()
        _settings.value = coerced
        settingsWrites.trySend(coerced)
    }

    fun retrySettingsSave() {
        settingsWrites.trySend(_settings.value)
    }

    fun dismissSettingsSaveError() {
        _settingsSaveError.value = false
    }

    fun delete(id: String) {
        viewModelScope.launch { ready.await(); env.bookRepository.delete(id) }
    }

    fun toggleBookmark(blockIndex: Int, offsetPx: Int = 0, label: String = "") {
        val current = _screen.value as? Screen.Reading ?: return
        viewModelScope.launch(work) {
            ready.await()
            env.bookRepository.toggleBookmark(
                bookId = current.id,
                document = current.document,
                blockIndex = blockIndex,
                offsetPx = offsetPx,
                customLabel = label
            )
        }
    }

    fun removeBookmark(bookmarkId: String) {
        viewModelScope.launch(work) {
            ready.await()
            env.bookRepository.removeBookmark(bookmarkId)
        }
    }

    fun updateBookmarkLabel(bookmarkId: String, label: String) {
        viewModelScope.launch(work) {
            ready.await()
            env.bookRepository.updateBookmarkLabel(bookmarkId, label)
        }
    }

    companion object {
        fun factory(env: AppEnv): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(env) as T
        }

        private val SAMPLE_LATEX_TOC_MARKDOWN = """
# Mathematical Analysis & Physics Primer

Welcome to **Verso**. Use the **Table of Contents** button in the top bar (or the bottom chapter bar) to jump between chapters and subsections, and tap **Search** to find keywords across the document. All inline and display \(\LaTeX\) equations are typeset natively.

## 1. Algebraic Foundations

For any quadratic polynomial \(ax^2 + bx + c = 0\) with \(a \neq 0\) and coefficients in \(\mathbb{R}\), the roots are given by the classical quadratic formula:

\[x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a} \tag{1.1}\]

### 1.1 Binomial Theorem & Series

For any non-negative integer \(n \in \mathbb{N}\), the binomial expansion of \((x + y)^n\) is expressed using the binomial coefficient \(\binom{n}{k}\):

\[(x + y)^n = \sum_{k=0}^{n} \binom{n}{k} x^{n-k} y^k = \sum_{k=0}^{n} \frac{n!}{k!(n-k)!} x^{n-k} y^k \tag{1.2}\]

## 2. Calculus & Gaussian Integrals

One of the most celebrated identities in analysis is the Euler–Poisson Gaussian integral over the real line \(\mathbb{R}\):

\[\int_{-\infty}^{\infty} e^{-x^2} \, dx = \sqrt{\pi} \tag{2.1}\]

### 2.1 Fundamental Limits & Derivatives

The derivative of a smooth function \(f(x)\) at a point \(x_0\) is defined by the limit:

\[f'(x_0) = \lim_{h \to 0} \frac{f(x_0 + h) - f(x_0)}{h}\]

And the Riemann zeta function \(\zeta(s)\) for \(\Re(s) > 1\) is given by both an infinite series and an Euler product over primes \(p\):

\[\zeta(s) = \sum_{n=1}^{\infty} \frac{1}{n^s} = \prod_{p} \left( \frac{1}{1 - p^{-s}} \right) \tag{2.2}\]

## 3. Linear Algebra & Matrices

Consider a rotation matrix \(R(\theta) \in \mathbb{R}^{2 \times 2}\) and the identity matrix \(I_2\):

\[R(\theta) = \begin{pmatrix} \cos\theta & -\sin\theta \\ \sin\theta & \cos\theta \end{pmatrix}, \quad \det R(\theta) = \cos^2\theta + \sin^2\theta = 1 \tag{3.1}\]

### 3.1 Piecewise Functions & Cases

The absolute value \(|x|\) and the ReLU activation function \(\sigma(x)\) can be written using piecewise cases:

\[|x| = \begin{cases} x & \text{if } x \ge 0 \\ -x & \text{if } x < 0 \end{cases}\]

## 4. Quantum Mechanics & Electrodynamics

The time-dependent Schrödinger wave equation governs the evolution of the state vector \(\psi(\vec{r}, t)\) under the Hamiltonian operator \(\hat{H}\):

\[i\hbar \frac{\partial \psi}{\partial t} = \left( -\frac{\hbar^2}{2m} \nabla^2 + V(\vec{r}, t) \right) \psi(\vec{r}, t) \tag{4.1}\]

### 4.1 Maxwell's Equations in Differential Form

In vacuum, the electric field \(\vec{E}\) and magnetic field \(\vec{B}\) satisfy the coupled system:

\[\begin{aligned} \nabla \cdot \vec{E} &= \frac{\rho}{\varepsilon_0} \\ \nabla \cdot \vec{B} &= 0 \\ \nabla \times \vec{E} &= -\frac{\partial \vec{B}}{\partial t} \\ \nabla \times \vec{B} &= \mu_0 \vec{J} + \mu_0 \varepsilon_0 \frac{\partial \vec{E}}{\partial t} \end{aligned} \tag{4.2}\]
        """.trimIndent()
    }
}
