# Synthesis — Phase 04-chat-drivers

Judged three independently-drafted decompositions (risk / mvp / verification). Source-validated
against the live tree before merging: `okhttp-sse` + `logging-interceptor` are already on the
classpath but `mockwebserver` is NOT; `ProviderType.driverType` already collapses
OPENROUTER→OPENAI_COMPATIBLE; `ProviderRepository` already exposes `observeChatMapping()`,
`keyFor(id)`, `observeProfiles()`; `ProfileStore` already sets `ignoreUnknownKeys = true`;
`ProfileValidator` returns `List<FieldError>` over a `ProfileField` enum with no numeric fields yet;
`EquerryNavHost` takes `providersContent/slotsContent/probeContent` lambdas + a `Route` object and
`NavigationTest` stubs those lambdas.

## Scores

Scale: 1 (weak) – 5 (strong).

| Dimension                  | risk | mvp | verification |
| -------------------------- | :--: | :-: | :----------: |
| Criteria coverage          | 5 — all 5 mapped, c-4 deepest (key-in-log + key-in-URL split) | 5 — all 5, but c-4 leans on driver tests | 5 — all 5, c-4 spread across 7 tasks, c-2 most granular |
| Test-contract specificity  | 5 — named failing test per quirk (malformed_sse, done_sentinel, partial_ndjson) | 3 — contracts present but several tests bundled per task; thinner per-assertion naming | 5 — every task is a named JVM test; pure parse/map/redact units are line-drivable |
| Granularity                | 4 — 9 tasks, clean one-risk-per-task; driver split good | 3 — 6 tasks, t-5/t-6 are large multi-concern merges (4–5 files each) | 3 — 10 tasks; pure-function split is testable but t-2/t-3 are arguably too fine |
| Wave correctness           | 4 — 3 waves; t-7 (factory) in wave 2 alongside drivers is slightly tight | 4 — 3 waves, clean; t-5 mega-task limits wave-2 parallelism | 5 — 4 waves; dependencies precise (parsers/builders before driver wiring before VM) |

**Skeleton: `verification`.** It has the cleanest dependency graph (4 waves with parsers and
request-builders correctly preceding driver wiring, and the VM correctly fenced behind both the
driver and the session/profile work), and every task's acceptance is already a named JVM test —
which is exactly the artifact a dross plan needs. Its weakness (over-fine pure-function splits and a
missing redacting-HTTP-client task as risk frames it) is fixable by grafting from risk and mvp.

## Merged plan

Phase 04-chat-drivers — 10 tasks across 4 waves

### Wave 1

t-1  Define chat message + token + error model  **[verification+mvp]**
  files:    providers/drivers/ChatMessage.kt, providers/drivers/ChatModels.kt
  covers:   c-1, c-4, c-5
  contract: ChatMessage carries role (SYSTEM/USER/ASSISTANT) + content; the token type
            distinguishes Delta(text) from Done; ChatError is a sealed type with
            Network/Auth/Http(code)/Malformed cases each holding a UI-safe human-readable
            message. ChatError construction must never embed a request key. If a role or error
            case is dropped, the exhaustiveness/round-trip assertions in ChatModelsTest fail to
            compile/assert; if a ChatError message is built by concatenating the key,
            ChatModelsTest.errorMessageNeverContainsKey fails.
  depends_on: []

t-2  Pure error mapper for failed requests  **[verification]**
  files:    providers/drivers/ChatErrorMapper.kt
  covers:   c-4
  contract: ChatErrorMapper.map(...) turns HTTP 401 → ChatError.Auth, other non-2xx →
            ChatError.Http(code), IOException/UnknownHostException → ChatError.Network, parse
            failure → ChatError.Malformed. Table cases in ChatErrorMapperTest fail on any broken
            branch; a second test asserts no mapped message contains a key substring fed through
            the input.
  depends_on: [t-1]

t-3  Key-redaction utility + redacting OkHttp logging policy  **[verification+risk]**
  files:    providers/drivers/KeyRedaction.kt, providers/drivers/ChatHttpClient.kt
  covers:   c-4
  contract: KeyRedaction.redact(text, key) replaces every occurrence of a non-blank key with a
            fixed mask and is a no-op for blank keys (KeyRedactionTest.keyNeverSurvivesRedaction).
            ChatHttpClient factory returns one shared OkHttpClient whose logging interceptor
            redacts Authorization/x-api-key headers and never logs bodies; auth is injected via
            header only, never URL/query. ChatHttpClientTest.key_never_in_logged_request (captures
            the interceptor log sink + asserts request URL carries no key) fails if a key leaks.
  depends_on: [t-1]

