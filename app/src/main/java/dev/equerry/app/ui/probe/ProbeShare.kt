package dev.equerry.app.ui.probe

import android.content.Intent

/**
 * Builds the share-sheet intent for exporting probe results (export_format locked:
 * CSV via the Android share-sheet). Kept as a pure builder so the action, MIME type,
 * and payload are unit-testable independently of the screen that launches it.
 */
object ProbeShare {

    const val CSV_MIME = "text/csv"

    fun csvShareIntent(csv: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = CSV_MIME
            putExtra(Intent.EXTRA_TEXT, csv)
        }
}
