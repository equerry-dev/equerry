package dev.equerry.app.providers.drivers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KeyRedactionTest {

    @Test
    fun keyNeverSurvivesRedaction() {
        val key = "sk-secret-ABCDEF1234567890"
        val text = "Authorization: Bearer $key (retry with $key)"
        val out = KeyRedaction.redact(text, key)
        assertFalse("every occurrence must be masked", out.contains(key))
        assertEquals(
            "Authorization: Bearer ${KeyRedaction.MASK} (retry with ${KeyRedaction.MASK})",
            out,
        )
    }

    @Test
    fun blank_or_null_key_is_a_no_op() {
        assertEquals("hello", KeyRedaction.redact("hello", ""))
        assertEquals("hello", KeyRedaction.redact("hello", "   "))
        assertEquals("hello", KeyRedaction.redact("hello", null))
    }
}
