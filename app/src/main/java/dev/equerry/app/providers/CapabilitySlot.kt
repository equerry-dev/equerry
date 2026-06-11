package dev.equerry.app.providers

/**
 * A job the assistant can route to a provider. Each slot maps to at most one
 * [ProviderProfile]. [CHAT] (phase 04), [VISION] (phase 07 screen-context) and [STT]/[TTS]
 * (phase 08 remote-stt-tts) are wired; the rest are surfaced in the UI as "coming soon"
 * (spec decision `slot_scope`) so [active] gates them.
 */
enum class CapabilitySlot(val displayName: String, val active: Boolean) {
    CHAT("Chat", active = true),
    VISION("Vision", active = true),
    STT("Speech-to-text", active = true),
    TTS("Text-to-speech", active = true),
    OCR("OCR", active = false),
    EMBEDDINGS("Embeddings", active = false),
}
