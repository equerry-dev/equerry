package dev.equerry.app.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderTypeTest {

    @Test
    fun ollama_does_not_require_a_key() {
        assertFalse(ProviderType.OLLAMA.requiresKey)
    }

    @Test
    fun anthropic_and_openai_require_a_key() {
        assertTrue(ProviderType.ANTHROPIC.requiresKey)
        assertTrue(ProviderType.OPENAI_COMPATIBLE.requiresKey)
    }

    @Test
    fun openrouter_routes_through_the_openai_compatible_driver() {
        assertEquals(ProviderType.OPENAI_COMPATIBLE, ProviderType.OPENROUTER.driverType)
    }

    @Test
    fun non_openrouter_types_drive_themselves() {
        assertEquals(ProviderType.OLLAMA, ProviderType.OLLAMA.driverType)
        assertEquals(ProviderType.ANTHROPIC, ProviderType.ANTHROPIC.driverType)
        assertEquals(ProviderType.OPENAI_COMPATIBLE, ProviderType.OPENAI_COMPATIBLE.driverType)
    }

    @Test
    fun openrouter_prefills_and_locks_its_base_url() {
        assertEquals("https://openrouter.ai/api/v1", ProviderType.OPENROUTER.defaultBaseUrl)
        assertTrue(ProviderType.OPENROUTER.baseUrlLocked)
    }

    @Test
    fun every_type_offers_model_presets() {
        for (type in ProviderType.entries) {
            assertTrue("${type.name} must offer model presets", type.modelPresets.isNotEmpty())
        }
    }

    @Test
    fun tool_capability_is_true_for_all_types_except_ollama() {
        // Flip any row and this fails. Ollama is best-effort incapable (unsupported_provider_ux).
        assertTrue(ProviderType.ANTHROPIC.supportsTools)
        assertTrue(ProviderType.OPENAI_COMPATIBLE.supportsTools)
        assertTrue(ProviderType.OPENROUTER.supportsTools)
        assertFalse(ProviderType.OLLAMA.supportsTools)
    }

    @Test
    fun only_chat_slot_is_active() {
        assertTrue(CapabilitySlot.CHAT.active)
        val others = CapabilitySlot.entries.filter { it != CapabilitySlot.CHAT }
        assertTrue(others.none { it.active })
    }
}
