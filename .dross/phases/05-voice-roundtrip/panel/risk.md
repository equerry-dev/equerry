Phase 05-voice-roundtrip — 9 tasks across 4 waves

LENS: risk. The graph is shaped so every failure mode is owned and tested by exactly one
task. The framework-bound session stays thin (manual-only); all branching logic lives behind
interface seams in a `voice/` controller + ViewModel that unit tests drive with fakes that
*inject* the failure (no-match, timeout, denied, engine-missing, stream-error, mid-listen
dismiss, re-arm race). A risk has no owner unless a fake can reproduce it in a JVM test.

Wave 1
  t-1  Declare RECORD_AUDIO + permission seam
       files:    app/src/main/AndroidManifest.xml
                 app/src/main/java/dev/equerry/app/voice/MicPermission.kt
                 app/src/test/java/dev/equerry/app/voice/MicPermissionTest.kt
       covers:   c-1, c-5
       description: Add <uses-permission RECORD_AUDIO>. Define a MicPermission interface
                 (status(): Granted|Denied|PermanentlyDenied) + a single PermissionGate that
                 the controller queries before arming the mic. No Android calls in the
                 interface — the impl wraps ContextCompat.checkSelfPermission.
       contract: If RECORD_AUDIO is missing from the manifest, the (Robolectric/manifest)
                 assertion in MicPermissionTest that the declared permission set contains
                 RECORD_AUDIO fails. If the gate maps a denied result to anything other than
                 the single shared Denied state, MicPermissionTest.deniedMapsToGuidanceState
                 fails — proving every entry point funnels to one guidance state (mic_permission).

  t-2  STT capture behind a recognizer seam
       files:    app/src/main/java/dev/equerry/app/voice/SpeechToText.kt
                 app/src/main/java/dev/equerry/app/voice/SystemSpeechToText.kt
                 app/src/test/java/dev/equerry/app/voice/SystemSpeechToTextTest.kt
       covers:   c-2, c-5
       description: SpeechToText interface emitting Flow<SttEvent> (Partial, Final(text),
                 EndOfSpeech, Error(SttError)). SystemSpeechToText wraps SpeechRecognizer +
                 RecognitionListener, mapping onError codes to a sealed SttError
                 (NoMatch, Timeout, Busy, PermissionDenied, Unavailable). NOT routed through
                 EquerryRecognitionService (the stub). Recognizer is constructor-injected as a
                 factory lambda so the test substitutes a fake.
       contract: Feed a fake recognizer onError(ERROR_NO_MATCH): SystemSpeechToTextTest asserts
                 the flow emits SttError.NoMatch and completes (no crash, no hang). Separate
                 cases assert ERROR_SPEECH_TIMEOUT→Timeout, ERROR_INSUFFICIENT_PERMISSIONS→
                 PermissionDenied, ERROR_RECOGNIZER_BUSY→Busy. If onResults arrives the flow
                 emits Final with the first hypothesis; if the code mismaps any error the
                 corresponding case fails.

  t-3  TTS playback behind a speaker seam
       files:    app/src/main/java/dev/equerry/app/voice/TextToSpeech.kt
                 app/src/main/java/dev/equerry/app/voice/SystemTextToSpeech.kt
                 app/src/test/java/dev/equerry/app/voice/SystemTextToSpeechTest.kt
       covers:   c-4, c-5
       description: TextToSpeech interface (init(): TtsInitResult Ready|Failed|MissingEngine;
                 speak(utterance); speakSentences(stream); stop(); shutdown()). SystemTextToSpeech
                 wraps android.speech.tts.TextToSpeech, mapping onInit status != SUCCESS and a
                 null/zero engine to Failed/MissingEngine. Engine constructor-injected via factory
                 so a fake drives init outcomes.
       contract: A fake engine reporting onInit(ERROR): SystemTextToSpeechTest asserts init()
                 returns Failed and a subsequent speak() is a safe no-op (never throws). With
                 no engine available init() returns MissingEngine. If speak() is called before a
                 Ready init the test asserts it is dropped, not crashed.

  t-4  Voice settings store (turn + speak timing)
       files:    app/src/main/java/dev/equerry/app/data/VoiceSettingsStore.kt
                 app/src/main/java/dev/equerry/app/di/PersistenceModule.kt
                 app/src/test/java/dev/equerry/app/data/VoiceSettingsStoreTest.kt
       covers:   c-1, c-4
       description: New DataStore-backed store (same shape as SlotMappingStore) persisting
                 turnControl (CONTINUOUS default | SINGLE_TURN) and speakTiming (WHOLE_REPLY
                 default | SENTENCE_BY_SENTENCE). Add a @Provides @Singleton in PersistenceModule.
       contract: VoiceSettingsStoreTest asserts an unwritten store emits CONTINUOUS + WHOLE_REPLY
                 (the locked defaults) — if a default flips, the test fails. After setTurnControl
                 (SINGLE_TURN) the flow emits SINGLE_TURN and survives re-read. A wrong/garbled
                 persisted value falls back to the default rather than throwing.

