package dev.equerry.app.providers.drivers

/** Who authored a turn in a chat exchange. Drivers map provider-specific role strings to this. */
enum class ChatRole { SYSTEM, USER, ASSISTANT }

/**
 * One internal, provider-neutral chat turn. Drivers translate each provider's request/response
 * shape to/from this type so the rest of the app never sees a provider's wire format (spec §4).
 */
data class ChatMessage(
    val role: ChatRole,
    val content: String,
)
