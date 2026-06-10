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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import dev.equerry.app.ui.chat.ChatRoute
import dev.equerry.app.ui.probe.ProbeLogRoute
import dev.equerry.app.ui.providers.ProviderEditRoute
import dev.equerry.app.ui.providers.ProviderListRoute
import dev.equerry.app.ui.slots.SlotsRoute
import dev.equerry.app.ui.theme.EquerryTheme

/** Settings / onboarding host. Owns the navigation graph over the provider + slot screens. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EquerryTheme { EquerryNavHost() }
        }
    }
}

object Route {
    const val HOME = "home"
    const val PROVIDERS = "providers"
    const val SLOTS = "slots"
    const val PROBE = "probe"
    const val CHAT = "chat"
    const val EDIT = "edit"
}

/**
 * App navigation graph. The provider/slot destinations are injectable so tests can
 * exercise the graph without the Hilt-backed routes; production uses the real screens.
 */
@Composable
fun EquerryNavHost(
    navController: NavHostController = rememberNavController(),
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
) {
    NavHost(navController = navController, startDestination = Route.HOME) {
        composable(Route.HOME) {
            HomeScreen(
                onProviders = { navController.navigate(Route.PROVIDERS) },
                onSlots = { navController.navigate(Route.SLOTS) },
                onProbe = { navController.navigate(Route.PROBE) },
                onChat = { navController.navigate(Route.CHAT) },
            )
        }
        composable(Route.PROVIDERS) { providersContent() }
        composable(Route.SLOTS) { slotsContent() }
        composable(Route.PROBE) { probeContent() }
        composable(Route.CHAT) { chatContent() }
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
private fun HomeScreen(onProviders: () -> Unit, onSlots: () -> Unit, onProbe: () -> Unit, onChat: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Equerry") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Bring your own backend.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            HomeEntry("Chat", "Talk to your configured chat provider", onChat)
            HomeEntry("Providers", "Manage your AI backend profiles", onProviders)
            HomeEntry("Capability slots", "Route jobs to your providers", onSlots)
            HomeEntry("Probe log", "Assist-probe results from each invocation", onProbe)
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
