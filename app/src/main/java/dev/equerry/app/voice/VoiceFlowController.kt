package dev.equerry.app.voice

import dev.equerry.app.data.SpeakTiming
import dev.equerry.app.data.TurnControl
import dev.equerry.app.data.VoiceSettingsStore
import dev.equerry.app.providers.drivers.ChatRole
import dev.equerry.app.ui.chat.ChatViewModel
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
) {

    private val _state = MutableStateFlow(VoiceFlowState.Idle)
    val state: StateFlow<VoiceFlowState> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * Begin the listen→send→speak loop. No-op while a turn is already running (the active-job guard
     * prevents a double-arm from rapid triggers). In [TurnControl.CONTINUOUS] the loop re-arms for a
     * follow-up after each turn fully settles; in [TurnControl.SINGLE_TURN] it ends after one Q&A.
     */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            if (!isMicGranted()) {
                // Guidance for the denied state is routed by t-11; here we simply never arm STT.
                _state.value = VoiceFlowState.Idle
                return@launch
            }
            tts.init()
            do {
                _state.value = VoiceFlowState.Listening

                var finalText: String? = null
                stt.listen().collect { event ->
                    when (event) {
                        is SttEvent.Partial -> _state.value = VoiceFlowState.Transcribing
                        is SttEvent.Final -> {
                            finalText = event.text
                            _state.value = VoiceFlowState.Transcribing
                        }
                        SttEvent.EndOfSpeech -> _state.value = VoiceFlowState.Transcribing
                        is SttEvent.Error -> Unit // routed in t-11
                    }
                }

                // The STT flow has completed — speech ended. Auto-send the recognized text.
                val text = finalText
                if (text.isNullOrBlank()) break
                sendAndSpeak(text)
                // Re-arm for a follow-up only once the prior turn has fully settled (above), so the
                // mic is never armed twice for one turn.
                val turn = settings.turnControl().first()
            } while (turn == TurnControl.CONTINUOUS && isActive)
            _state.value = VoiceFlowState.Idle
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
                // Feed newly-streamed text to the chunker for sentence-by-sentence speaking.
                if (timing == SpeakTiming.SENTENCE_BY_SENTENCE) {
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
        if (finalUi.error != null || finalUi.unmapped) {
            // The reply failed or no provider is mapped — guidance is t-11's job; do not speak.
            _state.value = VoiceFlowState.Idle
            return
        }

        _state.value = VoiceFlowState.Speaking
        if (timing == SpeakTiming.SENTENCE_BY_SENTENCE) {
            chunker.finish().forEach { tts.speak(it) }
        } else {
            finalUi.lastReply?.let { tts.speak(it) }
        }
        _state.value = VoiceFlowState.Idle // continuous re-arm is added in t-8
    }
}
