package dev.equerry.app.ui.providers

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.equerry.app.data.ProfileStore
import dev.equerry.app.data.SecretStore
import dev.equerry.app.data.SlotMappingStore
import dev.equerry.app.providers.ProfileDraft
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderListViewModelTest {

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
    private lateinit var vm: ProviderListViewModel

    private fun draft(label: String) =
        ProfileDraft(label, ProviderType.OLLAMA, "http://localhost:11434", "", "llama3.2")

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        scope = CoroutineScope(Dispatchers.IO + Job())
        val file = File(tmp.newFolder(), "s.preferences_pb")
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }
        repository = ProviderRepository(ProfileStore(ds), FakeSecretStore(), SlotMappingStore(ds))
        vm = ProviderListViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        scope.cancel()
    }

    @Test
    fun exposes_saved_profiles_by_label() = runBlocking {
        repository.addProfile(draft("Home Ollama"))
        repository.addProfile(draft("Work Box"))

        val shown = vm.profiles.first { it.size == 2 }
        assertEquals(setOf("Home Ollama", "Work Box"), shown.map { it.label }.toSet())
    }

    @Test
    fun delete_removes_a_profile() = runBlocking {
        repository.addProfile(draft("Keep"))
        val victim = repository.addProfile(draft("Delete me"))
        vm.profiles.first { it.size == 2 }

        vm.delete(victim.id)

        val after = vm.profiles.first { list -> list.none { it.id == victim.id } }
        assertEquals(1, after.size)
        assertTrue(after.any { it.label == "Keep" })
    }
}
