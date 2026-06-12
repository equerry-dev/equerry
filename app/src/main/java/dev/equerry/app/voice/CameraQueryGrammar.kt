package dev.equerry.app.voice

/**
 * Narrow classifier for the spoken "look through the camera" trigger — the real-world counterpart to
 * [ScreenQueryGrammar]. Only utterances that clearly reference the camera or the physical scene in
 * front of the user match, so an ordinary "what am I looking at" (no camera mention) still routes to
 * the on-screen path. The voice loop checks this BEFORE [ScreenQueryGrammar] so "camera" always wins.
 */
object CameraQueryGrammar {

    private val PHRASES = listOf(
        "through the camera", "with the camera", "with your camera", "use the camera",
        "using the camera", "look through the camera", "open the camera", "point the camera",
        "look with the camera", "camera and tell me", "see through the camera",
        "what's in front of me", "what is in front of me",
        "what am i holding", "what am i pointing at", "what i'm pointing at",
    )

    fun isCameraQuery(transcript: String): Boolean {
        val norm = transcript.lowercase()
            .replace(Regex("[^a-z0-9' ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (norm.isBlank()) return false
        return PHRASES.any { norm.contains(it) }
    }
}
