/**
 * One driver per provider `type` (OPENAI_COMPATIBLE, ANTHROPIC, OLLAMA, OpenRouter preset,
 * WHISPER_HTTP, OPENAI_TTS, SYSTEM, CUSTOM_HTTP). Drivers normalize each provider's
 * request/response shape to the app's internal ChatMessage / ToolSpec / ToolCall types;
 * that translation never leaks upward (spec §4).
 */
package dev.equerry.app.providers.drivers
