package dev.equerry.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class YesNoGrammarTest {

    @Test
    fun clear_affirmatives_classify_as_yes() {
        for (s in listOf("yes", "Yeah", "start it", "yes please", "OK.")) {
            assertEquals("'$s' should be YES", YesNoGrammar.Verdict.YES, YesNoGrammar.classify(s))
        }
    }

    @Test
    fun clear_negatives_classify_as_no() {
        for (s in listOf("no", "Nope", "cancel", "stop")) {
            assertEquals("'$s' should be NO", YesNoGrammar.Verdict.NO, YesNoGrammar.classify(s))
        }
    }

    @Test
    fun ambiguous_or_offtopic_is_unrecognised_and_never_yes() {
        for (s in listOf("what time is it", "maybe", "i think so", "tomorrow")) {
            val v = YesNoGrammar.classify(s)
            assertEquals("'$s' should be UNRECOGNISED", YesNoGrammar.Verdict.UNRECOGNISED, v)
            assertNotEquals("'$s' must never fire (YES)", YesNoGrammar.Verdict.YES, v)
        }
    }
}
