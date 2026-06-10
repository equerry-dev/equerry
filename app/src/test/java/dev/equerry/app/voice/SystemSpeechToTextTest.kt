package dev.equerry.app.voice

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies t-2's contract: each SpeechRecognizer error code maps to its [SttError] and the flow
 * completes (no crash/hang), and onResults yields a [SttEvent.Final] with the first hypothesis.
 * Robolectric supplies a real [Bundle]; the recognizer is a fake driving the listener directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SystemSpeechToTextTest {

    /** Runs a scripted action against the listener when start() is called; records destruction. */
    private class FakeHandle(val onStart: (RecognitionListener) -> Unit) : RecognizerHandle {
        var destroyed = false
        override fun start(listener: RecognitionListener) = onStart(listener)
        override fun stop() = Unit
        override fun destroy() {
            destroyed = true
        }
    }

    private fun sttDriving(onStart: (RecognitionListener) -> Unit) =
        SystemSpeechToText { FakeHandle(onStart) }

    @Test
    fun no_match_error_maps_to_NoMatch_and_completes() = runBlocking {
        val events = withTimeout(2_000) {
            sttDriving { it.onError(SpeechRecognizer.ERROR_NO_MATCH) }.listen().toList()
        }
        assertEquals(listOf(SttEvent.Error(SttError.NoMatch)), events)
    }

    @Test
    fun every_mapped_error_code_resolves_to_its_stt_error() {
        assertEquals(SttError.NoMatch, sttErrorFor(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(SttError.Timeout, sttErrorFor(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertEquals(SttError.PermissionDenied, sttErrorFor(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertEquals(SttError.Busy, sttErrorFor(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        // Anything outside the explicit set degrades to Unavailable rather than crashing.
        assertEquals(SttError.Unavailable, sttErrorFor(SpeechRecognizer.ERROR_CLIENT))
    }

    @Test
    fun results_emit_final_with_the_first_hypothesis() = runBlocking {
        val results = Bundle().apply {
            putStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION,
                arrayListOf("what time is it", "what time is at"),
            )
        }
        val events = withTimeout(2_000) {
            sttDriving { it.onResults(results) }.listen().toList()
        }
        assertEquals(listOf(SttEvent.Final("what time is it")), events)
    }
}
