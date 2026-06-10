Phase 04-chat-drivers — 9 tasks across 3 waves

Risk lens: the graph is shaped by what breaks. Each failure mode — malformed/partial SSE, mid-stream disconnect, non-2xx, key-in-log/key-in-URL, unmapped-CHAT, provider-specific JSON quirks, history-context drift, session-survives-nav vs lost-on-death — is owned and tested by exactly one task.

Wave 1
  t-1  Define streaming chat contract types
       files:    providers/drivers/ChatDriver.kt, providers/drivers/ChatMessage.kt, providers/drivers/ChatError.kt
       covers:   c-1, c-4
       contract: ChatDriver.stream() must return Flow<ChatToken> and a terminal failure must surface as a typed ChatError (AuthFailed/Unreachable/HttpStatus/MalformedResponse) — if the sealed ChatError hierarchy loses a variant a when-exhaustiveness test in ChatErrorTest fails to compile/assert all branches.

  t-2  Add request_params to profile model + store
       files:    providers/ProviderProfile.kt, providers/ProfileValidator.kt, data/ProfileStore.kt
       covers:   c-1
       contract: ProviderProfile gains optional systemPrompt/temperature/maxTokens; ProfileStore round-trips them. If a blank/null param is serialized as a present value (not omitted) or a saved temperature fails to decode, ProfileStoreTest.params_round_trip_and_blank_means_absent fails. Validator: a non-numeric temperature/maxTokens entry yields a FieldError — ProfileValidatorTest.rejects_garbage_numeric_params fails if not.

  t-3  Build redacting OkHttp client factory
       files:    providers/drivers/ChatHttpClient.kt
       covers:   c-4
       contract: Factory returns an OkHttpClient whose logging interceptor redacts Authorization/x-api-key headers and never logs bodies; auth is injected via header only. If a key reaches a log line or a query string, ChatHttpClientTest.key_never_in_logged_request (captures the interceptor log sink + asserts request URL has no key) fails.

Wave 2 (depends t-1, t-2, t-3)
  t-4  Implement Anthropic SSE driver
       files:    providers/drivers/AnthropicChatDriver.kt
       covers:   c-2, c-4, c-5
       contract: Maps Anthropic Messages SSE (content_block_delta text deltas, system as top-level field, prior turns as messages[]). If a malformed/blank SSE data line or a missing content_block is parsed as empty text instead of skipped, AnthropicChatDriverTest.malformed_sse_line_does_not_emit_blank_token fails; a 401 body maps to ChatError.AuthFailed (not generic) or AnthropicChatDriverTest.maps_401_to_auth_failed fails.

  t-5  Implement OpenAI-compatible + OpenRouter SSE driver
       files:    providers/drivers/OpenAiCompatibleChatDriver.kt
       covers:   c-2, c-4, c-5
       contract: Maps OpenAI chat/completions SSE (choices[].delta.content, [DONE] sentinel terminates, system+history as messages[]). Serves both OPENAI_COMPATIBLE and OPENROUTER (base-URL preset). If the [DONE] sentinel is parsed as JSON and throws, or OpenRouter's base URL is not honored, OpenAiCompatibleChatDriverTest.done_sentinel_terminates_without_parse_error / .openrouter_uses_preset_base_url fails.

  t-6  Implement Ollama streaming-JSON driver
       files:    providers/drivers/OllamaChatDriver.kt
       covers:   c-2, c-5
       contract: Maps Ollama /api/chat NDJSON (one JSON object per line, message.content deltas, done=true terminates, no API key sent). If a partial/half-buffered NDJSON line is parsed before newline and crashes the Flow, OllamaChatDriverTest.partial_ndjson_line_buffers_until_newline fails; OllamaChatDriverTest.sends_no_authorization_header fails if a key header leaks onto a keyless provider.

  t-7  Route slot to driver + assemble request from history
       files:    providers/drivers/ChatDriverFactory.kt, providers/ProviderRepository.kt
       covers:   c-2, c-5
       contract: Factory selects driver by ProviderType.driverType (OpenRouter→OpenAI-compatible) and pulls key via keyFor(id); repository exposes a send(history) entry that forwards prior turns. If a type maps to the wrong driver, ChatDriverFactoryTest.openrouter_routes_to_openai_driver fails; if only the latest message (not prior turns) is forwarded, ChatDriverFactoryTest.full_history_passed_to_driver fails.

