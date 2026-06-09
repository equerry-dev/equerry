package dev.equerry.app.ui.providers

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.equerry.app.data.ProfileStore
import dev.equerry.app.data.SecretStore
import dev.equerry.app.data.SlotMappingStore
import dev.equerry.app.providers.ProfileField
import dev.equerry.app.providers.ProviderRepository
import dev.equerry.app.providers.ProviderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
        Dispatchers.resetMain()
        scope.cancel()
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
}
