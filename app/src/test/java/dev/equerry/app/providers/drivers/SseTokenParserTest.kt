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
}
