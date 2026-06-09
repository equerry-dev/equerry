package dev.equerry.app.providers

/**
 * A job the assistant can route to a provider. Each slot maps to at most one
 * [ProviderProfile]. Only [CHAT] is wired in v0.1; the rest are surfaced in the UI
 * as "coming soon" (spec decision `slot_scope`) so [active] gates them.
 */
enum class CapabilitySlot(val displayName: String, val active: Boolean) {
    CHAT("Chat", active = true),
    VISION("Vision", active = false),
    STT("Speech-to-text", active = false),
    TTS("Text-to-speech", active = false),
    OCR("OCR", active = false),
    EMBEDDINGS("Embeddings", active = false),
}
