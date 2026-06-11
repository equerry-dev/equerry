package dev.equerry.app.ui.chat

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.equerry.app.data.ProfileStore
import dev.equerry.app.data.SecretStore
import dev.equerry.app.data.SlotMappingStore
import dev.equerry.app.providers.ProfileDraft
import dev.equerry.app.providers.ProviderProfile
import dev.equerry.app.providers.ProviderRepository
import dev.equerry.app.providers.ProviderType
import dev.equerry.app.providers.drivers.AnthropicChatDriver
import dev.equerry.app.providers.drivers.ChatDriverFactory
import dev.equerry.app.providers.drivers.ChatError
import dev.equerry.app.providers.drivers.ChatHttpClient
import dev.equerry.app.providers.drivers.ChatRole
import dev.equerry.app.providers.drivers.OllamaChatDriver
import dev.equerry.app.providers.drivers.OpenAiChatDriver
import dev.equerry.app.tools.actions.ActionRunner
import dev.equerry.app.tools.actions.PlannedAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
class ChatViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeSecretStore : SecretStore {
        private val keys = mutableMapOf<String, String>()
        override fun putKey(profileId: String, key: String) { keys[profileId] = key }
        override fun getKey(profileId: String): String? = keys[profileId]
        override fun removeKey(profileId: String) { keys.remove(profileId) }
    }

    /** Records what was launched without touching the Android activity stack. */
    private class FakeActionRunner : ActionRunner {
        val ran = mutableListOf<PlannedAction>()
        var launches = true
        override fun run(action: PlannedAction): Boolean { ran += action; return launches }
    }

    private lateinit var scope: CoroutineScope
    private lateinit var server: MockWebServer
    private lateinit var repository: ProviderRepository
    private lateinit var runner: FakeActionRunner
    private lateinit var vm: ChatViewModel

    private val key = "sk-secret-ABCDEF1234567890"

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        scope = CoroutineScope(Dispatchers.IO + Job())
        server = MockWebServer()
        server.start()
        val file = File(tmp.newFolder(), "s.preferences_pb")
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }
        repository = ProviderRepository(ProfileStore(ds), FakeSecretStore(), SlotMappingStore(ds))
        val client = ChatHttpClient.create()
        val factory = ChatDriverFactory(OpenAiChatDriver(client), AnthropicChatDriver(client), OllamaChatDriver(client))
        runner = FakeActionRunner()
        vm = ChatViewModel(repository, factory, dev.equerry.app.providers.drivers.ChatSession(), runner)
    }

    @After
    fun tearDown() {
        server.shutdown()
        runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        Dispatchers.resetMain()
    }

    private suspend fun configureChatProvider(): ProviderProfile {
        val created = repository.addProfile(
            ProfileDraft("Mock", ProviderType.OPENAI_COMPATIBLE, server.url("/").toString(), key, "m"),
        )
        repository.setChatSlot(created.id)
        return created
    }

    private fun sseReply(vararg deltas: String): MockResponse =
        MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
            buildString {
                deltas.forEach { append("""data: {"choices":[{"delta":{"content":"$it"}}]}""").append("\n\n") }
                append("data: [DONE]\n\n")
            },
        )

    private suspend fun awaitState(predicate: (ChatUiState) -> Boolean) =
        withTimeout(5_000) { vm.state.first(predicate) }

    @Test
    fun live_tokens_append_to_the_growing_assistant_bubble() = runBlocking {
        configureChatProvider()
        server.enqueue(sseReply("Hel", "lo"))

        vm.onInputChange("hi")
        vm.send()

        val done = awaitState { !it.streaming && it.transcript.size == 2 && it.error == null }
        assertEquals(ChatRole.USER, done.transcript[0].role)
        assertEquals("hi", done.transcript[0].content)
        assertEquals(ChatRole.ASSISTANT, done.transcript[1].role)
        assertEquals("Hello", done.transcript[1].content)
    }

    @Test
    fun unmapped_send_sets_guidance_and_never_calls_the_driver() = runBlocking {
        // No CHAT slot configured.
        vm.onInputChange("hi")
        vm.send()

        awaitState { it.unmapped }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun second_send_carries_the_first_turn_as_context() = runBlocking {
        configureChatProvider()
        server.enqueue(sseReply("firstreply"))
        server.enqueue(sseReply("secondreply"))

        vm.onInputChange("firstq"); vm.send()
        awaitState { !it.streaming && it.transcript.size == 2 }

        vm.onInputChange("secondq"); vm.send()
        awaitState { !it.streaming && it.transcript.size == 4 }

        server.takeRequest() // first
        val secondBody = server.takeRequest().body.readUtf8()
        assertTrue("prior user turn must be carried", secondBody.contains("firstq"))
        assertTrue("prior assistant turn must be carried", secondBody.contains("firstreply"))
        assertTrue(secondBody.contains("secondq"))
    }

    @Test
    fun new_chat_clears_transcript_and_session_context() = runBlocking {
        configureChatProvider()
        server.enqueue(sseReply("firstreply"))
        server.enqueue(sseReply("secondreply"))

        vm.onInputChange("firstq"); vm.send()
        awaitState { !it.streaming && it.transcript.size == 2 }

        vm.newChat()
        assertTrue(vm.state.value.transcript.isEmpty())

        vm.onInputChange("secondq"); vm.send()
        awaitState { !it.streaming && it.transcript.size == 2 }

        server.takeRequest() // first
        val afterReset = server.takeRequest().body.readUtf8()
        assertTrue(afterReset.contains("secondq"))
        assertFalse("cleared session must not resend the first turn", afterReset.contains("firstq"))
    }

    @Test
    fun programmatic_send_text_matches_the_typed_path_and_exposes_the_final_reply() = runBlocking {
        configureChatProvider()
        server.enqueue(sseReply("Hel", "lo"))

        // Voice path: send text directly, with no onInputChange typed into the box.
        vm.send("hi")

        val done = awaitState { !it.streaming && it.transcript.size == 2 && it.error == null }
        assertEquals(ChatRole.USER, done.transcript[0].role)
        assertEquals("hi", done.transcript[0].content)
        assertEquals(ChatRole.ASSISTANT, done.transcript[1].role)
        assertEquals("Hello", done.transcript[1].content)
        // The completed reply is exposed for whole-reply TTS (t-7).
        assertEquals("Hello", done.lastReply)
    }

    @Test
    fun programmatic_send_text_with_no_mapping_guides_and_never_calls_the_driver() = runBlocking {
        // No CHAT slot configured.
        vm.send("hi")

        awaitState { it.unmapped }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun a_failed_request_renders_a_keyless_error() = runBlocking {
        configureChatProvider()
        server.enqueue(MockResponse().setResponseCode(401).setHeader("Content-Type", "text/event-stream"))

        vm.onInputChange("hi")
        vm.send()

        val errored = awaitState { it.error != null }
        assertEquals(ChatError.Auth.message, errored.error)
        assertFalse("error must never leak the key", errored.error!!.contains(key))
    }

    private suspend fun configureOllamaProvider(): ProviderProfile {
        val created = repository.addProfile(
            ProfileDraft("Local", ProviderType.OLLAMA, server.url("/").toString(), "", "llama3.2"),
        )
        repository.setChatSlot(created.id)
        return created
    }

    /** An OpenAI tool-call SSE reply: one tool call (args delivered whole), then finish + DONE. */
    private fun sseToolReply(name: String, argsEscaped: String, id: String = "call_1"): MockResponse =
        MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"$id","function":{"name":"$name","arguments":"$argsEscaped"}}]}}]}""" + "\n\n" +
                """data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""" + "\n\n" +
                "data: [DONE]\n\n",
        )

    /** An OpenAI SSE reply carrying two tool calls (indexes 0 and 1) then finish + DONE. */
    private fun sseTwoToolReply(): MockResponse =
        MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c0","function":{"name":"set_timer","arguments":"{\"seconds\":60}"}}]}}]}""" + "\n\n" +
                """data: {"choices":[{"delta":{"tool_calls":[{"index":1,"id":"c1","function":{"name":"set_alarm","arguments":"{\"hour\":7,\"minute\":30}"}}]}}]}""" + "\n\n" +
                """data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""" + "\n\n" +
                "data: [DONE]\n\n",
        )

    private fun ndjsonReply(text: String): MockResponse =
        MockResponse().setHeader("Content-Type", "application/x-ndjson").setBody(
            """{"message":{"role":"assistant","content":"$text"},"done":false}""" + "\n" + """{"done":true}""" + "\n",
        )

    @Test
    fun a_single_timer_tool_call_is_staged_and_not_fired() = runBlocking {
        configureChatProvider()
        server.enqueue(sseToolReply("set_timer", """{\"seconds\":300}"""))

        vm.send("set a 5 minute timer")

        val s = awaitState { !it.streaming && it.pendingActions.isNotEmpty() }
        assertEquals(1, s.pendingActions.size)
        assertTrue("a tool call must produce an action, not prose", s.pendingActions[0] is PlannedAction.Staged.Timer)
        assertTrue("a staged timer must not auto-fire", runner.ran.isEmpty())
    }

    @Test
    fun confirming_a_staged_timer_runs_it_once_with_a_note_that_never_says_sent() = runBlocking {
        configureChatProvider()
        server.enqueue(sseToolReply("set_timer", """{\"seconds\":300}"""))

        vm.send("set a 5 minute timer")
        awaitState { it.pendingActions.isNotEmpty() }
        vm.confirmAction(0)

        val s = vm.state.value
        assertEquals(1, runner.ran.size)
        assertTrue(s.pendingActions.isEmpty())
        val note = s.actionNotes.last()
        assertTrue("note describes the timer", note.contains("Timer"))
        assertFalse("note must never claim a send", note.contains("sent"))
    }

    @Test
    fun cancelling_a_staged_action_discards_it_without_running() = runBlocking {
        configureChatProvider()
        server.enqueue(sseToolReply("set_timer", """{\"seconds\":300}"""))

        vm.send("set a timer")
        awaitState { it.pendingActions.isNotEmpty() }
        vm.cancelAction(0)

        assertTrue(vm.state.value.pendingActions.isEmpty())
        assertTrue("cancel must not run the action", runner.ran.isEmpty())
    }

    @Test
    fun a_single_handoff_launches_directly_with_a_note() = runBlocking {
        configureChatProvider()
        server.enqueue(sseToolReply("draft_sms", """{\"to\":\"Mum\",\"body\":\"omw\"}"""))

        vm.send("text mum omw")

        val s = awaitState { !it.streaming && it.actionNotes.isNotEmpty() }
        assertEquals("single hand-off launches directly", 1, runner.ran.size)
        assertTrue(s.pendingActions.isEmpty())
        assertTrue(s.actionNotes.last().contains("Opened"))
        assertFalse(s.actionNotes.last().contains("sent"))
    }

    @Test
    fun two_tool_calls_make_a_two_entry_pending_list_each_independently_runnable() = runBlocking {
        configureChatProvider()
        server.enqueue(sseTwoToolReply())

        vm.send("set a timer and an alarm")
        awaitState { !it.streaming && it.pendingActions.size == 2 }

        // Skip entry 0; entry 1 stays runnable.
        vm.cancelAction(0)
        assertEquals(1, vm.state.value.pendingActions.size)
        vm.confirmAction(0)
        assertEquals(1, runner.ran.size)
        assertTrue(vm.state.value.pendingActions.isEmpty())
    }

    @Test
    fun a_malformed_tool_call_posts_guidance_without_crashing() = runBlocking {
        configureChatProvider()
        // Valid JSON args but the required 'seconds' is missing -> planner Malformed.
        server.enqueue(sseToolReply("set_timer", """{\"label\":\"tea\"}"""))

        vm.send("set a timer")

        val s = awaitState { !it.streaming && it.actionGuidance != null }
        assertTrue(s.actionGuidance != null)
        assertTrue("malformed call drops no pending action and never crashes", s.pendingActions.isEmpty())
    }

    @Test
    fun sending_with_a_tool_incapable_provider_sets_the_banner_flag() = runBlocking {
        configureOllamaProvider()
        server.enqueue(ndjsonReply("hi"))

        vm.send("hello")

        assertTrue(awaitState { it.toolsUnsupported }.toolsUnsupported)
    }
}
