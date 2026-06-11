package dev.equerry.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.equerry.app.providers.ProviderProfile
import dev.equerry.app.providers.ProviderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProfileStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newScope() = CoroutineScope(Dispatchers.IO + Job())

    private fun dataStore(scope: CoroutineScope, file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { file }

    private suspend fun CoroutineScope.close() = coroutineContext[Job]!!.cancelAndJoin()

    @Test
    fun saved_profiles_round_trip_across_a_reload() = runBlocking {
        val file = File(tmp.root, "profiles.preferences_pb")
        val profiles = listOf(
            ProviderProfile("id-1", "Home Ollama", ProviderType.OLLAMA, "http://localhost:11434", "llama3.2"),
            ProviderProfile("id-2", "Work Claude", ProviderType.ANTHROPIC, "https://api.anthropic.com", "claude-sonnet-4-6"),
        )

        val scope1 = newScope()
        ProfileStore(dataStore(scope1, file)).save(profiles)
        scope1.close()

        // A fresh DataStore over the same file simulates an app restart.
        val scope2 = newScope()
        val loaded = ProfileStore(dataStore(scope2, file)).profiles().first()
        scope2.close()

        assertEquals(profiles, loaded)
    }

    @Test
    fun request_params_round_trip_across_a_reload() = runBlocking {
        val file = File(tmp.root, "params.preferences_pb")
        val profiles = listOf(
            ProviderProfile(
                "id-1", "Tuned Claude", ProviderType.ANTHROPIC, "https://api.anthropic.com",
                "claude-sonnet-4-6", systemPrompt = "be brief", temperature = 0.7, maxTokens = 512,
            ),
        )

        val scope1 = newScope()
        ProfileStore(dataStore(scope1, file)).save(profiles)
        scope1.close()

        val scope2 = newScope()
        val loaded = ProfileStore(dataStore(scope2, file)).profiles().first()
        scope2.close()

        assertEquals(profiles, loaded)
    }

    @Test
    fun legacyJsonStillDecodes() = runBlocking {
        val file = File(tmp.root, "legacy.preferences_pb")
        // JSON written before the request-param fields existed (no systemPrompt/temperature/maxTokens).
        val legacy =
            """[{"id":"id-1","label":"Old","type":"OLLAMA","baseUrl":"http://localhost:11434","model":"llama3.2"}]"""

        val scope1 = newScope()
        dataStore(scope1, file).edit { it[stringPreferencesKey("profiles_json")] = legacy }
        scope1.close()

        val scope2 = newScope()
        val loaded = ProfileStore(dataStore(scope2, file)).profiles().first()
        scope2.close()

        assertEquals(
            listOf(ProviderProfile("id-1", "Old", ProviderType.OLLAMA, "http://localhost:11434", "llama3.2")),
            loaded,
        )
    }

    @Test
    fun tts_voice_round_trips_across_a_reload() = runBlocking {
        val file = File(tmp.root, "voice.preferences_pb")
        val profiles = listOf(
            ProviderProfile(
                "id-1", "Whisper+TTS", ProviderType.OPENAI_COMPATIBLE, "https://api.openai.com/v1",
                "tts-1", ttsVoice = "alloy",
            ),
        )

        val scope1 = newScope()
        ProfileStore(dataStore(scope1, file)).save(profiles)
        scope1.close()

        val scope2 = newScope()
        val loaded = ProfileStore(dataStore(scope2, file)).profiles().first()
        scope2.close()

        assertEquals(profiles, loaded)
        assertEquals("alloy", loaded.single().ttsVoice)
    }

    @Test
    fun legacy_json_without_tts_voice_decodes_to_null() = runBlocking {
        val file = File(tmp.root, "legacy_voice.preferences_pb")
        // JSON written before the ttsVoice field existed.
        val legacy =
            """[{"id":"id-1","label":"Old","type":"OPENAI_COMPATIBLE","baseUrl":"https://api.openai.com/v1","model":"tts-1"}]"""

        val scope1 = newScope()
        dataStore(scope1, file).edit { it[stringPreferencesKey("profiles_json")] = legacy }
        scope1.close()

        val scope2 = newScope()
        val loaded = ProfileStore(dataStore(scope2, file)).profiles().first()
        scope2.close()

        assertEquals(null, loaded.single().ttsVoice)
    }

    @Test
    fun empty_store_returns_empty_list() = runBlocking {
        val file = File(tmp.root, "empty.preferences_pb")
        val scope = newScope()
        val loaded = ProfileStore(dataStore(scope, file)).profiles().first()
        scope.close()
        assertEquals(emptyList<ProviderProfile>(), loaded)
    }
}
