package dev.equerry.app.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class VoiceGuidanceFactoryTest {

    @Test
    fun a_401_with_a_secret_in_the_body_never_leaks_into_the_guidance() {
        // The upstream 401 body echoes the API key; the mapper sees only the status code, so the
        // secret cannot reach the user-facing message (r-03).
        val leakyResponseBody = """{"error":{"message":"Incorrect API key sk-secret-ABCDEF1234567890"}}"""
        val error = RemoteAudioError.forStatus(401)
        val guidance = VoiceGuidanceFactory.remoteEngineFailed(error)

        assertFalse(guidance.message.contains("sk-secret-ABCDEF1234567890"))
        assertFalse(guidance.message.contains(leakyResponseBody))
        assertTrue("offers the consented system-engine retry", guidance.canRetryWithSystem)
    }

    @Test
    fun auth_network_and_unavailable_each_yield_a_distinct_message() {
        val auth = VoiceGuidanceFactory.remoteEngineFailed(RemoteAudioError.Auth).message
        val network = VoiceGuidanceFactory.remoteEngineFailed(RemoteAudioError.Network).message
        val unavailable = VoiceGuidanceFactory.remoteEngineFailed(RemoteAudioError.Unavailable).message

        assertNotEquals(auth, network)
        assertNotEquals(auth, unavailable)
        assertNotEquals(network, unavailable)
    }

    @Test
    fun errors_are_derived_from_status_and_exception_type_only() {
        assertTrue(RemoteAudioError.forStatus(401) is RemoteAudioError.Auth)
        assertTrue(RemoteAudioError.forStatus(403) is RemoteAudioError.Auth)
        assertTrue(RemoteAudioError.forStatus(500) is RemoteAudioError.Unavailable)
        assertTrue(RemoteAudioError.forException(IOException("boom")) is RemoteAudioError.Network)
        assertTrue(RemoteAudioError.forException(IllegalStateException("boom")) is RemoteAudioError.Unavailable)
    }
}
