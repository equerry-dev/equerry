package dev.equerry.app.providers

import dev.equerry.app.data.ProfileStore
import dev.equerry.app.data.SecretStore
import dev.equerry.app.data.SlotMappingStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Single entry point for provider profiles and capability-slot mappings. Splits secrets
 * from the rest on write: the profile (key-free) goes to [ProfileStore], the API key goes
 * to [SecretStore] only (criterion c-2). Deleting a profile cascades to its key and any
 * slot mapping pointing at it.
 */
class ProviderRepository(
    private val profileStore: ProfileStore,
    private val secretStore: SecretStore,
    private val slotMappingStore: SlotMappingStore,
) {

    fun observeProfiles(): Flow<List<ProviderProfile>> = profileStore.profiles()

    /** The profile currently mapped to CHAT, or null if unmapped (or its profile is gone). */
    fun observeChatMapping(): Flow<ProviderProfile?> =
        combine(slotMappingStore.mapping(CapabilitySlot.CHAT), profileStore.profiles()) { mappedId, profiles ->
            mappedId?.let { id -> profiles.firstOrNull { it.id == id } }
        }

    /** The profile currently mapped to VISION, or null if unmapped (or its profile is gone). */
    fun observeVisionMapping(): Flow<ProviderProfile?> =
        combine(slotMappingStore.mapping(CapabilitySlot.VISION), profileStore.profiles()) { mappedId, profiles ->
            mappedId?.let { id -> profiles.firstOrNull { it.id == id } }
        }

    suspend fun addProfile(draft: ProfileDraft): ProviderProfile {
        val profile = ProviderProfile(
            id = UUID.randomUUID().toString(),
            label = draft.label,
            type = draft.type,
            baseUrl = draft.baseUrl,
            model = draft.model,
            // Blank form input means "use the provider default" -> null in the domain model.
            systemPrompt = draft.systemPrompt.takeIf { it.isNotBlank() },
            temperature = draft.temperature.toDoubleOrNull(),
            maxTokens = draft.maxTokens.toIntOrNull(),
        )
        profileStore.save(profileStore.profiles().first() + profile)
        if (draft.key.isNotBlank()) {
            secretStore.putKey(profile.id, draft.key)
        }
        return profile
    }

    /**
     * Replaces the stored profile of the same id. [newKey] controls the secret: null leaves
     * it untouched, blank removes it, otherwise it is stored.
     */
    suspend fun updateProfile(profile: ProviderProfile, newKey: String? = null) {
        profileStore.save(profileStore.profiles().first().map { if (it.id == profile.id) profile else it })
        when {
            newKey == null -> Unit
            newKey.isBlank() -> secretStore.removeKey(profile.id)
            else -> secretStore.putKey(profile.id, newKey)
        }
    }

    suspend fun deleteProfile(profileId: String) {
        profileStore.save(profileStore.profiles().first().filterNot { it.id == profileId })
        secretStore.removeKey(profileId)
        for (slot in CapabilitySlot.entries) {
            if (slotMappingStore.mapping(slot).first() == profileId) {
                slotMappingStore.setMapping(slot, null)
            }
        }
    }

    suspend fun setChatSlot(profileId: String?) =
        slotMappingStore.setMapping(CapabilitySlot.CHAT, profileId)

    suspend fun setVisionSlot(profileId: String?) =
        slotMappingStore.setMapping(CapabilitySlot.VISION, profileId)

    fun keyFor(profileId: String): String? = secretStore.getKey(profileId)
}
