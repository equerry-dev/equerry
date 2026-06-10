package dev.equerry.app.providers.drivers

import dev.equerry.app.providers.ProviderProfile
import dev.equerry.app.providers.ProviderType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatDriverTest {

    private lateinit var server: MockWebServer
    private val client = ChatHttpClient.create()
    private val key = "sk-secret-ABCDEF1234567890"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun profile(type: ProviderType, model: String = "m") =
        ProviderProfile("id", "L", type, server.url("/").toString(), model)

    private fun sse(vararg dataPayloads: String): MockResponse =
        MockResponse()
            .setHeader("Content-Type", "text/event-stream")
            .setBody(dataPayloads.joinToString("") { "data: $it\n\n" })

    private fun replyText(tokens: List<ChatToken>) =
        tokens.filterIsInstance<ChatToken.Delta>().joinToString("") { it.text }

    private val userTurn = listOf(ChatMessage(ChatRole.USER, "hi"))

    @Test
    fun openai_stream_concatenates_to_the_reply_and_keeps_key_in_header_not_path() = runBlocking {
        server.enqueue(
            sse(
                """{"choices":[{"delta":{"content":"Hello"}}]}""",
                """{"choices":[{"delta":{"content":", world"}}]}""",
                "[DONE]",
            ),
        )

        val tokens = OpenAiChatDriver(client).send(profile(ProviderType.OPENAI_COMPATIBLE), key, userTurn).toList()
        assertEquals("Hello, world", replyText(tokens))

        val recorded = server.takeRequest()
        assertEquals("Bearer $key", recorded.getHeader("Authorization"))
        assertFalse("key must not be in the path", recorded.path!!.contains(key))
        assertFalse("key must not be in the body", recorded.body.readUtf8().contains(key))
    }

    @Test
    fun anthropic_stream_concatenates_to_the_reply_with_key_in_x_api_key() = runBlocking {
        server.enqueue(
            sse(
                """{"type":"content_block_delta","delta":{"type":"text_delta","text":"Hel"}}""",
                """{"type":"content_block_delta","delta":{"type":"text_delta","text":"lo"}}""",
                """{"type":"message_stop"}""",
            ),
        )

        val tokens = AnthropicChatDriver(client).send(profile(ProviderType.ANTHROPIC), key, userTurn).toList()
        assertEquals("Hello", replyText(tokens))

        val recorded = server.takeRequest()
        assertEquals(key, recorded.getHeader("x-api-key"))
        assertFalse(recorded.path!!.contains(key))
    }

    @Test
    fun ollama_ndjson_stream_concatenates_to_the_reply_and_sends_no_auth_header() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {"message":{"content":"Hello"},"done":false}
                {"message":{"content":", world"},"done":false}
                {"done":true}
                """.trimIndent() + "\n",
            ),
        )

        val tokens = OllamaChatDriver(client).send(profile(ProviderType.OLLAMA, "llama3.2"), key = "", messages = userTurn).toList()
        assertEquals("Hello, world", replyText(tokens))

        val recorded = server.takeRequest()
        assertEquals(null, recorded.getHeader("Authorization"))
    }

    @Test
    fun a_401_surfaces_chat_error_auth() {
        server.enqueue(MockResponse().setResponseCode(401).setHeader("Content-Type", "text/event-stream"))

        val failure = runCatching {
            runBlocking { OpenAiChatDriver(client).send(profile(ProviderType.OPENAI_COMPATIBLE), key, userTurn).toList() }
        }.exceptionOrNull()

        assertTrue("expected a ChatException", failure is ChatException)
        assertEquals(ChatError.Auth, (failure as ChatException).error)
    }
}