Wave 2 (depends t-1, t-2, t-3, t-4)
  t-5  Voice-flow controller: listen→send→speak loop
       files:    app/src/main/java/dev/equerry/app/voice/VoiceFlowController.kt
                 app/src/test/java/dev/equerry/app/voice/VoiceFlowControllerTest.kt
       covers:   c-1, c-2, c-3, c-4
       description: Pure-Kotlin controller (no Android UI) wiring PermissionGate → SpeechToText →
                 the existing ChatViewModel.send/stream path → TextToSpeech, exposing a
                 VoiceFlowState (Idle, Listening, Transcribing, Sending, Speaking). On STT
                 EndOfSpeech it auto-stops and auto-sends (turn_control: no tap-to-stop). Speak
                 timing reads VoiceSettingsStore (whole reply at end vs sentence-by-sentence off
                 the token stream). Reuses ChatViewModel/ChatSession — does NOT reinvent send.
       contract: Drive fake STT Final("what time is it")→EndOfSpeech with a stubbed chat stream:
                 VoiceFlowControllerTest asserts the transcript shows the user turn, the streamed
                 reply renders, and exactly one whole-reply speak() fires after streaming completes
                 (WHOLE_REPLY). Under SENTENCE_BY_SENTENCE, asserts each completed sentence is
                 queued to TTS as deltas arrive. If auto-send doesn't fire on EndOfSpeech, the
                 "no send was issued" assertion fails (proves auto-stop/auto-send wiring).
       depends_on: t-1, t-2, t-3, t-4

  t-6  Failure routing: STT/TTS/no-provider → guidance
       files:    app/src/main/java/dev/equerry/app/voice/VoiceFlowController.kt
                 app/src/main/java/dev/equerry/app/voice/VoiceGuidance.kt
                 app/src/test/java/dev/equerry/app/voice/VoiceFlowFailureTest.kt
       covers:   c-5
       description: Map every partial-failure into a single VoiceGuidance surface (reusing the
                 ChatUiState.unmapped/error fields where they exist, adding voice-specific
                 guidance for mic-denied, STT-unavailable, TTS-missing). Owns: no CHAT provider
                 mapped, provider stream error mid-reply, STT NoMatch/Timeout/Busy/Unavailable,
                 TTS Failed/MissingEngine, mic Denied/PermanentlyDenied. Never throws to the
                 framework session.
       contract: VoiceFlowFailureTest, one case per mode: (a) no CHAT mapping → unmapped guidance,
                 STT never armed; (b) chat stream throws ChatException mid-reply → keyless error
                 shown, partial reply retained, NOT spoken; (c) STT NoMatch → "didn't catch that"
                 guidance, no crash; (d) TTS Failed → reply still rendered, spoken-aloud silently
                 skipped with a one-line notice; (e) mic Denied → shared guidance + settings path,
                 STT never armed. Each asserts state == expected guidance AND no exception escapes.
       depends_on: t-5

Wave 3 (depends t-5, t-6)
  t-7  Concurrency: dismiss + continuous re-arm races
       files:    app/src/main/java/dev/equerry/app/voice/VoiceFlowController.kt
                 app/src/test/java/dev/equerry/app/voice/VoiceFlowConcurrencyTest.kt
       covers:   c-1, c-5
       description: Make the loop cancellation-safe. On session dismissal cancel listening/
                 streaming/speaking and release STT+TTS. In continuous mode, re-arm the mic only
                 after the prior turn fully settles (reply done + speak done/aborted), guarding
                 against double-arm. Single-turn ends after one Q&A. Idempotent stop().
       contract: VoiceFlowConcurrencyTest: (a) dismiss() mid-Listening → STT released, no Final is
                 processed afterward, state → Idle (asserts the recognizer's release was called
                 once); (b) dismiss() mid-stream → stream collection cancelled, no speak() fires;
                 (c) CONTINUOUS: after a completed turn the mic re-arms exactly once — a test that
                 fires two rapid EndOfSpeech-equivalent triggers asserts startListening ran once,
                 not twice (re-arm race); (d) SINGLE_TURN: no re-arm after the first turn. A
                 leaked re-arm or post-dismiss Final fails the respective assertion.
       depends_on: t-5, t-6

  t-8  Voice settings screen (edit both toggles)
       files:    app/src/main/java/dev/equerry/app/ui/voicesettings/VoiceSettingsScreen.kt
                 app/src/main/java/dev/equerry/app/ui/voicesettings/VoiceSettingsViewModel.kt
                 app/src/main/java/dev/equerry/app/MainActivity.kt
                 app/src/test/java/dev/equerry/app/ui/voicesettings/VoiceSettingsViewModelTest.kt
       covers:   c-1, c-4
       description: HiltViewModel exposing the two settings as StateFlow + setters over
                 VoiceSettingsStore; a Compose screen with two choice controls and the mic-
                 permission guidance/grant entry point (settings path of mic_permission). Add a
                 VOICE route + Home entry in MainActivity.
       contract: VoiceSettingsViewModelTest asserts toggling turnControl to SINGLE_TURN persists
                 and re-emits, and that the VM seeds from the store's current values (not a hard-
                 coded default) — if it ignores the persisted value the seed assertion fails. Mic-
                 denied state surfaces the same guidance string the in-session path uses.
       depends_on: t-4

