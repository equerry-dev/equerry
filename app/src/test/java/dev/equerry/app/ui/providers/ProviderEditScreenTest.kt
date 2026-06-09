package dev.equerry.app.ui.providers

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.equerry.app.data.ProfileStore
import dev.equerry.app.data.SecretStore
import dev.equerry.app.data.SlotMappingStore
import dev.equerry.app.providers.ProviderRepository
import dev.equerry.app.ui.theme.EquerryTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProviderEditScreenTest {

    @get:Rule(order = 0)
    val tmp = TemporaryFolder()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private class FakeSecretStore : SecretStore {
        private val keys = mutableMapOf<String, String>()
        override fun putKey(profileId: String, key: String) { keys[profileId] = key }
        override fun getKey(profileId: String): String? = keys[profileId]
        override fun removeKey(profileId: String) { keys.remove(profileId) }
    }

    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        scope = CoroutineScope(Dispatchers.IO + Job())
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        Dispatchers.resetMain()
    }

    private fun newViewModel(): ProviderEditViewModel {
        val file = File(tmp.newFolder(), "s.preferences_pb")
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }
        val repo = ProviderRepository(ProfileStore(ds), FakeSecretStore(), SlotMappingStore(ds))
        return ProviderEditViewModel(repo)
    }

    private fun setScreen(vm: ProviderEditViewModel) {
        compose.setContent {
            EquerryTheme { ProviderEditRoute(onBack = {}, onSaved = {}, viewModel = vm) }
        }
    }

    @Test
    fun ollama_hides_the_api_key_field() {
        setScreen(newViewModel()) // VM defaults to OLLAMA
        compose.onAllNodesWithText("API key").assertCountEquals(0)
        compose.onNodeWithText("Ollama runs locally — no API key required.").assertIsDisplayed()
    }

    @Test
    fun anthropic_shows_the_api_key_field() {
        setScreen(newViewModel())
        compose.onNodeWithText("Anthropic").performClick()
        compose.onNodeWithText("API key").assertIsDisplayed()
    }

    @Test
    fun invalid_submit_surfaces_an_inline_error() {
        setScreen(newViewModel())
        compose.onNodeWithText("Anthropic").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Anthropic requires an API key.").assertIsDisplayed()
    }

    @Test
    fun model_presets_are_offered_for_the_selected_type() {
        setScreen(newViewModel())
        compose.onNodeWithText("Anthropic").performClick()
        // Presets render as inline quick-pick chips for the selected type. The chip sits
        // below the fold in the scrollable form, so assert presence rather than display.
        compose.onNodeWithText("claude-sonnet-4-6").assertExists()
    }
}
