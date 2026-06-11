package dev.equerry.app.voice

/**
 * Narrow classifier for the spoken "ask about my screen" trigger. Deliberately conservative: only
 * an utterance that clearly references the screen/page routes to the screen-context flow; anything
 * else is treated as an ordinary question and goes to the normal chat send. Matches normalised
 * substrings so STT phrasing variations ("what's on my screen", "what am I looking at") still hit,
 * without firing on unrelated questions.
 */
object ScreenQueryGrammar {

    private val PHRASES = listOf(
        "on my screen", "on the screen", "on this screen", "on screen",
        "what am i looking at", "what am i seeing", "see on my screen",
        "describe my screen", "describe the screen", "describe this screen",
        "read my screen", "read the screen", "my screen say", "the screen say",
        "on the page",
    )

    fun isScreenQuery(transcript: String): Boolean {
        val norm = transcript.lowercase()
            .replace(Regex("[^a-z0-9' ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (norm.isBlank()) return false
        return PHRASES.any { norm.contains(it) }
    }
}
