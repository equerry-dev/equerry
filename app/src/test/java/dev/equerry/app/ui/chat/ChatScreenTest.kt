package dev.equerry.app.ui.chat

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import dev.equerry.app.providers.drivers.ChatMessage
import dev.equerry.app.providers.drivers.ChatRole
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
class ChatScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val guidance = "map it to the CHAT slot"

    private fun setScreen(state: ChatUiState) {
        compose.setContent {
            EquerryTheme { ChatScreen(state = state, onInput = {}, onSend = {}, onNewChat = {}) }
        }
    }

    @Test
    fun transcript_bubbles_render_their_text() {
        setScreen(
            ChatUiState(
                transcript = listOf(
                    ChatMessage(ChatRole.USER, "hi there"),
                    ChatMessage(ChatRole.ASSISTANT, "Hello, world"),
                ),
            ),
        )
        compose.onNodeWithText("hi there").assertIsDisplayed()
        compose.onNodeWithText("Hello, world").assertIsDisplayed()
    }

    @Test
    fun guidance_banner_shows_when_unmapped() {
        setScreen(ChatUiState(unmapped = true))
        compose.onNodeWithText(guidance, substring = true).assertIsDisplayed()
    }

    @Test
    fun guidance_banner_hidden_when_a_provider_is_mapped() {
        setScreen(ChatUiState(unmapped = false))
        compose.onAllNodesWithText(guidance, substring = true).assertCountEquals(0)
    }

    @Test
    fun error_line_renders_when_set() {
        setScreen(ChatUiState(error = "Authentication failed. Check the API key configured for this provider."))
        compose.onNodeWithText("Authentication failed", substring = true).assertIsDisplayed()
    }
}
