package dev.equerry.app.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopGrammarTest {

    @Test
    fun recognises_end_session_commands() {
        assertTrue(StopGrammar.isStop("Equerry off"))
        assertTrue(StopGrammar.isStop("equerry, stop"))
        assertTrue(StopGrammar.isStop("stop listening please"))
        assertTrue(StopGrammar.isStop("Goodbye Equerry."))
        assertTrue(StopGrammar.isStop("end the session"))
        assertTrue(StopGrammar.isStop("turn yourself off"))
    }

    @Test
    fun ignores_ordinary_utterances() {
        assertFalse(StopGrammar.isStop("where is the nearest bus stop"))
        assertFalse(StopGrammar.isStop("what's the weather today"))
        assertFalse(StopGrammar.isStop("turn off the kitchen light")) // not a session-end intent
        assertFalse(StopGrammar.isStop(""))
        assertFalse(StopGrammar.isStop("   "))
    }
}
