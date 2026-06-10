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
import dev.equerry.app.tools.actions.ActionNotes
import dev.equerry.app.tools.actions.ActionPlanner
import dev.equerry.app.tools.actions.ActionRunner
import dev.equerry.app.tools.actions.PlannedAction
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
    /**
     * The most recently completed assistant reply, or null while none has completed (or while one
     * is streaming). Combined with [streaming] flipping to false and [error] being null, this is
     * the "reply done" signal the voice flow reads to speak the whole reply (t-7).
     */
    val lastReply: String? = null,
    /**
     * Actions awaiting the user: a single staged timer/alarm (a "Start now?" card) or a multi-action
     * pending list (each runnable independently). Hand-offs that launched directly never appear here.
     */
    val pendingActions: List<PlannedAction> = emptyList(),
    /** Deterministic, past-tense notes of actions Equerry ran/opened (never "sent"). */
    val actionNotes: List<String> = emptyList(),
    /** A one-line guidance string when a requested action couldn't be run (with a Settings link in UI). */
    val actionGuidance: String? = null,
    /** True when the CHAT-mapped provider can't run actions — the UI shows the capability banner. */
    val toolsUnsupported: Boolean = false,
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
    private val actionRunner: ActionRunner,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    fun onInputChange(value: String) = _state.update { it.copy(input = value) }

    /** Send the current input box (the typed path). Delegates to [send]. */
    fun send() = send(_state.value.input)

    /**
     * Send [text] to the CHAT-mapped provider and stream the reply. The single round-trip path for
     * both the typed input and the voice flow (t-7) — no provider-call logic is duplicated. With no
     * provider mapped it surfaces guidance and never touches a driver (c-5).
     */
    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.streaming) return
        viewModelScope.launch {
            val profile = repository.observeChatMapping().first()
            if (profile == null) {
                // No CHAT provider configured: guide the user, never call a driver (c-3).
                _state.update { it.copy(unmapped = true, error = null) }
                return@launch
            }
            val key = repository.keyFor(profile.id).orEmpty()
            val requestMessages = session.messagesForRequest(trimmed, profile.systemPrompt)
            session.append(ChatMessage(ChatRole.USER, trimmed))
            _state.update {
                it.copy(
                    transcript = it.transcript +
                        ChatMessage(ChatRole.USER, trimmed) +
                        ChatMessage(ChatRole.ASSISTANT, ""),
                    input = "",
                    streaming = true,
                    unmapped = false,
                    error = null,
                    lastReply = null,
                    // Reset per-turn action UI; surface the capability banner if this provider can't run actions.
                    pendingActions = emptyList(),
                    actionNotes = emptyList(),
                    actionGuidance = null,
                    toolsUnsupported = !profile.type.supportsTools,
                )
            }

            val reply = StringBuilder()
            val toolCalls = mutableListOf<ChatToken.ToolCall>()
            var failed = false
            driverFactory.send(profile, key, requestMessages)
                .catch { e ->
                    failed = true
                    val message = (e as? ChatException)?.error?.message
                        ?: "Something went wrong talking to the provider."
                    _state.update { it.copy(error = message, streaming = false) }
                }
                .collect { token ->
                    when (token) {
                        is ChatToken.Delta -> {
                            reply.append(token.text)
                            _state.update { it.copy(transcript = it.transcript.withLastAssistant(reply.toString())) }
                        }
                        is ChatToken.ToolCall -> toolCalls.add(token)
                        ChatToken.Done -> Unit
                    }
                }

            if (!failed) {
                val replyText = reply.toString()
                if (replyText.isNotEmpty()) session.append(ChatMessage(ChatRole.ASSISTANT, replyText))
                _state.update { it.copy(streaming = false, lastReply = replyText.ifEmpty { null }) }
                if (toolCalls.isNotEmpty()) dispatchActions(ActionPlanner.plan(toolCalls))
            }
        }
    }

    /**
     * Route a planned set of actions. A single hand-off launches directly (handoff_execution); a
     * single staged timer/alarm becomes a "Start now?" card; several actions become a pending list.
     * Calls the model couldn't be turned into a valid action become guidance, never a crash (c-5).
     */
    private fun dispatchActions(plan: List<PlannedAction>) {
        val malformed = plan.filterIsInstance<PlannedAction.Malformed>()
        val runnable = plan.filter { it !is PlannedAction.Malformed }
        val notes = mutableListOf<String>()
        var pending = emptyList<PlannedAction>()
        when {
            runnable.size == 1 && runnable[0] is PlannedAction.Handoff -> {
                val action = runnable[0]
                notes += if (actionRunner.run(action)) ActionNotes.of(action) else NO_HANDLER_NOTE
            }
            runnable.isNotEmpty() -> pending = runnable
        }
        _state.update {
            it.copy(
                pendingActions = pending,
                actionNotes = it.actionNotes + notes,
                actionGuidance = if (malformed.isEmpty()) it.actionGuidance else ACTION_GUIDANCE,
            )
        }
    }

    /** Confirm (Start/Open) the pending action at [index]: run it once and post a deterministic note. */
    fun confirmAction(index: Int) {
        val action = _state.value.pendingActions.getOrNull(index) ?: return
        val note = if (actionRunner.run(action)) ActionNotes.of(action) else NO_HANDLER_NOTE
        _state.update {
            it.copy(
                pendingActions = it.pendingActions.filterIndexed { i, _ -> i != index },
                actionNotes = it.actionNotes + note,
            )
        }
    }

    /** Skip/cancel the pending action at [index] without running it. */
    fun cancelAction(index: Int) {
        _state.update { it.copy(pendingActions = it.pendingActions.filterIndexed { i, _ -> i != index }) }
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

private const val NO_HANDLER_NOTE = "Couldn't open an app for that action — none is installed to handle it."
private const val ACTION_GUIDANCE = "Some requested actions couldn't be run with this provider."
