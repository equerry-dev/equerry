package dev.equerry.app.ui.slots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.equerry.app.providers.CapabilitySlot
import dev.equerry.app.providers.ProviderProfile
import dev.equerry.app.providers.ProviderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the Capability Slots screen. All slots are listed; only [CapabilitySlot.active]
 * ones (CHAT in v0.1) are interactive. [chatUnconfigured] drives the guidance shown when no
 * profile is mapped to CHAT (criterion c-5).
 */
data class SlotsUiState(
    val chatProfile: ProviderProfile? = null,
    val profiles: List<ProviderProfile> = emptyList(),
) {
    val slots: List<CapabilitySlot> = CapabilitySlot.entries
    val chatUnconfigured: Boolean get() = chatProfile == null
}

@HiltViewModel
class SlotsViewModel @Inject constructor(
    private val repository: ProviderRepository,
) : ViewModel() {

    val state: StateFlow<SlotsUiState> =
        combine(repository.observeChatMapping(), repository.observeProfiles()) { chat, profiles ->
            SlotsUiState(chatProfile = chat, profiles = profiles)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SlotsUiState())

    fun mapChatTo(profileId: String) {
        viewModelScope.launch { repository.setChatSlot(profileId) }
    }

    fun clearChat() {
        viewModelScope.launch { repository.setChatSlot(null) }
    }
}
