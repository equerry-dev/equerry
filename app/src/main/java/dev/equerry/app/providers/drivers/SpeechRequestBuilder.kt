package dev.equerry.app.providers.drivers

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A built, provider-shaped remote text-to-speech (speech) request. The API key lives ONLY in
 * [headers] (Authorization) — never in [path] or [body] (r-03). [path] is relative to the
 * profile's base URL; [body] is the JSON payload and the response is the audio clip bytes.
 */
data class SpeechHttpRequest(
    val path: String,
    val body: String,
    val headers: Map<String, String>,
)

/**
 * Builds the OpenAI-compatible `audio/speech` request: a JSON body with the model, the input text,
 * the selected voice (omitted when blank, so the provider default applies — `tts_playback`), and a
 * broadly-decodable response_format of mp3. Mirrors [ChatRequestBuilder] — keys never leave the
 * Authorization header.
 */
object SpeechRequestBuilder {

    fun build(model: String, input: String, voice: String?, key: String): SpeechHttpRequest {
        val body = buildJsonObject {
            put("model", model)
            put("input", input)
            // Blank/null voice means "use the provider default" — omit the field entirely.
            voice?.takeIf { it.isNotBlank() }?.let { put("voice", it) }
            put("response_format", "mp3")
        }
        return SpeechHttpRequest(
            path = "audio/speech",
            body = body.toString(),
            headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer $key",
            ),
        )
    }
}
