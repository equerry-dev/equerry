package dev.equerry.app.voice

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.equerry.app.data.ProfileStore
import dev.equerry.app.data.SecretStore
import dev.equerry.app.data.SlotMappingStore
import dev.equerry.app.data.SpeakTiming
import dev.equerry.app.data.TurnControl
import dev.equerry.app.data.VoiceSettingsStore
import dev.equerry.app.providers.ProfileDraft
import dev.equerry.app.providers.ProviderRepository
import dev.equerry.app.providers.ProviderType
import dev.equerry.app.providers.drivers.AnthropicChatDriver
import dev.equerry.app.providers.drivers.ChatDriverFactory
import dev.equerry.app.providers.drivers.ChatHttpClient
import dev.equerry.app.providers.drivers.ChatSession
import dev.equerry.app.tools.actions.ActionRunner
import dev.equerry.app.tools.actions.PlannedAction
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
class VoiceFlowControllerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeSecretStore : SecretStore {
        private val keys = mutableMapOf<String, String>()
        override fun putKey(profileId: String, key: String) { keys[profileId] = key }
        override fun getKey(profileId: String): String? = keys[profileId]
        override fun removeKey(profileId: String) { keys.remove(profileId) }
    }

    /** Emits a scripted STT event sequence and then completes (mirrors the real flow ending). */
    private class FakeSpeechToText(private val events: List<SttEvent>) : SpeechToText {
        override fun listen(): Flow<SttEvent> = events.asFlow()
    }

    /** Returns a different scripted sequence per listen() call and logs each arm into [log]. */
    private class ScriptedSpeechToText(
        private val log: MutableList<String>,
        private val scripts: List<List<SttEvent>>,
    ) : SpeechToText {
        private var call = 0
        override fun listen(): Flow<SttEvent> {
            log.add("listen")
            return scripts.getOrElse(call++) { emptyList() }.asFlow()
        }
    }

    /** Records every utterance handed to TTS. */
    private class FakeTextToSpeech : TextToSpeech {
        val spoken = mutableListOf<String>()
        override suspend fun init() = TtsInitResult.Ready
        override fun speak(utterance: String) { spoken.add(utterance) }
        override suspend fun speakSentences(sentences: Flow<String>) = sentences.collect { spoken.add(it) }
        override suspend fun awaitDone() = Unit
        override fun stop() = Unit
        override fun shutdown() = Unit
    }

    /** Logs speak/awaitDone into a shared [log] so STT-arm ordering vs the prompt can be asserted. */
    private class RecordingTextToSpeech(private val log: MutableList<String>) : TextToSpeech {
        override suspend fun init() = TtsInitResult.Ready
        override fun speak(utterance: String) { log.add("speak:$utterance") }
        override suspend fun speakSentences(sentences: Flow<String>) = sentences.collect { log.add("speak:$it") }
        override suspend fun awaitDone() { log.add("awaitDone") }
        override fun stop() = Unit
        override fun shutdown() = Unit
    }

    private lateinit var dataScope: CoroutineScope
    private lateinit var controllerScope: CoroutineScope
    private lateinit var server: MockWebServer
    private lateinit var repository: ProviderRepository
    private lateinit var vm: ChatViewModel
    private lateinit var settings: VoiceSettingsStore
    private lateinit var actionRuns: MutableList<PlannedAction>

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
        actionRuns = mutableListOf()
        vm = ChatViewModel(repository, factory, ChatSession(), ActionRunner { actionRuns.add(it); true })
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

    private fun controllerWith(stt: SpeechToText, tts: TextToSpeech) =
        VoiceFlowController(
            chat = vm,
            stt = stt,
            tts = tts,
            settings = settings,
            scope = controllerScope,
            isMicGranted = { true },
        )

    @Test
    fun end_of_speech_auto_sends_and_speaks_the_whole_reply_once() = runBlocking {
        configureChatProvider()
        settings.setTurnControl(TurnControl.SINGLE_TURN) // one turn — continuous re-arm is t-8's concern
        server.enqueue(sseReply("Hel", "lo"))
        val tts = FakeTextToSpeech()
        // Realistic STT order: end-of-speech, then the final transcript (which ends the flow).
        val controller = controllerWith(
            FakeSpeechToText(listOf(SttEvent.EndOfSpeech, SttEvent.Final("what time is it"))),
            tts,
        )

        controller.start()

        // The transcript shows the spoken question and the streamed reply (auto-send fired).
        val done = withTimeout(5_000) {
            vm.state.first { !it.streaming && it.transcript.size == 2 && it.lastReply != null }
        }
        assertEquals("what time is it", done.transcript[0].content)
        assertEquals("Hello", done.transcript[1].content)

        // Whole reply spoken exactly once, after streaming completed.
        withTimeout(5_000) { controller.state.first { it == VoiceFlowState.Idle && tts.spoken.isNotEmpty() } }
        assertEquals(listOf("Hello"), tts.spoken)
    }

    @Test
    fun sentence_mode_queues_each_completed_sentence_to_tts() = runBlocking {
        configureChatProvider()
        settings.setTurnControl(TurnControl.SINGLE_TURN) // one turn — continuous re-arm is t-8's concern
        settings.setSpeakTiming(SpeakTiming.SENTENCE_BY_SENTENCE)
        server.enqueue(sseReply("Hello there. ", "How are you?"))
        val tts = FakeTextToSpeech()
        val controller = controllerWith(
            FakeSpeechToText(listOf(SttEvent.EndOfSpeech, SttEvent.Final("hi"))),
            tts,
        )

        controller.start()

        withTimeout(5_000) { vm.state.first { !it.streaming && it.transcript.size == 2 && it.lastReply != null } }
        withTimeout(5_000) { controller.state.first { it == VoiceFlowState.Idle && tts.spoken.size == 2 } }

        // Each completed sentence reached TTS, matching SpeakChunker's output (t-6).
        assertEquals(listOf("Hello there.", "How are you?"), tts.spoken)
    }

    /** An OpenAI tool-call SSE reply: one tool call (args whole), then finish + DONE. */
    private fun sseToolReply(name: String, argsEscaped: String): MockResponse =
        MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"$name","arguments":"$argsEscaped"}}]}}]}""" + "\n\n" +
                """data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""" + "\n\n" +
                "data: [DONE]\n\n",
        )

    private fun voiceConfirmController(log: MutableList<String>, question: String, answer: String) =
        VoiceFlowController(
            chat = vm,
            stt = ScriptedSpeechToText(log, listOf(listOf(SttEvent.Final(question)), listOf(SttEvent.Final(answer)))),
            tts = RecordingTextToSpeech(log),
            settings = settings,
            scope = controllerScope,
            isMicGranted = { true },
        )

    private suspend fun runOneTurnToIdle(controller: VoiceFlowController) {
        controller.start()
        // Turn started, then settled back to Idle (the single terminal Idle, after any spoken confirm).
        withTimeout(5_000) { controller.state.first { it != VoiceFlowState.Idle } }
        withTimeout(5_000) { controller.state.first { it == VoiceFlowState.Idle } }
    }

    @Test
    fun staged_timer_asks_start_now_then_arms_listen_and_yes_fires_once() = runBlocking {
        configureChatProvider()
        settings.setTurnControl(TurnControl.SINGLE_TURN)
        server.enqueue(sseToolReply("set_timer", """{\"seconds\":300}"""))
        val log = mutableListOf<String>()

        runOneTurnToIdle(voiceConfirmController(log, "set a 5 minute timer", "yes"))

        assertEquals("a spoken yes fires the staged action exactly once", 1, actionRuns.size)
        assertTrue(vm.state.value.pendingActions.isEmpty())
        // The confirm listen arms only AFTER the "Start now?" prompt finishes (not re-transcribed as yes).
        val askIdx = log.indexOf("speak:Start now?")
        assertTrue("the prompt must be spoken", askIdx >= 0)
        assertEquals("awaitDone", log[askIdx + 1])
        assertEquals("listen", log[askIdx + 2])
    }

    @Test
    fun staged_timer_no_discards_without_firing() = runBlocking {
        configureChatProvider()
        settings.setTurnControl(TurnControl.SINGLE_TURN)
        server.enqueue(sseToolReply("set_timer", """{\"seconds\":300}"""))
        val log = mutableListOf<String>()

        runOneTurnToIdle(voiceConfirmController(log, "set a timer", "no"))

        assertEquals("a spoken no must not fire the action", 0, actionRuns.size)
        assertTrue("a spoken no discards the staged action", vm.state.value.pendingActions.isEmpty())
    }

    @Test
    fun staged_timer_unrecognised_reply_leaves_it_staged_and_never_fires() = runBlocking {
        configureChatProvider()
        settings.setTurnControl(TurnControl.SINGLE_TURN)
        server.enqueue(sseToolReply("set_timer", """{\"seconds\":300}"""))
        val log = mutableListOf<String>()

        runOneTurnToIdle(voiceConfirmController(log, "set a timer", "what time is it"))

        assertEquals("an ambiguous reply must never fire the action", 0, actionRuns.size)
        assertEquals("the action stays staged as a tappable card", 1, vm.state.value.pendingActions.size)
    }

    @Test
    fun an_email_handoff_is_never_voice_confirmed() = runBlocking {
        configureChatProvider()
        settings.setTurnControl(TurnControl.SINGLE_TURN)
        server.enqueue(sseToolReply("draft_email", """{\"to\":\"a@b.com\"}"""))
        val log = mutableListOf<String>()

        runOneTurnToIdle(voiceConfirmController(log, "email a at b dot com", "yes"))

        // The hand-off launched directly (handoff_execution); it is never offered a spoken Start-now.
        assertFalse("email/SMS must never be voice-fired", log.contains("speak:Start now?"))
    }
}
