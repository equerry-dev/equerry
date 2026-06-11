package dev.equerry.app.providers.drivers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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

/**
 * Per-stream stateful assembler for the OpenAI / OpenRouter chat-completions stream. Text deltas and
 * the `[DONE]` sentinel pass straight through like the stateless [SseTokenParser]; `tool_calls`
 * argument fragments — which OpenAI splits across many SSE events keyed by `index` — are accumulated
 * here and emitted as one [ChatToken.ToolCall] per index when `finish_reason` is `tool_calls`. One
 * instance per request (drivers construct a fresh one), since the accumulation is per-stream.
 */
class OpenAiStreamParser {

    private data class Acc(var name: String = "", val args: StringBuilder = StringBuilder(), var id: String? = null)

    private val calls = LinkedHashMap<Int, Acc>()

    fun feed(data: String): List<TokenResult> {
        val trimmed = data.trim()
        if (trimmed == "[DONE]") return listOf(TokenResult.Emit(ChatToken.Done))
        return runCatching {
            val choice = sseJson.parseToJsonElement(trimmed)
                .jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: return@runCatching listOf(TokenResult.Skip)
            val delta = choice["delta"]?.jsonObject
            val toolCalls = delta?.get("tool_calls")?.jsonArray
            if (toolCalls != null) {
                for (tc in toolCalls) {
                    val o = tc.jsonObject
                    val idx = o["index"]?.jsonPrimitive?.intOrNull ?: 0
                    val acc = calls.getOrPut(idx) { Acc() }
                    o["id"]?.jsonPrimitive?.contentOrNull?.let { acc.id = it }
                    val fn = o["function"]?.jsonObject
                    fn?.get("name")?.jsonPrimitive?.contentOrNull?.let { acc.name = it }
                    fn?.get("arguments")?.jsonPrimitive?.contentOrNull?.let { acc.args.append(it) }
                }
                return@runCatching listOf(TokenResult.Skip)
            }
            val finish = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
            if (finish == "tool_calls" && calls.isNotEmpty()) {
                val assembled = calls.values.map { Triple(it.name, it.args.toString().ifEmpty { "{}" }, it.id) }
                calls.clear()
                return@runCatching assembleToolCalls(assembled)
            }
            val content = delta?.get("content")?.jsonPrimitive?.contentOrNull
            if (content.isNullOrEmpty()) listOf(TokenResult.Skip) else listOf(TokenResult.Emit(ChatToken.Delta(content)))
        }.getOrElse { listOf(TokenResult.Fail(ChatError.Malformed)) }
    }
}

/**
 * Per-stream stateful assembler for the Anthropic messages stream. Text deltas and `message_stop`
 * pass through; a `content_block_start` of type `tool_use` opens a tool block whose `input_json_delta`
 * fragments accumulate, and `content_block_stop` closes it into one [ChatToken.ToolCall]. One instance
 * per request.
 */
class AnthropicStreamParser {

    private var toolName: String? = null
    private var toolId: String? = null
    private val toolArgs = StringBuilder()

    fun feed(data: String): List<TokenResult> = runCatching {
        val obj = sseJson.parseToJsonElement(data.trim()).jsonObject
        when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            "content_block_start" -> {
                val block = obj["content_block"]?.jsonObject
                if (block?.get("type")?.jsonPrimitive?.contentOrNull == "tool_use") {
                    toolName = block["name"]?.jsonPrimitive?.contentOrNull
                    toolId = block["id"]?.jsonPrimitive?.contentOrNull
                    toolArgs.setLength(0)
                }
                listOf(TokenResult.Skip)
            }
            "content_block_delta" -> {
                val delta = obj["delta"]?.jsonObject
                when (delta?.get("type")?.jsonPrimitive?.contentOrNull) {
                    "text_delta" -> {
                        val text = delta["text"]?.jsonPrimitive?.contentOrNull
                        if (text.isNullOrEmpty()) listOf(TokenResult.Skip) else listOf(TokenResult.Emit(ChatToken.Delta(text)))
                    }
                    "input_json_delta" -> {
                        delta["partial_json"]?.jsonPrimitive?.contentOrNull?.let { toolArgs.append(it) }
                        listOf(TokenResult.Skip)
                    }
                    else -> listOf(TokenResult.Skip)
                }
            }
            "content_block_stop" -> {
                val name = toolName
                if (name != null) {
                    val raw = toolArgs.toString().ifEmpty { "{}" }
                    val id = toolId
                    toolName = null
                    toolId = null
                    toolArgs.setLength(0)
                    assembleToolCalls(listOf(Triple(name, raw, id)))
                } else {
                    listOf(TokenResult.Skip)
                }
            }
            "message_stop" -> listOf(TokenResult.Emit(ChatToken.Done))
            else -> listOf(TokenResult.Skip)
        }
    }.getOrElse { listOf(TokenResult.Fail(ChatError.Malformed)) }
}

/**
 * Validates each assembled `(name, argsJson, id)` parses as JSON and turns it into a ToolCall Emit.
 * If any args string is non-JSON / truncated, the whole batch maps to a single
 * [ChatError.Malformed] — never a half-parsed ToolCall (feeds c-5).
 */
private fun assembleToolCalls(calls: List<Triple<String, String, String?>>): List<TokenResult> {
    if (calls.any { runCatching { sseJson.parseToJsonElement(it.second) }.isFailure }) {
        return listOf(TokenResult.Fail(ChatError.Malformed))
    }
    return calls.map { TokenResult.Emit(ChatToken.ToolCall(it.first, it.second, it.third)) }
}
