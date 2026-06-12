package dev.equerry.app.voice

import dev.equerry.app.data.SpeakTiming
import dev.equerry.app.data.TurnControl
import dev.equerry.app.data.VoiceSettingsStore
import dev.equerry.app.providers.drivers.ChatRole
import dev.equerry.app.screencontext.ScreenContext
import dev.equerry.app.tools.actions.PlannedAction
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
    // Supplies the current screen capture for a spoken screen-context query; null = none available,
    // so the utterance falls through to a normal chat send. Defaulted so non-session callers/tests
    // (which never ask about the screen) are unaffected.
    private val screenContext: () -> ScreenContext? = { null },
    // Captures one camera frame for a spoken "look through the camera" query: opens the capture UI,
    // returns the frame (image + OCR text) or null on denial/failure/cancel. Suspends while the user
    // aims, so the loop naturally pauses (no mic re-arm) until the capture settles. Defaulted inert so
    // non-camera callers/tests are unaffected.
    private val cameraContext: suspend () -> ScreenContext? = { null },
    // Consented system-engine fallback (locked `failover_consented`): when the active engine is a
    // remote one and it fails, the user is offered a one-tap retry on these system engines — never an
    // automatic switch. Defaulted to inert (no fallback) so callers that don't wire it are unaffected.
    private val systemStt: SpeechToText? = null,
    private val systemTts: TextToSpeech? = null,
    private val isSttRemote: suspend () -> Boolean = { false },
    private val isTtsRemote: suspend () -> Boolean = { false },
) {

    private val _state = MutableStateFlow(VoiceFlowState.Idle)
    val state: StateFlow<VoiceFlowState> = _state.asStateFlow()

    // The engines this and subsequent turns use. They start as the injected (possibly remote) engines
    // and only switch to the system engines after the user invokes [retryWithSystem].
    private var activeStt: SpeechToText = stt
    private var activeTts: TextToSpeech = tts

    // True when the last turn failed on a remote engine and a consented system-retry is available.
    private var systemRetryPending: Boolean = false

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
                ttsReady = activeTts.init() == TtsInitResult.Ready
                // A remote TTS that failed to init offers a consented system retry instead of going
                // silent (locked failover_consented) — never an automatic switch.
                if (!ttsReady && canOfferSystemFallback(activeTts, systemTts, isTtsRemote())) {
                    _guidance.value = VoiceGuidanceFactory.remoteEngineFailed(RemoteAudioError.Unavailable)
                    systemRetryPending = true
                    _state.value = VoiceFlowState.Idle
                    return@launch
                }
                do {
                    _guidance.value = null
                    _state.value = VoiceFlowState.Listening

                    var finalText: String? = null
                    var sttError: SttError? = null
                    activeStt.listen().collect { event ->
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

                    // The STT flow has completed — speech ended (or errored). A remote-engine failure
                    // offers a consented system retry rather than the generic STT guidance.
                    sttError?.let { error ->
                        _guidance.value = if (canOfferSystemFallback(activeStt, systemStt, isSttRemote())) {
                            systemRetryPending = true
                            VoiceGuidanceFactory.remoteEngineFailed(RemoteAudioError.Unavailable)
                        } else {
                            VoiceGuidanceFactory.sttError(error)
                        }
                    }
                    val text = finalText
                    if (text.isNullOrBlank()) break
                    // A spoken end-session command ("Equerry off") ends the auto-listening loop: briefly
                    // acknowledge aloud (when TTS is usable), then break so the controller settles to Idle.
                    if (StopGrammar.isStop(text)) {
                        if (ttsReady) {
                            activeTts.speak("Goodbye.")
                            activeTts.awaitDone()
                        }
                        break
                    }
                    // Routing, most specific first: a spoken camera query opens the camera and
                    // describes the captured frame; a screen query routes to the screen capture when one
                    // is available; everything else is an ordinary chat send. Camera is checked before
                    // screen so an explicit "camera" mention wins over the screen path.
                    val screen = screenContext()
                    if (CameraQueryGrammar.isCameraQuery(text)) {
                        val camera = cameraContext()
                        if (camera != null) {
                            sendAndSpeak { chat.askAboutCamera(camera) }
                        } else {
                            _guidance.value = VoiceGuidance("Couldn't open the camera — check the camera permission.")
                            _state.value = VoiceFlowState.Idle
                        }
                    } else if (screen != null && ScreenQueryGrammar.isScreenQuery(text)) {
                        sendAndSpeak { chat.askAboutScreen(screen) }
                    } else {
                        sendAndSpeak { chat.send(text) }
                    }
                    // Re-arm for a follow-up only once the prior turn has fully settled (above), so
                    // the mic is never armed twice for one turn.
                    val turn = settings.turnControl().first()
                } while (turn == TurnControl.CONTINUOUS && isActive)
                _state.value = VoiceFlowState.Idle
            } catch (c: CancellationException) {
                throw c // dismissal — let stop()'s cancellation propagate
            } catch (e: Exception) {
                // Never throw to the framework session; surface a generic guidance instead (c-5).
                // Log the cause to stderr (→ logcat System.err) so the otherwise-invisible failure is
                // diagnosable; this keeps the controller Android-free (no android.util.Log).
                e.printStackTrace()
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
        activeTts.stop()
        _state.value = VoiceFlowState.Idle
    }

    /**
     * Consented system-engine retry (locked `failover_consented`). No-op unless a remote-engine
     * failure left a retry pending and a system fallback is configured: only then — and only when the
     * user invokes this — does the controller switch [activeStt]/[activeTts] to the system engines and
     * re-run the turn. Never called automatically, so a remote failure never silently routes audio to
     * the system engine.
     */
    fun retryWithSystem() {
        if (!systemRetryPending) return
        systemRetryPending = false
        systemStt?.let { activeStt = it }
        systemTts?.let { activeTts = it }
        _guidance.value = null
        start()
    }

    /**
     * Whether a failure on [active] should offer the consented system retry: a system fallback exists,
     * the active engine isn't already that fallback (so we don't loop), and the active engine was the
     * remote one for this turn.
     */
    private fun canOfferSystemFallback(active: Any, fallback: Any?, remote: Boolean): Boolean =
        fallback != null && active !== fallback && remote

    /**
     * Run one turn: [dispatch] kicks off the round-trip on [ChatViewModel] (a normal send or a
     * screen-context query), then this waits for it to settle and speaks the reply. A screen query
     * that can't proceed settles via [ChatUiState.screenNote] (no streaming) — spoken aloud, then the
     * loop drops back to ordinary chat (blank_screen).
     */
    private suspend fun sendAndSpeak(dispatch: () -> Unit) {
        _state.value = VoiceFlowState.Sending
        val timing = settings.speakTiming().first()
        val chunker = SpeakChunker(timing)
        var spokenSoFar = ""
        var sawStreaming = false

        dispatch()
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
                        chunker.feed(delta).forEach { activeTts.speak(it) }
                    }
                }
            }
            // A turn settles when streaming stops — whether it produced reply text, an error, an
            // unmapped state, or only tool calls — OR when a screen query posts a guidance note
            // without streaming (askAboutScreen clears any stale note synchronously first).
            .first { (sawStreaming && !it.streaming) || it.screenNote != null }

        val finalUi = chat.state.value
        when {
            // Screen couldn't be read / nothing configured — speak the note, then fall back to chat.
            finalUi.screenNote != null -> {
                if (ttsReady) {
                    activeTts.speak(finalUi.screenNote)
                    activeTts.awaitDone()
                }
                _guidance.value = VoiceGuidance(finalUi.screenNote)
                _state.value = VoiceFlowState.Idle
                return
            }
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
        } else {
            if (timing == SpeakTiming.SENTENCE_BY_SENTENCE) {
                chunker.finish().forEach { activeTts.speak(it) }
            } else {
                finalUi.lastReply?.let { activeTts.speak(it) }
            }
            // Wait until Equerry has actually stopped talking before the loop re-arms the mic,
            // otherwise STT captures the TTS output and the assistant answers itself (continuous).
            activeTts.awaitDone()
        }
        // If the turn staged a benign on-device action, offer a spoken "Start now?" before settling.
        confirmStagedActionByVoice()
        _state.value = VoiceFlowState.Idle
    }

    /**
     * Spoken confirm for a staged timer/alarm. Speaks "Start now?" and only AFTER the prompt finishes
     * ([TextToSpeech.awaitDone]) arms a narrow yes/no listen — arming earlier would let the spoken
     * prompt be re-transcribed as a "yes". A "yes" fires the action once; a "no" discards it; anything
     * else (including a silent timeout) leaves it staged as a tappable card. Outward hand-offs
     * (email/SMS) are never voice-fired — only [PlannedAction.Staged] actions reach here
     * (benign_voice_confirm).
     */
    private suspend fun confirmStagedActionByVoice() {
        val index = chat.state.value.pendingActions.indexOfFirst { it is PlannedAction.Staged }
        if (index < 0) return
        if (!ttsReady) return // can't ask aloud — leave it staged as a tappable card

        _state.value = VoiceFlowState.Speaking
        activeTts.speak("Start now?")
        activeTts.awaitDone()

        _state.value = VoiceFlowState.Listening
        var answer: String? = null
        activeStt.listen().collect { event -> if (event is SttEvent.Final) answer = event.text }

        when (YesNoGrammar.classify(answer.orEmpty())) {
            YesNoGrammar.Verdict.YES -> chat.confirmAction(index)
            YesNoGrammar.Verdict.NO -> chat.cancelAction(index)
            YesNoGrammar.Verdict.UNRECOGNISED -> Unit // leave it staged as a tappable card
        }
    }
}
