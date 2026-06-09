package dev.equerry.app

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Smoke test for the JVM test toolchain wired in t-2: Robolectric resolves an Android
 * Context and Compose UI tests render, both under `./gradlew testDebugUnitTest`.
 *
 * Pinned to SDK 34 — Robolectric 4.14 has no android-all jar for compileSdk 36. Uses a
 * plain Application (not the @HiltAndroidApp EquerryApplication) so the smoke check does
 * not depend on the Hilt graph; later Robolectric tests follow the same SDK/app pattern.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToolchainSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun robolectric_resolves_application_context() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("dev.equerry.app", context.packageName)
    }

    @Test
    fun compose_ui_test_renders_a_node() {
        composeRule.setContent {
            Text("toolchain-ok")
        }
        composeRule.onNodeWithText("toolchain-ok").assertIsDisplayed()
    }
}
