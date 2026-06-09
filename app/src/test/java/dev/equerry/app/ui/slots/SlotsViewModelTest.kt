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
    fun only_chat_slot_is_active() {
        val slots = vm.state.value.slots
        assertTrue(slots.first { it == CapabilitySlot.CHAT }.active)
        assertTrue(slots.filter { it != CapabilitySlot.CHAT }.none { it.active })
    }

    @Test
    fun chat_is_unconfigured_when_no_profile_is_mapped() = runBlocking {
        val state = vm.state.first()
        assertTrue(state.chatUnconfigured)
    }

    @Test
    fun mapping_a_profile_clears_the_unconfigured_state() = runBlocking {
        val profile = repository.addProfile(draft("Home Ollama"))

        vm.mapChatTo(profile.id)

        val mapped = vm.state.first { it.chatProfile != null }
        assertEquals(profile.id, mapped.chatProfile?.id)
        assertFalse(mapped.chatUnconfigured)
    }
}
