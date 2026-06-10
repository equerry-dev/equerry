# Phase 05-voice-roundtrip — synthesis

Merged from three independent drafts (risk / mvp / verification). I authored none; this judges
and grafts. Source validation against `app/src/main/java/dev/equerry/app`: the `voice/` package
already exists; `ChatRoute`/`ChatScreen` exist; the `equerry_settings` DataStore + `SlotMappingStore`
idiom is confirmed; the `@EntryPoint` + `EntryPointAccessors` pattern is present in the session;
`ChatViewModel.send()` reads `_state.value.input` (so a programmatic `send(text)` overload is a
genuine, currently-missing seam). Verification's `assistant/voice/` path does NOT match the existing
`voice/` package — corrected to `voice/` in the merge.

## Scores

Scale: each dimension scored /5.

| Draft | Criteria coverage | Test-contract specificity | Granularity | Wave correctness |
|-------|-------------------|---------------------------|-------------|------------------|
| risk | 5 — 5/5, every failure mode owned by exactly one task (t-2/t-3/t-6/t-7) | 5 — per-error-code contracts (NoMatch/Timeout/Busy/PermissionDenied), one assert per branch | 4 — finest split; STT/TTS/perm as 3 seams + a dedicated failure task + a concurrency task | 4 — 4 waves; correct deps but the wave-3 concurrency split adds a serial hop t-5→t-6→t-7 |
| mvp | 4 — 5/5 but c-5 leans entirely on t-4's catch-all clause; no dedicated failure task | 3 — solid happy-path + one combined guidance clause; less per-error granularity; STT/TTS impls untested | 5 — leanest 6 tasks; clean reuse-first shape (send(text) overload is the sharp insight) | 5 — 2 waves, maximal parallelism, every dep real and minimal |
| verification | 5 — 5/5; backward-from-contract; isolates SpeakChunker + VoiceTranscript as their own contracts | 5 — sharpest per-mode chunker + transcript contracts; names the manual-vs-tested seam per task | 3 — 9 tasks but over-split (chunker + transcript + ports as separate units beyond the seam need) | 4 — 3 waves; good, but wrong `assistant/voice/` path and a separate adapter task (t-7) that duplicates t-2 |

**Skeleton: risk (9 tasks / 4 waves).** It has the strongest failure ownership — the locked
`mic_permission` "same guidance everywhere" and c-5 "never silent-fail/crash" are the phase's real
hazards, and risk is the only draft that gives each failure mode a single owner and a dedicated
failure-routing task (t-6) plus a concurrency/re-arm task (t-7) that the others fold into the happy
path. I graft mvp's `send(text)` overload and lean settings shape, and verification's per-mode
SpeakChunker contract and explicit manual-seam labelling, onto that skeleton.

## Merged plan

