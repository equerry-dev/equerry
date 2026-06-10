package dev.equerry.app.tools.actions

/**
 * A single action derived from one model tool call. [callId] is the provider's tool-call id when
 * supplied (for correlation), else null.
 *
 * - [Staged] actions are benign and on-device (timer/alarm) — they fire only after a "Start now?"
 *   confirm (tap or spoken yes), never automatically.
 * - [Handoff] actions are outward (calendar/email/sms) — they open a pre-filled system app where the
 *   user commits; Equerry never sends/saves itself (r-02).
 * - [Malformed] is a tool call Equerry couldn't turn into a valid action (unknown tool, bad/missing
 *   args) — surfaced as guidance, never thrown.
 */
sealed interface PlannedAction {

    val callId: String?

    sealed interface Staged : PlannedAction {
        data class Timer(val seconds: Int, val label: String?, override val callId: String?) : Staged
        data class Alarm(val hour: Int, val minute: Int, val label: String?, override val callId: String?) : Staged
    }

    sealed interface Handoff : PlannedAction {
        data class Calendar(
            val title: String,
            val beginMillis: Long,
            val endMillis: Long?,
            val location: String?,
            override val callId: String?,
        ) : Handoff

        data class Email(val to: String, val subject: String?, val body: String?, override val callId: String?) : Handoff
        data class Sms(val to: String?, val body: String, override val callId: String?) : Handoff
    }

    data class Malformed(val toolName: String, val reason: String, override val callId: String?) : PlannedAction
}
