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
 * ones (CHAT and VISION in v0.1) are interactive. [chatUnconfigured] drives the CHAT guidance
 * banner; each active-but-unmapped slot also shows its own "No provider mapped" row (criterion c-5).
 */
data class SlotsUiState(
    val chatProfile: ProviderProfile? = null,
    val visionProfile: ProviderProfile? = null,
    val profiles: List<ProviderProfile> = emptyList(),
) {
    val slots: List<CapabilitySlot> = CapabilitySlot.entries
    val chatUnconfigured: Boolean get() = chatProfile == null

    /** The profile mapped to [slot] (null for unmapped or non-interactive slots). */
    fun mappedProfile(slot: CapabilitySlot): ProviderProfile? = when (slot) {
        CapabilitySlot.CHAT -> chatProfile
        CapabilitySlot.VISION -> visionProfile
        else -> null
    }
}

@HiltViewModel
class SlotsViewModel @Inject constructor(
    private val repository: ProviderRepository,
) : ViewModel() {

    val state: StateFlow<SlotsUiState> =
        combine(
            repository.observeChatMapping(),
            repository.observeVisionMapping(),
            repository.observeProfiles(),
        ) { chat, vision, profiles ->
            SlotsUiState(chatProfile = chat, visionProfile = vision, profiles = profiles)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SlotsUiState())

    /** Map [slot] to [profileId]. Only the active slots (CHAT, VISION) are mappable. */
    fun map(slot: CapabilitySlot, profileId: String) {
        viewModelScope.launch {
            when (slot) {
                CapabilitySlot.CHAT -> repository.setChatSlot(profileId)
                CapabilitySlot.VISION -> repository.setVisionSlot(profileId)
                else -> Unit
            }
        }
    }

    /** Clear the provider mapped to [slot]. */
    fun clear(slot: CapabilitySlot) {
        viewModelScope.launch {
            when (slot) {
                CapabilitySlot.CHAT -> repository.setChatSlot(null)
                CapabilitySlot.VISION -> repository.setVisionSlot(null)
                else -> Unit
            }
        }
    }
}
