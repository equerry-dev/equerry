package dev.equerry.app.tools.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Launches a planned action by firing its [Intent]. The interface is a seam so the ViewModel can be
 * unit-tested without touching the Android activity stack. [run] returns whether an activity was
 * actually launched — false when no app can handle the intent — so the caller can post guidance
 * rather than a false success. It never sends anything itself: hand-offs only open a pre-filled app
 * (r-02), and the deterministic confirmation copy lives in [ActionNotes].
 */
fun interface ActionRunner {
    fun run(action: PlannedAction): Boolean
}

/** Real runner: builds the action's Intent and starts it. */
class AndroidActionRunner(private val context: Context) : ActionRunner {

    override fun run(action: PlannedAction): Boolean {
        val intent = intentFor(action) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun intentFor(action: PlannedAction): Intent? = when (action) {
        is PlannedAction.Staged.Timer -> TimerAlarmIntents.setTimer(action.seconds, action.label)
        is PlannedAction.Staged.Alarm -> TimerAlarmIntents.setAlarm(action.hour, action.minute, action.label)
        is PlannedAction.Handoff.Calendar -> HandoffIntents.calendarEvent(action.title, action.beginMillis, action.endMillis, action.location)
        is PlannedAction.Handoff.Email -> HandoffIntents.email(action.to, action.subject, action.body)
        is PlannedAction.Handoff.Sms -> HandoffIntents.sms(action.body, action.to)
        is PlannedAction.Malformed -> null
    }
}

/**
 * Deterministic, past-tense copy for what Equerry DID. It must never claim a send — for hand-offs
 * Equerry only opened a pre-filled app and can't observe whether the user sent (action_followup).
 */
object ActionNotes {

    fun of(action: PlannedAction): String = when (action) {
        is PlannedAction.Staged.Timer -> "Timer started — ${mmss(action.seconds)}"
        is PlannedAction.Staged.Alarm -> "Alarm set — ${hhmm(action.hour, action.minute)}"
        is PlannedAction.Handoff.Calendar -> "Opened your calendar to add \"${action.title}\""
        is PlannedAction.Handoff.Email -> "Opened an email to ${action.to} (you send it)"
        is PlannedAction.Handoff.Sms -> "Opened a text to ${action.to ?: "your contact"} (you send it)"
        is PlannedAction.Malformed -> "Couldn't run ${action.toolName}: ${action.reason}"
    }

    private fun mmss(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

    private fun hhmm(hour: Int, minute: Int): String = "$hour:${minute.toString().padStart(2, '0')}"
}

@Module
@InstallIn(SingletonComponent::class)
object ActionRunnerModule {

    @Provides
    @Singleton
    fun provideActionRunner(@ApplicationContext context: Context): ActionRunner = AndroidActionRunner(context)
}
