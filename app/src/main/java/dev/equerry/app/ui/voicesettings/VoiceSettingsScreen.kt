package dev.equerry.app.ui.voicesettings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.equerry.app.data.SpeakTiming
import dev.equerry.app.data.TurnControl
import dev.equerry.app.voice.MicPermission
import dev.equerry.app.voice.MicPermissionState

/** Stateful entry point wired to [VoiceSettingsViewModel]. */
@Composable
fun VoiceSettingsRoute(viewModel: VoiceSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var micState by remember {
        mutableStateOf(viewModel.micState(MicPermission.isGranted(context)))
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> micState = viewModel.micState(granted) }

    VoiceSettingsScreen(
        state = state,
        micState = micState,
        onTurnControl = viewModel::setTurnControl,
        onSpeakTiming = viewModel::setSpeakTiming,
        onRequestMic = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
        onOpenSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                },
            )
        },
    )
}

/** Voice settings: turn-taking, speak timing, and the settings-path microphone grant. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(
    state: VoiceSettingsUiState,
    micState: MicPermissionState,
    onTurnControl: (TurnControl) -> Unit,
    onSpeakTiming: (SpeakTiming) -> Unit,
    onRequestMic: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Voice") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ChoiceGroup(
                title = "After a reply",
                options = TurnControl.entries,
                selected = state.turnControl,
                label = { if (it == TurnControl.CONTINUOUS) "Keep listening (continuous)" else "Stop after one answer" },
                onSelect = onTurnControl,
            )
            ChoiceGroup(
                title = "Speak the reply",
                options = SpeakTiming.entries,
                selected = state.speakTiming,
                label = { if (it == SpeakTiming.WHOLE_REPLY) "All at once when finished" else "Sentence by sentence" },
                onSelect = onSpeakTiming,
            )
            MicSection(micState = micState, onRequestMic = onRequestMic, onOpenSettings = onOpenSettings)
        }
    }
}

@Composable
private fun <T> ChoiceGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        options.forEach { option ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = option == selected, onClick = { onSelect(option) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = { onSelect(option) })
                Text(label(option), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun MicSection(micState: MicPermissionState, onRequestMic: () -> Unit, onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Microphone", style = MaterialTheme.typography.titleMedium)
        when (micState) {
            MicPermissionState.Ready ->
                Text("Microphone access is on.", style = MaterialTheme.typography.bodyMedium)

            is MicPermissionState.Denied -> {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        micState.guidance,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRequestMic) { Text("Grant") }
                    if (micState.openSettings) {
                        Button(onClick = onOpenSettings) { Text("Open settings") }
                    }
                }
            }
        }
    }
}
