package dev.equerry.app.voice

import dev.equerry.app.data.SpeakTiming
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

    /** Begin a listening turn. No-op while one is already running. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            if (!isMicGranted()) {
                // Guidance for the denied state is routed by t-11; here we simply never arm STT.
                _state.value = VoiceFlowState.Idle
                return@launch
            }
            tts.init()
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
            if (text.isNullOrBlank()) {
                _state.value = VoiceFlowState.Idle
            } else {
                sendAndSpeak(text)
            }
        }
    }

    /** Stop the current turn and silence TTS. Hardened for cancellation/idempotency in t-8. */
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
