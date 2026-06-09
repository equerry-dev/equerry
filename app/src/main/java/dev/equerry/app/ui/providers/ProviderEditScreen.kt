package dev.equerry.app.ui.providers

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.equerry.app.providers.ProfileField
import dev.equerry.app.providers.ProviderType

/** Stateful entry point wired to the [ProviderEditViewModel]; navigates back on save. */
@Composable
fun ProviderEditRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ProviderEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    ProviderEditScreen(
        state = state,
        onLabel = viewModel::onLabelChange,
        onType = viewModel::selectType,
        onBaseUrl = viewModel::onBaseUrlChange,
        onKey = viewModel::onKeyChange,
        onModel = viewModel::onModelChange,
        onSave = viewModel::save,
        onBack = onBack,
    )
}

private fun ProviderType.shortLabel() = when (this) {
    ProviderType.OLLAMA -> "Ollama"
    ProviderType.ANTHROPIC -> "Anthropic"
    ProviderType.OPENAI_COMPATIBLE -> "OpenAI-compat"
    ProviderType.OPENROUTER -> "OpenRouter"
}

/**
 * Create / edit form. Reshapes to the selected type: hides the key field for keyless
 * Ollama, locks the OpenRouter base URL, offers per-type model presets. Inline errors
 * come from the ViewModel's validation; Save persists only when valid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditScreen(
    state: ProviderEditUiState,
    onLabel: (String) -> Unit,
    onType: (ProviderType) -> Unit,
    onBaseUrl: (String) -> Unit,
    onKey: (String) -> Unit,
    onModel: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New provider") },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(onClick = onSave) { Text("Save") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FieldLabel("Label", required = true)
            OutlinedTextField(
                value = state.label,
                onValueChange = onLabel,
                label = { Text("Label") },
                placeholder = { Text("e.g. Work Claude") },
                singleLine = true,
                isError = state.errorFor(ProfileField.LABEL) != null,
                supportingText = { state.errorFor(ProfileField.LABEL)?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel("Type")
            TypePickerGrid(selected = state.type, onSelect = onType)

            FieldLabel("Connection")
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = { if (!state.baseUrlLocked) onBaseUrl(it) },
                label = { Text(if (state.baseUrlLocked) "Base URL · locked" else "Base URL") },
                readOnly = state.baseUrlLocked,
                singleLine = true,
                trailingIcon = { if (state.baseUrlLocked) Icon(Icons.Rounded.Lock, "Locked") },
                isError = state.errorFor(ProfileField.BASE_URL) != null,
                supportingText = {
                    val err = state.errorFor(ProfileField.BASE_URL)
                    when {
                        err != null -> Text(err)
                        state.type == ProviderType.OLLAMA -> Text("Ollama runs locally — no API key required.")
                        else -> {}
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.keyFieldVisible) {
                var reveal by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = state.key,
                    onValueChange = onKey,
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton({ reveal = !reveal }) {
                            Icon(
                                if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Toggle key visibility",
                            )
                        }
                    },
                    isError = state.errorFor(ProfileField.KEY) != null,
                    supportingText = { state.errorFor(ProfileField.KEY)?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FieldLabel("Model")
            ModelField(value = state.model, presets = state.modelPresets, onValueChange = onModel)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FieldLabel(text: String, required: Boolean = false) {
    Row(Modifier.padding(top = 10.dp, bottom = 4.dp)) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (required) {
            Text(" *", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypePickerGrid(selected: ProviderType, onSelect: (ProviderType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProviderType.entries.chunked(2).forEach { row ->
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                row.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = selected == t,
                        onClick = { onSelect(t) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = row.size),
                    ) { Text(t.shortLabel()) }
                }
            }
        }
    }
}

/** Free-text model entry plus per-type preset quick-pick chips. */
@Composable
private fun ModelField(value: String, presets: List<String>, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Model") },
        singleLine = true,
        supportingText = { Text("Free text, or tap a preset for this type.") },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { id ->
            AssistChip(onClick = { onValueChange(id) }, label = { Text(id) })
        }
    }
}
