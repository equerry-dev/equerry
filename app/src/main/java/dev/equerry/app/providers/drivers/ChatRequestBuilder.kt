package dev.equerry.app.providers.drivers

import dev.equerry.app.providers.ProviderType
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A built, provider-shaped chat request. The API key lives ONLY in [headers] (Authorization /
 * x-api-key) — never in [path] or [body] (r-03). [path] is relative to the profile's base URL.
 */
data class ChatHttpRequest(
    val path: String,
    val body: String,
    val headers: Map<String, String>,
)

/**
 * Turns a normalized message list + optional params into the JSON body, relative path, and auth
 * headers each provider type expects. Always sets streaming on (locked `reply_streaming`).
 * [type] is the resolved driver type (callers pass `profile.type.driverType`, so OpenRouter has
 * already collapsed to [ProviderType.OPENAI_COMPATIBLE]).
 */
object ChatRequestBuilder {

    private const val ANTHROPIC_VERSION = "2023-06-01"
    private const val ANTHROPIC_DEFAULT_MAX_TOKENS = 1024

    fun build(
        type: ProviderType,
        model: String,
        messages: List<ChatMessage>,
        key: String,
        temperature: Double? = null,
        maxTokens: Int? = null,
    ): ChatHttpRequest = when (type) {
        ProviderType.ANTHROPIC -> anthropic(model, messages, key, temperature, maxTokens)
        ProviderType.OLLAMA -> ollama(model, messages, temperature, maxTokens)
        // OPENAI_COMPATIBLE and (defensively) OPENROUTER — same wire shape.
        else -> openAi(model, messages, key, temperature, maxTokens)
    }

    private fun ChatRole.wire(): String = when (this) {
        ChatRole.SYSTEM -> "system"
        ChatRole.USER -> "user"
        ChatRole.ASSISTANT -> "assistant"
    }

    private fun openAi(
        model: String,
        messages: List<ChatMessage>,
        key: String,
        temperature: Double?,
        maxTokens: Int?,
    ): ChatHttpRequest {
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            putJsonArray("messages") {
                for (m in messages) addJsonObject {
                    put("role", m.role.wire())
                    put("content", m.content)
                }
            }
            temperature?.let { put("temperature", it) }
            maxTokens?.let { put("max_tokens", it) }
        }
        return ChatHttpRequest(
            path = "chat/completions",
            body = body.toString(),
            headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer $key",
            ),
        )
    }

    private fun anthropic(
        model: String,
        messages: List<ChatMessage>,
        key: String,
        temperature: Double?,
        maxTokens: Int?,
    ): ChatHttpRequest {
        // Anthropic takes the system prompt as a top-level field, not a message.
        val system = messages.filter { it.role == ChatRole.SYSTEM }.joinToString("\n") { it.content }
        val turns = messages.filter { it.role != ChatRole.SYSTEM }
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            // max_tokens is required by the Anthropic messages API — default when the user left it blank.
            put("max_tokens", maxTokens ?: ANTHROPIC_DEFAULT_MAX_TOKENS)
            if (system.isNotBlank()) put("system", system)
            putJsonArray("messages") {
                for (m in turns) addJsonObject {
                    put("role", m.role.wire())
                    put("content", m.content)
                }
            }
            temperature?.let { put("temperature", it) }
        }
        return ChatHttpRequest(
            path = "v1/messages",
            body = body.toString(),
            headers = mapOf(
                "Content-Type" to "application/json",
                "x-api-key" to key,
                "anthropic-version" to ANTHROPIC_VERSION,
            ),
        )
    }

    private fun ollama(
        model: String,
        messages: List<ChatMessage>,
        temperature: Double?,
        maxTokens: Int?,
    ): ChatHttpRequest {
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            putJsonArray("messages") {
                for (m in messages) addJsonObject {
                    put("role", m.role.wire())
                    put("content", m.content)
                }
            }
            if (temperature != null || maxTokens != null) {
                putJsonObject("options") {
                    temperature?.let { put("temperature", it) }
                    maxTokens?.let { put("num_predict", it) } // Ollama's name for the reply-length cap.
                }
            }
        }
        // Ollama is keyless — no auth header.
        return ChatHttpRequest(
            path = "api/chat",
            body = body.toString(),
            headers = mapOf("Content-Type" to "application/json"),
        )
    }
}
