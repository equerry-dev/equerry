package dev.equerry.app.providers.drivers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatModelsTest {

    @Test
    fun chat_role_carries_the_three_expected_authors() {
        assertEquals(
            listOf("SYSTEM", "USER", "ASSISTANT"),
            ChatRole.entries.map { it.name },
        )
    }

    @Test
    fun chat_message_round_trips_role_and_content() {
        val msg = ChatMessage(ChatRole.USER, "hello")
        assertEquals(ChatRole.USER, msg.role)
        assertEquals("hello", msg.content)
    }

    @Test
    fun chat_token_distinguishes_delta_from_done() {
        val token: ChatToken = ChatToken.Delta("hi")
        // Exhaustive when over the sealed hierarchy: dropping a case fails to compile.
        val text: String = when (token) {
            is ChatToken.Delta -> token.text
            ChatToken.Done -> ""
        }
        assertEquals("hi", text)
        assertEquals("", run { val d: ChatToken = ChatToken.Done; if (d is ChatToken.Delta) d.text else "" })
    }

    @Test
    fun http_error_message_includes_the_status_code() {
        assertTrue(ChatError.Http(503).message.contains("503"))
    }

    @Test
    fun every_chat_error_case_exposes_a_non_blank_message() {
        // Exhaustive when: dropping a ChatError case fails to compile here.
        val errors = listOf(
            ChatError.Network,
            ChatError.Auth,
            ChatError.Http(500),
            ChatError.Malformed,
        )
        for (e in errors) {
            val msg = when (e) {
                ChatError.Network -> e.message
                ChatError.Auth -> e.message
                is ChatError.Http -> e.message
                ChatError.Malformed -> e.message
            }
            assertTrue("$e must expose a non-blank message", msg.isNotBlank())
        }
    }

    @Test
    fun errorMessageNeverContainsKey() {
        val key = "sk-secret-ABCDEF1234567890"
        val errors = listOf(
            ChatError.Network,
            ChatError.Auth,
            ChatError.Http(401),
            ChatError.Malformed,
        )
        for (e in errors) {
            assertFalse(
                "${e::class.simpleName}.message must never contain an API key",
                e.message.contains(key),
            )
        }
    }
}
