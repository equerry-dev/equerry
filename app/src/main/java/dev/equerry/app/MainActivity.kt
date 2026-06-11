package dev.equerry.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.equerry.app.data.OnboardingStore
import dev.equerry.app.providers.ProviderRepository
import dev.equerry.app.ui.chat.ChatRoute
import dev.equerry.app.ui.onboarding.OnboardingRoute
import dev.equerry.app.ui.probe.ProbeLogRoute
import dev.equerry.app.ui.providers.ProviderEditRoute
import dev.equerry.app.ui.providers.ProviderListRoute
import dev.equerry.app.ui.slots.SlotsRoute
import dev.equerry.app.ui.theme.EquerryTheme
import dev.equerry.app.ui.voicesettings.VoiceSettingsRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Settings / onboarding host. Owns the navigation graph over the provider + slot screens. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EquerryTheme { EquerryRoot() }
        }
    }
}

object Route {
    const val HOME = "home"
    const val ONBOARDING = "onboarding"
    const val PROVIDERS = "providers"
    const val SLOTS = "slots"
    const val PROBE = "probe"
    const val CHAT = "chat"
    const val VOICE = "voice"
    const val EDIT = "edit"
}

/** Where the app opens: onboarding until it's completed, the home screen afterwards (c-3). */
fun startDestinationFor(onboardingCompleted: Boolean): String =
    if (onboardingCompleted) Route.HOME else Route.ONBOARDING

/** App-level state: whether onboarding is done and whether CHAT is still unmapped (banner). */
data class RootUiState(
    val ready: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val chatUnconfigured: Boolean = true,
)

@HiltViewModel
class RootViewModel @Inject constructor(
    onboardingStore: OnboardingStore,
    repository: ProviderRepository,
) : ViewModel() {
    val state: StateFlow<RootUiState> =
        combine(onboardingStore.completed(), repository.observeChatMapping()) { done, chat ->
            RootUiState(ready = true, onboardingCompleted = done, chatUnconfigured = chat == null)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootUiState())
}

@Composable
private fun EquerryRoot(viewModel: RootViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    // Wait for the first emission so the start destination is resolved once, with no flicker.
    if (!ui.ready) return
    EquerryNavHost(
        startDestination = startDestinationFor(ui.onboardingCompleted),
        showSetupBanner = ui.chatUnconfigured,
    )
}

/**
 * App navigation graph. The provider/slot destinations are injectable so tests can
 * exercise the graph without the Hilt-backed routes; production uses the real screens.
 */
@Composable
fun EquerryNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Route.HOME,
    showSetupBanner: Boolean = false,
    onboardingContent: @Composable () -> Unit = {
        OnboardingRoute(
            onFinished = {
                navController.navigate(Route.HOME) {
                    popUpTo(Route.ONBOARDING) { inclusive = true }
                }
            },
            onAddProvider = { navController.navigate(Route.EDIT) },
        )
    },
    providersContent: @Composable () -> Unit = {
        ProviderListRoute(
            onAdd = { navController.navigate(Route.EDIT) },
            onEdit = { id -> navController.navigate("${Route.EDIT}?profileId=$id") },
        )
    },
    slotsContent: @Composable () -> Unit = {
        SlotsRoute(onAddProvider = { navController.navigate(Route.EDIT) })
    },
    probeContent: @Composable () -> Unit = { ProbeLogRoute() },
    chatContent: @Composable () -> Unit = { ChatRoute() },
    voiceContent: @Composable () -> Unit = { VoiceSettingsRoute() },
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Route.ONBOARDING) { onboardingContent() }
        composable(Route.HOME) {
            HomeScreen(
                onProviders = { navController.navigate(Route.PROVIDERS) },
                onSlots = { navController.navigate(Route.SLOTS) },
                onProbe = { navController.navigate(Route.PROBE) },
                onChat = { navController.navigate(Route.CHAT) },
                onVoice = { navController.navigate(Route.VOICE) },
                onSetup = { navController.navigate(Route.ONBOARDING) },
                showSetupBanner = showSetupBanner,
            )
        }
        composable(Route.PROVIDERS) { providersContent() }
        composable(Route.SLOTS) { slotsContent() }
        composable(Route.PROBE) { probeContent() }
        composable(Route.CHAT) { chatContent() }
        composable(Route.VOICE) { voiceContent() }
        composable(
            route = "${Route.EDIT}?profileId={profileId}",
            arguments = listOf(
                navArgument("profileId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            ProviderEditRoute(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                profileId = backStackEntry.arguments?.getString("profileId"),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    onProviders: () -> Unit,
    onSlots: () -> Unit,
    onProbe: () -> Unit,
    onChat: () -> Unit,
    onVoice: () -> Unit,
    onSetup: () -> Unit,
    showSetupBanner: Boolean,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Equerry") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Bring your own backend.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showSetupBanner) {
                SetupBanner(onSetup)
            }
            Spacer(Modifier.height(4.dp))
            HomeEntry("Chat", "Talk to your configured chat provider", onChat)
            HomeEntry("Providers", "Manage your AI backend profiles", onProviders)
            HomeEntry("Capability slots", "Route jobs to your providers", onSlots)
            HomeEntry("Probe log", "Assist-probe results from each invocation", onProbe)
            HomeEntry("Voice", "Spoken Q&A and listening behaviour", onVoice)
            HomeEntry("Set up Equerry", "Choose your default assistant and providers", onSetup)
        }
    }
}

@Composable
private fun SetupBanner(onSetup: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Finish setting up Equerry", style = MaterialTheme.typography.titleMedium)
            Text(
                "Connect a chat provider to start using Equerry.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onSetup) { Text("Finish setup") }
        }
    }
}

@Composable
private fun HomeEntry(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
