package dev.equerry.app.ui.providers

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
class ProviderListScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun profile(label: String, type: ProviderType) =
        ProviderProfile(id = label, label = label, type = type, baseUrl = "http://localhost:11434", model = "m")

    @Test
    fun renders_a_row_per_profile_label() {
        val profiles = listOf(
            profile("Home Ollama", ProviderType.OLLAMA),
            profile("Work Claude", ProviderType.ANTHROPIC),
        )
        compose.setContent {
            EquerryTheme { ProviderListScreen(profiles, onAdd = {}, onEdit = {}, onDelete = {}) }
        }

        compose.onNodeWithText("Home Ollama").assertIsDisplayed()
        compose.onNodeWithText("Work Claude").assertIsDisplayed()
    }

    @Test
    fun overflow_opens_the_delete_confirmation() {
        val profiles = listOf(profile("Home Ollama", ProviderType.OLLAMA))
        compose.setContent {
            EquerryTheme { ProviderListScreen(profiles, onAdd = {}, onEdit = {}, onDelete = {}) }
        }

        compose.onNodeWithContentDescription("More options for Home Ollama").performClick()
        compose.onNodeWithText("Delete provider?").assertIsDisplayed()
    }

    @Test
    fun empty_state_shows_a_call_to_action() {
        compose.setContent {
            EquerryTheme { ProviderListScreen(emptyList(), onAdd = {}, onEdit = {}, onDelete = {}) }
        }
        compose.onNodeWithText("No providers yet").assertIsDisplayed()
        compose.onNodeWithText("Add your first provider").assertIsDisplayed()
    }
}
