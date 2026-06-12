package dev.equerry.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Single source of truth for CAMERA permission state. Mirrors [MicPermission], kept deliberately
 * simple (a boolean grant + one guidance line). The grant is requested up front from a calm UI
 * (Voice Settings) — never mid voice-session, where the active-microphone privacy indicator covers
 * the runtime permission dialog and blocks the "Allow" button. A spoken camera query therefore only
 * launches the capture activity when this already reads granted; otherwise it surfaces [GUIDANCE].
 */
object CameraPermission {

    /** Shown when a camera query is attempted without the grant. */
    const val GUIDANCE = "Camera access is off — turn it on in Voice settings to look through the camera."

    /** Reads the live CAMERA grant. */
    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
}
