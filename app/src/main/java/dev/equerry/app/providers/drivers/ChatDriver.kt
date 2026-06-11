package dev.equerry.app.providers.drivers

import dev.equerry.app.providers.ProviderProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import javax.inject.Inject

/**
 * Thrown into a reply [Flow] to carry a [ChatError] to the collector. Collect with `.catch { }`
 * and render [ChatError.message] (it is always key-free, per r-03).
 */
class ChatException(val error: ChatError) : Exception(error.message)

/** Streams one chat reply for [profile] as incremental [ChatToken]s; fails the Flow with [ChatException]. */
interface ChatDriver {
    fun send(profile: ProviderProfile, key: String, messages: List<ChatMessage>): Flow<ChatToken>
}

private val JSON_MEDIA = "application/json".toMediaType()

/** Builds the OkHttp request from the per-type [ChatRequestBuilder] output. Key lives in headers only. */
private fun buildRequest(profile: ProviderProfile, key: String, messages: List<ChatMessage>): Request {
    val built = ChatRequestBuilder.build(
        type = profile.type.driverType,
        model = profile.model,
        messages = messages,
        key = key,
        temperature = profile.temperature,
        maxTokens = profile.maxTokens,
    )
    val url = profile.baseUrl.trimEnd('/') + "/" + built.path
    val builder = Request.Builder().url(url).post(built.body.toRequestBody(JSON_MEDIA))
    // Content-Type comes from the request body's media type; the rest (auth) are headers.
    built.headers.forEach { (name, value) -> if (!name.equals("Content-Type", ignoreCase = true)) builder.header(name, value) }
    return builder.build()
}

/** Shared SSE transport: drives an okhttp-sse EventSource and decodes each event with [parse]. */
private fun sseChatFlow(
    client: OkHttpClient,
    request: Request,
    parse: (String) -> List<TokenResult>,
): Flow<ChatToken> = callbackFlow {
    val listener = object : EventSourceListener() {
        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            // One SSE event can decode to several tokens (e.g. an assembled tool call at finish),
            // so the per-stream parser returns a list rather than a single result.
            for (result in parse(data)) {
                when (result) {
                    is TokenResult.Emit -> {
                        trySend(result.token)
                        if (result.token is ChatToken.Done) {
                            eventSource.cancel()
                            close()
                            return
                        }
                    }
                    TokenResult.Skip -> Unit
                    is TokenResult.Fail -> {
                        eventSource.cancel()
                        close(ChatException(result.error))
                        return
                    }
                }
            }
        }

        override fun onClosed(eventSource: EventSource) {
            close()
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            val error = when {
                response != null && !response.isSuccessful -> ChatErrorMapper.fromHttpStatus(response.code)
                t != null -> ChatErrorMapper.fromThrowable(t)
                else -> ChatError.Malformed
            }
            close(ChatException(error))
        }
    }
    val eventSource = EventSources.createFactory(client).newEventSource(request, listener)
    awaitClose { eventSource.cancel() }
}

/** OpenAI / OpenRouter — SSE with the `choices[].delta` shape. */
class OpenAiChatDriver @Inject constructor(private val client: OkHttpClient) : ChatDriver {
    override fun send(profile: ProviderProfile, key: String, messages: List<ChatMessage>): Flow<ChatToken> =
        sseChatFlow(client, buildRequest(profile, key, messages), OpenAiStreamParser()::feed)
}

/** Anthropic — SSE with typed `content_block_delta` / `message_stop` events. */
class AnthropicChatDriver @Inject constructor(private val client: OkHttpClient) : ChatDriver {
    override fun send(profile: ProviderProfile, key: String, messages: List<ChatMessage>): Flow<ChatToken> =
        sseChatFlow(client, buildRequest(profile, key, messages), AnthropicStreamParser()::feed)
}

/** Ollama — newline-delimited JSON (not SSE); read the streamed body line by line. */
class OllamaChatDriver @Inject constructor(private val client: OkHttpClient) : ChatDriver {
    override fun send(profile: ProviderProfile, key: String, messages: List<ChatMessage>): Flow<ChatToken> = flow {
        val response = try {
            client.newCall(buildRequest(profile, key, messages)).execute()
        } catch (e: IOException) {
            throw ChatException(ChatErrorMapper.fromThrowable(e))
        }
        response.use {
            if (!it.isSuccessful) throw ChatException(ChatErrorMapper.fromHttpStatus(it.code))
            val source = it.body?.source() ?: throw ChatException(ChatError.Malformed)
            val parser = OllamaStreamParser()
            try {
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    for (result in parser.feed(line + "\n")) {
                        when (result) {
                            is TokenResult.Emit -> {
                                emit(result.token)
                                if (result.token is ChatToken.Done) return@flow
                            }
                            TokenResult.Skip -> Unit
                            is TokenResult.Fail -> throw ChatException(result.error)
                        }
                    }
                }
            } catch (e: IOException) {
                throw ChatException(ChatErrorMapper.fromThrowable(e))
            }
            when (val tail = parser.finish()) {
                is TokenResult.Emit -> emit(tail.token)
                is TokenResult.Fail -> throw ChatException(tail.error)
                else -> Unit
            }
        }
    }.flowOn(Dispatchers.IO)
}
