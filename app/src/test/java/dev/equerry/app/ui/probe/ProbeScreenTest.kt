package dev.equerry.app.ui.probe

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.equerry.app.assistant.ProbeRecord
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
class ProbeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun rec(
        i: Int = 1,
        nodeCount: Int = 5,
        arrived: Boolean = true,
        blocked: Boolean = false,
        width: Int? = 1080,
        height: Int? = 2400,
    ) = ProbeRecord(
        packageName = "com.example.app$i",
        timestamp = i.toLong(),
        structureProvided = true,
        nodeCount = nodeCount,
        screenshotArrived = arrived,
        screenshotBlocked = blocked,
        screenshotWidth = width,
        screenshotHeight = height,
    )

    @Test
    fun session_screen_shows_capture_fields() {
        compose.setContent { EquerryTheme { ProbeSessionScreen(rec()) } }
        compose.onNodeWithText("com.example.app1").assertIsDisplayed()
        compose.onNodeWithText("AssistStructure: yes (5 nodes)").assertIsDisplayed()
        compose.onNodeWithText("Screenshot: arrived (1080x2400)").assertIsDisplayed()
    }

    @Test
    fun session_screen_shows_blocked_screenshot() {
        compose.setContent {
            EquerryTheme { ProbeSessionScreen(rec(arrived = false, blocked = true, width = null, height = null)) }
        }
        compose.onNodeWithText("Screenshot: blocked").assertIsDisplayed()
    }

    @Test
    fun log_screen_shows_row_per_record_and_export_affordance() {
        var exported = false
        compose.setContent {
            EquerryTheme { ProbeLogScreen(listOf(rec(1), rec(2)), onExport = { exported = true }) }
        }
        compose.onNodeWithText("com.example.app1").assertIsDisplayed()
        compose.onNodeWithText("com.example.app2").assertIsDisplayed()

        compose.onNodeWithText("Export CSV").assertIsDisplayed().performClick()
        assertTrue(exported)
    }

    @Test
    fun csv_share_intent_has_action_mime_and_payload() {
        val csv = "package,nodes\r\ncom.example.app,5"
        val intent = ProbeShare.csvShareIntent(csv)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/csv", intent.type)
        assertEquals(csv, intent.getStringExtra(Intent.EXTRA_TEXT))
    }
}