t-4  In-memory session history holder  **[verification+risk]**
  files:    providers/drivers/ChatSession.kt
  covers:   c-5
  contract: @Singleton-scoped ChatSession.append/turns accumulate user+assistant turns in order;
            messagesForRequest() returns prior turns ahead of the new user message (prepending a
            system prompt when supplied); clear() empties it. Survives in-app navigation (singleton),
            New-chat clears, no persistence. ChatSessionTest asserts multi-turn ordering and that
            clear() (New-chat) empties history.
  depends_on: [t-1]

t-5  Add request-params fields to profile + store + validator  **[risk+verification+mvp]**
  files:    providers/ProviderProfile.kt, providers/ProfileValidator.kt, data/ProfileStore.kt
  covers:   c-1, c-5
  contract: ProviderProfile + ProfileDraft gain optional systemPrompt:String?, temperature:Double?,
            maxTokens:Int? (blank/null = provider default — locked). StoredProfile round-trips them;
            because Json{ignoreUnknownKeys=true} is already set, legacy JSON without these keys still
            decodes (ProfileStoreTest.legacyJsonStillDecodes). ProfileValidator gains numeric fields:
            all-blank validates; non-numeric temperature/maxTokens yields a FieldError
            (ProfileValidatorTest.rejects_garbage_numeric_params). ProviderRepositoryTest asserts the
            three fields survive addProfile→observeProfiles and the key is still never persisted.
  depends_on: []

### Wave 2 (depends t-1, t-2, t-3)

t-6  SSE token parser (Anthropic + OpenAI/OpenRouter)  **[verification]**
  files:    providers/drivers/SseTokenParser.kt
  covers:   c-2, c-4
  contract: Pure functions decode one OpenAI `data:` chunk and one Anthropic content_block_delta
            event into Delta(text); OpenAI `[DONE]` sentinel and Anthropic message_stop yield Done.
            SseTokenParserTest feeds a captured real chunk sequence per format and asserts the
            concatenated Deltas equal the expected reply; a malformed/partial `data:` line (incl. the
            `[DONE]` sentinel never parsed as JSON) yields ChatError.Malformed rather than throwing.
  depends_on: [t-1, t-2, t-3]

t-7  Ollama streaming-JSON token parser  **[verification+risk]**
  files:    providers/drivers/OllamaStreamParser.kt
  covers:   c-2, c-4
  contract: Decodes Ollama newline-delimited JSON objects, emitting Delta(message.content) per line
            and Done on `"done":true`. OllamaStreamParserTest asserts a multi-line capture
            concatenates to the full reply and that a truncated/half-buffered final line buffers
            until newline (does not crash the Flow) and ultimately maps to ChatError.Malformed.
  depends_on: [t-1, t-2, t-3]

t-8  Request body builders per provider type  **[verification]**
  files:    providers/drivers/ChatRequestBuilder.kt
  covers:   c-2, c-4, c-5
  contract: Builders produce JSON body + relative path + auth-header shape per type (Anthropic
            x-api-key + version header; OpenAI/OpenRouter Bearer; Ollama keyless) from messages +
            optional params. ChatRequestBuilderTest asserts: stream=true set; every prior turn from
            t-4 carried (c-5); system prompt/temperature/max_tokens present only when set, omitted
            when blank; produced body/path contains no API key (key in headers only, never
            path/query — r-03). Fails if a param leaks into the URL or a turn is dropped.
  depends_on: [t-1, t-4, t-5]

### Wave 3 (depends t-6, t-7, t-8, and t-1/t-2/t-3)

