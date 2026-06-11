package dev.equerry.app.voice

/**
 * Narrow classifier for the spoken "Start now?" confirm. Deliberately narrow: only a clear
 * affirmative or negative phrase matches; anything ambiguous or off-topic is [Verdict.UNRECOGNISED],
 * so a mis-heard or stray utterance can NEVER collapse to YES and fire a staged action
 * (timer_alarm_confirm mis-recognition safeguard).
 */
object YesNoGrammar {

    enum class Verdict { YES, NO, UNRECOGNISED }

    private val YES = setOf(
        "yes", "yeah", "yep", "yup", "yes please", "sure", "ok", "okay",
        "start", "start it", "go", "go ahead", "do it", "confirm",
    )
    private val NO = setOf(
        "no", "nope", "cancel", "stop", "don't", "do not",
        "nevermind", "never mind", "no thanks", "no thank you",
    )

    fun classify(transcript: String): Verdict {
        val norm = transcript.lowercase()
            .replace(Regex("[^a-z0-9' ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return when (norm) {
            in YES -> Verdict.YES
            in NO -> Verdict.NO
            else -> Verdict.UNRECOGNISED
        }
    }
}
