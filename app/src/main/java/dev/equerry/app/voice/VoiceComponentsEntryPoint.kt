package dev.equerry.app.voice

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.equerry.app.data.VoiceSettingsStore
import dev.equerry.app.providers.ProviderRepository

/**
 * Hands the singleton collaborators a [VoiceFlowController] needs to a context that isn't a Hilt
 * injection site — e.g. a Compose route that builds the controller around its screen-scoped
 * ChatViewModel. STT/TTS are created per-use via their `fromContext` factories, not pulled here.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface VoiceComponentsEntryPoint {
    fun voiceSettingsStore(): VoiceSettingsStore
    fun providerRepository(): ProviderRepository
}
