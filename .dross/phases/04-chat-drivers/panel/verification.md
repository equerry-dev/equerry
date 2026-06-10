Phase 04-chat-drivers — 10 tasks across 4 waves

Lens: VERIFICATION. Every task is shaped so its acceptance is a JVM unit test
(./gradlew testDebugUnitTest, Robolectric/MockWebServer available). Driver logic is
split into pure parse/map/redact functions that a test can drive line-by-line, with
MockWebServer covering the HTTP/SSE seam end-to-end. UI is thin over a unit-testable
ViewModel + session holder.

Wave 1
  t-1  Define chat message + token + error model
       files:    app/src/main/java/dev/equerry/app/providers/drivers/ChatMessage.kt
                 app/src/main/java/dev/equerry/app/providers/drivers/ChatModels.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/ChatModelsTest.kt
       covers:   c-1, c-4, c-5
       contract: ChatMessage carries role (SYSTEM/USER/ASSISTANT) + content; the token
                 type distinguishes a Delta(text) from Done; ChatError is a sealed type
                 with Network/Auth/Http(code)/Malformed cases each holding a
                 human-readable message. If a role or error case is dropped, the
                 exhaustiveness/round-trip assertions in ChatModelsTest fail to compile
                 or assert.

  t-2  Pure error mapper for failed requests
       files:    app/src/main/java/dev/equerry/app/providers/drivers/ChatErrorMapper.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/ChatErrorMapperTest.kt
       covers:   c-4
       contract: ChatErrorMapper.map(...) turns HTTP 401 -> ChatError.Auth, any other
                 non-2xx -> ChatError.Http(code), a thrown IOException/UnknownHostException
                 -> ChatError.Network, and a parse failure -> ChatError.Malformed.
                 If the 401->Auth branch or the unreachable-host->Network branch breaks,
                 the corresponding table case in ChatErrorMapperTest fails. A second test
                 asserts no mapped message contains a key substring fed through the input.

  t-3  Key-redaction utility + redacting log policy
       files:    app/src/main/java/dev/equerry/app/providers/drivers/KeyRedaction.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/KeyRedactionTest.kt
       covers:   c-4
       contract: KeyRedaction.redact(text, key) replaces every occurrence of a non-blank
                 key with a fixed mask and is a no-op for blank keys. If redaction stops
                 catching a key embedded mid-string (header dump, error body), the
                 "key never survives redaction" assertion in KeyRedactionTest fails.

  t-4  In-memory session history holder
       files:    app/src/main/java/dev/equerry/app/providers/drivers/ChatSession.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/ChatSessionTest.kt
       covers:   c-5
       contract: ChatSession.append/turns accumulate user+assistant turns in order;
                 messagesForRequest() returns prior turns ahead of the new user message
                 (and prepends a system prompt when supplied); clear() empties it. If
                 follow-up context assembly regresses (prior turns dropped/reordered),
                 the multi-turn ordering assertion in ChatSessionTest fails; a clear()
                 test covers New-chat reset.

  t-5  Add request-params fields to profile + validator
       files:    app/src/main/java/dev/equerry/app/providers/ProviderProfile.kt
                 app/src/main/java/dev/equerry/app/providers/ProfileValidator.kt
                 app/src/test/java/dev/equerry/app/providers/ProfileValidatorTest.kt
                 app/src/test/java/dev/equerry/app/providers/ProviderRepositoryTest.kt
       covers:   c-5
       contract: ProviderProfile + ProfileDraft gain optional systemPrompt:String?,
                 temperature:Double?, maxTokens:Int? (blank/null = provider default).
                 ProfileValidatorTest asserts a profile with all three blank still
                 validates, and a non-numeric temperature/maxTokens is flagged.
                 ProviderRepositoryTest asserts the three fields survive an
                 addProfile -> observeProfiles round-trip (and key still never persisted).

Wave 2 (depends t-1, t-2, t-3)
  t-6  SSE token parser (Anthropic + OpenAI/OpenRouter)
       files:    app/src/main/java/dev/equerry/app/providers/drivers/SseTokenParser.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/SseTokenParserTest.kt
       covers:   c-2, c-4
       contract: Pure functions decode one OpenAI `data:` chunk and one Anthropic
                 content_block_delta event into a Delta(text); the OpenAI `[DONE]`
                 sentinel and Anthropic message_stop yield Done. SseTokenParserTest
                 feeds a captured real chunk sequence per format and asserts the
                 concatenated Deltas equal the expected reply; a malformed/partial
                 `data:` line yields ChatError.Malformed rather than throwing — if the
                 malformed-SSE path regresses, that test fails.

  t-7  Ollama streaming-JSON token parser
       files:    app/src/main/java/dev/equerry/app/providers/drivers/OllamaStreamParser.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/OllamaStreamParserTest.kt
       covers:   c-2, c-4
       contract: Parser decodes Ollama newline-delimited JSON objects, emitting
                 Delta(message.content) per line and Done on the `"done":true` object.
                 OllamaStreamParserTest asserts a multi-line capture concatenates to the
                 full reply and that a truncated final line maps to ChatError.Malformed
                 instead of crashing.

  t-8  Request body builders per provider type
       files:    app/src/main/java/dev/equerry/app/providers/drivers/ChatRequestBuilder.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/ChatRequestBuilderTest.kt
       covers:   c-2, c-4, c-5
       contract: Builders produce the JSON body + relative path + auth header shape per
                 type (Anthropic x-api-key + version header; OpenAI/OpenRouter Bearer;
                 Ollama keyless) from messages + optional params. Test asserts: stream=true
                 is set; the request carries every prior turn from t-4 (c-5); system
                 prompt/temperature/max_tokens appear only when set and are omitted when
                 blank; and the produced body/path string contains no API key (key lives
                 in headers only, never path/query — r-03). If a param leaks into the URL
                 or a turn is dropped, ChatRequestBuilderTest fails.

