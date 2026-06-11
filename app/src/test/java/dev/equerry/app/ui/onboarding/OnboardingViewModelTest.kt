package dev.equerry.app.ui.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import dev.equerry.app.assistant.DefaultAssistantDetector
import dev.equerry.app.assistant.DefaultAssistantState
import dev.equerry.app.data.OnboardingStore
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OnboardingViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Controllable detector — never touches Settings.Secure, so the VM logic is tested in isolation. */
    private class FakeDetector(ctx: Context, var current: DefaultAssistantState) : DefaultAssistantDetector(ctx) {
        override fun state(): DefaultAssistantState = current
    }

    private class FakeSecretStore : SecretStore {
        private val keys = mutableMapOf<String, String>()
        override fun putKey(profileId: String, key: String) { keys[profileId] = key }
        override fun getKey(profileId: String): String? = keys[profileId]
        override fun removeKey(profileId: String) { keys.remove(profileId) }
    }

    private lateinit var scope: CoroutineScope
    private lateinit var repository: ProviderRepository
    private lateinit var onboardingStore: OnboardingStore
    private lateinit var detector: FakeDetector

    private fun viewModel(): OnboardingViewModel = OnboardingViewModel(detector, onboardingStore, repository)

    private fun chatDraft() = ProfileDraft("Home Ollama", ProviderType.OLLAMA, "http://localhost:11434", "", "llama3.2")

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        scope = CoroutineScope(Dispatchers.IO + Job())
        val file = java.io.File(tmp.newFolder(), "onboarding.preferences_pb")
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }
        repository = ProviderRepository(ProfileStore(ds), FakeSecretStore(), SlotMappingStore(ds))
        onboardingStore = OnboardingStore(ds)
        detector = FakeDetector(context, DefaultAssistantState.NOT_DEFAULT)
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        Dispatchers.resetMain()
    }

    @Test
    fun advances_in_the_locked_step_order() {
        val vm = viewModel()
        assertEquals(OnboardingStep.WELCOME, vm.state.value.step)
        vm.advance(); assertEquals(OnboardingStep.SET_DEFAULT, vm.state.value.step)
        vm.advance(); assertEquals(OnboardingStep.MAP_CHAT, vm.state.value.step)
        vm.advance(); assertEquals(OnboardingStep.DONE, vm.state.value.step)
    }

    @Test
    fun set_default_step_is_deferrable_when_not_default_or_unknown() {
        val vm = viewModel()
        vm.advance() // -> SET_DEFAULT

        detector.current = DefaultAssistantState.NOT_DEFAULT
        vm.refresh()
        vm.advance()
        assertEquals("not-default must not dead-end the set-default step", OnboardingStep.MAP_CHAT, vm.state.value.step)

        // And the same from UNKNOWN, from a fresh VM.
        val vm2 = viewModel()
        vm2.advance() // -> SET_DEFAULT
        detector.current = DefaultAssistantState.UNKNOWN
        vm2.refresh()
        vm2.advance()
        assertEquals("unknown must not dead-end the set-default step", OnboardingStep.MAP_CHAT, vm2.state.value.step)
    }

    @Test
    fun default_state_flips_to_true_after_refresh_on_return() {
        detector.current = DefaultAssistantState.NOT_DEFAULT
        val vm = viewModel()
        assertFalse(vm.state.value.isDefaultAssistant)

        // User flips Equerry to default in system Settings, then returns.
        detector.current = DefaultAssistantState.IS_DEFAULT
        vm.refresh()
        assertTrue(vm.state.value.isDefaultAssistant)
    }

    @Test
    fun completion_does_not_flip_before_chat_is_mapped() = runBlocking {
        val vm = viewModel()

        vm.finish() // no CHAT mapping yet
        assertFalse("completed must not flip without a CHAT mapping", vm.state.value.completed)
    }

    @Test
    fun completion_flips_once_chat_is_mapped() = runBlocking {
        val vm = viewModel()
        val profile = repository.addProfile(chatDraft())
        repository.setChatSlot(profile.id)
        vm.state.first { it.chatMapped }

        vm.finish()

        assertTrue(vm.state.first { it.completed }.completed)
    }

    @Test
    fun dismiss_leaves_setup_pending_and_not_completed() {
        val vm = viewModel()
        vm.advance() // mid-wizard

        vm.dismiss()

        assertFalse(vm.state.value.completed)
        assertTrue(vm.state.value.pendingSetup)
    }
}
