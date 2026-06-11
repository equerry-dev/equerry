package dev.equerry.app.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceVadTest {

    // threshold 1000, window 3 silent frames — pinned so a one-frame change to either the boundary
    // logic or a default breaks these assertions.
    private fun vad() = SilenceVad(amplitudeThreshold = 1000, silenceFrames = 3)

    @Test
    fun trips_exactly_at_the_silence_window_boundary_not_one_frame_early() {
        val vad = vad()
        assertFalse("loud frame starts speech, never stops", vad.onFrame(2000))
        assertFalse("1st silent frame", vad.onFrame(100))
        assertFalse("2nd silent frame — still one short of the window", vad.onFrame(100))
        assertTrue("3rd silent frame hits the window boundary", vad.onFrame(100))
    }

    @Test
    fun a_single_amplitude_dip_does_not_trip() {
        val vad = vad()
        vad.onFrame(2000) // speech started
        // Alternating dip/loud never accumulates the consecutive run.
        repeat(10) {
            assertFalse("single dip", vad.onFrame(100))
            assertFalse("loud resets the run", vad.onFrame(2000))
        }
    }

    @Test
    fun a_never_quiet_sequence_never_trips() {
        val vad = vad()
        repeat(50) { assertFalse(vad.onFrame(5000)) }
    }

    @Test
    fun leading_silence_before_speech_is_ignored_then_window_still_exact() {
        val vad = vad()
        // Silence before the user speaks must not count toward the stop window.
        repeat(20) { assertFalse("pre-speech silence ignored", vad.onFrame(50)) }
        assertFalse("speech starts", vad.onFrame(2000))
        assertFalse(vad.onFrame(100))
        assertFalse(vad.onFrame(100))
        assertTrue("exactly 3 trailing-silence frames after speech", vad.onFrame(100))
    }
}
