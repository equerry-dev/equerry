package dev.equerry.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists whether first-run onboarding has been completed, on the shared "equerry_settings"
 * DataStore (VoiceSettingsStore pattern).
 *
 * A missing key reads as `false` so a fresh user always sees onboarding — the flag is never
 * defaulted to completed, which would silently suppress onboarding for a new install (c-3).
 */
class OnboardingStore(private val dataStore: DataStore<Preferences>) {

    fun completed(): Flow<Boolean> = dataStore.data.map { it[COMPLETED_KEY] ?: false }

    suspend fun setCompleted(value: Boolean) {
        dataStore.edit { it[COMPLETED_KEY] = value }
    }

    private companion object {
        val COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
    }
}
