package dev.equerry.app.voice

import dev.equerry.app.data.SpeakTiming
import dev.equerry.app.data.TurnControl
import dev.equerry.app.data.VoiceSettingsStore
import dev.equerry.app.providers.drivers.ChatRole
import dev.equerry.app.ui.chat.ChatViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Coarse phase of one voice turn, surfaced for the session UI. */
enum class VoiceFlowState { Idle, Listening, Transcribing, Sending, Speaking }

/**
 * Drives one spoken question-and-answer turn: listen (STT) → auto-send the transcript to the
 * CHAT provider via [ChatViewModel] → speak the reply (TTS) per the persisted [SpeakTiming].
 *
 * Pure-Kotlin and Android-free so the whole turn is unit-testable against fakes. It reuses
 * [ChatViewModel.send] and its streaming state — it never reinvents the provider round-trip.
 * Listening auto-stops and auto-sends when the STT flow completes (turn_control: no tap-to-stop).
 *
 * This task establishes the single-turn flow; cancellation/continuous re-arm (t-8) and failure
 * routing (t-11) layer onto this controller.
 */
class VoiceFlowController(
    private val chat: ChatViewModel,
    private val stt: SpeechToText,
    private val tts: TextToSpeech,
    private val settings: VoiceSettingsStore,
    private val scope: CoroutineScope,
    private val isMicGranted: () -> Boolean,
    private val isChatConfigured: suspend () -> Boolean = { true },
) {

    private val _state = MutableStateFlow(VoiceFlowState.Idle)
    val state: StateFlow<VoiceFlowState> = _state.asStateFlow()

    /** The current failure guidance, or null when none. The single surface for every failure mode (c-5). */
    private val _guidance = MutableStateFlow<VoiceGuidance?>(null)
    val guidance: StateFlow<VoiceGuidance?> = _guidance.asStateFlow()

    private var job: Job? = null
    private var ttsReady = false

    /**
     * Begin the listen→send→speak loop. No-op while a turn is already running (the active-job guard
     * prevents a double-arm from rapid triggers). In [TurnControl.CONTINUOUS] the loop re-arms for a
     * follow-up after each turn fully settles; in [TurnControl.SINGLE_TURN] it ends after one Q&A.
     */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                // Pre-flight: never arm STT if the mic is denied or no provider is mapped.
                if (!isMicGranted()) {
                    _guidance.value = VoiceGuidanceFactory.micDenied()
                    _state.value = VoiceFlowState.Idle
                    return@launch
                }
                if (!isChatConfigured()) {
                    _guidance.value = VoiceGuidanceFactory.noChatProvider()
                    _state.value = VoiceFlowState.Idle
                    return@launch
                }
                ttsReady = tts.init() == TtsInitResult.Ready
                do {
                    _guidance.value = null
                    _state.value = VoiceFlowState.Listening

                    var finalText: String? = null
                    var sttError: SttError? = null
                    stt.listen().collect { event ->
                        when (event) {
                            is SttEvent.Partial -> _state.value = VoiceFlowState.Transcribing
                            is SttEvent.Final -> {
                                finalText = event.text
                                _state.value = VoiceFlowState.Transcribing
                            }
                            SttEvent.EndOfSpeech -> _state.value = VoiceFlowState.Transcribing
                            is SttEvent.Error -> sttError = event.error
                        }
                    }

                    // The STT flow has completed — speech ended (or errored).
                    sttError?.let { _guidance.value = VoiceGuidanceFactory.sttError(it) }
                    val text = finalText
                    if (text.isNullOrBlank()) break
                    sendAndSpeak(text)
                    // Re-arm for a follow-up only once the prior turn has fully settled (above), so
                    // the mic is never armed twice for one turn.
                    val turn = settings.turnControl().first()
                } while (turn == TurnControl.CONTINUOUS && isActive)
                _state.value = VoiceFlowState.Idle
            } catch (c: CancellationException) {
                throw c // dismissal — let stop()'s cancellation propagate
            } catch (e: Exception) {
                // Never throw to the framework session; surface a generic guidance instead (c-5).
                _guidance.value = VoiceGuidance("Something went wrong with the voice session.")
                _state.value = VoiceFlowState.Idle
            }
        }
    }

    /**
     * Stop the loop, cancel any in-flight listening/streaming/speaking, and silence TTS. Idempotent:
     * cancelling an already-stopped controller is safe. Cancelling the job unwinds the STT flow's
     * awaitClose, which releases the recognizer.
     */
    fun stop() {
        job?.cancel()
        job = null
        tts.stop()
        _state.value = VoiceFlowState.Idle
    }

    private suspend fun sendAndSpeak(text: String) {
        _state.value = VoiceFlowState.Sending
        val timing = settings.speakTiming().first()
        val chunker = SpeakChunker(timing)
        var spokenSoFar = ""
        var sawStreaming = false

        chat.send(text)
        chat.state
            .onEach { ui ->
                if (ui.streaming) sawStreaming = true
                // Feed newly-streamed text to the chunker for sentence-by-sentence speaking (only
                // when TTS is usable — a failed engine must not block the round-trip).
                if (ttsReady && timing == SpeakTiming.SENTENCE_BY_SENTENCE) {
                    val assistant = ui.transcript.lastOrNull { it.role == ChatRole.ASSISTANT }?.content.orEmpty()
                    if (assistant.length > spokenSoFar.length) {
                        val delta = assistant.substring(spokenSoFar.length)
                        spokenSoFar = assistant
                        chunker.feed(delta).forEach { tts.speak(it) }
                    }
                }
            }
            .first { sawStreaming && !it.streaming && (it.lastReply != null || it.error != null || it.unmapped) }

        val finalUi = chat.state.value
        when {
            finalUi.unmapped -> {
                _guidance.value = VoiceGuidanceFactory.noChatProvider()
                _state.value = VoiceFlowState.Idle
                return
            }
            // Stream failed mid-reply: show the key-free error, keep the partial reply, never speak it.
            finalUi.error != null -> {
                _guidance.value = VoiceGuidanceFactory.replyError(finalUi.error)
                _state.value = VoiceFlowState.Idle
                return
            }
        }

        _state.value = VoiceFlowState.Speaking
        if (!ttsReady) {
            // The reply is already rendered in the transcript; we just can't speak it (c-5).
            _guidance.value = VoiceGuidanceFactory.ttsUnavailable()
        } else if (timing == SpeakTiming.SENTENCE_BY_SENTENCE) {
            chunker.finish().forEach { tts.speak(it) }
        } else {
            finalUi.lastReply?.let { tts.speak(it) }
        }
        _state.value = VoiceFlowState.Idle
    }
}
