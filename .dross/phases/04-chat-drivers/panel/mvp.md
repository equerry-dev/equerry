Phase 04-chat-drivers — 6 tasks across 3 waves

Wave 1
  t-1  Define chat driver contract + message model
       files:    providers/drivers/ChatDriver.kt, providers/drivers/ChatModels.kt
       covers:   c-1, c-4, c-5
       description: ChatDriver interface exposing `fun stream(req: ChatRequest): Flow<ChatToken>` (live token Flow, locked). ChatModels holds ChatMessage(role, content), ChatRequest(messages, model, baseUrl, key, systemPrompt?, temperature?, maxTokens?), and a sealed ChatError(NETWORK, AUTH, HTTP_STATUS, MALFORMED) carrying a UI-safe human message. ChatError construction must never embed the key.
       contract: if a ChatError message is built by concatenating the request key, ChatModelsTest.errorMessageNeverContainsKey fails; if the Flow type is made non-incremental (single emission) the contract test asserting multiple token emissions fails.

  t-2  Extend ProviderProfile with request params
       files:    providers/ProviderProfile.kt, data/ProfileStore.kt
       covers:   c-1
       description: Add optional systemPrompt:String?, temperature:Double?, maxTokens:Int? to ProviderProfile and ProfileDraft; persist them in StoredProfile (blank/null = provider default, locked). ignoreUnknownKeys keeps old stored JSON loadable.
       contract: if the new fields aren't round-tripped through StoredProfile, ProfileStoreTest.requestParamsRoundTrip fails; if a pre-existing JSON blob without these keys fails to decode, ProfileStoreTest.legacyJsonStillDecodes fails.

  t-3  Implement Anthropic + OpenAI-compatible SSE drivers
       files:    providers/drivers/AnthropicDriver.kt, providers/drivers/OpenAiCompatDriver.kt, providers/drivers/SseSupport.kt
       covers:   c-2, c-4, c-5
       description: Two ChatDriver impls over okhttp-sse. Anthropic: /v1/messages, x-api-key header, content_block_delta parsing. OpenAiCompat: /v1/chat/completions, Bearer header, choices[].delta.content parsing — also serves OpenRouter (driverType reuse, no new file). Both build messages from full history (c-5), apply system/temperature/max-tokens when set, map non-2xx→ChatError.HTTP_STATUS/AUTH and parse failures→ChatError.MALFORMED. SseSupport = shared EventSource→Flow bridge + OkHttp client with a redacting logging interceptor.
       contract: if the malformed-SSE path doesn't surface ChatError.MALFORMED, OpenAiCompatDriverTest.malformedDeltaEmitsMalformedError fails; if a 401 isn't mapped, AnthropicDriverTest.unauthorizedMapsToAuthError fails; if the auth header is logged in cleartext, SseSupportTest.loggingInterceptorRedactsAuthHeader fails; if only the last message (not history) is sent, OpenAiCompatDriverTest.requestIncludesPriorTurns fails.

  t-4  Implement Ollama streaming-JSON driver
       files:    providers/drivers/OllamaDriver.kt
       covers:   c-2, c-4, c-5
       description: ChatDriver impl over /api/chat reading newline-delimited streaming JSON (no SSE), emitting message.content deltas as ChatToken; keyless; full history; system/options(temperature/num_predict) when set; non-2xx/unreachable→ChatError, bad JSON line→ChatError.MALFORMED. Reuses SseSupport's OkHttp client from t-3.
       contract: if a streaming-JSON chunk isn't decoded into a token, OllamaDriverTest.streamingJsonChunksBecomeTokens fails; if an unreachable host doesn't map to ChatError.NETWORK, OllamaDriverTest.unreachableHostMapsToNetworkError fails.

