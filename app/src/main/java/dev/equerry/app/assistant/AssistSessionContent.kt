package dev.equerry.app.assistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.equerry.app.ui.chat.ChatScreen
import dev.equerry.app.ui.chat.ChatUiState
import dev.equerry.app.voice.VoiceGuidance

/**
 * The assist session's screen content, extracted from the framework-bound
 * [EquerryVoiceInteractionSession] so it is unit-testable (see AssistSessionContentTest) — the
 * session itself can't be rendered without a real VoiceInteractionSession. Shows the failure-guidance
 * banner, the screen-context note, the "Ask about this screen" button (c-1), and the reused phase-04
 * chat surface. The session wires the callbacks to its ChatViewModel / VoiceFlowController.
 */
@Composable
fun AssistSessionContent(
    state: ChatUiState,
    guidance: VoiceGuidance?,
    onAskScreen: () -> Unit,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onNewChat: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        guidance?.let { g ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    g.message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        // Screen-context note (couldn't read the screen / nothing configured), then drop to chat.
        state.screenNote?.let { note ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    note,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        // Ask about the current screen: routes the capture to VISION (or the OCR/CHAT fallback).
        Button(
            onClick = onAskScreen,
            enabled = !state.streaming,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("askScreenButton"),
        ) {
            Text("Ask about this screen")
        }
        ChatScreen(
            state = state,
            onInput = onInput,
            onSend = onSend,
            onNewChat = onNewChat,
        )
    }
}
