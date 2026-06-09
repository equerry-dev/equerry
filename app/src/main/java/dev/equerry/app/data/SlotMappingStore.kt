package dev.equerry.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.equerry.app.providers.CapabilitySlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists which profile id each [CapabilitySlot] maps to. Only CHAT is wired in v0.1;
 * a null mapping (or a cleared key) means the slot is unconfigured.
 */
class SlotMappingStore(private val dataStore: DataStore<Preferences>) {

    fun mapping(slot: CapabilitySlot): Flow<String?> = dataStore.data.map { it[key(slot)] }

    suspend fun setMapping(slot: CapabilitySlot, profileId: String?) {
        dataStore.edit { prefs ->
            if (profileId == null) prefs.remove(key(slot)) else prefs[key(slot)] = profileId
        }
    }

    private fun key(slot: CapabilitySlot) = stringPreferencesKey("slot_${slot.name}")
}
