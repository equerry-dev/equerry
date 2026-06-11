package dev.equerry.app.providers.drivers

import dev.equerry.app.providers.ProviderType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRequestBuilderTest {

    private val key = "sk-secret-ABCDEF1234567890"

    private fun body(req: ChatHttpRequest) = Json.parseToJsonElement(req.body).jsonObject

    @Test
    fun openai_sets_streaming_and_carries_every_prior_turn() {
        val messages = listOf(
            ChatMessage(ChatRole.USER, "first"),
            ChatMessage(ChatRole.ASSISTANT, "reply one"),
            ChatMessage(ChatRole.USER, "second"),
        )
        val req = ChatRequestBuilder.build(ProviderType.OPENAI_COMPATIBLE, "gpt-4o", messages, key)
        val json = body(req)

        assertTrue("stream must be true", json["stream"]!!.jsonPrimitive.boolean)
        val sent = json["messages"]!!.jsonArray
        assertEquals(3, sent.size)
        assertEquals("first", sent[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("second", sent[2].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun optional_params_are_omitted_when_blank_and_present_when_set() {
        val messages = listOf(ChatMessage(ChatRole.USER, "hi"))

        val without = body(ChatRequestBuilder.build(ProviderType.OPENAI_COMPATIBLE, "gpt-4o", messages, key))
        assertFalse(without.containsKey("temperature"))
        assertFalse(without.containsKey("max_tokens"))

        val with = body(
            ChatRequestBuilder.build(
                ProviderType.OPENAI_COMPATIBLE, "gpt-4o", messages, key, temperature = 0.5, maxTokens = 100,
            ),
        )
        assertEquals(0.5, with["temperature"]!!.jsonPrimitive.double, 0.0001)
        assertEquals(100, with["max_tokens"]!!.jsonPrimitive.int)
    }

    @Test
    fun key_lives_in_headers_only_never_in_body_or_path() {
        val messages = listOf(ChatMessage(ChatRole.USER, "hi"))

        val openai = ChatRequestBuilder.build(ProviderType.OPENAI_COMPATIBLE, "gpt-4o", messages, key)
        assertEquals("Bearer $key", openai.headers["Authorization"])
        assertFalse(openai.body.contains(key))
        assertFalse(openai.path.contains(key))

        val anthropic = ChatRequestBuilder.build(ProviderType.ANTHROPIC, "claude-sonnet-4-6", messages, key)
        assertEquals(key, anthropic.headers["x-api-key"])
        assertFalse(anthropic.body.contains(key))
        assertFalse(anthropic.path.contains(key))
    }

    @Test
    fun anthropic_lifts_system_prompt_out_and_always_sets_max_tokens() {
        val messages = listOf(
            ChatMessage(ChatRole.SYSTEM, "be brief"),
            ChatMessage(ChatRole.USER, "hi"),
        )
        val req = ChatRequestBuilder.build(ProviderType.ANTHROPIC, "claude-sonnet-4-6", messages, key)
        val json = body(req)

        assertEquals("be brief", json["system"]!!.jsonPrimitive.content)
        // System is not also a message; only the user turn remains.
        assertEquals(1, json["messages"]!!.jsonArray.size)
        // Required by the Anthropic API even though the user left it blank.
        assertEquals(1024, json["max_tokens"]!!.jsonPrimitive.int)
        assertEquals("2023-06-01", req.headers["anthropic-version"])
    }

    @Test
    fun ollama_is_keyless_and_nests_params_under_options() {
        val messages = listOf(ChatMessage(ChatRole.USER, "hi"))
        val req = ChatRequestBuilder.build(
            ProviderType.OLLAMA, "llama3.2", messages, key = "", temperature = 0.3, maxTokens = 50,
        )
        val json = body(req)

        assertFalse("Ollama must not carry an auth header", req.headers.containsKey("Authorization"))
        assertFalse(req.headers.containsKey("x-api-key"))
        val options = json["options"]!!.jsonObject
        assertEquals(0.3, options["temperature"]!!.jsonPrimitive.double, 0.0001)
        assertEquals(50, options["num_predict"]!!.jsonPrimitive.int)
    }

    private val toolNames = listOf("set_timer", "set_alarm", "create_calendar_event", "draft_email", "draft_sms")

    @Test
    fun openai_body_carries_all_five_tools_as_functions() {
        val req = ChatRequestBuilder.build(ProviderType.OPENAI_COMPATIBLE, "gpt-4o", listOf(ChatMessage(ChatRole.USER, "hi")), key)
        val tools = body(req)["tools"]!!.jsonArray

        assertEquals(5, tools.size)
        assertTrue("every OpenAI tool is type:function", tools.all { it.jsonObject["type"]!!.jsonPrimitive.content == "function" })
        assertEquals(toolNames, tools.map { it.jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content })
    }

    @Test
    fun anthropic_body_carries_all_five_tools_with_input_schema() {
        val req = ChatRequestBuilder.build(ProviderType.ANTHROPIC, "claude-sonnet-4-6", listOf(ChatMessage(ChatRole.USER, "hi")), key)
        val tools = body(req)["tools"]!!.jsonArray

        assertEquals(5, tools.size)
        assertEquals(toolNames, tools.map { it.jsonObject["name"]!!.jsonPrimitive.content })
        assertTrue("every Anthropic tool carries input_schema", tools.all { it.jsonObject.containsKey("input_schema") })
    }

    @Test
    fun ollama_body_carries_no_tools_array() {
        val req = ChatRequestBuilder.build(ProviderType.OLLAMA, "llama3.2", listOf(ChatMessage(ChatRole.USER, "hi")), key = "")
        assertFalse("Ollama is judged tool-incapable; no tools array", body(req).containsKey("tools"))
    }

    @Test
    fun tools_payload_never_embeds_the_api_key() {
        val openai = ChatRequestBuilder.build(ProviderType.OPENAI_COMPATIBLE, "gpt-4o", listOf(ChatMessage(ChatRole.USER, "hi")), key)
        val anthropic = ChatRequestBuilder.build(ProviderType.ANTHROPIC, "claude-sonnet-4-6", listOf(ChatMessage(ChatRole.USER, "hi")), key)
        assertFalse(openai.body.contains(key))
        assertFalse(anthropic.body.contains(key))
    }
}
