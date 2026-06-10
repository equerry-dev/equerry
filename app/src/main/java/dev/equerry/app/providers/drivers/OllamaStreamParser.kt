package dev.equerry.app.providers.drivers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses Ollama's `/api/chat` newline-delimited JSON stream. Stateful: chunks arrive split at
 * arbitrary byte boundaries, so a partial trailing line is buffered until its newline rather than
 * parsed half-formed. Each complete line yields a [TokenResult] — `message.content` -> Delta,
 * `"done":true` -> Done. A truncated final line surfaced via [finish] maps to [ChatError.Malformed]
 * rather than crashing the Flow.
 */
class OllamaStreamParser {

    private val buffer = StringBuilder()

    /** Feed a raw chunk; returns a [TokenResult] for each complete line it completed. */
    fun feed(chunk: String): List<TokenResult> {
        buffer.append(chunk)
        val results = mutableListOf<TokenResult>()
        while (true) {
            val newline = buffer.indexOf("\n")
            if (newline < 0) break
            val line = buffer.substring(0, newline)
            buffer.delete(0, newline + 1)
            if (line.isNotBlank()) results.add(parseLine(line))
        }
        return results
    }

    /**
     * Signals end of stream. A non-empty leftover means the stream ended mid-line; it is parsed,
     * and if incomplete it maps to [ChatError.Malformed]. Returns null when nothing is buffered.
     */
    fun finish(): TokenResult? {
        val rest = buffer.toString().trim()
        buffer.clear()
        return if (rest.isEmpty()) null else parseLine(rest)
    }

    private fun parseLine(line: String): TokenResult =
        runCatching {
            val obj = ollamaJson.parseToJsonElement(line.trim()).jsonObject
            if (obj["done"]?.jsonPrimitive?.booleanOrNull == true) {
                TokenResult.Emit(ChatToken.Done)
            } else {
                val content = obj["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                if (content.isNullOrEmpty()) TokenResult.Skip else TokenResult.Emit(ChatToken.Delta(content))
            }
        }.getOrElse { TokenResult.Fail(ChatError.Malformed) }

    private companion object {
        val ollamaJson = Json { ignoreUnknownKeys = true }
    }
}
