package dev.equerry.app.providers

/**
 * A provider backend type. Each entry carries the per-type contract the create/edit
 * form and validator depend on: whether an API key is required, the prefilled base
 * URL (if any), whether that URL is locked, the suggested model ids, and which
 * driver actually services the type.
 *
 * OpenRouter is modelled as its own selectable type but routes through the
 * OpenAI-compatible driver (spec decision `adaptive_form` / locked stack: "OpenRouter
 * is a base-URL preset on the OpenAI-compatible driver").
 */
enum class ProviderType(
    val displayName: String,
    val requiresKey: Boolean,
    val defaultBaseUrl: String?,
    val baseUrlLocked: Boolean,
    val modelPresets: List<String>,
) {
    OLLAMA(
        displayName = "Ollama",
        requiresKey = false,
        defaultBaseUrl = "http://localhost:11434",
        baseUrlLocked = false,
        modelPresets = listOf("llama3.2", "qwen2.5", "mistral", "phi3"),
    ),
    ANTHROPIC(
        displayName = "Anthropic",
        requiresKey = true,
        defaultBaseUrl = "https://api.anthropic.com",
        baseUrlLocked = false,
        modelPresets = listOf("claude-sonnet-4-6", "claude-opus-4-8", "claude-haiku-4-5"),
    ),
    OPENAI_COMPATIBLE(
        displayName = "OpenAI-compatible",
        requiresKey = true,
        defaultBaseUrl = null,
        baseUrlLocked = false,
        modelPresets = listOf("gpt-4o", "gpt-4o-mini", "o3-mini"),
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        requiresKey = true,
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        baseUrlLocked = true,
        modelPresets = listOf("anthropic/claude-sonnet-4.6", "openai/gpt-4o", "google/gemini-2.0-flash"),
    ),
    ;

    /** The driver type that services this provider. OpenRouter reuses the OpenAI-compatible driver. */
    val driverType: ProviderType
        get() = if (this == OPENROUTER) OPENAI_COMPATIBLE else this

    /**
     * Whether this type is treated as tool-call capable — drives the action-tools payload (t-2) and
     * the chat-screen capability banner (unsupported_provider_ux). Judged best-effort at the type
     * level: Ollama tool support varies by model, so it is treated as incapable here.
     */
    val supportsTools: Boolean
        get() = this != OLLAMA

    /**
     * Whether this type is treated as image (VISION) capable — drives the screen-context image path
     * vs the Assist/OCR text fallback (`vision_capability`). Best-effort at the type level: the
     * remote types host multimodal models; Ollama's vision support varies by model, so it is treated
     * as incapable here. A runtime capability error still degrades to the text path at send time.
     */
    val supportsImages: Boolean
        get() = this != OLLAMA
}
