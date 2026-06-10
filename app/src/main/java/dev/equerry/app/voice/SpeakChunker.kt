package dev.equerry.app.voice

import dev.equerry.app.data.SpeakTiming

/**
 * Turns a streamed reply (fed as deltas) into TTS utterances according to [SpeakTiming]. Pure and
 * Android-free.
 *
 * - [SpeakTiming.SENTENCE_BY_SENTENCE]: [feed] emits each completed sentence as its boundary
 *   (`.`, `?`, `!` followed by whitespace) streams in; [finish] flushes any trailing fragment.
 * - [SpeakTiming.WHOLE_REPLY]: [feed] emits nothing; [finish] emits exactly one utterance equal to
 *   the whole reply.
 *
 * A single instance handles one reply; create a fresh chunker per turn.
 */
class SpeakChunker(private val timing: SpeakTiming) {

    private val buffer = StringBuilder()

    /** Append a streamed [delta]; returns any utterances now ready to speak (empty for WHOLE_REPLY). */
    fun feed(delta: String): List<String> {
        buffer.append(delta)
        return if (timing == SpeakTiming.SENTENCE_BY_SENTENCE) drainCompletedSentences() else emptyList()
    }

    /** Signal the reply is complete; returns the final utterance(s) and resets the buffer. */
    fun finish(): List<String> {
        val remainder = buffer.toString().trim()
        buffer.setLength(0)
        return if (remainder.isEmpty()) emptyList() else listOf(remainder)
    }

    /**
     * Pulls every complete sentence out of [buffer], leaving the trailing (still-incomplete)
     * fragment behind. A sentence ends at `.`, `?`, or `!` that is followed by whitespace — so a
     * terminator at the very end of the buffer stays buffered until more text (or [finish]) arrives.
     */
    private fun drainCompletedSentences(): List<String> {
        val out = mutableListOf<String>()
        var start = 0
        var i = 0
        while (i < buffer.length) {
            val c = buffer[i]
            if ((c == '.' || c == '?' || c == '!') && i + 1 < buffer.length && buffer[i + 1].isWhitespace()) {
                val sentence = buffer.substring(start, i + 1).trim()
                if (sentence.isNotEmpty()) out.add(sentence)
                var j = i + 1
                while (j < buffer.length && buffer[j].isWhitespace()) j++
                start = j
                i = j
            } else {
                i++
            }
        }
        if (start > 0) {
            val remainder = buffer.substring(start)
            buffer.setLength(0)
            buffer.append(remainder)
        }
        return out
    }
}
