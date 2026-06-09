package dev.equerry.app.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.equerry.app.providers.FieldError
import dev.equerry.app.providers.ProfileDraft
import dev.equerry.app.providers.ProfileField
import dev.equerry.app.providers.ProfileValidator
import dev.equerry.app.providers.ProviderProfile
import dev.equerry.app.providers.ProviderRepository
import dev.equerry.app.providers.ProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Editable state for the create/edit form, with per-type derived hints. */
data class ProviderEditUiState(
    val label: String = "",
    val type: ProviderType = ProviderType.OLLAMA,
    val baseUrl: String = "",
    val key: String = "",
    val model: String = "",
    val errors: List<FieldError> = emptyList(),
    val saved: Boolean = false,
    val editing: Boolean = false,
) {
    val keyFieldVisible: Boolean get() = type.requiresKey
    val baseUrlLocked: Boolean get() = type.baseUrlLocked
    val modelPresets: List<String> get() = type.modelPresets
    fun errorFor(field: ProfileField): String? = errors.firstOrNull { it.field == field }?.message
}

/**
 * Backs the create/edit form. Adapts the form to the selected [ProviderType] (prefilled
 * base URL, key visibility, model presets) and gates save on [ProfileValidator].
 */
@HiltViewModel
class ProviderEditViewModel @Inject constructor(
    private val repository: ProviderRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProviderEditUiState())
    val state: StateFlow<ProviderEditUiState> = _state.asStateFlow()

    private var editingId: String? = null

    init {
        selectType(ProviderType.OLLAMA)
    }

    /** Loads an existing profile into the form for editing. The key stays in the secret store. */
    fun load(profileId: String) {
        viewModelScope.launch {
            val profile = repository.observeProfiles().first().firstOrNull { it.id == profileId } ?: return@launch
            editingId = profile.id
            _state.value = ProviderEditUiState(
                label = profile.label,
                type = profile.type,
                baseUrl = profile.baseUrl,
                key = "",
                model = profile.model,
                editing = true,
            )
        }
    }

    fun onLabelChange(value: String) = clearAnd(ProfileField.LABEL) { it.copy(label = value) }
    fun onBaseUrlChange(value: String) = clearAnd(ProfileField.BASE_URL) { it.copy(baseUrl = value) }
    fun onKeyChange(value: String) = clearAnd(ProfileField.KEY) { it.copy(key = value) }
    fun onModelChange(value: String) = clearAnd(ProfileField.MODEL) { it.copy(model = value) }

    fun selectType(type: ProviderType) {
        _state.update {
            it.copy(
                type = type,
                baseUrl = type.defaultBaseUrl ?: "",
                key = if (type.requiresKey) it.key else "",
                errors = emptyList(),
            )
        }
    }

    fun save() {
        val current = _state.value
        val draft = ProfileDraft(current.label, current.type, current.baseUrl, current.key, current.model)
        val id = editingId
        var errors = ProfileValidator.validate(draft)
        // When editing, a blank key means "keep the saved secret" — don't require one.
        if (id != null && current.key.isBlank()) {
            errors = errors.filterNot { it.field == ProfileField.KEY }
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        viewModelScope.launch {
            if (id != null) {
                repository.updateProfile(
                    ProviderProfile(id, current.label, current.type, current.baseUrl, current.model),
                    newKey = current.key.ifBlank { null },
                )
            } else {
                repository.addProfile(draft)
            }
            _state.update { it.copy(saved = true) }
        }
    }

    private inline fun clearAnd(field: ProfileField, transform: (ProviderEditUiState) -> ProviderEditUiState) {
        _state.update { transform(it).copy(errors = it.errors.filterNot { e -> e.field == field }) }
    }
}
