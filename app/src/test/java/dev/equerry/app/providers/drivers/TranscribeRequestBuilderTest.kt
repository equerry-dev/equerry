package dev.equerry.app.providers.drivers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TranscribeRequestBuilderTest {

    private val key = "sk-secret-ABCDEF1234567890"

    @Test
    fun builds_the_transcriptions_path_and_carries_the_model() {
        val req = TranscribeRequestBuilder.build("whisper-1", key)
        assertEquals("audio/transcriptions", req.path)
        assertEquals("whisper-1", req.formFields["model"])
    }

    @Test
    fun key_lives_in_the_authorization_header_only_never_in_path_or_form() {
        val req = TranscribeRequestBuilder.build("whisper-1", key)
        assertEquals("Bearer $key", req.headers["Authorization"])
        // r-03: the key must not leak into the URL/query or the multipart text fields.
        assertFalse(req.path.contains(key))
        assertFalse(req.formFields.values.any { it.contains(key) })
        assertFalse(req.formFields.values.any { it.contains("Bearer") })
    }
}
