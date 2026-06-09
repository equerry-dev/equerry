package dev.equerry.app.ui.slots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.equerry.app.providers.CapabilitySlot
import dev.equerry.app.providers.ProviderProfile
import dev.equerry.app.ui.components.SlotRow
import dev.equerry.app.ui.components.TypeBadge

/** Stateful entry point wired to [SlotsViewModel]. */
@Composable
fun SlotsRoute(
    onAddProvider: () -> Unit,
    viewModel: SlotsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SlotsScreen(state = state, onMapChat = viewModel::mapChatTo, onAddProvider = onAddProvider)
}

private fun iconFor(slot: CapabilitySlot): ImageVector = when (slot) {
    CapabilitySlot.CHAT -> Icons.Rounded.ChatBubbleOutline
    CapabilitySlot.VISION -> Icons.Rounded.Visibility
    CapabilitySlot.STT -> Icons.Rounded.Mic
    CapabilitySlot.TTS -> Icons.Rounded.VolumeUp
    CapabilitySlot.OCR -> Icons.Rounded.DocumentScanner
    CapabilitySlot.EMBEDDINGS -> Icons.Rounded.Hub
}

private fun subtitleFor(slot: CapabilitySlot): String = when (slot) {
    CapabilitySlot.CHAT -> "Conversational replies"
    CapabilitySlot.VISION -> "Image understanding"
    CapabilitySlot.STT -> "Speech to text"
    CapabilitySlot.TTS -> "Text to speech"
    CapabilitySlot.OCR -> "Text from images"
    CapabilitySlot.EMBEDDINGS -> "Vector indexing"
}

/**
 * Capability slots. CHAT is the only live slot; the rest render disabled ("SOON").
 * When CHAT is unmapped, a guidance banner points the user to map a provider. Tapping
 * CHAT opens a bottom-sheet picker keyed by profile label.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotsScreen(
    state: SlotsUiState,
    onMapChat: (String) -> Unit,
    onAddProvider: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val chat = state.chatProfile

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Capability slots")
                    Text(
                        "Route jobs to your providers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            })
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(pad).padding(horizontal = 16.dp),
        ) {
            if (chat == null) item { UnmappedBanner(onAddProvider) }

            item {
                SlotRow(
                    slot = CapabilitySlot.CHAT,
                    icon = iconFor(CapabilitySlot.CHAT),
                    mappedLabel = chat?.let { "${it.label}  ·  ${it.model}" },
                    subtitle = subtitleFor(CapabilitySlot.CHAT),
                    onClick = { showPicker = true },
                    modifier = Modifier.testTag("chatSlot"),
                )
            }
            item {
                Text(
                    "COMING SOON",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            items(CapabilitySlot.entries.filter { !it.active }) { slot ->
                SlotRow(
                    slot = slot,
                    icon = iconFor(slot),
                    mappedLabel = null,
                    subtitle = subtitleFor(slot),
                    onClick = {},
                )
            }
        }
    }

    if (showPicker) {
        ModalBottomSheet(onDismissRequest = { showPicker = false }) {
            ProfilePickerSheet(
                profiles = state.profiles,
                selectedId = chat?.id,
                onPick = { onMapChat(it); showPicker = false },
                onAddProvider = { showPicker = false; onAddProvider() },
                onCancel = { showPicker = false },
            )
        }
    }
}

@Composable
private fun UnmappedBanner(onAddProvider: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        onClick = onAddProvider,
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Info, null, tint = cs.onPrimaryContainer)
            Column {
                Text("Map a provider to CHAT", style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Equerry can't answer until CHAT points at a provider profile. Tap the slot below to choose one — or add a provider first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ProfilePickerSheet(
    profiles: List<ProviderProfile>,
    selectedId: String?,
    onPick: (String) -> Unit,
    onAddProvider: () -> Unit,
    onCancel: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var choice by remember { mutableStateOf(selectedId) }

    Column(Modifier.padding(horizontal = 8.dp).padding(bottom = 20.dp)) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
            Text("Map the CHAT slot", style = MaterialTheme.typography.titleMedium)
            Text("Choose a saved provider profile", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }

        if (profiles.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No providers to map yet", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onAddProvider) { Text("Add a provider") }
            }
            return
        }

        profiles.forEach { p ->
            Row(
                Modifier.fillMaxWidth()
                    .selectable(selected = choice == p.id, onClick = { choice = p.id })
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                RadioButton(selected = choice == p.id, onClick = { choice = p.id })
                Column(Modifier.weight(1f)) {
                    Text(p.label, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Text("${p.type.displayName}  ·  ${p.model}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
                TypeBadge(p.type)
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { choice?.let(onPick) }, enabled = choice != null) { Text("Map provider") }
        }
    }
}
