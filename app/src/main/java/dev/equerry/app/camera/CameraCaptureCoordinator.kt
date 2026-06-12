package dev.equerry.app.camera

import android.content.Context
import android.content.Intent
import dev.equerry.app.screencontext.ScreenContext
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the (suspending) voice flow and the framework-bound [CameraCaptureActivity]. A spoken
 * camera query calls [capture], which launches the activity and suspends until it delivers a single
 * captured frame (image bytes + OCR text as a [ScreenContext]) or null (permission denied / capture
 * failed / cancelled). Both the caller and the activity resolve the SAME Hilt singleton, so the
 * result crosses the activity boundary without an Activity Result registry (a VoiceInteractionSession
 * has none). The captured frame is a transient request payload — never written to storage.
 */
@Singleton
class CameraCaptureCoordinator @Inject constructor() {

    @Volatile
    private var pending: CompletableDeferred<ScreenContext?>? = null

    /** Launch the capture activity and suspend until it returns a frame (or null). */
    suspend fun capture(context: Context): ScreenContext? {
        // Supersede any orphaned prior request so a stuck deferred never wedges the next turn.
        pending?.complete(null)
        val deferred = CompletableDeferred<ScreenContext?>()
        pending = deferred
        val intent = Intent(context, CameraCaptureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
        return deferred.await()
    }

    /** Called by [CameraCaptureActivity] with the captured frame, or null on failure/cancel. */
    fun deliver(result: ScreenContext?) {
        pending?.complete(result)
        pending = null
    }
}
