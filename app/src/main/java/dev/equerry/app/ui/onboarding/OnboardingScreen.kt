package dev.equerry.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.equerry.app.assistant.DefaultAssistantState

/**
 * First-run wizard route. Steps through welcome -> set-default -> map-CHAT -> done; navigates
 * away via [onFinished] once completion is persisted (the soft gate in [OnboardingViewModel]).
 * The set-default intent + return refresh are wired in t-9.
 */
@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    onAddProvider: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnboardingScreen(
        state = state,
        onAdvance = viewModel::advance,
        onBack = viewModel::back,
        onSetDefault = { /* t-9: launch ACTION_VOICE_INPUT_SETTINGS + refresh on return */ },
        onAddProvider = onAddProvider,
        onFinish = {
            viewModel.finish()
            if (state.completed) onFinished()
        },
        onSkip = {
            viewModel.dismiss()
            onFinished()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnboardingScreen(
    state: OnboardingUiState,
    onAdvance: () -> Unit,
    onBack: () -> Unit,
    onSetDefault: () -> Unit,
    onAddProvider: () -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Set up Equerry") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.step) {
                OnboardingStep.WELCOME -> {
                    Text("Welcome to Equerry", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Your private, bring-your-own-backend assistant. Two quick steps: set Equerry as your default assistant, then connect a chat provider.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                OnboardingStep.SET_DEFAULT -> {
                    Text("Set Equerry as default", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Make Equerry your system assistant so the assist gesture opens it. You can do this later — it's optional to continue.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        when (state.defaultState) {
                            DefaultAssistantState.IS_DEFAULT -> "Equerry is your default assistant."
                            DefaultAssistantState.NOT_DEFAULT -> "Equerry is not your default assistant yet."
                            DefaultAssistantState.UNKNOWN -> "Default assistant status is unknown on this device."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onSetDefault) { Text("Set as default assistant") }
                }

                OnboardingStep.MAP_CHAT -> {
                    Text("Connect a chat provider", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Add a provider profile and map it to the Chat slot. Onboarding finishes once Chat has a provider.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (state.chatMapped) "Chat is connected." else "Chat is not connected yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onAddProvider) { Text("Add a provider") }
                }

                OnboardingStep.DONE -> {
                    Text("You're all set", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (state.chatMapped) {
                            "Equerry is ready. Tap Finish to start using it."
                        } else {
                            "Connect a chat provider before finishing — Equerry needs a chat backend."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (state.step != OnboardingStep.WELCOME) {
                    TextButton(onClick = onBack) { Text("Back") }
                }
                TextButton(onClick = onSkip) { Text("Skip for now") }
                Spacer(Modifier.weight(1f))
                if (state.step == OnboardingStep.DONE) {
                    Button(onClick = onFinish, enabled = state.chatMapped) { Text("Finish") }
                } else {
                    Button(onClick = onAdvance) { Text("Next") }
                }
            }
        }
    }
}
