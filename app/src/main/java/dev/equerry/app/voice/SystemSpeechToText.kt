package dev.equerry.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Minimal seam over the parts of [SpeechRecognizer] this feature uses, so [SystemSpeechToText]'s
 * flow wiring and error mapping are unit-testable against a fake. The real recognizer is
 * framework-bound and verified at integration (t-10).
 */
interface RecognizerHandle {
    fun start(listener: RecognitionListener)
    fun stop()
    fun destroy()
}

/** Maps a [SpeechRecognizer] error code onto a routable [SttError]. Constants inline at compile time. */
internal fun sttErrorFor(code: Int): SttError = when (code) {
    SpeechRecognizer.ERROR_NO_MATCH -> SttError.NoMatch
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SttError.Timeout
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SttError.PermissionDenied
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SttError.Busy
    else -> SttError.Unavailable
}

/**
 * System STT via [SpeechRecognizer], exposed as a [SttEvent] flow. The recognizer is obtained
 * through [handleFactory] so tests substitute a fake that drives the [RecognitionListener]
 * callbacks directly; production uses [fromContext]. Not routed through EquerryRecognitionService
 * — v0.1 uses the system recognizer.
 */
class SystemSpeechToText(
    private val handleFactory: () -> RecognizerHandle,
) : SpeechToText {

    override fun listen(): Flow<SttEvent> = callbackFlow {
        val handle = handleFactory()
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                trySend(SttEvent.EndOfSpeech)
            }

            override fun onError(error: Int) {
                trySend(SttEvent.Error(sttErrorFor(error)))
                close()
            }

            override fun onResults(results: Bundle?) {
                trySend(SttEvent.Final(firstHypothesis(results).orEmpty()))
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                firstHypothesis(partialResults)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { trySend(SttEvent.Partial(it)) }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
        handle.start(listener)
        awaitClose { handle.destroy() }
    }

    private fun firstHypothesis(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    companion object {
        /** Production factory: a [RecognizerHandle] backed by a real [SpeechRecognizer]. */
        fun fromContext(context: Context): SystemSpeechToText = SystemSpeechToText {
            SystemRecognizerHandle(SpeechRecognizer.createSpeechRecognizer(context))
        }
    }
}

/** Framework-bound [RecognizerHandle] over a real [SpeechRecognizer]. Verified at integration (t-10). */
private class SystemRecognizerHandle(
    private val recognizer: SpeechRecognizer,
) : RecognizerHandle {
    override fun start(listener: RecognitionListener) {
        recognizer.setRecognitionListener(listener)
        recognizer.startListening(recognizerIntent())
    }

    override fun stop() = recognizer.stopListening()

    override fun destroy() = recognizer.destroy()

    private fun recognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
}
