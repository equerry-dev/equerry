package dev.equerry.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.equerry.app.providers.ProviderProfile
import dev.equerry.app.providers.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists the list of [ProviderProfile]s (non-secret fields only — API keys live in
 * [SecretStore]) as JSON in DataStore. An internal [StoredProfile] DTO keeps the domain
 * model free of serialization concerns and of any key field.
 */
class ProfileStore(private val dataStore: DataStore<Preferences>) {

    fun profiles(): Flow<List<ProviderProfile>> = dataStore.data.map { prefs ->
        prefs[KEY]?.let { decode(it) } ?: emptyList()
    }

    suspend fun save(profiles: List<ProviderProfile>) {
        dataStore.edit { it[KEY] = encode(profiles) }
    }

    private fun encode(profiles: List<ProviderProfile>): String =
        json.encodeToString(profiles.map { it.toStored() })

    private fun decode(raw: String): List<ProviderProfile> =
        json.decodeFromString<List<StoredProfile>>(raw).map { it.toDomain() }

    @Serializable
    private data class StoredProfile(
        val id: String,
        val label: String,
        val type: String,
        val baseUrl: String,
        val model: String,
    )

    private fun ProviderProfile.toStored() = StoredProfile(id, label, type.name, baseUrl, model)

    private fun StoredProfile.toDomain() =
        ProviderProfile(id, label, ProviderType.valueOf(type), baseUrl, model)

    private companion object {
        val KEY = stringPreferencesKey("profiles_json")
        val json = Json { ignoreUnknownKeys = true }
    }
}
