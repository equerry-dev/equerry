package dev.equerry.app.voice

import dev.equerry.app.data.SpeakTiming
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeakChunkerTest {

    @Test
    fun sentence_mode_emits_each_completed_sentence_and_flushes_the_tail() {
        val chunker = SpeakChunker(SpeakTiming.SENTENCE_BY_SENTENCE)

        assertEquals(listOf("Hi."), chunker.feed("Hi. "))
        assertEquals(emptyList<String>(), chunker.feed("How are"))
        assertEquals(listOf("How are you?"), chunker.feed(" you? Bye."))
        // "Bye." has no trailing whitespace, so it stays buffered until finish().
        assertEquals(listOf("Bye."), chunker.finish())
    }

    @Test
    fun sentence_mode_full_sequence_matches_the_expected_utterances() {
        val chunker = SpeakChunker(SpeakTiming.SENTENCE_BY_SENTENCE)
        val utterances = buildList {
            addAll(chunker.feed("Hi. "))
            addAll(chunker.feed("How are"))
            addAll(chunker.feed(" you? Bye."))
            addAll(chunker.finish())
        }
        assertEquals(listOf("Hi.", "How are you?", "Bye."), utterances)
    }

    @Test
    fun whole_reply_mode_holds_until_done_then_emits_one_utterance() {
        val chunker = SpeakChunker(SpeakTiming.WHOLE_REPLY)

        assertEquals(emptyList<String>(), chunker.feed("Hi. "))
        assertEquals(emptyList<String>(), chunker.feed("How are"))
        assertEquals(emptyList<String>(), chunker.feed(" you? Bye."))
        // Exactly one utterance equal to the whole reply, only at done.
        assertEquals(listOf("Hi. How are you? Bye."), chunker.finish())
    }
}
