package dev.equerry.app.ui.slots

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.equerry.app.providers.ProviderProfile
import dev.equerry.app.providers.ProviderType
import dev.equerry.app.ui.theme.EquerryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SlotsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun profile(label: String) =
        ProviderProfile(id = label, label = label, type = ProviderType.OLLAMA, baseUrl = "http://localhost:11434", model = "llama3.2")

    @Test
    fun chat_is_active_others_are_coming_soon_with_guidance_when_unmapped() {
        compose.setContent {
            EquerryTheme {
                SlotsScreen(
                    state = SlotsUiState(chatProfile = null, profiles = listOf(profile("Home Ollama"))),
                    onMapChat = {},
                    onAddProvider = {},
                )
            }
        }

        // CHAT active + unmapped, guidance banner present
        compose.onNodeWithText("No provider mapped").assertIsDisplayed()
        compose.onNodeWithText("Map a provider to CHAT").assertIsDisplayed()
        // disabled slots render with a SOON pill (LazyColumn only composes visible rows,
        // so assert a disabled capability + at least one SOON rather than a fixed count)
        compose.onNodeWithText("VISION").assertExists()
        compose.onAllNodesWithText("SOON").onFirst().assertExists()
    }

    @Test
    fun tapping_chat_opens_a_picker_listing_profiles() {
        compose.setContent {
            EquerryTheme {
                SlotsScreen(
                    state = SlotsUiState(chatProfile = null, profiles = listOf(profile("Home Ollama"), profile("Work Box"))),
                    onMapChat = {},
                    onAddProvider = {},
                )
            }
        }

        compose.onNodeWithTag("chatSlot").performClick()

        // The picker sheet lists saved profile labels (overlay geometry → assert presence).
        compose.onNodeWithText("Map the CHAT slot").assertExists()
        compose.onNodeWithText("Home Ollama").assertExists()
        compose.onNodeWithText("Work Box").assertExists()
    }
}
