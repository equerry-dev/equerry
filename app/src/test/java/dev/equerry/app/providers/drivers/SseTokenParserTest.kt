package dev.equerry.app.providers.drivers

import org.junit.Assert.assertEquals
import org.junit.Test

class SseTokenParserTest {

    /** Concatenates the text of every Delta a sequence of data payloads decodes to. */
    private fun reply(payloads: List<String>, parse: (String) -> TokenResult): String =
        buildString {
            for (p in payloads) {
                val r = parse(p)
                if (r is TokenResult.Emit && r.token is ChatToken.Delta) append((r.token as ChatToken.Delta).text)
            }
        }

    @Test
    fun openai_deltas_concatenate_to_the_full_reply() {
        val payloads = listOf(
            """{"choices":[{"delta":{"role":"assistant"}}]}""",
            """{"choices":[{"delta":{"content":"Hello"}}]}""",
            """{"choices":[{"delta":{"content":", world"}}]}""",
        )
        assertEquals("Hello, world", reply(payloads, SseTokenParser::parseOpenAiData))
    }

    @Test
    fun openai_done_sentinel_yields_done_and_is_never_parsed_as_json() {
        assertEquals(TokenResult.Emit(ChatToken.Done), SseTokenParser.parseOpenAiData("[DONE]"))
    }

    @Test
    fun openai_malformed_line_maps_to_malformed_without_throwing() {
        assertEquals(TokenResult.Fail(ChatError.Malformed), SseTokenParser.parseOpenAiData("{not valid json"))
    }

    @Test
    fun anthropic_deltas_concatenate_to_the_full_reply() {
        val payloads = listOf(
            """{"type":"message_start"}""",
            """{"type":"content_block_delta","delta":{"type":"text_delta","text":"He"}}""",
            """{"type":"content_block_delta","delta":{"type":"text_delta","text":"llo"}}""",
        )
        assertEquals("Hello", reply(payloads, SseTokenParser::parseAnthropicData))
    }

    @Test
    fun anthropic_message_stop_yields_done() {
        assertEquals(
            TokenResult.Emit(ChatToken.Done),
            SseTokenParser.parseAnthropicData("""{"type":"message_stop"}"""),
        )
    }

    @Test
    fun anthropic_malformed_line_maps_to_malformed_without_throwing() {
        assertEquals(TokenResult.Fail(ChatError.Malformed), SseTokenParser.parseAnthropicData("}{"))
    }

    /** Collects every ChatToken a sequence of events decodes to via a stateful per-stream parser. */
    private fun tokens(events: List<String>, feed: (String) -> List<TokenResult>): List<ChatToken> =
        events.flatMap(feed).filterIsInstance<TokenResult.Emit>().map { it.token }

    @Test
    fun openai_tool_call_fragmented_across_events_assembles_one_toolcall() {
        val parser = OpenAiStreamParser()
        val events = listOf(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"set_timer","arguments":"{\"sec"}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"onds\":300}"}}]}}]}""",
            """{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""",
        )
        assertEquals(
            listOf(ChatToken.ToolCall("set_timer", """{"seconds":300}""", "call_1")),
            tokens(events, parser::feed),
        )
    }

    @Test
    fun openai_tool_call_args_truncated_at_finish_map_to_malformed() {
        val parser = OpenAiStreamParser()
        parser.feed("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"set_timer","arguments":"{\"seconds\":"}}]}}]}""")
        assertEquals(
            listOf(TokenResult.Fail(ChatError.Malformed)),
            parser.feed("""{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}"""),
        )
    }

    @Test
    fun stateless_parseOpenAiData_never_assembles_a_tool_call_on_its_own() {
        // The stateless function sees a tool_calls delta as nothing to emit — so an accumulator
        // threaded through the stream is required; feeding events to it alone yields no ToolCall.
        val r = SseTokenParser.parseOpenAiData(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"set_timer","arguments":"{}"}}]}}]}""",
        )
        assertEquals(TokenResult.Skip, r)
    }

    @Test
    fun anthropic_tool_use_sequence_assembles_one_toolcall() {
        val parser = AnthropicStreamParser()
        val events = listOf(
            """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_1","name":"set_alarm","input":{}}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\"hour\":7,"}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"\"minute\":30}"}}""",
            """{"type":"content_block_stop","index":0}""",
        )
        assertEquals(
            listOf(ChatToken.ToolCall("set_alarm", """{"hour":7,"minute":30}""", "toolu_1")),
            tokens(events, parser::feed),
        )
    }

    @Test
    fun anthropic_tool_use_with_truncated_json_maps_to_malformed() {
        val parser = AnthropicStreamParser()
        parser.feed("""{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"t1","name":"set_timer"}}""")
        parser.feed("""{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\"seconds\":"}}""")
        assertEquals(
            listOf(TokenResult.Fail(ChatError.Malformed)),
            parser.feed("""{"type":"content_block_stop","index":0}"""),
        )
    }
}
