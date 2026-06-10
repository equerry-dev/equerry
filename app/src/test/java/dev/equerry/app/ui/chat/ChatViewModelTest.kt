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

    private lateinit var scope: CoroutineScope
    private lateinit var server: MockWebServer
    private lateinit var repository: ProviderRepository
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
        vm = ChatViewModel(repository, factory, dev.equerry.app.providers.drivers.ChatSession())
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
    fun a_failed_request_renders_a_keyless_error() = runBlocking {
        configureChatProvider()
        server.enqueue(MockResponse().setResponseCode(401).setHeader("Content-Type", "text/event-stream"))

        vm.onInputChange("hi")
        vm.send()

        val errored = awaitState { it.error != null }
        assertEquals(ChatError.Auth.message, errored.error)
        assertFalse("error must never leak the key", errored.error!!.contains(key))
    }
}
