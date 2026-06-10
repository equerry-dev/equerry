package dev.equerry.app.voice

import android.speech.tts.TextToSpeech as AndroidTextToSpeech
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies t-3's contract: a failed init yields [TtsInitResult.Failed] and leaves speak() a safe
 * no-op; a null engine yields [TtsInitResult.MissingEngine]; speak() before a Ready init is dropped
 * rather than crashing (c-4 best-effort, c-5 never-crash). The TTS init status constants inline at
 * compile time, so no Android runtime is needed — the engine is a fake.
 */
class SystemTextToSpeechTest {

    private class FakeEngine : TtsEngine {
        val spoken = mutableListOf<String>()
        var shutdown = false
        override fun speak(text: String) {
            spoken.add(text)
        }
        override fun stop() = Unit
        override fun shutdown() {
            shutdown = true
        }
    }

    @Test
    fun init_failure_returns_Failed_and_speak_is_a_safe_noop() = runBlocking {
        val engine = FakeEngine()
        val tts = SystemTextToSpeech { onInit ->
            onInit(AndroidTextToSpeech.ERROR)
            engine
        }

        assertEquals(TtsInitResult.Failed, withTimeout(2_000) { tts.init() })

        tts.speak("hello") // must not throw
        assertEquals(emptyList<String>(), engine.spoken)
    }

    @Test
    fun no_engine_returns_MissingEngine() = runBlocking {
        val tts = SystemTextToSpeech { _ -> null }
        assertEquals(TtsInitResult.MissingEngine, withTimeout(2_000) { tts.init() })
    }

    @Test
    fun speak_before_init_is_dropped() {
        val engine = FakeEngine()
        val tts = SystemTextToSpeech { _ -> engine } // init never invoked
        tts.speak("hello") // dropped, no crash
        assertEquals(emptyList<String>(), engine.spoken)
    }

    @Test
    fun speak_after_ready_init_reaches_the_engine() = runBlocking {
        val engine = FakeEngine()
        val tts = SystemTextToSpeech { onInit ->
            onInit(AndroidTextToSpeech.SUCCESS)
            engine
        }
        assertEquals(TtsInitResult.Ready, withTimeout(2_000) { tts.init() })

        tts.speak("hello")
        assertEquals(listOf("hello"), engine.spoken)
    }
}
