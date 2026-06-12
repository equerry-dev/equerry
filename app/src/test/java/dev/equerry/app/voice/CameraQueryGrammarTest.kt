package dev.equerry.app.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraQueryGrammarTest {

    @Test
    fun recognises_camera_referencing_utterances() {
        assertTrue(CameraQueryGrammar.isCameraQuery("What am I looking at through the camera?"))
        assertTrue(CameraQueryGrammar.isCameraQuery("look through the camera and tell me"))
        assertTrue(CameraQueryGrammar.isCameraQuery("use the camera to see this"))
        assertTrue(CameraQueryGrammar.isCameraQuery("what's in front of me"))
        assertTrue(CameraQueryGrammar.isCameraQuery("what am I pointing at"))
    }

    @Test
    fun ignores_screen_and_ordinary_utterances() {
        // No camera mention — these belong to the on-screen path, not the camera path.
        assertFalse(CameraQueryGrammar.isCameraQuery("what's on my screen"))
        assertFalse(CameraQueryGrammar.isCameraQuery("what am I looking at"))
        assertFalse(CameraQueryGrammar.isCameraQuery("what's the weather today"))
        assertFalse(CameraQueryGrammar.isCameraQuery(""))
        assertFalse(CameraQueryGrammar.isCameraQuery("   "))
    }
}
