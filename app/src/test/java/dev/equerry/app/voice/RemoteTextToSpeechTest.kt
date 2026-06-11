package dev.equerry.app.voice

import dev.equerry.app.providers.drivers.ChatHttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTextToSpeechTest {

    /** Records the texts it was asked to synthesize; the clip echoes the text so order is checkable. */
    private class RecordingSynth : SpeechSynthesizer {
        val texts = mutableListOf<String>()
        override suspend fun synthesize(text: String): ByteArray {
            texts.add(text)
            return text.toByteArray()
        }
    }

    private class ThrowingSynth(private val error: Throwable) : SpeechSynthesizer {
        override suspend fun synthesize(text: String): ByteArray = throw error
    }

    private class FakePlayer(private val initResult: Boolean = true) : ClipPlayer {
        val enqueued = mutableListOf<ByteArray>()
        private val drain = CompletableDeferred<Unit>()
        var released = false
        override suspend fun init(): Boolean = initResult
        override fun enqueue(clip: ByteArray) { enqueued.add(clip) }
        override suspend fun awaitDone() = drain.await()
        override fun stop() = Unit
        override fun release() { released = true }
        fun completeDrain() = drain.complete(Unit)
    }

    // Unconfined makes the FIFO worker process each Speak inline on send, so assertions are deterministic.
    private fun scope() = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun speak_posts_once_and_enqueues_one_clip() = runBlocking {
        val synth = RecordingSynth()
        val player = FakePlayer()
        val tts = RemoteTextToSpeech(synth, player, scope())

        assertEquals(TtsInitResult.Ready, tts.init())
        tts.speak("hello")
        player.completeDrain()
        tts.awaitDone()

        assertEquals(listOf("hello"), synth.texts)
        assertEquals(1, player.enqueued.size)
        tts.shutdown()
    }

    @Test
    fun speak_sentences_posts_per_sentence_and_enqueues_clips_in_order() = runBlocking {
        val synth = RecordingSynth()
        val player = FakePlayer()
        val tts = RemoteTextToSpeech(synth, player, scope())
        tts.init()

        tts.speakSentences(flowOf("one.", "two.", "three."))
        player.completeDrain()
        tts.awaitDone()

        assertEquals(listOf("one.", "two.", "three."), synth.texts)
        assertEquals(listOf("one.", "two.", "three."), player.enqueued.map { String(it) })
        tts.shutdown()
    }

    @Test
    fun await_done_resumes_only_after_the_player_drains() = runBlocking {
        val player = FakePlayer()
        val tts = RemoteTextToSpeech(RecordingSynth(), player, scope())
        tts.init()
        tts.speak("hi")

        val done = async(Dispatchers.Unconfined) { tts.awaitDone() }
        assertFalse("awaitDone gates the mic re-arm until playback drains", done.isCompleted)

        player.completeDrain()
        done.await()
        assertTrue(done.isCompleted)
        tts.shutdown()
    }

    @Test
    fun speak_on_a_failed_init_is_a_guarded_noop() = runBlocking {
        val synth = RecordingSynth()
        val player = FakePlayer(initResult = false)
        val tts = RemoteTextToSpeech(synth, player, scope())

        assertEquals(TtsInitResult.Failed, tts.init())
        tts.speak("hello") // must not throw
        tts.awaitDone()     // no-op when not ready

        assertEquals(emptyList<String>(), synth.texts)
        assertEquals(0, player.enqueued.size)
        tts.shutdown()
    }

    @Test
    fun a_transport_500_surfaces_remote_audio_error_without_throwing() = runBlocking {
        val player = FakePlayer()
        val errors = mutableListOf<RemoteAudioError>()
        val tts = RemoteTextToSpeech(
            ThrowingSynth(RemoteAudioHttpException(500)), player, scope(), onError = { errors.add(it) },
        )
        tts.init()

        tts.speak("boom") // must not throw into the caller
        player.completeDrain()
        tts.awaitDone()

        assertEquals(listOf(RemoteAudioError.Unavailable), errors)
        assertEquals(0, player.enqueued.size)
        tts.shutdown()
    }

    @Test
    fun production_synth_posts_to_audio_speech_and_omits_a_blank_voice() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("mp3-bytes"))
        server.start()
        try {
            val synth = OkHttpSpeechSynthesizer(
                client = ChatHttpClient.create(),
                baseUrl = server.url("/").toString(),
                model = "tts-1",
                voice = "", // blank -> provider default, voice field omitted
                key = "sk-secret-XYZ",
            )

            val bytes = synth.synthesize("hello there")
            assertEquals("mp3-bytes", String(bytes))

            val recorded = server.takeRequest()
            assertEquals("/audio/speech", recorded.path)
            val body = recorded.body.readUtf8()
            assertTrue(body.contains("\"input\":\"hello there\""))
            assertFalse("blank voice must be omitted", body.contains("voice"))
            // r-03: the key rides the (redacted) Authorization header, never the body.
            assertFalse(body.contains("sk-secret-XYZ"))
            assertEquals("Bearer sk-secret-XYZ", recorded.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }
}
