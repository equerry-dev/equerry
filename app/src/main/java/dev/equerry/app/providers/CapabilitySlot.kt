package dev.equerry.app.providers

/**
 * A job the assistant can route to a provider. Each slot maps to at most one
 * [ProviderProfile]. [CHAT] (phase 04) and [VISION] (phase 07 screen-context) are wired;
 * the rest are surfaced in the UI as "coming soon" (spec decision `slot_scope`) so [active]
 * gates them.
 */
enum class CapabilitySlot(val displayName: String, val active: Boolean) {
    CHAT("Chat", active = true),
    VISION("Vision", active = true),
    STT("Speech-to-text", active = false),
    TTS("Text-to-speech", active = false),
    OCR("OCR", active = false),
    EMBEDDINGS("Embeddings", active = false),
}
