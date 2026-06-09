package dev.equerry.app.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidatorTest {

    private fun draft(
        label: String = "My backend",
        type: ProviderType = ProviderType.ANTHROPIC,
        baseUrl: String = "https://api.anthropic.com",
        key: String = "sk-123",
        model: String = "claude-sonnet-4-6",
    ) = ProfileDraft(label, type, baseUrl, key, model)

    private fun fields(draft: ProfileDraft) =
        ProfileValidator.validate(draft).map { it.field }

    @Test
    fun blank_label_is_flagged() {
        assertTrue(ProfileField.LABEL in fields(draft(label = "  ")))
    }

    @Test
    fun anthropic_with_empty_key_is_flagged() {
        assertTrue(ProfileField.KEY in fields(draft(type = ProviderType.ANTHROPIC, key = "")))
    }

    @Test
    fun openai_compatible_with_empty_key_is_flagged() {
        assertTrue(
            ProfileField.KEY in fields(
                draft(type = ProviderType.OPENAI_COMPATIBLE, baseUrl = "https://api.example.com", key = ""),
            ),
        )
    }

    @Test
    fun ollama_with_empty_base_url_is_flagged() {
        assertTrue(
            ProfileField.BASE_URL in fields(
                draft(type = ProviderType.OLLAMA, baseUrl = "", key = ""),
            ),
        )
    }

    @Test
    fun ollama_with_empty_key_is_not_flagged_for_key() {
        // Ollama is keyless — a missing key must NOT fire the KEY rule.
        assertFalse(
            ProfileField.KEY in fields(
                draft(type = ProviderType.OLLAMA, baseUrl = "http://localhost:11434", key = ""),
            ),
        )
    }

    @Test
    fun empty_model_is_flagged() {
        assertTrue(ProfileField.MODEL in fields(draft(model = "")))
    }

    @Test
    fun valid_keyed_draft_has_no_errors() {
        assertEquals(emptyList<FieldError>(), ProfileValidator.validate(draft()))
    }

    @Test
    fun valid_keyless_ollama_draft_has_no_errors() {
        val ollama = draft(
            label = "Home Ollama",
            type = ProviderType.OLLAMA,
            baseUrl = "http://localhost:11434",
            key = "",
            model = "llama3.2",
        )
        assertEquals(emptyList<FieldError>(), ProfileValidator.validate(ollama))
    }
}
