package dev.equerry.app.providers.drivers

import dev.equerry.app.providers.ProviderProfile
import dev.equerry.app.providers.ProviderType
import javax.inject.Inject

/**
 * Selects the [ChatDriver] for a profile by its resolved driver type. OpenRouter collapses to the
 * OpenAI-compatible driver (via [ProviderType.driverType]), so a single OpenAI driver services both.
 */
class ChatDriverFactory @Inject constructor(
    private val openAi: OpenAiChatDriver,
    private val anthropic: AnthropicChatDriver,
    private val ollama: OllamaChatDriver,
) {
    fun forProfile(profile: ProviderProfile): ChatDriver = when (profile.type.driverType) {
        ProviderType.ANTHROPIC -> anthropic
        ProviderType.OLLAMA -> ollama
        else -> openAi
    }

    /** Convenience: resolve the driver for [profile] and stream its reply. */
    fun send(profile: ProviderProfile, key: String, messages: List<ChatMessage>) =
        forProfile(profile).send(profile, key, messages)
}