Wave 3 (depends t-4, t-5, t-6, t-7)
  t-8  Session holder + chat ViewModel
       files:    ui/chat/ChatSessionHolder.kt, ui/chat/ChatViewModel.kt
       covers:   c-1, c-3, c-5
       contract: In-memory holder survives nav (singleton-scoped), New-chat clears it, no persistence. Sending with no CHAT mapping emits a guidance state (no driver call, no crash) — ChatViewModelTest.unmapped_chat_emits_guidance_not_crash fails otherwise. Live tokens append to the in-progress message — ChatViewModelTest.tokens_append_live fails if the reply only appears on completion. A driver ChatError becomes a human-readable error bubble with no key text — ChatViewModelTest.error_bubble_is_readable_and_keyless fails. Follow-up sends prior turns — ChatViewModelTest.followup_includes_prior_turns fails.

  t-9  Chat screen + nav route
       files:    ui/chat/ChatScreen.kt, MainActivity.kt
       covers:   c-1, c-3
       contract: Dedicated screen on its own Route.CHAT reachable from Home; renders streamed reply, a New-chat action, and the unmapped-CHAT guidance with a "configure a provider" affordance. If the route is unreachable, NavigationTest.home_navigates_to_the_chat_route fails; if the unmapped guidance/CTA is absent, ChatScreenTest.unmapped_state_shows_configure_cta fails.

## Coverage
- c-1 (typed message → real reply rendered): t-1, t-2, t-8, t-9
- c-2 (all four provider types parse correctly): t-4, t-5, t-6, t-7
- c-3 (no CHAT mapping → in-UI guidance, no crash/no-op): t-8, t-9
- c-4 (failed request → readable error, key never logged/in-message): t-1, t-3, t-4, t-5
- c-5 (follow-up sends prior turns as context): t-4, t-5, t-6, t-7, t-8

## Judgment calls
- Split drivers into three tasks (t-4/t-5/t-6) rather than one "implement drivers": each provider's parse quirks are distinct failure surfaces (Anthropic content_block_delta vs OpenAI [DONE] sentinel vs Ollama NDJSON buffering); one test owner per quirk is the whole point of the risk lens. Rejected a single driver task because a malformed-SSE bug in one provider would hide behind another's green test.
- OpenAI-compatible and OpenRouter share one task (t-5), matching the locked stack decision ("OpenRouter is a base-URL preset on the OpenAI-compatible driver") — a separate OpenRouter driver would be dead duplicate code; the only OpenRouter-specific risk (wrong base URL) is covered by a dedicated assertion in t-5/t-7.
- Made the redacting HTTP client (t-3) its own wave-1 task instead of folding key-handling into each driver. The key-leak path (r-03) is a single cross-cutting risk; owning it in one factory + one test means the never-log guarantee is enforced once, not re-proven (and re-riskable) in three drivers.
- Put the unmapped-CHAT guard (c-3) in the ViewModel (t-8), not the screen (t-9). The "no silent no-op / no crash" risk is logic, not layout — testing it at the ViewModel makes the guard assertable without Compose, and t-9 only verifies the CTA renders.
- request_params (t-2) is wave-1 and bundled with the model+store change rather than split per field: it is one schema extension across two layers (model+persistence) under the 5-file/2-layer cap, and the real risk (blank=provider-default vs serialized-empty, garbage numerics) is a single round-trip/validation surface.
- Session holder + ViewModel combined (t-8) because the in-memory-survives-nav / clear-on-New-chat / lost-on-death semantics are only observable through the ViewModel's consumption of the holder; splitting them would create a holder task with no externally-testable behavior.
