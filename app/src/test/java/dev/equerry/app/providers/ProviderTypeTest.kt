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
    fun image_capability_is_true_for_all_types_except_ollama() {
        // Mirrors tool capability: best-effort at the type level (vision_capability). Flip any row
        // and this fails. Ollama's vision support varies by model, so it is treated as incapable.
        assertTrue(ProviderType.ANTHROPIC.supportsImages)
        assertTrue(ProviderType.OPENAI_COMPATIBLE.supportsImages)
        assertTrue(ProviderType.OPENROUTER.supportsImages)
        assertFalse(ProviderType.OLLAMA.supportsImages)
    }

    @Test
    fun stt_and_tts_capability_are_true_only_for_openai_compatible() {
        // Remote audio (Whisper-style transcribe / OpenAI-style speech) is scoped to the one type
        // that hosts those endpoints (provider_config_capability_flags). Flip any row and this fails:
        // granting a flag to a wrong type OR removing it from OPENAI_COMPATIBLE both go red.
        assertTrue(ProviderType.OPENAI_COMPATIBLE.supportsStt)
        assertTrue(ProviderType.OPENAI_COMPATIBLE.supportsTts)
        assertFalse(ProviderType.ANTHROPIC.supportsStt)
        assertFalse(ProviderType.ANTHROPIC.supportsTts)
        assertFalse(ProviderType.OLLAMA.supportsStt)
        assertFalse(ProviderType.OLLAMA.supportsTts)
        assertFalse(ProviderType.OPENROUTER.supportsStt)
        assertFalse(ProviderType.OPENROUTER.supportsTts)
    }

    @Test
    fun wired_slots_are_active_and_only_ocr_and_embeddings_are_not() {
        assertTrue(CapabilitySlot.CHAT.active)
        assertTrue("VISION is wired by the screen-context phase", CapabilitySlot.VISION.active)
        assertTrue("STT is wired by the remote-stt-tts phase", CapabilitySlot.STT.active)
        assertTrue("TTS is wired by the remote-stt-tts phase", CapabilitySlot.TTS.active)
        // Only OCR and EMBEDDINGS remain coming-soon; if either is activated this fails,
        // and if STT/TTS regress to inactive the asserts above fail.
        val inactive = CapabilitySlot.entries.filter { !it.active }
        assertEquals(listOf(CapabilitySlot.OCR, CapabilitySlot.EMBEDDINGS), inactive)
    }
}
