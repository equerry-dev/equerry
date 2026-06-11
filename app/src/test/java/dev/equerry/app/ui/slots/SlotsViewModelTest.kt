package dev.equerry.app.ui.slots

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.equerry.app.data.ProfileStore
import dev.equerry.app.data.SecretStore
import dev.equerry.app.data.SlotMappingStore
import dev.equerry.app.providers.CapabilitySlot
import dev.equerry.app.providers.ProfileDraft
import dev.equerry.app.providers.ProviderRepository
import dev.equerry.app.providers.ProviderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SlotsViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeSecretStore : SecretStore {
        private val keys = mutableMapOf<String, String>()
        override fun putKey(profileId: String, key: String) { keys[profileId] = key }
        override fun getKey(profileId: String): String? = keys[profileId]
        override fun removeKey(profileId: String) { keys.remove(profileId) }
    }

    private lateinit var scope: CoroutineScope
    private lateinit var repository: ProviderRepository
    private lateinit var vm: SlotsViewModel

    private fun draft(label: String) =
        ProfileDraft(label, ProviderType.OLLAMA, "http://localhost:11434", "", "llama3.2")

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        scope = CoroutineScope(Dispatchers.IO + Job())
        val file = File(tmp.newFolder(), "s.preferences_pb")
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }
        repository = ProviderRepository(ProfileStore(ds), FakeSecretStore(), SlotMappingStore(ds))
        vm = SlotsViewModel(repository)
    }

    @After
    fun tearDown() {
        // Stop DataStore emissions before releasing Main, else producer threads race resetMain().
        runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        Dispatchers.resetMain()
    }

    @Test
    fun wired_slots_are_active_and_only_ocr_and_embeddings_are_not() {
        assertTrue(CapabilitySlot.CHAT.active)
        assertTrue(CapabilitySlot.VISION.active)
        assertTrue(CapabilitySlot.STT.active)
        assertTrue(CapabilitySlot.TTS.active)
        assertFalse(CapabilitySlot.OCR.active)
        assertFalse(CapabilitySlot.EMBEDDINGS.active)
    }

    @Test
    fun mapping_the_stt_slot_sets_the_stt_profile() = runBlocking {
        val profile = repository.addProfile(
            ProfileDraft("OpenAI", ProviderType.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "sk-o", "whisper-1"),
        )

        vm.map(CapabilitySlot.STT, profile.id)

        val mapped = vm.state.first { it.sttProfile != null }
        assertEquals(profile.id, mapped.sttProfile?.id)
        assertEquals(profile.id, mapped.mappedProfile(CapabilitySlot.STT)?.id)
    }

    @Test
    fun stt_picker_excludes_types_without_audio_support() = runBlocking {
        repository.addProfile(ProfileDraft("Local", ProviderType.OLLAMA, "http://localhost:11434", "", "llama3.2"))
        repository.addProfile(
            ProfileDraft("Claude", ProviderType.ANTHROPIC, "https://api.anthropic.com", "sk-a", "claude-sonnet-4-6"),
        )
        val openai = repository.addProfile(
            ProfileDraft("OpenAI", ProviderType.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "sk-o", "whisper-1"),
        )

        val state = vm.state.first { it.profiles.size == 3 }
        // Only the OpenAI-compatible profile is an eligible STT candidate; Ollama/Anthropic are filtered out.
        assertEquals(listOf(openai.id), state.candidatesFor(CapabilitySlot.STT).map { it.id })
    }

    @Test
    fun chat_is_unconfigured_when_no_profile_is_mapped() = runBlocking {
        val state = vm.state.first()
        assertTrue(state.chatUnconfigured)
    }

    @Test
    fun mapping_a_profile_clears_the_unconfigured_state() = runBlocking {
        val profile = repository.addProfile(draft("Home Ollama"))

        vm.map(CapabilitySlot.CHAT, profile.id)

        val mapped = vm.state.first { it.chatProfile != null }
        assertEquals(profile.id, mapped.chatProfile?.id)
        assertFalse(mapped.chatUnconfigured)
    }

    @Test
    fun mapping_the_vision_slot_sets_the_vision_profile() = runBlocking {
        val profile = repository.addProfile(draft("Vision Box"))

        vm.map(CapabilitySlot.VISION, profile.id)

        val mapped = vm.state.first { it.visionProfile != null }
        assertEquals(profile.id, mapped.visionProfile?.id)
        assertEquals(profile.id, mapped.mappedProfile(CapabilitySlot.VISION)?.id)
    }
}
