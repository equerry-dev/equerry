package dev.equerry.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * r-01 guard (screen-context, c-4): Equerry reads the screen ONLY via the sanctioned Assist API,
 * never an AccessibilityService — a permanent non-goal. Every AccessibilityService must be guarded
 * by BIND_ACCESSIBILITY_SERVICE, so if one is ever added to the manifest this fails instead of the
 * non-goal silently slipping in. The screen-context capture comes solely from onHandleAssist /
 * onHandleScreenshot in the VoiceInteractionSession.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NoAccessibilityServiceTest {

    @Test
    fun no_declared_service_is_an_accessibility_service() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SERVICES)
        val services = info.services ?: emptyArray()

        assertTrue(
            "r-01: no AccessibilityService (BIND_ACCESSIBILITY_SERVICE) may be declared",
            services.none { it.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE" },
        )
    }
}
