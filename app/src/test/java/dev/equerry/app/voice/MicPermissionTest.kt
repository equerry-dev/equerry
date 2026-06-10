package dev.equerry.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies t-1's contract: RECORD_AUDIO is declared in the manifest (c-1 listening cannot start
 * without it) and the mic-permission guidance is byte-identical across entry points (locked
 * mic_permission "same guidance everywhere", c-5). Robolectric parses the merged manifest under
 * `testDebugUnitTest`; the evaluate() cases are pure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MicPermissionTest {

    @Test
    fun manifest_declares_record_audio() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions?.toList().orEmpty()
        assertTrue(
            "RECORD_AUDIO must be declared in the manifest, else STT capture (c-1) can never start",
            requested.contains(Manifest.permission.RECORD_AUDIO),
        )
    }

    @Test
    fun denied_guidance_is_identical_across_entry_points() {
        val inSession = MicPermission.evaluate(granted = false, MicPermissionEntryPoint.IN_SESSION)
        val settings = MicPermission.evaluate(granted = false, MicPermissionEntryPoint.SETTINGS)

        // The locked invariant: one Denied guidance everywhere. Diverging the message per entry
        // point would fail this equality.
        assertEquals(inSession, settings)
        assertTrue(inSession is MicPermissionState.Denied)
        assertTrue((inSession as MicPermissionState.Denied).guidance.isNotBlank())
    }

    @Test
    fun granted_evaluates_to_ready() {
        assertEquals(
            MicPermissionState.Ready,
            MicPermission.evaluate(granted = true, MicPermissionEntryPoint.IN_SESSION),
        )
    }
}
