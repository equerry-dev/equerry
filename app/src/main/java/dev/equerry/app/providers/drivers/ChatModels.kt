package dev.equerry.app.providers.drivers

/**
 * One unit of a streaming chat reply. Drivers expose replies as a Flow<ChatToken> (locked
 * decision `reply_streaming`): [Delta]s carry incremental text the UI appends live, [Done]
 * marks the end of the stream.
 */
sealed interface ChatToken {
    data class Delta(val text: String) : ChatToken
    data object Done : ChatToken
}

/**
 * A failure a chat request can end with, each carrying a UI-safe, human-readable [message].
 *
 * Privacy rule r-03: a ChatError must NEVER embed an API key. Messages are fixed templates that
 * interpolate only non-secret data (an HTTP status code); no case takes caller-supplied text that
 * could carry a key. If you ever need to add detail, redact it first — do not weaken this.
 */
sealed class ChatError {
    abstract val message: String

    /** The provider couldn't be reached at all (no connectivity, DNS, timeout, socket). */
    data object Network : ChatError() {
        override val message: String =
            "Couldn't reach the provider. Check your connection and the provider's base URL."
    }

    /** The provider rejected the credentials (HTTP 401/403). */
    data object Auth : ChatError() {
        override val message: String =
            "Authentication failed. Check the API key configured for this provider."
    }

    /** The provider returned a non-success status other than auth failure. */
    data class Http(val code: Int) : ChatError() {
        override val message: String = "The provider returned an error (HTTP $code)."
    }

    /** A reply (or stream chunk) arrived but couldn't be parsed into tokens. */
    data object Malformed : ChatError() {
        override val message: String =
            "The provider sent a response Equerry couldn't read."
    }
}
