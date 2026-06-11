package dev.equerry.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.equerry.app.assistant.DefaultAssistantDetector
import dev.equerry.app.assistant.DefaultAssistantState
import dev.equerry.app.data.OnboardingStore
import dev.equerry.app.providers.ProviderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The first-run wizard steps, in their fixed order (onboarding_shape locked decision). */
enum class OnboardingStep {
    WELCOME,
    SET_DEFAULT,
    MAP_CHAT,
    DONE;

    fun next(): OnboardingStep = entries.getOrElse(ordinal + 1) { DONE }
    fun previous(): OnboardingStep = entries.getOrElse(ordinal - 1) { WELCOME }
}

/**
 * Onboarding state. [completed] is the persisted soft gate — it only ever flips once a CHAT
 * provider is mapped ([chatMapped]); [pendingSetup] is what the HOME banner watches.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val defaultState: DefaultAssistantState = DefaultAssistantState.UNKNOWN,
    val chatMapped: Boolean = false,
    val completed: Boolean = false,
    val dismissed: Boolean = false,
) {
    val isDefaultAssistant: Boolean get() = defaultState == DefaultAssistantState.IS_DEFAULT

    /** Setup still owed until onboarding is completed (drives the HOME setup banner, t-8). */
    val pendingSetup: Boolean get() = !completed
}

/**
 * Drives the first-run wizard: welcome -> set-default -> map-CHAT -> done. The set-default step
 * is deferrable — the system selection can be declined or unavailable, so advancing past it never
 * dead-ends regardless of detection. Completion is a soft gate tied to the CHAT mapping (c-2).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val detector: DefaultAssistantDetector,
    private val onboardingStore: OnboardingStore,
    private val repository: ProviderRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState(defaultState = detector.state()))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeChatMapping().collect { profile ->
                _state.update { it.copy(chatMapped = profile != null) }
            }
        }
        viewModelScope.launch {
            onboardingStore.completed().collect { done ->
                _state.update { it.copy(completed = done) }
            }
        }
    }

    /** Advance to the next step. From SET_DEFAULT this is always allowed — the step is deferrable. */
    fun advance() = _state.update { it.copy(step = it.step.next()) }

    /** Step back one screen. */
    fun back() = _state.update { it.copy(step = it.step.previous()) }

    /** Re-query the detector — call on return from the system selection screen (c-1). */
    fun refresh() = _state.update { it.copy(defaultState = detector.state()) }

    /** Dismiss onboarding without finishing. Leaves [OnboardingUiState.completed] false. */
    fun dismiss() = _state.update { it.copy(dismissed = true) }

    /**
     * Finish onboarding. The soft gate: only persists completion when a CHAT provider is mapped,
     * so onboarding can never report "done" without a working chat backend (c-2 / c-3).
     */
    fun finish() {
        if (!_state.value.chatMapped) return
        viewModelScope.launch { onboardingStore.setCompleted(true) }
    }
}
