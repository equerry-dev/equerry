package dev.equerry.app.tools.actions

import android.content.Intent
import android.provider.AlarmClock

/**
 * Pure builders for the benign, on-device clock actions (timer / alarm). They RETURN [Intent]s and
 * never call `startActivity`, so the action + extras are directly assertable and the r-02 staging
 * boundary stays in the caller's hands. `EXTRA_SKIP_UI = false` so firing surfaces the clock app's
 * own UI rather than silently mutating device state.
 */
object TimerAlarmIntents {

    fun setTimer(seconds: Int, label: String? = null): Intent =
        Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        }

    fun setAlarm(hour: Int, minute: Int, label: String? = null): Intent =
        Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        }
}
