package dev.equerry.app.ui.voicesettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.equerry.app.data.SpeakTiming
import dev.equerry.app.data.TurnControl
import dev.equerry.app.data.VoiceSettingsStore
import dev.equerry.app.voice.MicPermission
import dev.equerry.app.voice.MicPermissionEntryPoint
import dev.equerry.app.voice.MicPermissionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The two persisted voice settings, seeded from [VoiceSettingsStore]. */
data class VoiceSettingsUiState(
    val turnControl: TurnControl = TurnControl.CONTINUOUS,
    val speakTiming: SpeakTiming = SpeakTiming.WHOLE_REPLY,
)

/**
 * Backs the voice settings screen. [state] is seeded from the persisted values (not the data-class
 * defaults), so reopening the screen reflects what the user last chose. The mic-permission settings
 * path delegates to [MicPermission] so its denial guidance is identical to the in-session path
 * (locked mic_permission). Uses [SharingStarted.Eagerly] to keep seeding deterministic.
 */
@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val store: VoiceSettingsStore,
) : ViewModel() {

    val state: StateFlow<VoiceSettingsUiState> =
        combine(store.turnControl(), store.speakTiming()) { turn, speak ->
            VoiceSettingsUiState(turnControl = turn, speakTiming = speak)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, VoiceSettingsUiState())

    fun setTurnControl(value: TurnControl) {
        viewModelScope.launch { store.setTurnControl(value) }
    }

    fun setSpeakTiming(value: SpeakTiming) {
        viewModelScope.launch { store.setSpeakTiming(value) }
    }

    /**
     * Evaluate the settings-path mic permission. Routes through [MicPermission] with the SETTINGS
     * entry point, so a denial yields the exact same guidance the in-session prompt shows.
     */
    fun micState(granted: Boolean): MicPermissionState =
        MicPermission.evaluate(granted, MicPermissionEntryPoint.SETTINGS)
}
