package dev.equerry.app.ui.chat

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.equerry.app.providers.drivers.ChatMessage
import dev.equerry.app.providers.drivers.ChatRole
import dev.equerry.app.tools.actions.PlannedAction
import dev.equerry.app.ui.theme.EquerryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun mic_button_fires_onMic_when_tapped() {
        var micTapped = false
        compose.setContent {
            EquerryTheme {
                ChatScreen(
                    state = ChatUiState(),
                    onInput = {},
                    onSend = {},
                    onNewChat = {},
                    onMic = { micTapped = true },
                )
            }
        }
        compose.onNodeWithContentDescription("Speak").performClick()
        assertTrue("tapping the mic must start the voice round-trip", micTapped)
    }

    @Test
    fun listening_indicator_shows_while_listening() {
        compose.setContent {
            EquerryTheme {
                ChatScreen(
                    state = ChatUiState(),
                    onInput = {},
                    onSend = {},
                    onNewChat = {},
                    listening = true,
                    onMic = {},
                )
            }
        }
        compose.onNodeWithText("Listening", substring = true).assertIsDisplayed()
    }

    @Test
    fun staged_timer_renders_start_cancel_card_and_fires_callbacks() {
        var confirmed = -1
        var cancelled = -1
        compose.setContent {
            EquerryTheme {
                ChatScreen(
                    state = ChatUiState(pendingActions = listOf(PlannedAction.Staged.Timer(300, null, "c1"))),
                    onInput = {}, onSend = {}, onNewChat = {},
                    onConfirmAction = { confirmed = it },
                    onCancelAction = { cancelled = it },
                )
            }
        }
        // Single staged action -> Start/Cancel card. The timer never fires without the tap.
        compose.onNodeWithText("Start").assertIsDisplayed()
        compose.onNodeWithText("Start").performClick()
        assertEquals(0, confirmed)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, cancelled)
    }

    @Test
    fun multi_action_list_renders_start_open_and_skip() {
        compose.setContent {
            EquerryTheme {
                ChatScreen(
                    state = ChatUiState(
                        pendingActions = listOf(
                            PlannedAction.Staged.Timer(60, null, "c0"),
                            PlannedAction.Handoff.Email("a@b.com", null, null, "c1"),
                        ),
                    ),
                    onInput = {}, onSend = {}, onNewChat = {},
                )
            }
        }
        compose.onNodeWithText("Start").assertIsDisplayed() // timer row
        compose.onNodeWithText("Open").assertIsDisplayed() // hand-off row
        compose.onAllNodesWithText("Skip").assertCountEquals(2) // each row skippable
    }

    @Test
    fun capability_banner_renders_with_settings_link_when_tools_unsupported() {
        var openedSettings = false
        compose.setContent {
            EquerryTheme {
                ChatScreen(
                    state = ChatUiState(toolsUnsupported = true),
                    onInput = {}, onSend = {}, onNewChat = {},
                    onOpenSettings = { openedSettings = true },
                )
            }
        }
        compose.onNodeWithText("tool-capable provider", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Change in Settings").performClick()
        assertTrue("banner link routes to settings", openedSettings)
    }

    @Test
    fun action_guidance_renders_with_settings_link() {
        compose.setContent {
            EquerryTheme {
                ChatScreen(
                    state = ChatUiState(actionGuidance = "Some requested actions couldn't be run with this provider."),
                    onInput = {}, onSend = {}, onNewChat = {},
                )
            }
        }
        compose.onNodeWithText("couldn't be run", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }
}
