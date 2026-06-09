package dev.equerry.app.data

/**
 * Stores per-profile API keys. Keys are secrets (project rule r-03): they live only
 * here, encrypted at rest, keyed by the owning profile id — never in DataStore, never
 * logged. [ProviderProfile][dev.equerry.app.providers.ProviderProfile] deliberately
 * carries no key field so a profile can never be persisted with its key attached.
 */
interface SecretStore {
    fun putKey(profileId: String, key: String)
    fun getKey(profileId: String): String?
    fun removeKey(profileId: String)
}