Wave 4 (depends t-5, t-6, t-7)
  t-9  Wire controller into the assist session (render ChatScreen)
       files:    app/src/main/java/dev/equerry/app/assistant/EquerryVoiceInteractionSession.kt
                 app/src/test/java/dev/equerry/app/voice/VoiceSessionWiringTest.kt
       covers:   c-1, c-3, c-5
       description: Switch the session content from ProbeSessionScreen to the ChatScreen
                 (session_ui_reuse), pull VoiceFlowController/ChatViewModel via a new @EntryPoint
                 (same pattern as ProbeStoreEntryPoint), start the controller on onShow/onCreate
                 so listening begins on invocation (c-1), and release on onDestroy (delegating to
                 t-7's stop). Keep ALL branching in the controller; the session only forwards
                 lifecycle. Probe dashboard stays reachable via the Probe log route only.
       contract: The framework session itself is verified MANUALLY (documented in the contract
                 comment: assist gesture → listening → spoken Q → reply renders → reply spoken).
                 The testable seam — VoiceSessionWiringTest — drives the controller's onShow/
                 onDestroy entry points against fakes and asserts onShow checks permission then
                 arms STT, and onDestroy calls the idempotent stop()/release exactly once (no
                 second invocation, no leak). If the session is wired to start listening without
                 the permission gate first, the "permission checked before arm" assertion fails.
       depends_on: t-5, t-6, t-7

## Coverage
- c-1 (assist → listening + STT capture starts): t-1 (permission precondition), t-4 (turn mode
  drives whether/how it listens), t-5 (controller enters Listening + arms STT), t-7 (re-arm safety),
  t-8 (mode setting), t-9 (session starts the loop on invocation).
- c-2 (utterance transcribed, shown as user question): t-2 (STT seam + Final text), t-5 (transcript
  shows the user turn via ChatViewModel).
- c-3 (sent to CHAT provider, streams in): t-5 (reuses ChatViewModel send/stream), t-9 (session
  renders the streaming ChatScreen).
- c-4 (completed reply spoken via TTS): t-3 (TTS seam), t-4 (speak-timing setting), t-5 (whole-reply
  vs sentence-by-sentence speak), t-8 (edit speak timing).
- c-5 (no provider / STT or TTS unavailable → clear guidance, never silent-fail/crash): t-1 (mic
  denied), t-2 (STT errors), t-3 (TTS missing/init fail), t-6 (single guidance routing for ALL
  modes), t-7 (mid-flow dismiss/race never crashes), t-9 (session never throws).

## Judgment calls
- Split STT (t-2), TTS (t-3), and permission (t-1) into three separate seam tasks rather than one
  "voice infra" task: each wraps a different framework class with a distinct failure surface, and the
  risk lens demands one owner per failure mode — a merged task would blur which test guards which error.
- Put failure-routing in its own task (t-6) layered on t-5 rather than folding error handling into the
  happy-path controller: the merge risk is that the happy path gets tested green while half the error
  branches stay uncovered. A dedicated task with one assertion per failure mode is the whole point of
  this lens. Both edit VoiceFlowController, so t-6 strictly depends on t-5 (same file, sequential).
- Concurrency (t-7) is its own wave-3 task, not part of t-5/t-6: dismiss-mid-listen and continuous
  re-arm races are timing failures that need their own cancellation-focused tests; bundling them with
  functional routing would let a race hide behind passing functional tests. Rejected alternative:
  treat re-arm as "just call startListening again" inside t-5 — that is exactly the unguarded race.
- Chose to REUSE ChatViewModel/ChatSession from inside VoiceFlowController (t-5) rather than build a
  parallel voice send path: spec/orientation mandate reusing the send/stream/unmapped/error logic.
  Rejected a voice-specific sender (would duplicate the unmapped + ChatException handling and the
  key-redaction guarantees).
- Kept the framework-bound session (t-9) as the LAST wave and explicitly manual-verified, pushing
  every branch into the controller. Rejected starting from the session (forward-from-tech) because
  framework code can't be unit-tested; the wiring test only asserts the controller entry points are
  called in the safe order (permission-before-arm, stop-once-on-destroy).
- Mic permission funnels to ONE shared guidance state owned by t-1 and consumed identically by t-6
  (in-session) and t-8 (settings), satisfying the locked mic_permission "same guidance everywhere"
  rather than two divergent denial UIs.

risk: 9 tasks across 4 waves, criteria covered 5/5