Wave 2 (depends t-1, t-2, t-3, t-4)
  t-5  Chat orchestration: service, session holder, driver resolution
       files:    providers/drivers/ChatService.kt, providers/drivers/ChatDriverFactory.kt, providers/drivers/ChatSession.kt, di/ChatModule.kt
       covers:   c-1, c-2, c-3, c-5
       description: ChatDriverFactory maps profile.type.driverType→ChatDriver instance. ChatSession = @Singleton in-memory history holder (survives navigation, New-chat clears, no persistence — locked). ChatService.send(text): resolves observeChatMapping()+keyFor(id), returns a typed "no CHAT mapping" result when unmapped (c-3, no crash/no-op), else appends user turn to ChatSession, builds ChatRequest with prior turns + profile request-params, runs the resolved driver's Flow, accumulates the assistant turn back into ChatSession on completion. ChatModule provides factory/service/session via Hilt.
       contract: if send is called with no CHAT profile and it throws or silently no-ops instead of returning the unmapped guard, ChatServiceTest.unmappedChatReturnsGuidance fails; if OPENROUTER doesn't resolve to the OpenAI-compat driver, ChatDriverFactoryTest.openRouterUsesOpenAiDriver fails; if a second send doesn't carry the first exchange, ChatServiceTest.followUpSendsPriorTurns fails; if New-chat doesn't empty history, ChatSessionTest.newChatClearsHistory fails.

Wave 3 (depends t-5)
  t-6  Chat screen, ViewModel, nav route + request-params form fields
       files:    ui/chat/ChatViewModel.kt, ui/chat/ChatScreen.kt, MainActivity.kt, ui/providers/ProviderEditViewModel.kt, ui/providers/ProviderEditScreen.kt
       covers:   c-1, c-3, c-4
       description: ChatViewModel drives ChatService, exposing message list + a streaming assistant bubble that appends tokens live (locked), a New-chat action, an unmapped-CHAT banner with a configure link, and an error bubble from ChatError.message. ChatScreen = dedicated screen; add Route.CHAT + composable in EquerryNavHost and a HomeEntry to reach it (locked dedicated route). Extend ProviderEdit VM/Screen with optional system-prompt/temperature/max-tokens inputs wired to the t-2 fields (blank = default).
       contract: if tokens replace rather than append, ChatScreenTest.tokensAppendLiveToBubble fails; if the unmapped state shows no configure guidance, ChatScreenTest.unmappedShowsConfigureBanner fails; if a ChatError isn't rendered as a readable bubble, ChatViewModelTest.errorSurfacesReadableMessage fails; if the CHAT route is absent, NavigationTest.chatRouteReachableFromHome fails; if request-param inputs don't persist, ProviderEditViewModelTest.requestParamsSaved fails.

## Coverage
- c-1 (typed message → real reply rendered): t-1, t-2, t-5, t-6
- c-2 (each of four provider types parses correctly): t-3, t-4, t-5
- c-3 (no CHAT mapping → in-UI guidance, no crash): t-5, t-6
- c-4 (failed request → readable error, key never logged/in message): t-1, t-3, t-4, t-6
- c-5 (follow-up sends prior turns as context): t-1, t-3, t-4, t-5

## Judgment calls
- Chose 3 driver tasks split as SSE-pair (t-3) + Ollama (t-4) over one-driver-per-task (4 tasks); rejected the 4-way split because Anthropic/OpenAI share the SSE bridge and would duplicate setup, and OpenRouter is a base-URL preset on the OpenAI driver (no file) per locked stack. Rejected one mega driver task because 4 files + shared plumbing exceeds the 5-file ceiling.
- Folded OkHttp/SSE/redacting-interceptor plumbing into t-3 (SseSupport.kt) rather than a standalone wave-1 network task; it's <2 files of glue and a network-only task would carry no criterion of its own.
- Put request_params persistence (t-2) in wave 1 next to the model, but its UI form fields in t-6; rejected a dedicated request_params task because the data change is a few fields on an existing file and the form change rides along with the screen layer already being touched.
- Merged ChatService + session holder + factory + DI into one wave-2 task (t-5); rejected splitting session-holder out because it's a single @Singleton class with one clear test and splitting would create a wave-2→wave-2 dependency with no parallelism gain.
- Merged the chat UI and the provider-form param fields into t-6 (one task, UI layer only, 5 files); rejected a separate provider-form task because it would touch only 2 files for <10 min and both are pure UI-layer edits gated on the same wave-2 output.

mvp: 6 tasks across 3 waves, criteria covered 5/5
