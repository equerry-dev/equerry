package dev.equerry.app.tools.actions

import android.content.Intent
import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HandoffIntentsTest {

    @Test
    fun calendar_event_inserts_into_events_with_title_and_times() {
        val i = HandoffIntents.calendarEvent("Standup", beginMillis = 1000L, endMillis = 2000L, location = "Room 1")
        assertEquals(Intent.ACTION_INSERT, i.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, i.data)
        assertEquals("Standup", i.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals(1000L, i.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L))
        assertEquals(2000L, i.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L))
    }

    @Test
    fun email_uses_sendto_mailto_with_subject_and_body() {
        val i = HandoffIntents.email("a@b.com", subject = "Hi", body = "There")
        assertEquals(Intent.ACTION_SENDTO, i.action)
        assertEquals("mailto", i.data?.scheme)
        assertEquals("Hi", i.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("There", i.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun sms_uses_sendto_sms_with_body() {
        val i = HandoffIntents.sms(body = "omw", to = "5551234")
        assertEquals(Intent.ACTION_SENDTO, i.action)
        assertEquals("sms", i.data?.scheme)
        assertEquals("omw", i.getStringExtra("sms_body"))
    }

    @Test
    fun handoff_builders_never_auto_send() {
        // r-02: hand-off opens a pre-filled app; it must never be an ACTION_SEND that could fire.
        val intents = listOf(
            HandoffIntents.email("a@b.com"),
            HandoffIntents.sms("hi"),
            HandoffIntents.calendarEvent("e", 1L),
        )
        for (i in intents) assertNotEquals(Intent.ACTION_SEND, i.action)
    }
}
