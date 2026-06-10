package dev.equerry.app.providers.drivers

/**
 * Masks API keys out of arbitrary text before it can be logged or surfaced (r-03). Used by any
 * layer that holds the key while building a string that might escape (debug logs, diagnostics).
 * The OkHttp client redacts auth headers on its own (see [ChatHttpClient]); this covers everything
 * else that isn't a header.
 */
object KeyRedaction {

    /** What a redacted key is replaced with. */
    const val MASK: String = "██"

    /** Replaces every occurrence of a non-blank [key] in [text] with [MASK]. Blank/null key: no-op. */
    fun redact(text: String, key: String?): String =
        if (key.isNullOrBlank()) text else text.replace(key, MASK)
}
