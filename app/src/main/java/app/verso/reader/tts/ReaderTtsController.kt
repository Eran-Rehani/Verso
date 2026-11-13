package app.verso.reader.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.verso.core.model.Document
import app.verso.core.tts.TtsTextFormatter
import app.verso.core.tts.TtsUtteranceItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

@Immutable
data class TtsState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val isEngineReady: Boolean = false,
    val queue: List<TtsUtteranceItem> = emptyList(),
    val currentQueueIndex: Int = 0,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val autoScroll: Boolean = true,
    val statusMessage: String? = null
) {
    val currentItem: TtsUtteranceItem?
        get() = queue.getOrNull(currentQueueIndex)

    val activeBlockIndex: Int?
        get() = if (isActive) currentItem?.blockIndex else null

    val hasPrevious: Boolean
        get() = currentQueueIndex > 0

    val hasNext: Boolean
        get() = currentQueueIndex < queue.lastIndex
}

class ReaderTtsController(
    context: Context,
    private val scope: CoroutineScope,
    initialDocument: Document
) {
    companion object {
        val AVAILABLE_SPEEDS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    }

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var engineAvailable = false
    private var fallbackJob: Job? = null
    private val utteranceCounter = AtomicLong(0L)
    @Volatile
    private var activeUtteranceId: String = ""

    private val _state = MutableStateFlow(
        TtsState(
            queue = TtsTextFormatter.buildQueue(initialDocument)
        )
    )
    val state: StateFlow<TtsState> = _state.asStateFlow()

    init {
        initEngine()
    }

    private fun initEngine() {
        try {
            tts = TextToSpeech(appContext) { status ->
                scope.launch(Dispatchers.Main.immediate) {
                    if (status == TextToSpeech.SUCCESS) {
                        val engine = tts
                        if (engine != null) {
                            val langResult = engine.setLanguage(Locale.getDefault())
                            if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                                langResult == TextToSpeech.LANG_NOT_SUPPORTED
                            ) {
                                engine.setLanguage(Locale.US)
                            }
                            engine.setSpeechRate(_state.value.speechRate)
                            engine.setPitch(_state.value.pitch)
                            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) {}

                                override fun onDone(utteranceId: String?) {
                                    if (utteranceId != null && utteranceId == activeUtteranceId) {
                                        scope.launch(Dispatchers.Main.immediate) {
                                            onUtteranceCompleted()
                                        }
                                    }
                                }

                                @Deprecated("Deprecated in Java")
                                override fun onError(utteranceId: String?) {
                                    if (utteranceId != null && utteranceId == activeUtteranceId) {
                                        scope.launch(Dispatchers.Main.immediate) {
                                            startFallbackCadenceForCurrent()
                                        }
                                    }
                                }

                                override fun onError(utteranceId: String?, errorCode: Int) {
                                    if (utteranceId != null && utteranceId == activeUtteranceId) {
                                        scope.launch(Dispatchers.Main.immediate) {
                                            startFallbackFallbackIfNeeded()
                                        }
                                    }
                                }
                            })
                            engineAvailable = true
                            _state.update { it.copy(isEngineReady = true, statusMessage = null) }
                            // If user already tapped play before init finished, start speaking now
                            if (_state.value.isActive && _state.value.isPlaying) {
                                speakCurrentInternal()
                            }
                        }
                    } else {
                        engineAvailable = false
                        _state.update {
                            it.copy(
                                isEngineReady = false,
                                statusMessage = "System voice unavailable · Visual pacing active"
                            )
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            engineAvailable = false
            _state.update {
                it.copy(
                    isEngineReady = false,
                    statusMessage = "System voice unavailable · Visual pacing active"
                )
            }
        }
    }

    fun updateDocument(document: Document) {
        stop()
        val newQueue = TtsTextFormatter.buildQueue(document)
        _state.update {
            it.copy(
                queue = newQueue,
                currentQueueIndex = 0,
                isActive = false,
                isPlaying = false
            )
        }
    }

    fun startFromBlock(blockIndex: Int) {
        val currentQueue = _state.value.queue
        if (currentQueue.isEmpty()) return
        val targetQueueIdx = TtsTextFormatter.findQueueIndexForBlock(currentQueue, blockIndex)
            .coerceIn(0, currentQueue.lastIndex)

        _state.update {
            it.copy(
                isActive = true,
                isPlaying = true,
                currentQueueIndex = targetQueueIdx
            )
        }
        speakCurrentInternal()
    }

    fun toggleBar(startBlockIndex: Int) {
        if (_state.value.isActive) {
            stop()
        } else {
            startFromBlock(startBlockIndex)
        }
    }

    fun togglePlayPause(fallbackStartBlock: Int = 0) {
        val cur = _state.value
        if (cur.queue.isEmpty()) return
        if (!cur.isActive) {
            startFromBlock(fallbackStartBlock)
            return
        }
        if (cur.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun pause() {
        fallbackJob?.cancel()
        fallbackJob = null
        activeUtteranceId = ""
        try {
            tts?.stop()
        } catch (_: Throwable) {}
        _state.update { it.copy(isPlaying = false) }
    }

    fun resume() {
        val cur = _state.value
        if (cur.queue.isEmpty()) return
        _state.update { it.copy(isActive = true, isPlaying = true) }
        speakCurrentInternal()
    }

    fun nextBlock() {
        val cur = _state.value
        if (cur.queue.isEmpty()) return
        val nextIdx = (cur.currentQueueIndex + 1).coerceAtMost(cur.queue.lastIndex)
        _state.update { it.copy(currentQueueIndex = nextIdx, isActive = true) }
        if (cur.isPlaying) {
            speakCurrentInternal()
        }
    }

    fun previousBlock() {
        val cur = _state.value
        if (cur.queue.isEmpty()) return
        val prevIdx = (cur.currentQueueIndex - 1).coerceAtLeast(0)
        _state.update { it.copy(currentQueueIndex = prevIdx, isActive = true) }
        if (cur.isPlaying) {
            speakCurrentInternal()
        }
    }

    fun jumpToBlock(blockIndex: Int, autoPlay: Boolean = true) {
        val cur = _state.value
        if (cur.queue.isEmpty()) return
        val idx = TtsTextFormatter.findQueueIndexForBlock(cur.queue, blockIndex)
            .coerceIn(0, cur.queue.lastIndex)
        _state.update {
            it.copy(
                isActive = true,
                currentQueueIndex = idx,
                isPlaying = if (autoPlay) true else it.isPlaying
            )
        }
        if (_state.value.isPlaying) {
            speakCurrentInternal()
        }
    }

    fun cycleSpeed() {
        val curRate = _state.value.speechRate
        val currentIdx = AVAILABLE_SPEEDS.indexOfFirst { kotlin.math.abs(it - curRate) < 0.05f }
        val nextRate = if (currentIdx == -1 || currentIdx == AVAILABLE_SPEEDS.lastIndex) {
            AVAILABLE_SPEEDS.first()
        } else {
            AVAILABLE_SPEEDS[currentIdx + 1]
        }
        setSpeechRate(nextRate)
    }

    fun setSpeechRate(rate: Float) {
        val coerced = rate.coerceIn(0.5f, 2.5f)
        try {
            tts?.setSpeechRate(coerced)
        } catch (_: Throwable) {}
        _state.update { it.copy(speechRate = coerced) }
        if (_state.value.isActive && _state.value.isPlaying) {
            speakCurrentInternal()
        }
    }

    fun toggleAutoScroll() {
        _state.update { it.copy(autoScroll = !it.autoScroll) }
    }

    fun stop() {
        fallbackJob?.cancel()
        fallbackJob = null
        activeUtteranceId = ""
        try {
            tts?.stop()
        } catch (_: Throwable) {}
        _state.update { it.copy(isActive = false, isPlaying = false) }
    }

    fun shutdown() {
        fallbackJob?.cancel()
        fallbackJob = null
        activeUtteranceId = ""
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Throwable) {}
        tts = null
    }

    private fun speakCurrentInternal() {
        fallbackJob?.cancel()
        fallbackJob = null

        val cur = _state.value
        val item = cur.currentItem ?: run {
            _state.update { it.copy(isPlaying = false) }
            return
        }

        val utteranceId = "verso_tts_${utteranceCounter.incrementAndGet()}"
        activeUtteranceId = utteranceId

        val engine = tts
        if (engine != null && engineAvailable) {
            try {
                engine.setSpeechRate(cur.speechRate)
                engine.setPitch(cur.pitch)
                val params = Bundle()
                val res = engine.speak(
                    item.spokenText,
                    TextToSpeech.QUEUE_FLUSH,
                    params,
                    utteranceId
                )
                if (res == TextToSpeech.ERROR) {
                    startFallbackCadenceForCurrent()
                }
            } catch (_: Throwable) {
                startFallbackCadenceForCurrent()
            }
        } else {
            startFallbackCadenceForCurrent()
        }
    }

    private fun startFallbackFallbackIfNeeded() {
        startFallbackCadenceForCurrent()
    }

    private fun startFallbackCadenceForCurrent() {
        fallbackJob?.cancel()
        val cur = _state.value
        val item = cur.currentItem ?: return
        val wordCount = item.spokenText.split(Regex("\\s+")).size.coerceAtLeast(1)
        val baseMs = (wordCount * 260L).coerceIn(1200L, 9000L)
        val scaledMs = (baseMs / cur.speechRate.coerceAtLeast(0.5f)).toLong().coerceAtLeast(600L)
        val expectedUtterance = activeUtteranceId

        fallbackJob = scope.launch(Dispatchers.Main.immediate) {
            delay(scaledMs)
            if (activeUtteranceId == expectedUtterance && _state.value.isPlaying) {
                onUtteranceCompleted()
            }
        }
    }

    private fun onUtteranceCompleted() {
        val cur = _state.value
        if (!cur.isActive || !cur.isPlaying) return
        if (cur.currentQueueIndex < cur.queue.lastIndex) {
            _state.update { it.copy(currentQueueIndex = cur.currentQueueIndex + 1) }
            speakCurrentInternal()
        } else {
            // Reached end of document
            _state.update { it.copy(isPlaying = false) }
        }
    }
}

@Composable
fun rememberReaderTtsController(document: Document): ReaderTtsController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(context) {
        ReaderTtsController(context, scope, document)
    }
    DisposableEffect(document) {
        controller.updateDocument(document)
        onDispose {}
    }
    DisposableEffect(controller) {
        onDispose {
            controller.shutdown()
        }
    }
    return controller
}
