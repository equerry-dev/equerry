package dev.equerry.app.voice

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.equerry.app.data.ProfileStore
import dev.equerry.app.data.SecretStore
import dev.equerry.app.data.SlotMappingStore
import dev.equerry.app.data.TurnControl
import dev.equerry.app.data.VoiceSettingsStore
import dev.equerry.app.providers.ProfileDraft
import dev.equerry.app.providers.ProviderRepository
import dev.equerry.app.providers.ProviderType
import dev.equerry.app.providers.drivers.AnthropicChatDriver
import dev.equerry.app.providers.drivers.ChatDriverFactory
import dev.equerry.app.providers.drivers.ChatHttpClient
import dev.equerry.app.providers.drivers.ChatSession
import dev.equerry.app.providers.drivers.OllamaChatDriver
import dev.equerry.app.providers.drivers.OpenAiChatDriver
import dev.equerry.app.ui.chat.ChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceFlowConcurrencyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeSecretStore : SecretStore {
        private val keys = mutableMapOf<String, String>()
        override fun putKey(profileId: String, key: String) { keys[profileId] = key }
        override fun getKey(profileId: String): String? = keys[profileId]
        override fun removeKey(profileId: String) { keys.remove(profileId) }
    }

    /**
     * Per-session scripted STT. Session i emits [sessions]\[i] then completes; a null/absent script
     * stays open until cancelled (simulates active listening). Start/release counts are StateFlows
     * so tests can await them deterministically.
     */
    private class ScriptedStt(private val sessions: List<List<SttEvent>?>) : SpeechToText {
        val starts = MutableStateFlow(0)
        val releases = MutableStateFlow(0)
        override fun listen(): Flow<SttEvent> = callbackFlow {
            val idx = starts.value
            starts.value = idx + 1
            val script = sessions.getOrNull(idx)
            if (script != null) {
                script.forEach { trySend(it) }
                close()
            }
            awaitClose { releases.value = releases.value + 1 }
        }
    }

    private class FakeTextToSpeech : TextToSpeech {
        val spoken = mutableListOf<String>()
        override suspend fun init() = TtsInitResult.Ready
        override fun speak(utterance: String) { spoken.add(utterance) }
        override suspend fun speakSentences(sentences: Flow<String>) = sentences.collect { spoken.add(it) }
        override fun stop() = Unit
        override fun shutdown() = Unit
    }

    private lateinit var dataScope: CoroutineScope
    private lateinit var controllerScope: CoroutineScope
    private lateinit var server: MockWebServer
    private lateinit var repository: ProviderRepository
    private lateinit var vm: ChatViewModel
    private lateinit var settings: VoiceSettingsStore

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        dataScope = CoroutineScope(Dispatchers.IO + Job())
        controllerScope = CoroutineScope(Dispatchers.Unconfined + Job())
        server = MockWebServer()
        server.start()
        val file = File(tmp.newFolder(), "s.preferences_pb")
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = dataScope) { file }
        repository = ProviderRepository(ProfileStore(ds), FakeSecretStore(), SlotMappingStore(ds))
        val client = ChatHttpClient.create()
        val factory = ChatDriverFactory(OpenAiChatDriver(client), AnthropicChatDriver(client), OllamaChatDriver(client))
        vm = ChatViewModel(repository, factory, ChatSession())
        settings = VoiceSettingsStore(ds)
    }

    @After
    fun tearDown() {
        server.shutdown()
        runBlocking {
            controllerScope.coroutineContext[Job]!!.cancelAndJoin()
            dataScope.coroutineContext[Job]!!.cancelAndJoin()
        }
        Dispatchers.resetMain()
    }

    private suspend fun configureChatProvider() {
        val created = repository.addProfile(
            ProfileDraft("Mock", ProviderType.OPENAI_COMPATIBLE, server.url("/").toString(), "sk-key-123456", "m"),
        )
        repository.setChatSlot(created.id)
    }

    private fun sseReply(vararg deltas: String): MockResponse =
        MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
            buildString {
                deltas.forEach { append("""data: {"choices":[{"delta":{"content":"$it"}}]}""").append("\n\n") }
                append("data: [DONE]\n\n")
            },
        )

    private fun controllerWith(stt: SpeechToText, tts: TextToSpeech) =
        VoiceFlowController(vm, stt, tts, settings, controllerScope, isMicGranted = { true })

    @Test
    fun dismiss_mid_listening_releases_stt_once_and_returns_to_idle() = runBlocking {
        val stt = ScriptedStt(listOf(null)) // session stays open (listening)
        val controller = controllerWith(stt, FakeTextToSpeech())

        controller.start()
        withTimeout(5_000) { stt.starts.first { it == 1 } }
        assertEquals(VoiceFlowState.Listening, controller.state.value)

        controller.stop()

        withTimeout(5_000) { stt.releases.first { it == 1 } }
        assertEquals(1, stt.starts.value) // no Final was ever processed; no re-listen
        assertEquals(VoiceFlowState.Idle, controller.state.value)
    }

    @Test
    fun dismiss_mid_stream_speaks_nothing() = runBlocking {
        configureChatProvider()
        // Body never arrives within the test window, so the reply stays mid-stream.
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBodyDelay(10, TimeUnit.SECONDS).setBody("data: [DONE]\n\n"),
        )
        val tts = FakeTextToSpeech()
        val controller = controllerWith(ScriptedStt(listOf(listOf(SttEvent.EndOfSpeech, SttEvent.Final("hi")))), tts)

        controller.start()
        // Wait until the reply is actually streaming, then dismiss.
        withTimeout(5_000) { vm.state.first { it.streaming } }
        controller.stop()

        assertTrue("nothing should be spoken when dismissed mid-stream", tts.spoken.isEmpty())
        assertEquals(VoiceFlowState.Idle, controller.state.value)
    }

    @Test
    fun continuous_rearms_once_per_turn_and_ignores_a_double_start() = runBlocking {
        configureChatProvider()
        settings.setTurnControl(TurnControl.CONTINUOUS)
        server.enqueue(sseReply("Hi"))
        // Turn 1 completes; the re-armed second session stays open.
        val stt = ScriptedStt(listOf(listOf(SttEvent.EndOfSpeech, SttEvent.Final("q1")), null))
        val controller = controllerWith(stt, FakeTextToSpeech())

        controller.start()

        // After turn 1 settles, the loop re-arms exactly once (start count reaches 2).
        withTimeout(5_000) { stt.starts.first { it == 2 } }
        assertEquals(VoiceFlowState.Listening, controller.state.value)

        // A duplicate trigger while a turn is active must not arm a third session.
        controller.start()
        assertEquals(2, stt.starts.value)

        controller.stop()
    }

    @Test
    fun single_turn_does_not_rearm() = runBlocking {
        configureChatProvider()
        settings.setTurnControl(TurnControl.SINGLE_TURN)
        server.enqueue(sseReply("Hi"))
        val stt = ScriptedStt(listOf(listOf(SttEvent.EndOfSpeech, SttEvent.Final("q1"))))
        val controller = controllerWith(stt, FakeTextToSpeech())

        controller.start()

        // The one reply completes, then the loop ends without re-arming.
        withTimeout(5_000) { vm.state.first { !it.streaming && it.transcript.size == 2 && it.lastReply != null } }
        withTimeout(5_000) { controller.state.first { it == VoiceFlowState.Idle } }
        assertEquals(1, stt.starts.value) // listened exactly once
    }
}
