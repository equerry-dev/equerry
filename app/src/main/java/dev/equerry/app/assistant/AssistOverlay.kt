package dev.equerry.app.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.equerry.app.voice.VoiceFlowState
import dev.equerry.app.voice.VoiceGuidance

/**
 * The headless assistant's minimal status overlay (replaces the reused chat surface — this reverses
 * the earlier `session_ui_reuse` decision so an assist invocation never brings up the chat window).
 * Shows only the current turn phase and any failure guidance; the conversation itself is spoken. The
 * session drives it from [VoiceFlowController.state] / [VoiceFlowController.guidance].
 */
@Composable
fun AssistOverlay(
    voiceState: VoiceFlowState,
    guidance: VoiceGuidance?,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 3.dp,
            modifier = Modifier.testTag("assistOverlay"),
        ) {
            Column(
                Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = iconFor(voiceState),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(4.dp),
                )
                Text(
                    text = labelFor(voiceState),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                guidance?.let { g ->
                    Text(
                        text = g.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun iconFor(state: VoiceFlowState): ImageVector = when (state) {
    VoiceFlowState.Speaking -> Icons.Rounded.GraphicEq
    else -> Icons.Rounded.Mic
}

private fun labelFor(state: VoiceFlowState): String = when (state) {
    VoiceFlowState.Idle -> "Tap to talk to Equerry"
    VoiceFlowState.Listening -> "Listening…"
    VoiceFlowState.Transcribing -> "Got it…"
    VoiceFlowState.Sending -> "Thinking…"
    VoiceFlowState.Speaking -> "Speaking…"
}