```
Phase 05-voice-roundtrip — 9 tasks across 4 waves

Wave 1
  t-1  Declare RECORD_AUDIO + shared mic-permission state                                   [risk+verification]
       files:    app/src/main/AndroidManifest.xml
                 app/src/main/java/dev/equerry/app/voice/MicPermission.kt
                 app/src/test/java/dev/equerry/app/voice/MicPermissionTest.kt
       covers:   c-1, c-5
       description: Add <uses-permission RECORD_AUDIO>. Define a MicPermission seam whose
                 evaluate(granted): Ready | Denied yields ONE Denied state (guidance text +
                 open-settings action) that is byte-identical regardless of entry point
                 (in-session vs settings) — the locked mic_permission "same guidance
                 everywhere". The impl wraps ContextCompat.checkSelfPermission; no Android
                 calls in the interface.
       test_contract: MicPermissionTest: (a) manifest resource-parse asserts RECORD_AUDIO is
                 declared (drop it → c-1 listening never starts); (b) evaluate(false) from the
                 in-session input and from the settings input produce an *equal* Denied
                 guidance — the cross-entry-point equality assertion fails if two divergent
                 denial messages ever exist; (c) evaluate(true) → Ready.
       depends_on:

  t-2  STT capture behind a recognizer seam                                                 [risk]
       files:    app/src/main/java/dev/equerry/app/voice/SpeechToText.kt
                 app/src/main/java/dev/equerry/app/voice/SystemSpeechToText.kt
                 app/src/test/java/dev/equerry/app/voice/SystemSpeechToTextTest.kt
       covers:   c-2, c-5
       description: SpeechToText interface emitting Flow<SttEvent> (Partial, Final(text),
                 EndOfSpeech, Error(SttError)). SystemSpeechToText wraps SpeechRecognizer +
                 RecognitionListener, mapping onError codes to a sealed SttError (NoMatch,
                 Timeout, Busy, PermissionDenied, Unavailable). NOT routed through
                 EquerryRecognitionService. Recognizer is constructor-injected as a factory
                 lambda so the test substitutes a fake. (The verification lens would leave this
                 impl manual-only; risk's per-error-code JVM contract is kept because each
                 onError mapping is a distinct c-5 branch worth guarding — see Disagreement D-5.)
       test_contract: Fake recognizer onError(ERROR_NO_MATCH) → flow emits SttError.NoMatch and
                 completes (no crash/hang). Separate cases: ERROR_SPEECH_TIMEOUT→Timeout,
                 ERROR_INSUFFICIENT_PERMISSIONS→PermissionDenied, ERROR_RECOGNIZER_BUSY→Busy.
                 onResults → Final with the first hypothesis. Any mismap fails its case.
       depends_on:

  t-3  TTS playback behind a speaker seam                                                   [risk]
       files:    app/src/main/java/dev/equerry/app/voice/TextToSpeech.kt
                 app/src/main/java/dev/equerry/app/voice/SystemTextToSpeech.kt
                 app/src/test/java/dev/equerry/app/voice/SystemTextToSpeechTest.kt
       covers:   c-4, c-5
       description: TextToSpeech interface (init(): TtsInitResult Ready|Failed|MissingEngine;
                 speak(utterance); speakSentences(stream); stop(); shutdown()). SystemTextToSpeech
                 wraps android.speech.tts.TextToSpeech, mapping onInit status != SUCCESS and a
                 null/zero engine to Failed/MissingEngine. Engine constructor-injected via factory
                 so a fake drives init outcomes.
       test_contract: Fake engine onInit(ERROR) → init() returns Failed and a subsequent speak()
                 is a safe no-op (never throws). No engine → init() returns MissingEngine. speak()
                 before a Ready init is dropped, not crashed.
       depends_on:

  t-4  Voice settings store (turn_control + speak_timing)                                   [risk+mvp+verification]
       files:    app/src/main/java/dev/equerry/app/data/VoiceSettingsStore.kt
                 app/src/main/java/dev/equerry/app/di/PersistenceModule.kt
                 app/src/test/java/dev/equerry/app/data/VoiceSettingsStoreTest.kt
       covers:   c-1, c-4
       description: One DataStore-backed store on the existing "equerry_settings" DataStore
                 (same shape as SlotMappingStore) persisting turnControl (CONTINUOUS default |
                 SINGLE_TURN) and speakTiming (WHOLE_REPLY default | SENTENCE_BY_SENTENCE), each
                 with a setter. Add a @Provides @Singleton in PersistenceModule. ONE store for
                 both settings (see Disagreement D-4).
       test_contract: VoiceSettingsStoreTest mirrors SlotMappingStoreTest's TemporaryFolder +
                 PreferenceDataStoreFactory idiom: (a) fresh store emits CONTINUOUS + WHOLE_REPLY
                 (locked defaults) — flipping a default fails; (b) after setTurnControl(SINGLE_TURN)
                 a reload over the same file emits SINGLE_TURN (survives reload); (c) a garbled
                 persisted value falls back to the default, not a throw.
       depends_on:

  t-5  Programmatic send(text) + completed-reply signal on ChatViewModel                    [mvp+verification]
       files:    app/src/main/java/dev/equerry/app/ui/chat/ChatViewModel.kt
                 app/src/test/java/dev/equerry/app/ui/chat/ChatViewModelTest.kt
       covers:   c-3, c-5
       description: Extract the existing send() body into send(text: String) (the input-driven
                 overload delegates to it); expose the completed assistant text (e.g. lastReply +
                 a done signal) so the controller can read the finished reply for whole-reply TTS.
                 NO new round-trip logic — the phase-04 stream/unmapped/error/key-redaction path
                 is reused verbatim. (Extending ChatViewModel rather than a voice-specific VM —
                 see Disagreement D-2. This is mvp's sharpest contribution; the skeleton lacked it
                 and would have had the controller poke ChatViewModel.input + send(), which does
                 not exist as a programmatic path today.)
       test_contract: ChatViewModelTest: send("hi") with a mapped provider yields the same
                 2-bubble transcript as the typed path AND exposes the finished assistant text
                 "Hello"; send("hi") with no CHAT mapping sets unmapped and makes zero driver
                 requests (c-5, reusing phase-04 guarantees).
       depends_on:

Wave 2 (depends t-1..t-5)
  t-6  SpeakChunker — stream → TTS utterances per speak_timing                              [verification]
       files:    app/src/main/java/dev/equerry/app/voice/SpeakChunker.kt
                 app/src/test/java/dev/equerry/app/voice/SpeakChunkerTest.kt
       covers:   c-4
       description: Pure function turning the reply token stream into TTS utterances per the
                 speak_timing setting: SENTENCE_BY_SENTENCE emits one utterance per completed
                 sentence as boundaries stream in (flush trailing fragment on done);
                 WHOLE_REPLY holds and emits exactly one utterance at done. Isolated because
                 sentence-boundary detection is the most edge-case-prone logic in the phase and
                 deserves its own per-mode contract independent of the STT/TTS fakes.
       test_contract: SpeakChunker(SENTENCE) fed deltas "Hi. " / "How are" / " you? Bye." emits
                 ["Hi.", "How are you?", "Bye."]; SpeakChunker(WHOLE_REPLY) emits exactly one
                 utterance equal to the full concatenation only at done. A boundary or hold-until-
                 done regression fails the per-mode expected-list assertion.
       depends_on: t-4

  t-7  Voice-flow controller: listen→send→speak loop                                        [risk+mvp+verification]
       files:    app/src/main/java/dev/equerry/app/voice/VoiceFlowController.kt
                 app/src/test/java/dev/equerry/app/voice/VoiceFlowControllerTest.kt
       covers:   c-1, c-2, c-3, c-4
       description: Pure-Kotlin controller (no Android UI) wiring MicPermission(t-1) →
                 SpeechToText(t-2) → ChatViewModel.send(text)(t-5) → SpeakChunker(t-6) →
                 TextToSpeech(t-3), exposing VoiceFlowState (Idle, Listening, Transcribing,
                 Sending, Speaking). On STT EndOfSpeech it auto-stops and auto-sends (turn_control:
                 no tap-to-stop). Reads VoiceSettingsStore(t-4) for turn + speak timing. Reuses
                 ChatViewModel/ChatSession — does NOT reinvent send.
       test_contract: Drive fake STT Final("what time is it")→EndOfSpeech with a stubbed/
                 MockWebServer-backed chat stream: the transcript shows the user turn, the streamed
                 reply renders, and under WHOLE_REPLY exactly one speak() of the full reply fires
                 after streaming completes; under SENTENCE_BY_SENTENCE each completed sentence is
                 queued to TTS as deltas arrive (matching t-6 output). If auto-send doesn't fire on
                 EndOfSpeech the "no send issued" assertion fails.
       depends_on: t-1, t-2, t-3, t-4, t-5, t-6

  t-9  Voice settings screen (edit both toggles + settings-path mic grant)                  [risk+mvp+verification]
       files:    app/src/main/java/dev/equerry/app/ui/voicesettings/VoiceSettingsScreen.kt
                 app/src/main/java/dev/equerry/app/ui/voicesettings/VoiceSettingsViewModel.kt
                 app/src/main/java/dev/equerry/app/MainActivity.kt
                 app/src/test/java/dev/equerry/app/ui/voicesettings/VoiceSettingsViewModelTest.kt
       covers:   c-1, c-4, c-5
       description: HiltViewModel exposing both settings as StateFlow + setters over
                 VoiceSettingsStore(t-4); a Compose screen with two choice controls plus the
                 mic-permission grant/guidance entry point (settings path of mic_permission,
                 reusing MicPermission(t-1) so guidance equals the in-session string). Add a
                 VOICE route + Home entry in MainActivity.
       test_contract: VoiceSettingsViewModelTest: toggling turnControl to SINGLE_TURN persists and
                 re-emits; the VM seeds from the store's current values (not a hardcoded default) —
                 ignoring the persisted value fails the seed assertion; toggling speakTiming calls
                 setSpeakTiming(SENTENCE). Mic-denied surfaces the SAME guidance string the
                 in-session path uses (reuses t-1). Screen render is the only manual bit.
       depends_on: t-1, t-4

Wave 3 (depends t-7)
  t-8  Concurrency: dismiss + continuous re-arm races                                       [risk]
       files:    app/src/main/java/dev/equerry/app/voice/VoiceFlowController.kt
                 app/src/test/java/dev/equerry/app/voice/VoiceFlowConcurrencyTest.kt
       covers:   c-1, c-5
       description: Make the loop cancellation-safe. On session dismissal cancel listening/
                 streaming/speaking and release STT+TTS. In CONTINUOUS, re-arm the mic only after
                 the prior turn fully settles (reply done + speak done/aborted), guarding against
                 double-arm. SINGLE_TURN ends after one Q&A. Idempotent stop(). (Own task, not
                 folded into t-7: dismiss-mid-listen and re-arm are timing failures that need
                 cancellation-focused tests, lest a race hide behind passing functional tests.)
       test_contract: VoiceFlowConcurrencyTest: (a) dismiss() mid-Listening → STT released once,
                 no Final processed after, state → Idle; (b) dismiss() mid-stream → collection
                 cancelled, no speak() fires; (c) CONTINUOUS: two rapid EndOfSpeech triggers →
                 startListening ran once, not twice; (d) SINGLE_TURN: no re-arm after the first
                 turn. A leaked re-arm or post-dismiss Final fails its assertion.
       depends_on: t-7

  t-11 Failure routing: STT/TTS/no-provider → single guidance surface                       [risk]
       files:    app/src/main/java/dev/equerry/app/voice/VoiceFlowController.kt
                 app/src/main/java/dev/equerry/app/voice/VoiceGuidance.kt
                 app/src/test/java/dev/equerry/app/voice/VoiceFlowFailureTest.kt
       covers:   c-5
       description: Map every partial-failure into ONE VoiceGuidance surface (reusing
                 ChatUiState.unmapped/error where present, adding voice-specific guidance for
                 mic-denied, STT-unavailable, TTS-missing). Owns: no CHAT provider mapped,
                 provider stream error mid-reply, STT NoMatch/Timeout/Busy/Unavailable, TTS
                 Failed/MissingEngine, mic Denied/PermanentlyDenied. Never throws to the framework
                 session. (Dedicated task layered on t-7 — same file, sequential — so the c-5
                 branches can't stay green-by-omission behind a passing happy path.)
       test_contract: VoiceFlowFailureTest, one case per mode: (a) no CHAT mapping → unmapped
                 guidance, STT never armed; (b) chat stream throws ChatException mid-reply →
                 key-free error shown, partial reply retained, NOT spoken; (c) STT NoMatch →
                 "didn't catch that" guidance, no crash; (d) TTS Failed → reply still rendered,
                 spoken-aloud silently skipped with a one-line notice; (e) mic Denied → shared
                 guidance + settings path, STT never armed. Each asserts state == expected guidance
                 AND no exception escapes.
       depends_on: t-7

Wave 4 (depends t-7, t-8, t-11)
  t-10 Wire controller into the assist session (render ChatScreen)                          [risk+mvp+verification]
       files:    app/src/main/java/dev/equerry/app/assistant/EquerryVoiceInteractionSession.kt
                 app/src/test/java/dev/equerry/app/voice/VoiceSessionWiringTest.kt
       covers:   c-1, c-3, c-5
       description: Switch the session content from ProbeSessionScreen to ChatRoute/ChatScreen
                 (session_ui_reuse, locked), pull VoiceFlowController/ChatViewModel via a new
                 @EntryPoint (same pattern as ProbeStoreEntryPoint), request RECORD_AUDIO +
                 start the controller on onShow/onCreate so listening begins on invocation (c-1),
                 release on onDestroy (delegating to t-8's idempotent stop). Keep AssistStructure
                 probe capture feeding ProbeStore but off the rendered surface; Probe dashboard
                 stays reachable via the Probe log route only. All branching stays in the
                 controller; the session only forwards lifecycle. (Android STT/TTS adapters are
                 the factory-injected impls from t-2/t-3 — NOT a separate adapter task; see
                 Disagreement D-5.)
       test_contract: The framework session is verified MANUALLY (documented in the contract
                 comment: assist gesture → listening → spoken Q → reply renders → reply spoken).
                 The testable seam VoiceSessionWiringTest drives the controller's onShow/onDestroy
                 entry points against fakes: onShow checks permission THEN arms STT; onDestroy calls
                 the idempotent stop()/release exactly once. Wiring that arms STT before the
                 permission gate fails the "permission checked before arm" assertion.
       depends_on: t-7, t-8, t-11
```