t-9  ChatDriver wiring + factory + MockWebServer end-to-end  **[verification+risk+mvp]**
  files:    providers/drivers/ChatDriver.kt, providers/drivers/ChatDriverFactory.kt,
            di/RepositoryModule.kt
  covers:   c-1, c-2, c-4, c-5
  contract: ChatDriver.send(profile, key, messages): Flow<ChatToken> wires the t-3 OkHttp client
            (+SSE EventSource) to the t-6/t-7 parsers and t-8 builders. ChatDriverFactory selects by
            profile.type.driverType (OpenRouter→OpenAI-compatible — already in ProviderType).
            ChatDriverTest runs MockWebServer once per provider type: canned streaming body →
            collected Flow's concatenated text equals expected reply (c-2); a 401 surfaces
            ChatError.Auth (c-4); a recorded-request assertion confirms the key is in the auth header
            and absent from the request path. Factory test: ChatDriverFactoryTest.openRouterUsesOpenAiDriver.
            NOTE: adds testImplementation okhttp3:mockwebserver (same okhttp 4.12.0) to
            libs.versions.toml + app/build.gradle.kts — not yet present.
  depends_on: [t-6, t-7, t-8]

### Wave 4 (depends t-4, t-5, t-9)

t-10 Chat screen + ViewModel + nav route + provider-form param fields  **[verification+mvp]**
  files:    ui/chat/ChatViewModel.kt, ui/chat/ChatScreen.kt, MainActivity.kt,
            ui/providers/ProviderEditViewModel.kt, ui/providers/ProviderEditScreen.kt
  covers:   c-1, c-3, c-4, c-5
  contract: ChatViewModel(repository, driverFactory, session) exposes state = messages + an
            unmapped-guidance flag + a per-message error. Driven by a fake ChatDriver,
            ChatViewModelTest asserts: (a) live tokens append to the growing assistant bubble as the
            Flow emits (c-1, locked live-streaming); (b) send() with no CHAT mapping sets the guidance
            flag and never calls the driver — no crash, no silent no-op (c-3); (c) a second send
            carries the first turn via the session (c-5); (d) newChat() clears the session; (e) a
            ChatError renders as a readable, keyless bubble (c-4). ChatScreen = dedicated screen; add
            Route.CHAT + a chatContent composable to EquerryNavHost and an onChat HomeEntry, following
            the existing providersContent/slotsContent/probeContent stub pattern — NavigationTest
            gains a chatContent stub + asserts Home navigates to Route.CHAT. ProviderEdit VM/Screen
            gain optional system-prompt/temperature/max-tokens inputs wired to the t-5 fields
            (blank = default); ProviderEditViewModelTest.requestParamsSaved asserts persistence.
  depends_on: [t-4, t-5, t-9]

### Coverage
- c-1: t-1, t-9, t-10
- c-2: t-6, t-7, t-8, t-9
- c-3: t-10
- c-4: t-1, t-2, t-3, t-6, t-7, t-8, t-9, t-10
- c-5: t-1, t-4, t-5, t-8, t-9, t-10

All five criteria covered. r-03 (key never logged / never in URL) is owned end-to-end:
redaction unit (t-3), no-key-in-body/path unit (t-8), recorded-request assertion (t-9),
keyless error bubble (t-10).

## Disagreements

**1. Driver decomposition: pure parsers vs. whole-driver tasks.**
- verification: split decode into pure `SseTokenParser`/`OllamaStreamParser` + `ChatRequestBuilder`
  units (t-6/t-7/t-8), with HTTP wiring in a separate MockWebServer task (t-9).
- risk: one task per *driver* (t-4 Anthropic, t-5 OpenAI/OpenRouter, t-6 Ollama) — parse + HTTP
  fused, one failing test per provider quirk.
- mvp: SSE-pair driver (t-3) + Ollama driver (t-4) fused, two tasks total.
- Provisional default: **verification's pure-parser split (kept t-6/t-7/t-8 + t-9).** Why it
  matters: the locked live-token Flow + the never-log-key constraint mean the highest-value
  assertions (malformed-line handling, `[DONE]`, NDJSON buffering, no-key-in-path) are exactly the
  ones that are flaky-to-impossible to pin down behind a live socket; isolating them as pure
  functions makes each a millisecond JVM test. risk's "one test owner per quirk" intent is preserved
  — it's just expressed as parser units rather than driver units. The MockWebServer end-to-end (t-9)
  still proves the real HTTP/SSE seam per provider type, capturing the value risk/mvp wanted from
  per-driver tests. Cost: 2 extra tasks vs. mvp. If the executor finds the parser/driver boundary
  artificial in code, t-6+t-9 (SSE) may collapse — flagged, not silently merged.

