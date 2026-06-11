package dev.equerry.app.voice

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSpeechToTextTest {

    /** Feeds scripted per-frame amplitudes to the VAD predicate, then returns a fixed clip. */
    private class FakeRecorder(
        private val amplitudes: List<Int>,
        private val clip: ByteArray = byteArrayOf(1, 2, 3),
    ) : AudioClipRecorder {
        override suspend fun record(shouldStop: (Int) -> Boolean): ByteArray {
            for (a in amplitudes) if (shouldStop(a)) break
            return clip
        }
    }

    private class FakeTransport(private val result: () -> String) : TranscribeTransport {
        override suspend fun transcribe(clip: ByteArray): String = result()
    }

    private fun vad() = { SilenceVad(amplitudeThreshold = 1000, silenceFrames = 3) }

    @Test
    fun emits_exactly_endofspeech_then_final_with_no_partials() = runBlocking {
        // loud frame starts speech, then 3 silent frames trip the VAD.
        val recorder = FakeRecorder(listOf(2000, 100, 100, 100, 100, 100))
        val stt = RemoteSpeechToText(recorder, FakeTransport { "what time is it" }, vad())

        val events = withTimeout(2_000) { stt.listen().toList() }

        assertEquals(listOf(SttEvent.EndOfSpeech, SttEvent.Final("what time is it")), events)
        assertTrue("remote STT never emits interim partials", events.none { it is SttEvent.Partial })
    }

    @Test
    fun transport_401_yields_terminal_unavailable_error_and_completes() = runBlocking {
        val recorder = FakeRecorder(listOf(2000, 100, 100, 100))
        val stt = RemoteSpeechToText(
            recorder,
            FakeTransport { throw RemoteAudioHttpException(401) },
            vad(),
        )

        // withTimeout proves the flow terminates (doesn't hang) after the error.
        val events = withTimeout(2_000) { stt.listen().toList() }

        assertEquals(listOf(SttEvent.EndOfSpeech, SttEvent.Error(SttError.Unavailable)), events)
    }
}
