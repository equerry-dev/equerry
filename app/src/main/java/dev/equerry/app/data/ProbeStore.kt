package dev.equerry.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.equerry.app.assistant.ProbeRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Append-only log of [ProbeRecord]s persisted as a JSON list in the settings DataStore,
 * mirroring [ProfileStore]. Records accumulate across many separate assist invocations
 * (probe_persistence decision), so the table survives process death.
 *
 * [append] read-modify-writes inside a single [edit] block: DataStore serialises edits,
 * so concurrent appends each see the prior list and none are lost. A read-then-write
 * split across the [edit] boundary would drop writes under concurrency.
 */
class ProbeStore(private val dataStore: DataStore<Preferences>) {

    fun records(): Flow<List<ProbeRecord>> = dataStore.data.map { prefs ->
        prefs[KEY]?.let { decode(it) } ?: emptyList()
    }

    suspend fun append(record: ProbeRecord) {
        dataStore.edit { prefs ->
            val current = prefs[KEY]?.let { decode(it) } ?: emptyList()
            prefs[KEY] = encode(current + record)
        }
    }

    private fun encode(records: List<ProbeRecord>): String = json.encodeToString(records)

    private fun decode(raw: String): List<ProbeRecord> = json.decodeFromString(raw)

    private companion object {
        val KEY = stringPreferencesKey("probe_records_json")
        val json = Json { ignoreUnknownKeys = true }
    }
}
