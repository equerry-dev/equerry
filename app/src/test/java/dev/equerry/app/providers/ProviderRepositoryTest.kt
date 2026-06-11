package dev.equerry.app.providers

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.equerry.app.data.ProfileStore
import dev.equerry.app.data.SecretStore
import dev.equerry.app.data.SlotMappingStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProviderRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeSecretStore : SecretStore {
        val keys = mutableMapOf<String, String>()
        override fun putKey(profileId: String, key: String) { keys[profileId] = key }
        override fun getKey(profileId: String): String? = keys[profileId]
        override fun removeKey(profileId: String) { keys.remove(profileId) }
    }

    private fun draft(label: String = "Work Claude", key: String = "sk-secret-xyz") =
        ProfileDraft(label, ProviderType.ANTHROPIC, "https://api.anthropic.com", key, "claude-sonnet-4-6")

    private fun openAiDraft(label: String = "Whisper") =
        ProfileDraft(label, ProviderType.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "sk-o", "whisper-1")

    private fun withRepo(
        block: suspend (repo: ProviderRepository, secret: FakeSecretStore, dataStore: DataStore<Preferences>) -> Unit,
    ) = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val file = File(tmp.newFolder(), "settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val secret = FakeSecretStore()
        val repo = ProviderRepository(ProfileStore(dataStore), secret, SlotMappingStore(dataStore))
        try {
            block(repo, secret, dataStore)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }

    @Test
    fun addProfile_stores_key_in_secret_store_not_in_profile_payload() = withRepo { repo, secret, dataStore ->
        val created = repo.addProfile(draft(key = "sk-secret-xyz"))

        // The key reached the secret store...
        assertEquals("sk-secret-xyz", secret.getKey(created.id))
        // ...and is absent from the raw JSON persisted by the profile store.
        val rawJson = dataStore.data.first()[stringPreferencesKey("profiles_json")]
        assertNotNull(rawJson)
        assertFalse("API key must not appear in the profile payload", rawJson!!.contains("sk-secret-xyz"))
    }

    @Test
    fun addProfile_carries_request_params_and_still_keeps_key_out_of_payload() = withRepo { repo, secret, dataStore ->
        val draftWithParams = ProfileDraft(
            label = "Tuned", type = ProviderType.ANTHROPIC, baseUrl = "https://api.anthropic.com",
            key = "sk-secret-xyz", model = "claude-sonnet-4-6",
            systemPrompt = "be brief", temperature = "0.7", maxTokens = "512",
        )

        val created = repo.addProfile(draftWithParams)
        val loaded = repo.observeProfiles().first().first { it.id == created.id }

        assertEquals("be brief", loaded.systemPrompt)
        assertEquals(0.7, loaded.temperature!!, 0.0001)
        assertEquals(512, loaded.maxTokens)
        // The key is still split off to the secret store, never the JSON payload.
        assertEquals("sk-secret-xyz", secret.getKey(created.id))
        val rawJson = dataStore.data.first()[stringPreferencesKey("profiles_json")]
        assertFalse("API key must not appear in the profile payload", rawJson!!.contains("sk-secret-xyz"))
    }

    @Test
    fun deleteProfile_removes_secret_and_clears_chat_mapping() = withRepo { repo, secret, _ ->
        val created = repo.addProfile(draft())
        repo.setChatSlot(created.id)
        assertEquals(created.id, repo.observeChatMapping().first()?.id)

        repo.deleteProfile(created.id)

        assertNull(secret.getKey(created.id))
        assertNull(repo.observeChatMapping().first())
        assertTrue(repo.observeProfiles().first().none { it.id == created.id })
    }

    @Test
    fun setVisionSlot_round_trips_through_observeVisionMapping() = withRepo { repo, _, _ ->
        val created = repo.addProfile(draft(label = "Vision Model"))
        assertNull("VISION starts unmapped", repo.observeVisionMapping().first())

        repo.setVisionSlot(created.id)
        assertEquals(created.id, repo.observeVisionMapping().first()?.id)

        repo.setVisionSlot(null)
        assertNull(repo.observeVisionMapping().first())
    }

    @Test
    fun deleteProfile_clears_vision_mapping_too() = withRepo { repo, _, _ ->
        val created = repo.addProfile(draft())
        repo.setVisionSlot(created.id)
        assertEquals(created.id, repo.observeVisionMapping().first()?.id)

        repo.deleteProfile(created.id)

        assertNull("deleting the mapped profile cascades to the VISION slot", repo.observeVisionMapping().first())
    }

    @Test
    fun setSttSlot_persists_and_resolves_from_a_fresh_repository() = withRepo { repo, secret, dataStore ->
        val created = repo.addProfile(openAiDraft())
        assertNull("STT starts unmapped", repo.observeSttMapping().first())

        repo.setSttSlot(created.id)

        // A fresh repository over the same store simulates an app restart (criterion c-1).
        val restarted = ProviderRepository(ProfileStore(dataStore), secret, SlotMappingStore(dataStore))
        assertEquals(created.id, restarted.observeSttMapping().first()?.id)
    }

    @Test
    fun setTtsSlot_round_trips_through_observeTtsMapping() = withRepo { repo, _, _ ->
        val created = repo.addProfile(openAiDraft("TTS Box"))
        assertNull("TTS starts unmapped", repo.observeTtsMapping().first())

        repo.setTtsSlot(created.id)
        assertEquals(created.id, repo.observeTtsMapping().first()?.id)

        repo.setTtsSlot(null)
        assertNull(repo.observeTtsMapping().first())
    }

    @Test
    fun deleteProfile_clears_stt_and_tts_mappings() = withRepo { repo, _, _ ->
        val created = repo.addProfile(openAiDraft())
        repo.setSttSlot(created.id)
        repo.setTtsSlot(created.id)

        repo.deleteProfile(created.id)

        assertNull("deleting the mapped profile cascades to the STT slot", repo.observeSttMapping().first())
        assertNull("deleting the mapped profile cascades to the TTS slot", repo.observeTtsMapping().first())
    }

    @Test
    fun addProfile_carries_the_tts_voice() = withRepo { repo, _, _ ->
        val created = repo.addProfile(openAiDraft().copy(ttsVoice = "alloy"))
        val loaded = repo.observeProfiles().first().first { it.id == created.id }
        assertEquals("alloy", loaded.ttsVoice)
    }

    @Test
    fun observeProfiles_reflects_add_update_delete() = withRepo { repo, _, _ ->
        val created = repo.addProfile(draft(label = "First"))
        assertTrue(repo.observeProfiles().first().any { it.id == created.id })

        repo.updateProfile(created.copy(label = "Renamed"))
        assertEquals("Renamed", repo.observeProfiles().first().first { it.id == created.id }.label)

        repo.deleteProfile(created.id)
        assertTrue(repo.observeProfiles().first().none { it.id == created.id })
    }
}
