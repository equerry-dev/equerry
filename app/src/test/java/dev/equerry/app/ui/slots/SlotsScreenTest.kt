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
import org.junit.Assert.assertEquals
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
    fun active_slots_show_guidance_when_unmapped_others_are_coming_soon() {
        compose.setContent {
            EquerryTheme {
                SlotsScreen(
                    state = SlotsUiState(chatProfile = null, visionProfile = null, profiles = listOf(profile("Home Ollama"))),
                    onMap = { _, _ -> },
                    onAddProvider = {},
                )
            }
        }

        // CHAT active + unmapped, guidance banner present
        compose.onNodeWithText("Map a provider to CHAT").assertIsDisplayed()
        // VISION is now an active, interactive slot (tagged) — not a "SOON" row.
        compose.onNodeWithTag("visionSlot").assertExists()
        // The still-inactive slots render with a SOON pill.
        compose.onAllNodesWithText("SOON").onFirst().assertExists()
    }

    @Test
    fun tapping_chat_opens_the_chat_picker_listing_profiles() {
        compose.setContent {
            EquerryTheme {
                SlotsScreen(
                    state = SlotsUiState(chatProfile = null, profiles = listOf(profile("Home Ollama"), profile("Work Box"))),
                    onMap = { _, _ -> },
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

    @Test
    fun tapping_vision_opens_the_vision_picker_and_selection_maps_that_slot() {
        var mapped: Pair<dev.equerry.app.providers.CapabilitySlot, String>? = null
        compose.setContent {
            EquerryTheme {
                SlotsScreen(
                    state = SlotsUiState(chatProfile = null, visionProfile = null, profiles = listOf(profile("Vision Box"))),
                    onMap = { slot, id -> mapped = slot to id },
                    onAddProvider = {},
                )
            }
        }

        compose.onNodeWithTag("visionSlot").performClick()
        compose.onNodeWithText("Map the VISION slot").assertExists()
        compose.onNodeWithText("Vision Box").performClick()
        compose.onNodeWithText("Map provider").performClick()

        assertEquals(dev.equerry.app.providers.CapabilitySlot.VISION to "Vision Box", mapped)
    }
}
