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
import dev.equerry.app.providers.drivers.ChatError
import dev.equerry.app.providers.drivers.ChatHttpClient
import dev.equerry.app.providers.drivers.ChatSession
import dev.equerry.app.tools.actions.ActionRunner
import dev.equerry.app.providers.drivers.OllamaChatDriver
import dev.equerry.app.providers.drivers.OpenAiChatDriver
import dev.equerry.app.ui.chat.ChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceFlowFailureTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeSecretStore : SecretStore {
        private val keys = mutableMapOf<String, String>()
        override fun putKey(profileId: String, key: String) { keys[profileId] = key }
        override fun getKey(profileId: String): String? = keys[profileId]
        override fun removeKey(profileId: String) { keys.remove(profileId) }
    }

    /** Emits a fixed script then completes; counts how many times it was armed. */
    private class CountingStt(private val events: List<SttEvent>) : SpeechToText {
        var starts = 0
        override fun listen(): Flow<SttEvent> {
            starts += 1
            return events.asFlow()
        }
    }

    private class FakeTextToSpeech(private val initResult: TtsInitResult = TtsInitResult.Ready) : TextToSpeech {
        val spoken = mutableListOf<String>()
        override suspend fun init() = initResult
        override fun speak(utterance: String) { spoken.add(utterance) }
        override suspend fun speakSentences(sentences: Flow<String>) = sentences.collect { spoken.add(it) }
        override suspend fun awaitDone() = Unit
        override fun stop() = Unit
        override fun shutdown() = Unit
    }

    private lateinit var dataScope: CoroutineScope
    private lateinit var controllerScope: CoroutineScope
    private lateinit var server: MockWebServer
    private lateinit var repository: ProviderRepository
    private lateinit var vm: ChatViewModel
    private lateinit var settings: VoiceSettingsStore

    private val key = "sk-secret-ABCDEF1234567890"

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
        vm = ChatViewModel(repository, factory, ChatSession(), ActionRunner { false })
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
            ProfileDraft("Mock", ProviderType.OPENAI_COMPATIBLE, server.url("/").toString(), key, "m"),
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

    private fun controllerWith(
        stt: SpeechToText,
        tts: TextToSpeech,
        chatConfigured: Boolean = true,
    ) = VoiceFlowController(
        vm, stt, tts, settings, controllerScope,
        isMicGranted = { true },
        isChatConfigured = { chatConfigured },
    )

    @Test
    fun no_chat_provider_guides_and_never_arms_stt() = runBlocking {
        val stt = CountingStt(listOf(SttEvent.EndOfSpeech, SttEvent.Final("hi")))
        val controller = controllerWith(stt, FakeTextToSpeech(), chatConfigured = false)

        controller.start()

        val guidance = withTimeout(5_000) { controller.guidance.first { it != null } }
        assertEquals(VoiceGuidanceFactory.noChatProvider(), guidance)
        assertEquals(0, stt.starts) // STT must never be armed with nothing to send to
        assertEquals(0, server.requestCount)
    }

    @Test
    fun mic_denied_guides_with_settings_path_and_never_arms_stt() = runBlocking {
        val stt = CountingStt(listOf(SttEvent.Final("hi")))
        val controller = VoiceFlowController(
            vm, stt, FakeTextToSpeech(), settings, controllerScope,
            isMicGranted = { false },
        )

        controller.start()

        val guidance = withTimeout(5_000) { controller.guidance.first { it != null } }!!
        assertEquals(VoiceGuidanceFactory.micDenied(), guidance)
        assertTrue("denied guidance must offer the settings path", guidance.canOpenSettings)
        assertEquals(0, stt.starts)
    }

    @Test
    fun stt_no_match_guides_without_crashing_or_sending() = runBlocking {
        configureChatProvider()
        settings.setTurnControl(TurnControl.SINGLE_TURN)
        val controller = controllerWith(CountingStt(listOf(SttEvent.Error(SttError.NoMatch))), FakeTextToSpeech())

        controller.start()

        val guidance = withTimeout(5_000) { controller.guidance.first { it != null } }
        assertEquals(VoiceGuidanceFactory.sttError(SttError.NoMatch), guidance)
        assertEquals("no recognized speech means no provider call", 0, server.requestCount)
        val end = withTimeout(5_000) { controller.state.first { it == VoiceFlowState.Idle } }
        assertEquals(VoiceFlowState.Idle, end)
    }

    @Test
    fun reply_error_shows_keyfree_guidance_and_speaks_a_retry_note() = runBlocking {
        configureChatProvider()
        settings.setTurnControl(TurnControl.SINGLE_TURN)
        server.enqueue(MockResponse().setResponseCode(401).setHeader("Content-Type", "text/event-stream"))
        val tts = FakeTextToSpeech()
        val controller = controllerWith(CountingStt(listOf(SttEvent.EndOfSpeech, SttEvent.Final("hi"))), tts)

        controller.start()

        val guidance = withTimeout(5_000) { controller.guidance.first { it != null } }!!
        assertEquals(ChatError.Auth.message, guidance.message)
        assertFalse("guidance must never leak the key", guidance.message.contains(key))
        // A hands-free user hears a brief retry note (not the failed reply, and never the key).
        assertTrue("a failed turn speaks a retry note", tts.spoken.any { it.contains("try again", ignoreCase = true) })
        assertFalse("the spoken note must never leak the key", tts.spoken.any { it.contains(key) })
        // The partial reply (user turn + empty assistant bubble) is retained, not discarded.
        assertEquals(2, vm.state.value.transcript.size)
    }

    @Test
    fun remote_stt_failure_offers_consented_retry_and_uses_system_only_after_invoked() = runBlocking {
        configureChatProvider()
        settings.setTurnControl(TurnControl.SINGLE_TURN)
        // The remote engine errors; the system engine is a spy that errors too (so the retry turn
        // settles without needing a provider reply). isSttRemote=true marks this turn's engine remote.
        val remoteStt = CountingStt(listOf(SttEvent.EndOfSpeech, SttEvent.Error(SttError.Unavailable)))
        val systemSttSpy = CountingStt(listOf(SttEvent.Error(SttError.NoMatch)))
        val controller = VoiceFlowController(
            vm, remoteStt, FakeTextToSpeech(), settings, controllerScope,
            isMicGranted = { true },
            isChatConfigured = { true },
            systemStt = systemSttSpy,
            systemTts = FakeTextToSpeech(),
            isSttRemote = { true },
        )

        controller.start()

        // (a) exactly one remote-failure guidance, offering the consented system retry.
        val guidance = withTimeout(5_000) { controller.guidance.first { it != null } }!!
        assertEquals(VoiceGuidanceFactory.remoteEngineFailed(RemoteAudioError.Unavailable), guidance)
        assertTrue("remote failure must offer the consented system retry", guidance.canRetryWithSystem)
        assertFalse("guidance must never leak the key", guidance.message.contains(key))
        // (b) the system engine is NOT used until the user consents — no silent fallback.
        assertEquals("system STT must not run without consent", 0, systemSttSpy.starts)

        // (c) consenting re-runs the turn on the system engine.
        controller.retryWithSystem()
        val armed = withTimeout(5_000) { controller.state.first { it == VoiceFlowState.Idle } }
        assertEquals(VoiceFlowState.Idle, armed)
        assertEquals("system STT is armed only after the consented retry", 1, systemSttSpy.starts)
    }

    @Test
    fun tts_failure_renders_the_reply_but_skips_speaking() = runBlocking {
        configureChatProvider()
        settings.setTurnControl(TurnControl.SINGLE_TURN)
        server.enqueue(sseReply("Hello"))
        val tts = FakeTextToSpeech(TtsInitResult.Failed)
        val controller = controllerWith(CountingStt(listOf(SttEvent.EndOfSpeech, SttEvent.Final("hi"))), tts)

        controller.start()

        val done = withTimeout(5_000) {
            vm.state.first { !it.streaming && it.transcript.size == 2 && it.lastReply != null }
        }
        assertEquals("Hello", done.transcript[1].content) // reply still rendered

        val guidance = withTimeout(5_000) { controller.guidance.first { it != null } }
        assertEquals(VoiceGuidanceFactory.ttsUnavailable(), guidance)
        assertTrue("a failed TTS must not produce speech", tts.spoken.isEmpty())
    }
}
