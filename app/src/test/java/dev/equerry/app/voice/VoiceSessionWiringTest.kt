package dev.equerry.app.voice

import dev.equerry.app.assistant.VoiceSessionCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Verifies t-10's testable wiring seam (the framework session itself is verified manually). The
 * coordinator must check mic permission BEFORE arming STT, request the mic instead of arming when
 * denied, and release exactly once on destroy.
 */
class VoiceSessionWiringTest {

    @Test
    fun on_show_checks_permission_then_arms_when_granted() {
        val events = mutableListOf<String>()
        val coordinator = VoiceSessionCoordinator(
            isMicGranted = { events.add("check"); true },
            arm = { events.add("arm") },
            release = { events.add("release") },
            requestMic = { events.add("requestMic") },
        )

        coordinator.onShow()

        // Permission is checked, THEN STT is armed. Arming before the check would change this order.
        assertEquals(listOf("check", "arm"), events)
    }

    @Test
    fun on_show_requests_mic_and_never_arms_when_denied() {
        val events = mutableListOf<String>()
        val coordinator = VoiceSessionCoordinator(
            isMicGranted = { events.add("check"); false },
            arm = { events.add("arm") },
            release = { events.add("release") },
            requestMic = { events.add("requestMic") },
        )

        coordinator.onShow()

        assertEquals(listOf("check", "requestMic"), events)
        assertFalse("STT must not be armed when the mic is denied", events.contains("arm"))
    }

    @Test
    fun on_destroy_releases_once_per_call() {
        var releases = 0
        val coordinator = VoiceSessionCoordinator(
            isMicGranted = { true },
            arm = {},
            release = { releases += 1 },
            requestMic = {},
        )

        coordinator.onDestroy()

        assertEquals(1, releases)
    }
}
