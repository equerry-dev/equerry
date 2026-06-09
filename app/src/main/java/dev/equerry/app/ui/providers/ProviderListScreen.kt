package dev.equerry.app.ui.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.equerry.app.providers.ProviderProfile

/** Stateful entry point wired to the [ProviderListViewModel]. */
@Composable
fun ProviderListRoute(
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: ProviderListViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    ProviderListScreen(profiles = profiles, onAdd = onAdd, onEdit = onEdit, onDelete = viewModel::delete)
}

/**
 * Provider list. Empty state shows a single centred CTA (FAB suppressed to avoid a
 * double call-to-action); populated state lists [dev.equerry.app.ui.components.ProfileCard]s
 * with an extended FAB. Delete is confirmed via dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderListScreen(
    profiles: List<ProviderProfile>,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<ProviderProfile?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Providers") }) },
        floatingActionButton = {
            if (profiles.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Add") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
    ) { pad ->
        if (profiles.isEmpty()) {
            EmptyProviders(Modifier.padding(pad), onAdd)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(pad).padding(horizontal = 16.dp),
            ) {
                item {
                    Text(
                        "SAVED PROFILES",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(profiles, key = { it.id }) { p ->
                    dev.equerry.app.ui.components.ProfileCard(
                        profile = p,
                        onClick = { onEdit(p.id) },
                        onMenu = { pendingDelete = p },
                    )
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete provider?") },
            text = { Text("Remove \"${target.label}\" and its stored key. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(target.id); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EmptyProviders(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(84.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(">_", style = MaterialTheme.typography.headlineSmall, color = cs.onSecondaryContainer)
        }
        Spacer(Modifier.height(22.dp))
        Text("No providers yet", style = MaterialTheme.typography.headlineSmall, color = cs.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "Equerry needs a backend to think with. Add a provider profile — your own Ollama, Anthropic, OpenAI-compatible or OpenRouter endpoint.",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        Spacer(Modifier.height(22.dp))
        Button(onClick = onAdd, shape = MaterialTheme.shapes.extraLarge) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add your first provider")
        }
    }
}
