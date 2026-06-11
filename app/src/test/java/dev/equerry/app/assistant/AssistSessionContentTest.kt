package dev.equerry.app.assistant

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.equerry.app.ui.chat.ChatUiState
import dev.equerry.app.ui.theme.EquerryTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Covers c-1's on-screen button surface, now that the assist session's content is an extractable
 * composable. Renders [AssistSessionContent] without a VoiceInteractionSession and verifies the
 * "Ask about this screen" button is wired to its callback and the screen-context note renders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssistSessionContentTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setContent(state: ChatUiState = ChatUiState(), onAskScreen: () -> Unit = {}) {
        compose.setContent {
            EquerryTheme {
                AssistSessionContent(
                    state = state,
                    guidance = null,
                    onAskScreen = onAskScreen,
                    onInput = {},
                    onSend = {},
                    onNewChat = {},
                )
            }
        }
    }

    @Test
    fun tapping_ask_about_this_screen_invokes_the_callback() {
        var asked = false
        setContent(onAskScreen = { asked = true })

        compose.onNodeWithTag("askScreenButton").performClick()

        assertTrue("the 'Ask about this screen' button must invoke onAskScreen (c-1)", asked)
    }

    @Test
    fun the_screen_context_note_is_rendered_when_present() {
        setContent(state = ChatUiState(screenNote = "Couldn't read this screen — it may block assistant capture."))

        compose.onNodeWithText("Couldn't read this screen — it may block assistant capture.").assertIsDisplayed()
    }
}
