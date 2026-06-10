package dev.equerry.app.providers

/** A field on the create/edit form that validation can flag. */
enum class ProfileField { LABEL, BASE_URL, KEY, MODEL, TEMPERATURE, MAX_TOKENS }

/** A single validation failure tied to the [field] that the UI should mark. */
data class FieldError(val field: ProfileField, val message: String)

/**
 * Per-type validation for a [ProfileDraft]. Rules adapt to [ProviderType]: a key is
 * required only when the type requires one (so a keyless Ollama draft is valid), while
 * label, base URL, and model are required for every type. Returns an empty list when
 * the draft is savable (spec criterion c-6).
 */
object ProfileValidator {

    fun validate(draft: ProfileDraft): List<FieldError> {
        val errors = mutableListOf<FieldError>()

        if (draft.label.isBlank()) {
            errors += FieldError(ProfileField.LABEL, "Give this profile a name.")
        }
        if (draft.baseUrl.isBlank()) {
            errors += FieldError(ProfileField.BASE_URL, "A base URL is required.")
        }
        if (draft.type.requiresKey && draft.key.isBlank()) {
            errors += FieldError(ProfileField.KEY, "${draft.type.displayName} requires an API key.")
        }
        if (draft.model.isBlank()) {
            errors += FieldError(ProfileField.MODEL, "Choose or enter a model.")
        }
        // Request params are optional (blank = provider default), but if given they must be numeric.
        if (draft.temperature.isNotBlank() && draft.temperature.toDoubleOrNull() == null) {
            errors += FieldError(ProfileField.TEMPERATURE, "Temperature must be a number.")
        }
        if (draft.maxTokens.isNotBlank() && draft.maxTokens.toIntOrNull() == null) {
            errors += FieldError(ProfileField.MAX_TOKENS, "Max tokens must be a whole number.")
        }

        return errors
    }
}
