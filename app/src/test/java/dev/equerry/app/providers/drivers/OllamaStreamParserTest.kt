package dev.equerry.app.providers.drivers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OllamaStreamParserTest {

    private fun deltaText(results: List<TokenResult>): String =
        buildString {
            for (r in results) if (r is TokenResult.Emit && r.token is ChatToken.Delta) {
                append((r.token as ChatToken.Delta).text)
            }
        }

    @Test
    fun multi_line_stream_concatenates_to_the_full_reply_and_ends_done() {
        val stream = """
            {"message":{"role":"assistant","content":"Hello"},"done":false}
            {"message":{"role":"assistant","content":", world"},"done":false}
            {"done":true}
        """.trimIndent() + "\n"

        val results = OllamaStreamParser().feed(stream)

        assertEquals("Hello, world", deltaText(results))
        assertEquals(TokenResult.Emit(ChatToken.Done), results.last())
    }

    @Test
    fun a_line_split_across_chunks_buffers_until_its_newline() {
        val parser = OllamaStreamParser()
        // First chunk ends mid-line: nothing emitted yet, must not crash.
        assertTrue(parser.feed("""{"message":{"content":"He""").isEmpty())
        // Completing the line emits the full delta.
        val results = parser.feed("""llo"},"done":false}""" + "\n")
        assertEquals("Hello", deltaText(results))
    }

    @Test
    fun a_truncated_final_line_maps_to_malformed_on_finish_without_crashing() {
        val parser = OllamaStreamParser()
        assertTrue(parser.feed("""{"message":{"content":"wor""").isEmpty())
        assertEquals(TokenResult.Fail(ChatError.Malformed), parser.finish())
    }

    @Test
    fun finish_with_nothing_buffered_returns_null() {
        assertEquals(null, OllamaStreamParser().finish())
    }
}
