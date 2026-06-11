package dev.equerry.app.providers.drivers

/** Who authored a turn in a chat exchange. Drivers map provider-specific role strings to this. */
enum class ChatRole { SYSTEM, USER, ASSISTANT }

/**
 * An image attached to a chat turn for a multimodal (VISION) request: the raw bytes and their MIME
 * type (e.g. "image/png"). Drivers base64-encode this into each provider's image content shape. The
 * bytes are transient request payload — they are never persisted (screen-context decision
 * `screen_in_history` keeps only the textual representation in history).
 */
data class ChatImage(val bytes: ByteArray, val mimeType: String) {
    // ByteArray uses identity equality by default; compare by content so two equal images match.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatImage) return false
        return mimeType == other.mimeType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + mimeType.hashCode()
}

/**
 * One internal, provider-neutral chat turn. Drivers translate each provider's request/response
 * shape to/from this type so the rest of the app never sees a provider's wire format (spec §4).
 * An optional [image] turns the turn multimodal; when null the turn is plain text.
 */
data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val image: ChatImage? = null,
)
