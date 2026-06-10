package dev.equerry.app.providers

/**
 * A saved provider backend. Deliberately carries NO API key: keys are secrets and
 * live only in the Keystore-backed encrypted store, referenced by [id] (project rule
 * r-03, spec criterion c-2). Persisting a profile must never persist its key.
 */
data class ProviderProfile(
    val id: String,
    val label: String,
    val type: ProviderType,
    val baseUrl: String,
    val model: String,
    /** Optional per-profile request params; null = use the provider's own default (locked `request_params`). */
    val systemPrompt: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
)

/**
 * The editable shape of a profile while it is being created or edited. Unlike
 * [ProviderProfile] this DOES hold the [key] so the form and [ProfileValidator] can
 * work with it in memory; on save the key is split off to the secret store and the
 * rest becomes a [ProviderProfile].
 */
data class ProfileDraft(
    val label: String,
    val type: ProviderType,
    val baseUrl: String,
    val key: String,
    val model: String,
    /**
     * Optional request params as raw form input (like [key], these are edited as text). Blank means
     * "use the provider default"; the numeric two are validated by [ProfileValidator] and parsed to
     * [ProviderProfile.temperature] / [ProviderProfile.maxTokens] on save.
     */
    val systemPrompt: String = "",
    val temperature: String = "",
    val maxTokens: String = "",
)
