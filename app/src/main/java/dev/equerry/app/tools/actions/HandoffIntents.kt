package dev.equerry.app.tools.actions

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract

/**
 * Pure builders for the outward, hand-off actions (calendar / email / SMS). Each RETURNS an [Intent]
 * that opens the relevant system app PRE-FILLED — never an `ACTION_SEND`/auto-commit action — so the
 * irreversible step (save / send) always happens in the target app under the user's hand (r-02).
 */
object HandoffIntents {

    fun calendarEvent(
        title: String,
        beginMillis: Long,
        endMillis: Long? = null,
        location: String? = null,
    ): Intent =
        Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
            endMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
            location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        }

    fun email(to: String, subject: String? = null, body: String? = null): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$to")).apply {
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            body?.let { putExtra(Intent.EXTRA_TEXT, it) }
        }

    fun sms(body: String, to: String? = null): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("sms:${to.orEmpty()}")).apply {
            putExtra("sms_body", body)
        }
}
