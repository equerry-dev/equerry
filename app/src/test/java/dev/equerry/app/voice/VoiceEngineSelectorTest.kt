package dev.equerry.app.voice

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.equerry.app.data.ProfileStore
import dev.equerry.app.data.SecretStore
import dev.equerry.app.data.SlotMappingStore
import dev.equerry.app.providers.ProfileDraft
import dev.equerry.app.providers.ProviderProfile
import dev.equerry.app.providers.ProviderRepository
import dev.equerry.app.providers.ProviderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VoiceEngineSelectorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeSecretStore : SecretStore {
        private val keys = mutableMapOf<String, String>()
        override fun putKey(profileId: String, key: String) { keys[profileId] = key }
        override fun getKey(profileId: String): String? = keys[profileId]
        override fun removeKey(profileId: String) { keys.remove(profileId) }
    }

    private class NoopStt : SpeechToText {
        override fun listen(): Flow<SttEvent> = flowOf()
    }

    private class NoopTts : TextToSpeech {
        override suspend fun init() = TtsInitResult.Ready
        override fun speak(utterance: String) = Unit
        override suspend fun speakSentences(sentences: Flow<String>) = Unit
        override suspend fun awaitDone() = Unit
        override fun stop() = Unit
        override fun shutdown() = Unit
    }

    private fun openAiDraft(label: String = "Whisper") =
        ProfileDraft(label, ProviderType.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "sk-o", "whisper-1")

    private fun withRepo(block: suspend (repo: ProviderRepository) -> Unit) = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val file = File(tmp.newFolder(), "settings.preferences_pb")
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }
        val repo = ProviderRepository(ProfileStore(dataStore), FakeSecretStore(), SlotMappingStore(dataStore))
        try {
            block(repo)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }

    @Test
    fun unmapped_stt_resolves_to_system_and_never_constructs_remote() = withRepo { repo ->
        var remoteBuilt = false
        val system = NoopStt()
        val selector = VoiceEngineSelector(
            repository = repo,
            systemStt = { system },
            remoteStt = { _, _ -> remoteBuilt = true; NoopStt() },
            systemTts = { NoopTts() },
            remoteTts = { _, _ -> NoopTts() },
        )

        val resolved = selector.resolveStt()

        assertSame("unmapped STT must use the system engine (c-5 no regression)", system, resolved)
        assertFalse("the remote engine must never be constructed when unmapped", remoteBuilt)
    }

    @Test
    fun mapped_stt_resolves_to_remote_built_with_the_profile_and_key() = withRepo { repo ->
        val profile = repo.addProfile(openAiDraft())
        repo.setSttSlot(profile.id)
        var capturedProfile: ProviderProfile? = null
        var capturedKey: String? = null
        val remote = NoopStt()
        val selector = VoiceEngineSelector(
            repository = repo,
            systemStt = { fail("system STT must not be used when a profile is mapped"); NoopStt() },
            remoteStt = { p, key -> capturedProfile = p; capturedKey = key; remote },
            systemTts = { NoopTts() },
            remoteTts = { _, _ -> NoopTts() },
        )

        val resolved = selector.resolveStt()

        assertSame(remote, resolved)
        assertEquals(profile.id, capturedProfile?.id)
        assertEquals("sk-o", capturedKey)
    }

    @Test
    fun unmapped_tts_resolves_to_system_and_mapped_tts_resolves_to_remote() = withRepo { repo ->
        val system = NoopTts()
        val remote = NoopTts()
        var remoteBuilt = false
        val selector = VoiceEngineSelector(
            repository = repo,
            systemStt = { NoopStt() },
            remoteStt = { _, _ -> NoopStt() },
            systemTts = { system },
            remoteTts = { _, _ -> remoteBuilt = true; remote },
        )

        assertSame(system, selector.resolveTts())
        assertFalse(remoteBuilt)

        val profile = repo.addProfile(openAiDraft("TTS Box"))
        repo.setTtsSlot(profile.id)
        assertSame(remote, selector.resolveTts())
    }

    @Test
    fun is_mapped_predicates_reflect_the_slot_mapping() = withRepo { repo ->
        val selector = VoiceEngineSelector(
            repository = repo,
            systemStt = { NoopStt() },
            remoteStt = { _, _ -> NoopStt() },
            systemTts = { NoopTts() },
            remoteTts = { _, _ -> NoopTts() },
        )

        assertFalse(selector.isSttMapped())
        assertFalse(selector.isTtsMapped())

        val profile = repo.addProfile(openAiDraft())
        repo.setSttSlot(profile.id)
        assertTrue(selector.isSttMapped())
        assertFalse("only STT was mapped", selector.isTtsMapped())

        repo.setTtsSlot(profile.id)
        assertTrue(selector.isTtsMapped())
    }

    @Test
    fun stt_engine_streams_the_remote_transcript_when_mapped() = withRepo { repo ->
        val profile = repo.addProfile(openAiDraft())
        repo.setSttSlot(profile.id)
        val remote = object : SpeechToText {
            override fun listen(): Flow<SttEvent> = flowOf(SttEvent.EndOfSpeech, SttEvent.Final("remote transcript"))
        }
        val selector = VoiceEngineSelector(
            repository = repo,
            systemStt = { fail("system STT must not run when mapped"); NoopStt() },
            remoteStt = { _, _ -> remote },
            systemTts = { NoopTts() },
            remoteTts = { _, _ -> NoopTts() },
        )

        // The switching engine the controller holds re-resolves to the remote engine, so the
        // transcript that flows to chat.send is the remote one — not system STT.
        val events = selector.sttEngine().listen().toList()
        assertEquals(SttEvent.Final("remote transcript"), events.last())
    }
}
