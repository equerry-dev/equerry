package dev.equerry.app.assistant

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Whether Equerry is the system's selected default digital assistant.
 *
 * Deliberately tri-state: [UNKNOWN] is distinct from [NOT_DEFAULT] so the onboarding
 * set-default step never reports "you're set up" off a missing/unreadable setting (c-1).
 */
enum class DefaultAssistantState { IS_DEFAULT, NOT_DEFAULT, UNKNOWN }

/**
 * Reads `Settings.Secure` to decide whether Equerry's [EquerryVoiceInteractionService]
 * holds the system voice-interaction (assistant) slot.
 *
 * `open` so the onboarding ViewModel's tests can subclass a fake without Robolectric
 * plumbing — the fakeable seam the plan calls for. Production binding is in `RepositoryModule`.
 */
open class DefaultAssistantDetector(private val context: Context) {

    /**
     * [DefaultAssistantState.IS_DEFAULT] only when the stored voice-interaction component is
     * Equerry's own service; [NOT_DEFAULT] when another (e.g. OEM) component holds the slot;
     * [UNKNOWN] when the setting is absent or blank. Never reports IS_DEFAULT without a match.
     */
    open fun state(): DefaultAssistantState {
        val stored = Settings.Secure.getString(context.contentResolver, KEY_VOICE_INTERACTION_SERVICE)
        if (stored.isNullOrBlank()) return DefaultAssistantState.UNKNOWN
        val current = ComponentName.unflattenFromString(stored)
        val ours = ComponentName(context, EquerryVoiceInteractionService::class.java)
        return if (current == ours) DefaultAssistantState.IS_DEFAULT else DefaultAssistantState.NOT_DEFAULT
    }

    /** The system assistant / voice-input selection screen the set-default step launches (t-9). */
    fun voiceInputSettingsIntent(): Intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)

    private companion object {
        /** Settings.Secure key holding the flattened ComponentName of the active assistant. */
        const val KEY_VOICE_INTERACTION_SERVICE = "voice_interaction_service"
    }
}
