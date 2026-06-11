package dev.equerry.app.assistant

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the tri-state default-assistant detection (t-1, c-1). Seeds the
 * `voice_interaction_service` secure setting Robolectric-side and asserts the detector
 * never reports IS_DEFAULT without a real component match.
 *
 * Pinned to SDK 34 — Robolectric has no android-all jar for compileSdk 36 (see ToolchainSmokeTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DefaultAssistantDetectorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val detector = DefaultAssistantDetector(context)

    private fun setVoiceInteractionService(value: String?) {
        Settings.Secure.putString(context.contentResolver, "voice_interaction_service", value)
    }

    @Test
    fun `is default when stored component is Equerry's own service`() {
        val ours = ComponentName(context, EquerryVoiceInteractionService::class.java)
        setVoiceInteractionService(ours.flattenToString())

        assertEquals(DefaultAssistantState.IS_DEFAULT, detector.state())
    }

    @Test
    fun `not default when a different OEM component holds the slot`() {
        setVoiceInteractionService("com.oem.assistant/.OemVoiceInteractionService")

        assertEquals(DefaultAssistantState.NOT_DEFAULT, detector.state())
    }

    @Test
    fun `unknown when the setting is absent`() {
        setVoiceInteractionService(null)

        assertEquals(DefaultAssistantState.UNKNOWN, detector.state())
    }

    @Test
    fun `unknown when the setting is blank (never a false IS_DEFAULT)`() {
        setVoiceInteractionService("")

        assertEquals(DefaultAssistantState.UNKNOWN, detector.state())
    }

    @Test
    fun `exposes the system voice-input settings intent`() {
        assertEquals(Settings.ACTION_VOICE_INPUT_SETTINGS, detector.voiceInputSettingsIntent().action)
    }
}
