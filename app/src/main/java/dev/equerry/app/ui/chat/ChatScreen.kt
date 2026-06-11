package dev.equerry.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import dev.equerry.app.providers.drivers.ChatMessage
import dev.equerry.app.providers.drivers.ChatRole
import dev.equerry.app.tools.actions.PlannedAction
import dev.equerry.app.voice.MicPermission
import dev.equerry.app.voice.SystemSpeechToText
import dev.equerry.app.voice.SystemTextToSpeech
import dev.equerry.app.voice.VoiceComponentsEntryPoint
import dev.equerry.app.voice.VoiceFlowController
import dev.equerry.app.voice.VoiceFlowState
import kotlinx.coroutines.flow.first

/**
 * Stateful entry point wired to [ChatViewModel]. Also builds a [VoiceFlowController] over the same
 * view model so the mic button runs the full voice round-trip (listen → send → speak) from the chat
 * screen, honouring the user's turn-taking + speak-timing settings.
 */
@Composable
fun ChatRoute(viewModel: ChatViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val controller = remember(viewModel) {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            VoiceComponentsEntryPoint::class.java,
        )
        VoiceFlowController(
            chat = viewModel,
            stt = SystemSpeechToText.fromContext(context.applicationContext),
            tts = SystemTextToSpeech.fromContext(context.applicationContext),
            settings = entry.voiceSettingsStore(),
            scope = scope,
            isMicGranted = { MicPermission.isGranted(context.applicationContext) },
            isChatConfigured = { entry.providerRepository().observeChatMapping().first() != null },
        )
    }
    val voiceState by controller.state.collectAsState()
    // Stop listening/speaking when leaving the chat screen.
    DisposableEffect(controller) { onDispose { controller.stop() } }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.start()
    }

    ChatScreen(
        state = state,
        listening = voiceState != VoiceFlowState.Idle,
        onInput = viewModel::onInputChange,
        onSend = viewModel::send,
        onNewChat = viewModel::newChat,
        onConfirmAction = viewModel::confirmAction,
        onCancelAction = viewModel::cancelAction,
        onMic = {
            if (MicPermission.isGranted(context.applicationContext)) {
                controller.start()
            } else {
                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
    )
}

/**
 * Dedicated chat surface: a scrolling transcript of user/assistant bubbles, a text input, and a
 * "New chat" reset. Shows configure guidance when no CHAT provider is mapped and an inline error
 * when a send fails.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onNewChat: () -> Unit,
    listening: Boolean = false,
    onMic: () -> Unit = {},
    onConfirmAction: (Int) -> Unit = {},
    onCancelAction: (Int) -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                actions = { TextButton(onClick = onNewChat) { Text("New chat") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (state.unmapped) {
                GuidanceBanner()
            }
            if (state.toolsUnsupported) {
                CapabilityBanner(onOpenSettings)
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.transcript) { message -> MessageBubble(message) }
            }
            // Staged/pending actions: a single staged timer/alarm is a "Start/Cancel" card; several
            // become a list where each row is independently runnable (Start/Open) or skippable.
            state.pendingActions.forEachIndexed { index, action ->
                ActionCard(
                    action = action,
                    index = index,
                    isList = state.pendingActions.size > 1,
                    onConfirm = onConfirmAction,
                    onCancel = onCancelAction,
                )
            }
            state.actionNotes.forEach { ActionNoteLine(it) }
            state.actionGuidance?.let { ActionGuidanceLine(it, onOpenSettings) }
            state.error?.let { ErrorLine(it) }
            if (listening) {
                Text(
                    "Listening…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            InputBar(
                value = state.input,
                enabled = !state.streaming,
                listening = listening,
                onInput = onInput,
                onSend = onSend,
                onMic = onMic,
            )
        }
    }
}

@Composable
private fun GuidanceBanner() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            "No chat provider configured. Add a provider and map it to the CHAT slot to start chatting.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun CapabilityBanner(onOpenSettings: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Actions need a tool-capable provider.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenSettings) { Text("Change in Settings") }
        }
    }
}

/** Human-readable label for a pending action shown on its card/list row. */
private fun actionLabel(action: PlannedAction): String = when (action) {
    is PlannedAction.Staged.Timer -> "Timer — ${action.seconds / 60}:${(action.seconds % 60).toString().padStart(2, '0')}"
    is PlannedAction.Staged.Alarm -> "Alarm — ${action.hour}:${action.minute.toString().padStart(2, '0')}"
    is PlannedAction.Handoff.Calendar -> "Calendar: ${action.title}"
    is PlannedAction.Handoff.Email -> "Email to ${action.to}"
    is PlannedAction.Handoff.Sms -> "Text to ${action.to ?: "your contact"}"
    is PlannedAction.Malformed -> action.toolName
}

@Composable
private fun ActionCard(
    action: PlannedAction,
    index: Int,
    isList: Boolean,
    onConfirm: (Int) -> Unit,
    onCancel: (Int) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(actionLabel(action), modifier = Modifier.weight(1f))
            // A multi-action row skips; a single staged card cancels.
            TextButton(onClick = { onCancel(index) }) { Text(if (isList) "Skip" else "Cancel") }
            // Benign staged actions start; hand-offs open the target app.
            Button(onClick = { onConfirm(index) }) { Text(if (action is PlannedAction.Staged) "Start" else "Open") }
        }
    }
}

@Composable
private fun ActionNoteLine(text: String) {
    Text(
        "✓ $text",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

@Composable
private fun ActionGuidanceLine(text: String, onOpenSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenSettings) { Text("Settings") }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val fromUser = message.role == ChatRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(message.content, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun ErrorLine(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun InputBar(
    value: String,
    enabled: Boolean,
    listening: Boolean,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Speak instead of type: starts the voice round-trip. Disabled while one is already running.
        IconButton(onClick = onMic, enabled = enabled && !listening) {
            Icon(
                Icons.Rounded.Mic,
                contentDescription = "Speak",
                tint = if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onInput,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message") },
            enabled = enabled,
        )
        IconButton(onClick = onSend, enabled = enabled && value.isNotBlank()) {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
        }
    }
}
