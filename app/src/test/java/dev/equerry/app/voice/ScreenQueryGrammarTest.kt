package dev.equerry.app.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenQueryGrammarTest {

    @Test
    fun recognises_screen_referencing_utterances() {
        assertTrue(ScreenQueryGrammar.isScreenQuery("What's on my screen?"))
        assertTrue(ScreenQueryGrammar.isScreenQuery("what am I looking at"))
        assertTrue(ScreenQueryGrammar.isScreenQuery("Describe this screen."))
        assertTrue(ScreenQueryGrammar.isScreenQuery("read the screen to me"))
        assertTrue(ScreenQueryGrammar.isScreenQuery("what does my screen say"))
    }

    @Test
    fun ignores_ordinary_questions() {
        assertFalse(ScreenQueryGrammar.isScreenQuery("what's the weather today"))
        assertFalse(ScreenQueryGrammar.isScreenQuery("set a timer for five minutes"))
        assertFalse(ScreenQueryGrammar.isScreenQuery("who won the game last night"))
        assertFalse(ScreenQueryGrammar.isScreenQuery(""))
        assertFalse(ScreenQueryGrammar.isScreenQuery("   "))
    }
}