**2. Redacting HTTP client: standalone wave-1 task vs. folded into the driver layer.**
- risk: standalone wave-1 `ChatHttpClient` task (t-3) — own the cross-cutting key-leak risk once.
- verification: standalone `KeyRedaction` pure unit (t-3), but the OkHttp client itself lives inside
  the driver wiring (t-9).
- mvp: folded entirely into `SseSupport.kt` inside the driver task (t-3), no standalone unit.
- Provisional default: **merged risk + verification — one wave-1 task carrying BOTH
  `KeyRedaction.kt` (pure unit) AND `ChatHttpClient.kt` (the shared redacting client factory).** Why
  it matters: r-03 is a hard rule; enforcing the never-log guarantee in one factory + one redaction
  unit means it is proven once and reused by all drivers, rather than re-implemented (and
  re-riskable) in three. mvp's fold-in loses the standalone redaction assertion that proves safety
  across header dumps / error bodies / URLs independent of any live request — that assertion is too
  valuable to drop. This is the one place the merge departs from the pure-verification skeleton: the
  client factory is pulled forward from t-9 into t-3.

**3. request_params validator + the "garbage numeric" test.**
- risk + verification: extend `ProfileValidator` and add a `rejects_garbage_numeric_params` /
  non-numeric-flagged test (temperature/maxTokens must parse as numbers).
- mvp: omits the validator entirely — only model + store fields, no numeric validation task.
- Provisional default: **include the validator extension (risk/verification).** Why it matters: the
  locked decision puts temperature/max-tokens as free-text-ish form fields; without numeric
  validation a user can type "hot" and the failure surfaces only as a provider 400 mid-request,
  violating the spirit of c-4's "human-readable error" by deferring it to the network. The live
  `ProfileValidator` already returns `List<FieldError>` over a `ProfileField` enum, so the extension
  is a natural, cheap addition. mvp's omission is treated as a gap, not a deliberate scope cut.

**4. ViewModel vs. session-holder ownership of c-3/c-5 logic.**
- risk: an explicit `ChatService`-less design — guard logic lives in the ViewModel (t-8); session
  holder fused with the ViewModel.
- mvp: a dedicated `ChatService` orchestration layer (t-5) holding resolution + unmapped-guard +
  history assembly, with the ViewModel as a thin driver on top.
- verification: session holder is a standalone injectable (t-4); the unmapped-guard + live-append
  logic lives in the ViewModel (t-10), tested against a fake driver — no separate service.
- Provisional default: **verification's no-`ChatService` shape — standalone `ChatSession` (t-4),
  guard + assembly in the ViewModel (t-10).** Why it matters: mvp's `ChatService` is real
  abstraction value but adds a layer with no criterion of its own, and the existing codebase wires
  ViewModels directly onto `ProviderRepository` (no service tier today), so introducing one here is
  unwarranted scope. Keeping the session as a standalone @Singleton (vs. risk's fuse-into-VM) is what
  makes the locked survives-nav / clear / lost-on-death semantics unit-testable without a
  recreated-activity Robolectric harness — risk's fused version would force exactly that harness.
  If history assembly in the VM grows unwieldy, extracting a `ChatService` later is a clean
  refactor; it is not load-bearing now.

**5. Provider-form param fields: bundled with chat UI vs. separate task.**
- mvp + verification: fold the provider-form param inputs into the chat-UI wave (t-6 / t-10) — both
  are UI-layer edits gated on the same upstream work.
- risk: does not surface a provider-form UI task at all (its t-2 stops at model+store+validator).
- Provisional default: **bundle into t-10 (mvp/verification).** Why it matters: the locked decision
  says edit the *existing* provider form; the fields are a few inputs on `ProviderEditScreen` /
  `ProviderEditViewModel`, pure UI gated on t-5's model change — a standalone task would touch ~2
  files for <10 min, under the merge threshold. risk's silence here is a coverage gap for c-1's
  "params actually reach the request" path; bundling closes it. Note: this leaves t-10 at 5 files
  (two UI surfaces) — acceptable, but the largest task in the plan; if it strains the file cap during
  execution, the provider-form fields are the clean split-out point.

synthesis: 10 tasks across 4 waves, 5 disagreements
