package dev.equerry.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.equerry.app.providers.ProviderRepository
import dev.equerry.app.providers.drivers.ChatDriverFactory
import dev.equerry.app.providers.drivers.ChatException
import dev.equerry.app.providers.drivers.ChatMessage
import dev.equerry.app.providers.drivers.ChatRole
import dev.equerry.app.providers.drivers.ChatSession
import dev.equerry.app.providers.drivers.ChatToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The chat screen's state: the visible transcript, the input box, and stream/guidance/error flags. */
data class ChatUiState(
    val transcript: List<ChatMessage> = emptyList(),
    val input: String = "",
    val streaming: Boolean = false,
    /** True when no provider is mapped to the CHAT slot — the UI shows configure guidance. */
    val unmapped: Boolean = false,
    /** A human-readable, key-free error for the last send, or null. */
    val error: String? = null,
)

/**
 * Backs the dedicated chat screen. Resolves the CHAT-mapped provider, streams its reply live into a
 * growing assistant bubble, and threads the in-memory [ChatSession] for multi-turn context. With no
 * provider mapped it surfaces guidance and never touches a driver.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ProviderRepository,
    private val driverFactory: ChatDriverFactory,
    private val session: ChatSession,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.streaming) return
        viewModelScope.launch {
            val profile = repository.observeChatMapping().first()
            if (profile == null) {
                // No CHAT provider configured: guide the user, never call a driver (c-3).
                _state.update { it.copy(unmapped = true, error = null) }
                return@launch
            }
            val key = repository.keyFor(profile.id).orEmpty()
            val requestMessages = session.messagesForRequest(text, profile.systemPrompt)
            session.append(ChatMessage(ChatRole.USER, text))
            _state.update {
                it.copy(
                    transcript = it.transcript +
                        ChatMessage(ChatRole.USER, text) +
                        ChatMessage(ChatRole.ASSISTANT, ""),
                    input = "",
                    streaming = true,
                    unmapped = false,
                    error = null,
                )
            }

            val reply = StringBuilder()
            var failed = false
            driverFactory.send(profile, key, requestMessages)
                .catch { e ->
                    failed = true
                    val message = (e as? ChatException)?.error?.message
                        ?: "Something went wrong talking to the provider."
                    _state.update { it.copy(error = message, streaming = false) }
                }
                .collect { token ->
                    if (token is ChatToken.Delta) {
                        reply.append(token.text)
                        _state.update { it.copy(transcript = it.transcript.withLastAssistant(reply.toString())) }
                    }
                }

            if (!failed) {
                session.append(ChatMessage(ChatRole.ASSISTANT, reply.toString()))
                _state.update { it.copy(streaming = false) }
            }
        }
    }

    /** "New chat": clear the in-memory thread and reset the screen. */
    fun newChat() {
        session.clear()
        _state.value = ChatUiState()
    }

    /** Replaces the content of the most recent assistant turn (the one currently streaming). */
    private fun List<ChatMessage>.withLastAssistant(text: String): List<ChatMessage> {
        val index = indexOfLast { it.role == ChatRole.ASSISTANT }
        if (index < 0) return this
        return toMutableList().also { it[index] = it[index].copy(content = text) }
    }
}