Wave 3 (depends t-6, t-7, t-8, plus t-1/t-2/t-3)
  t-9  ChatDriver wiring + MockWebServer end-to-end
       files:    app/src/main/java/dev/equerry/app/providers/drivers/ChatDriver.kt
                 app/src/main/java/dev/equerry/app/providers/drivers/ChatDriverFactory.kt
                 app/src/main/java/dev/equerry/app/di/RepositoryModule.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/ChatDriverTest.kt
       covers:   c-1, c-2, c-4
       contract: ChatDriver.send(profile, key, messages): Flow<ChatToken> wires okhttp
                 (+SSE EventSource) to the t-6/t-7 parsers and t-8 builders; the factory
                 selects by profile.type.driverType (OpenRouter -> OpenAI-compatible).
                 ChatDriverTest runs MockWebServer once per provider type: enqueues a
                 canned streaming body and asserts the collected Flow's concatenated text
                 equals the expected reply (c-2). A 401 response asserts the Flow surfaces
                 ChatError.Auth (c-4); a request-line/header assertion confirms the key is
                 in the auth header and absent from the recorded request path. NOTE: adds
                 testImplementation okhttp3:mockwebserver (same okhttp 4.12.0 version) to
                 libs.versions.toml + app/build.gradle.kts — not yet present.

Wave 4 (depends t-4, t-5, t-9)
  t-10 Chat screen + ViewModel on dedicated route
       files:    app/src/main/java/dev/equerry/app/ui/chat/ChatViewModel.kt
                 app/src/main/java/dev/equerry/app/ui/chat/ChatScreen.kt
                 app/src/main/java/dev/equerry/app/MainActivity.kt
                 app/src/test/java/dev/equerry/app/ui/chat/ChatViewModelTest.kt
                 app/src/test/java/dev/equerry/app/NavigationTest.kt
       covers:   c-1, c-3, c-5
       contract: ChatViewModel(repository, driverFactory, session) exposes a state with
                 messages + an unmapped-guidance flag + a per-message error. Driven by a
                 fake ChatDriver, ChatViewModelTest asserts: (a) live tokens append to the
                 growing assistant bubble as the Flow emits (c-1); (b) send() with no CHAT
                 mapping sets the guidance flag and never calls the driver — no crash, no
                 silent no-op (c-3); (c) a second send carries the first turn into the
                 driver call via the session (c-5); (d) newChat() clears the session.
                 NavigationTest gains a chatContent stub + Route.CHAT and asserts Home
                 navigates to the chat route. If the unmapped guard or live-append breaks,
                 the matching ChatViewModelTest case fails.

## Coverage
- c-1 (typed message -> rendered real reply): t-1, t-9, t-10
- c-2 (each provider type parses correctly): t-6, t-7, t-8, t-9
- c-3 (no CHAT mapping -> in-UI guidance, no crash/no-op): t-10
- c-4 (failed request -> human error, key never logged/in message): t-1, t-2, t-3, t-6, t-7, t-8, t-9
- c-5 (follow-up sends prior turns as context): t-4, t-5, t-8, t-10

## Judgment calls
- Split token decoding (t-6/t-7) out of the driver (t-9) as pure line->token functions: chosen over decoding inside the okhttp callback because SSE/JSON-stream edge cases (malformed/partial lines, [DONE], done:true) are then exhaustively unit-testable without a socket; rejected the all-in-driver approach because those branches would only be reachable through fragile MockWebServer timing.
- Error mapping (t-2) and key redaction (t-3) as standalone pure units rather than folded into the driver: lets c-4's 401/Network/Malformed table and the key-never-survives assertion run in milliseconds and independently of HTTP; rejected testing key-safety only via the driver because a single end-to-end assertion can't prove redaction across header dumps, error bodies, and URLs.
- ChatRequestBuilder (t-8) separated from ChatDriver: makes "prior turns included" (c-5) and "key never in URL/query" (r-03) assertable on a plain string without a live request; rejected building the body inline in t-9 because the no-key-in-path guarantee would then depend on reading a recorded request rather than a direct unit assertion.
- Session holder (t-4) as a plain injectable class, not ViewModel-internal state: lets append/ordering/clear semantics (c-5, New-chat reset) be tested directly and shared so it survives navigation (locked decision) without a Robolectric harness; rejected embedding history in the ViewModel because process-death/clear/survive semantics would need a recreated-activity test instead of a pure unit test.
- request_params added to ProviderProfile + validator (t-5) rather than a separate config object: the locked decision says extend the existing profile/form, and round-tripping through the existing ProfileStore is already covered by ProviderRepositoryTest patterns; rejected a parallel params store as contradicting the locked decision.
- ChatViewModel tested against a fake ChatDriver, not MockWebServer (t-10): keeps the unmapped-guard (c-3) and live-append (c-1) tests pure and deterministic; the real HTTP/SSE seam is already proven in t-9, so re-driving it through the VM would only add flakiness. Chose to extend the existing NavigationTest for the route rather than a full Robolectric ChatScreen render test, matching the established NavigationTest stub pattern.
- MockWebServer flagged as a new testImplementation in t-9 (not a separate wave-1 dep task): it is the one missing dependency, same okhttp version, and adding it is inseparable from the driver test that needs it; a standalone dep-only task would be under the 10-minute merge threshold.
