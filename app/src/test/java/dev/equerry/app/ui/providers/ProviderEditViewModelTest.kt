package dev.equerry.app.ui.providers

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.equerry.app.data.ProfileStore
import dev.equerry.app.data.SecretStore
import dev.equerry.app.data.SlotMappingStore
import dev.equerry.app.providers.ProfileDraft
import dev.equerry.app.providers.ProfileField
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderEditViewModelTest {

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
    private lateinit var vm: ProviderEditViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        scope = CoroutineScope(Dispatchers.IO + Job())
        val file = File(tmp.newFolder(), "s.preferences_pb")
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }
        repository = ProviderRepository(ProfileStore(ds), FakeSecretStore(), SlotMappingStore(ds))
        vm = ProviderEditViewModel(repository)
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        Dispatchers.resetMain()
    }

    @Test
    fun anthropic_prefills_base_url_and_requires_key() {
        vm.selectType(ProviderType.ANTHROPIC)
        val s = vm.state.value
        assertEquals("https://api.anthropic.com", s.baseUrl)
        assertTrue(s.keyFieldVisible)
    }

    @Test
    fun selecting_type_surfaces_its_model_presets() {
        vm.selectType(ProviderType.ANTHROPIC)
        assertEquals(ProviderType.ANTHROPIC.modelPresets, vm.state.value.modelPresets)
        assertTrue(vm.state.value.modelPresets.isNotEmpty())
    }

    @Test
    fun empty_key_blocks_save_and_shows_inline_error() = runBlocking {
        vm.selectType(ProviderType.ANTHROPIC)
        vm.onLabelChange("Work")
        vm.onModelChange("claude-sonnet-4-6")
        vm.onKeyChange("")
        vm.save()

        assertNotNull("expected an inline KEY error", vm.state.value.errorFor(ProfileField.KEY))
        assertTrue("invalid save must not persist a profile", repository.observeProfiles().first().isEmpty())
    }

    @Test
    fun valid_ollama_save_adds_exactly_one_profile() = runBlocking {
        vm.selectType(ProviderType.OLLAMA)
        vm.onLabelChange("Home Ollama")
        vm.onModelChange("llama3.2")
        vm.save()

        val profiles = repository.observeProfiles().first { it.isNotEmpty() }
        assertEquals(1, profiles.size)
        assertEquals("Home Ollama", profiles.first().label)
    }

    @Test
    fun requestParamsSaved() = runBlocking {
        vm.selectType(ProviderType.OLLAMA)
        vm.onLabelChange("Tuned Ollama")
        vm.onModelChange("llama3.2")
        vm.onSystemPromptChange("be brief")
        vm.onTemperatureChange("0.7")
        vm.onMaxTokensChange("256")
        vm.save()

        val profile = repository.observeProfiles().first { it.isNotEmpty() }.first()
        assertEquals("be brief", profile.systemPrompt)
        assertEquals(0.7, profile.temperature!!, 0.0001)
        assertEquals(256, profile.maxTokens)
    }

    @Test
    fun tts_voice_field_is_visible_only_for_tts_capable_types_and_persists_on_save() = runBlocking {
        // Visibility tracks the type's TTS capability (only OPENAI_COMPATIBLE in v0.1).
        vm.selectType(ProviderType.OLLAMA)
        assertFalse(vm.state.value.ttsVoiceFieldVisible)
        vm.selectType(ProviderType.OPENAI_COMPATIBLE)
        assertTrue(vm.state.value.ttsVoiceFieldVisible)

        vm.onLabelChange("OpenAI TTS")
        vm.onBaseUrlChange("https://api.openai.com/v1")
        vm.onKeyChange("sk-o")
        vm.onModelChange("tts-1")
        vm.onTtsVoiceChange("alloy")
        vm.save()

        val profile = repository.observeProfiles().first { it.isNotEmpty() }.first()
        assertEquals("alloy", profile.ttsVoice)
    }

    @Test
    fun non_numeric_temperature_blocks_save_with_inline_error() = runBlocking {
        vm.selectType(ProviderType.OLLAMA)
        vm.onLabelChange("Bad")
        vm.onModelChange("llama3.2")
        vm.onTemperatureChange("hot")
        vm.save()

        assertNotNull("expected an inline TEMPERATURE error", vm.state.value.errorFor(ProfileField.TEMPERATURE))
        assertTrue("invalid params must not persist a profile", repository.observeProfiles().first().isEmpty())
    }

    @Test
    fun load_prefills_the_form_and_marks_editing_without_exposing_the_key() = runBlocking {
        val created = repository.addProfile(
            ProfileDraft("Work Claude", ProviderType.ANTHROPIC, "https://api.anthropic.com", "sk-secret", "claude-opus-4-8"),
        )

        vm.load(created.id)
        val s = vm.state.first { it.editing }

        assertEquals("Work Claude", s.label)
        assertEquals(ProviderType.ANTHROPIC, s.type)
        assertEquals("claude-opus-4-8", s.model)
        assertEquals("", s.key) // the saved secret is never loaded into the form
    }

    @Test
    fun editing_updates_in_place_with_a_blank_key_kept() = runBlocking {
        val created = repository.addProfile(
            ProfileDraft("Old Label", ProviderType.ANTHROPIC, "https://api.anthropic.com", "sk-secret", "claude-sonnet-4-6"),
        )

        vm.load(created.id)
        vm.state.first { it.editing }
        vm.onLabelChange("New Label")
        vm.save() // key left blank — allowed when editing (keeps the saved secret)

        val profiles = repository.observeProfiles().first { list -> list.any { it.label == "New Label" } }
        assertEquals(1, profiles.size) // updated in place, not duplicated
        assertEquals(created.id, profiles.first().id)
    }
}
