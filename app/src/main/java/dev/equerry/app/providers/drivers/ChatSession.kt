package dev.equerry.app.providers.drivers

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The live conversation for the chat screen. A process-wide singleton, so the thread survives
 * in-app navigation away from and back to the screen; [clear] (the "New chat" action) resets it.
 * There is NO persistence — history is lost on process death (locked decision `session_boundary`).
 */
@Singleton
class ChatSession @Inject constructor() {

    private val history = mutableListOf<ChatMessage>()

    /** The accumulated turns so far, oldest first. */
    val turns: List<ChatMessage> get() = history.toList()

    /** Record a completed turn (user message or assistant reply) onto the thread. */
    fun append(message: ChatMessage) {
        history.add(message)
    }

    /**
     * Builds the message list to send for [userMessage]: an optional [systemPrompt] first (when
     * non-blank), then every prior turn in order, then the new user message. Does not mutate the
     * thread — callers [append] the user turn (and later the assistant reply) themselves.
     */
    fun messagesForRequest(userMessage: String, systemPrompt: String? = null): List<ChatMessage> =
        buildList {
            if (!systemPrompt.isNullOrBlank()) add(ChatMessage(ChatRole.SYSTEM, systemPrompt))
            addAll(history)
            add(ChatMessage(ChatRole.USER, userMessage))
        }

    /** "New chat": drop the entire thread. */
    fun clear() {
        history.clear()
    }
}
