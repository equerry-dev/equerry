package dev.equerry.app.providers.drivers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSessionTest {

    @Test
    fun request_carries_prior_turns_in_order_with_system_prompt_first() {
        val session = ChatSession()
        session.append(ChatMessage(ChatRole.USER, "hi"))
        session.append(ChatMessage(ChatRole.ASSISTANT, "hello"))

        val request = session.messagesForRequest("again", systemPrompt = "be brief")

        assertEquals(
            listOf(
                ChatMessage(ChatRole.SYSTEM, "be brief"),
                ChatMessage(ChatRole.USER, "hi"),
                ChatMessage(ChatRole.ASSISTANT, "hello"),
                ChatMessage(ChatRole.USER, "again"),
            ),
            request,
        )
    }

    @Test
    fun blank_system_prompt_is_not_prepended() {
        val session = ChatSession()
        val request = session.messagesForRequest("solo", systemPrompt = "  ")
        assertEquals(listOf(ChatMessage(ChatRole.USER, "solo")), request)
    }

    @Test
    fun new_chat_clears_history() {
        val session = ChatSession()
        session.append(ChatMessage(ChatRole.USER, "hi"))
        session.append(ChatMessage(ChatRole.ASSISTANT, "hello"))

        session.clear()

        assertTrue(session.turns.isEmpty())
        assertEquals(listOf(ChatMessage(ChatRole.USER, "fresh")), session.messagesForRequest("fresh"))
    }
}
