package dev.equerry.app.voice

/**
 * Narrow classifier for the spoken "end the auto-listening session" command (e.g. "Equerry off").
 * Deliberately conservative — only clear stop intents match, so an ordinary question that merely
 * contains the word "stop" (e.g. "where's the nearest bus stop") does not end the session. Matches
 * normalised substrings so STT phrasing variations still hit. Mirrors [ScreenQueryGrammar].
 */
object StopGrammar {

    private val PHRASES = listOf(
        "equerry off", "equerry stop", "equerry shut down", "equerry shutdown",
        "stop listening", "stop the session", "end the session", "end session",
        "goodbye equerry", "turn off listening", "turn yourself off", "that will be all",
    )

    fun isStop(transcript: String): Boolean {
        val norm = transcript.lowercase()
            .replace(Regex("[^a-z0-9' ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (norm.isBlank()) return false
        return PHRASES.any { norm.contains(it) }
    }
}
