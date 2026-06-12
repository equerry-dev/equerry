package dev.equerry.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.equerry.app.providers.ProviderProfile
import dev.equerry.app.providers.ProviderRepository
import dev.equerry.app.providers.drivers.ChatDriverFactory
import dev.equerry.app.providers.drivers.ChatError
import dev.equerry.app.providers.drivers.ChatException
import dev.equerry.app.providers.drivers.ChatImage
import dev.equerry.app.providers.drivers.ChatMessage
import dev.equerry.app.providers.drivers.ChatRole
import dev.equerry.app.providers.drivers.ChatSession
import dev.equerry.app.providers.drivers.ChatToken
import dev.equerry.app.screencontext.ScreenContext
import dev.equerry.app.screencontext.ScreenContextPlanner
import dev.equerry.app.screencontext.ScreenQueryPlan
import dev.equerry.app.tools.actions.ActionNotes
import dev.equerry.app.tools.actions.ActionPlanner
import dev.equerry.app.tools.actions.ActionRunner
import dev.equerry.app.tools.actions.PlannedAction
import dev.equerry.app.tools.ocr.OcrEngine
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
    /**
     * A one-line note for a screen-context query that couldn't proceed: the screen couldn't be read
     * (blank-screen) or no Vision/Chat provider is configured. Distinct from [error] (a provider
     * failure) and [unmapped] (the typed-chat path). The UI/voice flow surface it then drop to chat.
     */
    val screenNote: String? = null,
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
    // Defaulted so direct constructors that don't use screen-context (the assist session before its
    // OCR wiring lands in t-9, and the voice-flow tests) keep compiling; Hilt injects the real engine.
    private val ocrEngine: OcrEngine = NoOpOcrEngine,
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
        // Clear any prior screen-context note synchronously so a watcher (the voice flow) never
        // settles this turn on a stale note before the async send runs.
        if (_state.value.screenNote != null) _state.update { it.copy(screenNote = null) }
        viewModelScope.launch {
            val profile = repository.observeChatMapping().first()
            if (profile == null) {
                // No CHAT provider configured: guide the user, never call a driver (c-3).
                _state.update { it.copy(unmapped = true, error = null) }
                return@launch
            }
            val key = repository.keyFor(profile.id).orEmpty()
            val requestMessages = session.messagesForRequest(trimmed, effectiveSystemPrompt(profile))
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
                    screenNote = null,
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
                // Dispatch BEFORE clearing `streaming`, so a watcher resuming on !streaming already
                // sees the staged/pending actions (the voice flow's spoken-confirm relies on this).
                if (toolCalls.isNotEmpty()) dispatchActions(ActionPlanner.plan(toolCalls))
                _state.update { it.copy(streaming = false, lastReply = replyText.ifEmpty { null }) }
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

    /**
     * Answer a "what's on this screen?" query for [context]. Routes via [ScreenContextPlanner]:
     * the screenshot to a multimodal VISION provider (degrading to the Assist/OCR text path on a
     * capability error), the Assist/OCR text to VISION or CHAT, or a guidance note when the screen
     * can't be read / nothing is configured. Only the screen's TEXT joins the session history — the
     * image bytes are request-only and never retained (screen_in_history, c-6).
     */
    fun askAboutScreen(context: ScreenContext) {
        if (_state.value.streaming) return
        // Clear any prior note synchronously so a watcher settles only on THIS turn's outcome.
        _state.update { it.copy(screenNote = null, error = null) }
        viewModelScope.launch {
            val vision = repository.observeVisionMapping().first()
            val chat = repository.observeChatMapping().first()
            val plan = ScreenContextPlanner.plan(
                context = context,
                visionMapped = vision != null,
                visionImageCapable = vision?.type?.supportsImages == true,
                chatMapped = chat != null,
            )

            when (plan) {
                // Nothing configured / nothing readable: a note, and the chat input stays usable (blank_screen).
                ScreenQueryPlan.Unconfigured ->
                    _state.update { it.copy(screenNote = SCREEN_UNCONFIGURED_NOTE, error = null) }
                ScreenQueryPlan.BlankScreen ->
                    _state.update { it.copy(screenNote = BLANK_SCREEN_NOTE, error = null) }

                ScreenQueryPlan.ImageToVision -> {
                    beginScreenTurn()
                    // Try the image; degrade to the text path on the same VISION provider on a capability error.
                    val first = streamScreen(vision!!, image = context.screenshot, text = SCREEN_PROMPT, historyText = context.text)
                    if (first is StreamOutcome.Failed) {
                        if (isCapabilityError(first.error)) {
                            val text = resolveText(context)
                            val second = streamScreen(vision, image = null, text = textPrompt(text), historyText = text)
                            if (second is StreamOutcome.Failed) failScreen(second.message)
                        } else {
                            failScreen(first.message)
                        }
                    }
                }

                ScreenQueryPlan.TextToVision -> {
                    beginScreenTurn()
                    val text = resolveText(context)
                    val outcome = streamScreen(vision!!, image = null, text = textPrompt(text), historyText = text)
                    if (outcome is StreamOutcome.Failed) failScreen(outcome.message)
                }

                ScreenQueryPlan.OcrThenChat -> {
                    beginScreenTurn()
                    val text = resolveText(context)
                    val outcome = streamScreen(chat!!, image = null, text = textPrompt(text), historyText = text)
                    if (outcome is StreamOutcome.Failed) failScreen(outcome.message)
                }
            }
        }
    }

    /** Open the visible turn for a screen query: a friendly user bubble + a streaming assistant bubble. */
    private fun beginScreenTurn() {
        _state.update {
            it.copy(
                transcript = it.transcript +
                    ChatMessage(ChatRole.USER, SCREEN_QUERY_LABEL) +
                    ChatMessage(ChatRole.ASSISTANT, ""),
                input = "",
                streaming = true,
                unmapped = false,
                error = null,
                lastReply = null,
                pendingActions = emptyList(),
                actionNotes = emptyList(),
                actionGuidance = null,
                toolsUnsupported = false,
                screenNote = null,
            )
        }
    }

    /**
     * Stream one screen-query attempt from [profile] (resetting the assistant bubble first, so a
     * degrade retry doesn't show the failed partial). On success, retain [historyText] (text only —
     * never the image) plus the reply in the session and settle the UI. Returns the outcome so the
     * caller can decide whether to degrade or surface the error.
     */
    private suspend fun streamScreen(
        profile: ProviderProfile,
        image: ChatImage?,
        text: String,
        historyText: String,
    ): StreamOutcome {
        _state.update { it.copy(transcript = it.transcript.withLastAssistant("")) }
        val key = repository.keyFor(profile.id).orEmpty()
        val request = buildScreenRequest(profile, text, image)
        val outcome = collectStream(profile, key, request)
        if (outcome is StreamOutcome.Ok) {
            // History keeps the TEXT representation of the screen (image bytes never persisted, c-6).
            session.append(ChatMessage(ChatRole.USER, historyText))
            if (outcome.reply.isNotEmpty()) session.append(ChatMessage(ChatRole.ASSISTANT, outcome.reply))
            if (outcome.toolCalls.isNotEmpty()) dispatchActions(ActionPlanner.plan(outcome.toolCalls))
            _state.update { it.copy(streaming = false, lastReply = outcome.reply.ifEmpty { null }) }
        }
        return outcome
    }

    /** The request for a screen attempt: optional system prompt, prior turns, then this screen turn. */
    private fun buildScreenRequest(profile: ProviderProfile, text: String, image: ChatImage?): List<ChatMessage> =
        buildList {
            add(ChatMessage(ChatRole.SYSTEM, effectiveSystemPrompt(profile)))
            addAll(session.turns)
            add(ChatMessage(ChatRole.USER, text, image = image))
        }

    /** The screen's text: the Assist text when present, otherwise OCR of the screenshot ("" if neither). */
    private suspend fun resolveText(context: ScreenContext): String =
        if (context.hasText) context.text else context.screenshot?.let { ocrEngine.recognise(it) }.orEmpty()

    /** Stream a reply into the live assistant bubble, returning the result without finalising history. */
    private suspend fun collectStream(
        profile: ProviderProfile,
        key: String,
        request: List<ChatMessage>,
    ): StreamOutcome {
        val reply = StringBuilder()
        val toolCalls = mutableListOf<ChatToken.ToolCall>()
        var error: ChatError? = null
        var failed = false
        driverFactory.send(profile, key, request)
            .catch { e ->
                failed = true
                error = (e as? ChatException)?.error
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
        return if (failed) {
            StreamOutcome.Failed(error, error?.message ?: GENERIC_PROVIDER_ERROR)
        } else {
            StreamOutcome.Ok(reply.toString(), toolCalls)
        }
    }

    /** Settle a failed screen turn: show the key-free error, keep the partial reply, stop streaming. */
    private fun failScreen(message: String) {
        _state.update { it.copy(error = message, streaming = false) }
    }

    /**
     * A capability-class failure worth degrading the image path to text: a 4xx the model returned
     * because it can't accept the image (or a malformed/unparseable reply). Auth and network errors
     * are NOT capability errors — retrying with text wouldn't help, so they surface directly.
     */
    private fun isCapabilityError(error: ChatError?): Boolean =
        error is ChatError.Malformed || (error is ChatError.Http && error.code in 400..499)

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

// --- Screen-context (t-7) ---

/** The visible user bubble for a screen-context query (the screen content itself goes to the provider). */
private const val SCREEN_QUERY_LABEL = "What's on this screen?"

/** Instruction sent to the provider alongside the screen image/text. */
private const val SCREEN_PROMPT = "Look at the current screen and tell me concisely what's on it."

/** Shown when the Assist API returned nothing usable — the chat input stays available (blank_screen). */
private const val BLANK_SCREEN_NOTE = "Couldn't read this screen — it may block assistant capture. You can still ask me anything."

/** Shown when neither a Vision nor a Chat provider is mapped. */
private const val SCREEN_UNCONFIGURED_NOTE = "Map a Vision or Chat provider in Settings to ask about your screen."

private const val GENERIC_PROVIDER_ERROR = "Something went wrong talking to the provider."

/**
 * System message applied when a CHAT profile leaves its own prompt blank. Equerry is a voice-first
 * assistant whose host app handles speech I/O, so a bare model otherwise disclaims voice/audio
 * ability ("I can't do text-to-speech") and replies in markdown that reads poorly aloud. A profile's
 * own system prompt (when set) overrides this entirely.
 */
private const val DEFAULT_SYSTEM_PROMPT =
    "You are Equerry, a helpful hands-free voice assistant. The host app transcribes the user's " +
        "speech into your input and reads your replies aloud, so you effectively listen and speak — " +
        "never say you lack voice, audio, or text-to-speech ability. Reply in short, natural, " +
        "spoken-style sentences; avoid markdown, code blocks, and bullet lists unless asked."

/** A profile's own system prompt when set, otherwise the built-in [DEFAULT_SYSTEM_PROMPT]. */
private fun effectiveSystemPrompt(profile: ProviderProfile): String =
    profile.systemPrompt?.takeIf { it.isNotBlank() } ?: DEFAULT_SYSTEM_PROMPT

/** Builds the text turn for a screen query: the instruction, plus the screen's text when we have it. */
private fun textPrompt(screenText: String): String =
    if (screenText.isBlank()) SCREEN_PROMPT else "$SCREEN_PROMPT\n\nScreen text:\n$screenText"

/** Outcome of one streamed attempt — success carries the reply + any tool calls; failure the error. */
private sealed interface StreamOutcome {
    data class Ok(val reply: String, val toolCalls: List<ChatToken.ToolCall>) : StreamOutcome
    data class Failed(val error: ChatError?, val message: String) : StreamOutcome
}

/**
 * No-op OCR used as the [ChatViewModel] constructor default where a real engine isn't wired (the
 * assist session before t-9, and voice-flow tests). Recognises nothing, so screen-context degrades
 * to the blank-screen path rather than misbehaving. Hilt injects the real engine for the chat screen.
 */
private object NoOpOcrEngine : OcrEngine {
    override suspend fun recognise(image: ChatImage): String = ""
}
