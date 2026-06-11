package dev.equerry.app.tools.actions

import android.content.Intent
import android.provider.AlarmClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TimerAlarmIntentsTest {

    @Test
    fun set_timer_targets_the_clock_with_length_and_visible_ui() {
        val i = TimerAlarmIntents.setTimer(300)
        assertEquals(AlarmClock.ACTION_SET_TIMER, i.action)
        assertEquals(300, i.getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
        assertFalse("EXTRA_SKIP_UI must be false so the clock app surfaces the timer", i.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
    }

    @Test
    fun set_alarm_targets_the_clock_with_hour_and_minute() {
        val i = TimerAlarmIntents.setAlarm(7, 30)
        assertEquals(AlarmClock.ACTION_SET_ALARM, i.action)
        assertEquals(7, i.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(30, i.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
    }

    @Test
    fun timer_and_alarm_never_emit_a_send_or_edit_action() {
        // r-02: benign on-device actions only — never an outward/irreversible send or edit.
        for (action in listOf(TimerAlarmIntents.setTimer(60).action, TimerAlarmIntents.setAlarm(6, 0).action)) {
            assertNotEquals(Intent.ACTION_SEND, action)
            assertNotEquals(Intent.ACTION_EDIT, action)
        }
    }
}