Task-id note: ids preserve risk's skeleton numbering where grafts slot in; t-5 (send overload),
t-6 (chunker), t-9 (settings) are grafted into earlier waves, and risk's original t-6/t-7/t-9 are
renumbered t-11/t-8/t-10 to keep dependency order monotonic. No invented tasks — every task traces
to at least one source draft (tagged).

## Disagreements

**D-1 — Total task count: 6 (mvp) vs 9 (risk/verification).**
mvp argues the leanest graph (no dedicated failure task, no concurrency task, STT/TTS impls
untested) covers 5/5. risk and verification both spend extra tasks on isolating failure modes.
*Default taken:* 9 tasks (risk skeleton). *Why it matters:* the phase's whole hazard is c-5
("never silent-fail/crash") + the locked mic_permission "same guidance everywhere"; mvp's single
combined guidance clause in the controller test is exactly where a green happy path can hide an
uncovered error branch. The cost is two extra tasks; the payoff is one-owner-per-failure. If the
phase needs to ship faster, t-8 (concurrency) is the most defensible to fold back into t-7 — flag
for the planner.

**D-2 — Extend ChatViewModel vs add a voice-specific ViewModel.**
All three reuse phase-04 send/stream/unmapped/error logic, but differ on *how*: mvp/verification
extend ChatViewModel with a programmatic send(text) overload (and verification adds mic guidance to
ChatUiState); risk's controller calls "the existing ChatViewModel.send/stream path" without naming
that the current send() only reads `_state.value.input` and has no text-argument entry point.
*Default taken:* extend ChatViewModel with send(text: String) (t-5, mvp's insight). *Why it matters:*
validated against source — `ChatViewModel.send()` (line 50) trims `_state.value.input`; there is no
programmatic path today, so without t-5 the controller would have to stuff input + call send(),
racing the UI input field. Extending one VM (vs a parallel VoiceChatViewModel) also keeps the locked
session_ui_reuse honest: one state object drives typed and spoken chat.

**D-3 — Where mic-permission guidance lives.**
risk: a standalone MicPermission seam (t-1) consumed identically by the failure-routing task and the
settings screen. verification: guidance lives in ChatViewModel/ChatUiState (its t-6) since the
session renders ChatScreen anyway. mvp: no permission task at all — the shared denied state is just
a field in the controller, each entry point only makes a request call.
*Default taken:* standalone MicPermission seam (t-1, risk) as the single source of the Denied
guidance string, consumed by the controller's failure routing (t-11) and the settings screen (t-9).
*Why it matters:* the locked mic_permission demands byte-identical guidance across in-session and
settings entry points; a standalone seam lets one cross-entry-point equality test (t-1 contract)
*prove* that invariant, which neither a controller-field (mvp) nor a ChatUiState-field
(verification) asserts directly. Verification's instinct (banner content asserted in a VM test) is
still honoured — the session renders that guidance via ChatScreen.

**D-4 — One settings store vs per-setting.**
All three chose ONE VoiceSettingsStore for both turn_control and speak_timing; no real divergence.
*Default taken:* one store (t-4). Recorded for completeness; both are simple enum prefs on the same
existing "equerry_settings" DataStore (confirmed in source), below any split threshold.

**D-5 — How finely STT/TTS seams + impls are split.**
risk: one seam-task each for STT (t-2) and TTS (t-3), each with a *JVM* contract on the system impl's
error mapping. mvp: a single combined "STT/TTS seams + impls + RECORD_AUDIO" task, impls
manual-only. verification: ports as interfaces (in t-5) PLUS a separate wave-3 adapter task (its
t-7) that is manual-only, PLUS a separate VoiceTranscript mapping unit.
*Default taken:* two seam tasks (t-2, t-3) with risk's per-error-code JVM contracts; NO separate
adapter task (the factory-injected impls live in t-2/t-3 and are wired in t-10). *Why it matters:*
the onError→SttError mapping (NoMatch/Timeout/Busy/PermissionDenied/Unavailable) and onInit→
TtsInitResult mapping ARE distinct c-5 branches — leaving them manual-only (mvp) or in a separate
untested adapter (verification t-7) drops the cheapest place to catch a mis-mapped error code.
Verification's separate VoiceTranscript unit was NOT grafted: ChatViewModel.send(text) (t-5) already
owns "shown as the user's question," so a standalone transcript-mapping unit is redundant. Risk's
finer split wins here precisely because each wrapper has a different failure surface.

**D-6 — Wave count / parallelism: 2 (mvp) vs 3 (verification) vs 4 (risk).**
mvp maximises parallelism (2 waves); risk serialises failure-routing and concurrency into their own
waves (4). *Default taken:* 4 waves (risk), with t-5/t-6/t-9 grafted to run as early as their deps
allow (t-5 in wave 1, t-6/t-9 in wave 2) to recover some of mvp's parallelism. *Why it matters:*
t-8 (concurrency) and t-11 (failure routing) both edit VoiceFlowController.kt after t-7 creates it —
same-file sequential edits, so they cannot safely parallelise with t-7 or each other's controller
writes; the wave boundary reflects a real file-contention dependency, not ceremony. (t-8 and t-11
both depend only on t-7 and could run in parallel *if* the executor serialises their controller
edits; defaulting them to the same wave 3 is fine since both gate wave-4 wiring.)

## Coverage

| Criterion | Task ids |
|-----------|----------|
| c-1 (assist → listening + STT start) | t-1 (RECORD_AUDIO + perm precondition), t-4 (turn mode read on enter), t-7 (controller enters Listening + arms STT), t-8 (re-arm safety), t-9 (mode setting), t-10 (session starts loop on invocation) |
| c-2 (utterance transcribed, shown as user question) | t-2 (STT seam + Final text), t-5 (send(text) → user bubble), t-7 (transcript shows the user turn) |
| c-3 (sent to CHAT provider, streams in) | t-5 (reuses phase-04 send/stream), t-7 (auto-send on EndOfSpeech), t-10 (session renders streaming ChatScreen) |
| c-4 (completed reply spoken via TTS) | t-3 (TTS seam), t-4 (speak_timing setting), t-6 (SpeakChunker per-mode), t-7 (speak on done), t-9 (edit speak timing) |
| c-5 (no provider / STT or TTS unavailable → clear guidance, never silent-fail/crash) | t-1 (mic denied state), t-2 (STT error mapping), t-3 (TTS missing/init-fail), t-5 (unmapped reuse), t-8 (dismiss/race never crashes), t-9 (settings mic path), t-10 (session never throws), t-11 (single guidance routing for ALL modes) |

All of c-1..c-5 covered.

synthesis: 9 tasks across 4 waves, 6 disagreements
