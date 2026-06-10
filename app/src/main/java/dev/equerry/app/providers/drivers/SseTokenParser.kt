package dev.equerry.app.providers.drivers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Outcome of parsing one streamed chunk. [Emit] forwards a [ChatToken] (a [ChatToken.Delta] or
 * [ChatToken.Done]); [Skip] is a chunk with nothing to forward (a role-only delta, a keep-alive,
 * an unrecognised event); [Fail] carries the [ChatError] a malformed chunk maps to. Parsers return
 * this instead of throwing, so a bad line never crashes the reply Flow.
 */
sealed interface TokenResult {
    data class Emit(val token: ChatToken) : TokenResult
    data object Skip : TokenResult
    data class Fail(val error: ChatError) : TokenResult
}

private val sseJson = Json { ignoreUnknownKeys = true }

/**
 * Decodes a single Server-Sent-Events `data:` payload into a [TokenResult]. Stateless: OkHttp's
 * EventSource delivers one complete event at a time. Covers OpenAI/OpenRouter (`choices[].delta`)
 * and Anthropic (`content_block_delta` / `message_stop`).
 */
object SseTokenParser {

    /** OpenAI / OpenRouter chat-completions stream. The literal `[DONE]` sentinel ends the stream. */
    fun parseOpenAiData(data: String): TokenResult {
        val trimmed = data.trim()
        if (trimmed == "[DONE]") return TokenResult.Emit(ChatToken.Done)
        return runCatching {
            val content = sseJson.parseToJsonElement(trimmed)
                .jsonObject["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("delta")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.contentOrNull
            if (content.isNullOrEmpty()) TokenResult.Skip else TokenResult.Emit(ChatToken.Delta(content))
        }.getOrElse { TokenResult.Fail(ChatError.Malformed) }
    }

    /** Anthropic messages stream: typed events carry the text on `content_block_delta`. */
    fun parseAnthropicData(data: String): TokenResult =
        runCatching {
            val obj = sseJson.parseToJsonElement(data.trim()).jsonObject
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "content_block_delta" -> {
                    val text = obj["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                    if (text.isNullOrEmpty()) TokenResult.Skip else TokenResult.Emit(ChatToken.Delta(text))
                }
                "message_stop" -> TokenResult.Emit(ChatToken.Done)
                else -> TokenResult.Skip
            }
        }.getOrElse { TokenResult.Fail(ChatError.Malformed) }
}
