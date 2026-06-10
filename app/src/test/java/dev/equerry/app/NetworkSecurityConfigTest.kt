package dev.equerry.app

import android.security.NetworkSecurityPolicy
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression guard for the cleartext fix: Equerry is a bring-your-own-backend app, so users point
 * it at self-hosted providers that are commonly plaintext http (Ollama on localhost, llama.cpp on a
 * LAN/Tailscale host). targetSdk >= 28 blocks cleartext by default, which silently breaks every http
 * provider — so the manifest declares a network-security config permitting it. If that config is
 * ever dropped, this test fails instead of every http provider silently failing on-device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NetworkSecurityConfigTest {

    @Test
    fun cleartext_traffic_is_permitted_for_user_configured_http_providers() {
        assertTrue(
            "Cleartext http must be permitted or http providers (Ollama/localhost, self-hosted) break",
            NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted,
        )
    }
}
