package dev.equerry.app.providers.drivers

/**
 * A built, provider-shaped remote speech-to-text (transcribe) request. The API key lives ONLY in
 * [headers] (Authorization) — never in [path] or [formFields] (r-03). [path] is relative to the
 * profile's base URL. The audio clip itself is NOT carried here: the transport (RemoteSpeechToText)
 * appends the binary `file` part at send time, so this builder stays pure and binary-free.
 */
data class TranscribeHttpRequest(
    val path: String,
    val formFields: Map<String, String>,
    val headers: Map<String, String>,
)

/**
 * Builds the OpenAI-compatible `audio/transcriptions` request shape: the multipart text fields
 * (model + JSON response format) and the auth header. Mirrors [ChatRequestBuilder] — keys never
 * leave the Authorization header. Scoped to the OPENAI_COMPATIBLE-flavoured transcribe API
 * (`provider_config_capability_flags`); self-hosted whisper.cpp speaks the same shape.
 */
object TranscribeRequestBuilder {

    fun build(model: String, key: String): TranscribeHttpRequest =
        TranscribeHttpRequest(
            path = "audio/transcriptions",
            // response_format=json yields a parseable {"text": "..."} body for the transport.
            formFields = mapOf("model" to model, "response_format" to "json"),
            headers = mapOf("Authorization" to "Bearer $key"),
        )
}
