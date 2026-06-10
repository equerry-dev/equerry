package dev.equerry.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Where a microphone-permission check was triggered from. */
enum class MicPermissionEntryPoint { IN_SESSION, SETTINGS }

/** Outcome of a RECORD_AUDIO permission check. */
sealed interface MicPermissionState {
    /** Permission granted — listening may start. */
    data object Ready : MicPermissionState

    /**
     * Permission missing. [guidance] is the user-facing message; [openSettings] signals the UI
     * should offer a path to the app's settings so the mic can be enabled.
     */
    data class Denied(val guidance: String, val openSettings: Boolean) : MicPermissionState
}

/**
 * Single source of truth for RECORD_AUDIO permission state.
 *
 * [evaluate] is a pure mapping from a resolved grant flag to a [MicPermissionState]. The Denied
 * result is identical regardless of [MicPermissionEntryPoint]: the locked `mic_permission`
 * decision requires the same guidance whether the user reaches the mic from the in-session prompt
 * or from settings. The entry point is threaded through the API precisely so a future change that
 * tried to diverge the message per entry point would break [MicPermissionTest]'s equality check.
 *
 * [isGranted] is the only Android-touching call and is kept out of the pure mapping so the
 * cross-entry-point invariant stays unit-testable without a device.
 */
object MicPermission {

    /** The one denial state shown everywhere a mic grant is missing. */
    val deniedState = MicPermissionState.Denied(
        guidance = "Microphone access is off. Turn it on to talk to Equerry.",
        openSettings = true,
    )

    /** Map a resolved RECORD_AUDIO grant flag to a state. [entryPoint] never changes the result. */
    @Suppress("UNUSED_PARAMETER")
    fun evaluate(granted: Boolean, entryPoint: MicPermissionEntryPoint): MicPermissionState =
        if (granted) MicPermissionState.Ready else deniedState

    /** Reads the live RECORD_AUDIO grant. The only Android call; not part of the pure mapping. */
    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
