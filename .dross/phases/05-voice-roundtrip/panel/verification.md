# Phase 05-voice-roundtrip — verification-lens decomposition

Designed backward from test contracts. Every decision surface (turn-taking, speak-timing
chunking, transcript mapping, error/guidance routing, settings persistence) is pulled into a
pure or seam-driven unit. The framework-bound surfaces (`VoiceInteractionSession`,
`SpeechRecognizer`, `TextToSpeech`) are reduced to the thinnest adapters that implement an
interface the controller drives — so the controller is exercised entirely against fakes with
no Robolectric and no device. Only the adapter wiring is manual, and each manual surface names
the separately-contracted unit that holds its logic.

```
Phase 05-voice-roundtrip — 9 tasks across 3 waves

Wave 1
  t-1  Add RECORD_AUDIO + mic-permission state model
       files:    app/src/main/AndroidManifest.xml
                 app/src/main/java/dev/equerry/app/assistant/voice/MicPermission.kt
                 app/src/test/java/dev/equerry/app/assistant/voice/MicPermissionTest.kt
       covers:   c-1, c-5
       contract: MicPermission.evaluate(granted=false) yields a single Denied state whose
                 guidance text + settings-action are identical regardless of entry point
                 (in-session vs settings); evaluate(granted=true) yields Ready. If a code path
                 ever produced two different denial messages, the equality assertion across the
                 two entry-point inputs fails. Manifest test: a resource-parse asserts
                 RECORD_AUDIO is declared (drop it → c-1 listening can never start).

  t-2  Persist turn_control + speak_timing settings
       files:    app/src/main/java/dev/equerry/app/data/VoiceSettingsStore.kt
                 app/src/main/java/dev/equerry/app/di/PersistenceModule.kt
                 app/src/test/java/dev/equerry/app/data/VoiceSettingsStoreTest.kt
       covers:   c-1, c-4
       contract: VoiceSettingsStore default emission is TurnControl.CONTINUOUS and
                 SpeakTiming.WHOLE_REPLY on a fresh DataStore (defaults locked); after
                 setTurnControl(SINGLE_TURN) a reload via a second store over the same file
                 emits SINGLE_TURN. If a default is flipped or persistence drops, the
                 fresh-store default assertion or the survives-reload assertion fails.

  t-3  Map STT/TTS results into chat-transcript + error state
       files:    app/src/main/java/dev/equerry/app/assistant/voice/VoiceTranscript.kt
                 app/src/test/java/dev/equerry/app/assistant/voice/VoiceTranscriptTest.kt
       covers:   c-2, c-5
       contract: VoiceTranscript.fromRecognition(listOf("hello there")) produces the user
                 question text "hello there" exactly; fromRecognition(emptyList()) /
                 a recognizer error code maps to a key-free guidance string, never throws.
                 If the "best hypothesis" selection or the empty/error branch regresses, the
                 text-equality or the no-throw-returns-guidance assertion fails.

  t-4  Chunk a streaming reply into TTS utterances
       files:    app/src/main/java/dev/equerry/app/assistant/voice/SpeakChunker.kt
                 app/src/test/java/dev/equerry/app/assistant/voice/SpeakChunkerTest.kt
       covers:   c-4
       contract: SpeakChunker(SENTENCE) fed deltas "Hi. " / "How are" / " you? Bye." emits
                 utterances ["Hi.", "How are you?", "Bye."] — one per completed sentence as the
                 boundary streams in, with the trailing fragment flushed on done.
                 SpeakChunker(WHOLE_REPLY) emits exactly one utterance equal to the full
                 concatenation only at done. If sentence boundary detection or the whole-reply
                 hold-until-done regresses, the per-mode expected-list assertion fails.

Wave 2
  t-5  Build the voice-flow turn-taking controller
       files:    app/src/main/java/dev/equerry/app/assistant/voice/VoiceFlowController.kt
                 app/src/main/java/dev/equerry/app/assistant/voice/SpeechRecognizerPort.kt
                 app/src/main/java/dev/equerry/app/assistant/voice/TextToSpeechPort.kt
                 app/src/test/java/dev/equerry/app/assistant/voice/VoiceFlowControllerTest.kt
       covers:   c-1, c-2, c-3, c-4, c-5
       contract: With fake SpeechRecognizerPort + fake TextToSpeechPort + the phase-04
                 ChatViewModel (real, MockWebServer-backed) the controller, driven through
                 listen→onEndOfSpeech→onResults: (a) starts the recognizer once on enter
                 (c-1); (b) auto-sends the recognized text with no manual send call so the
                 transcript shows the user question then the streamed reply (c-2,c-3);
                 (c) on stream-done speaks via the TTS port exactly the SpeakChunker output
                 (c-4); (d) in CONTINUOUS re-arms the recognizer exactly once after speak
                 completes, in SINGLE_TURN it does not re-arm; (e) on recognizer error or
                 ChatException it routes to the same guidance/error state and never re-throws
                 (c-5). Each clause is its own @Test; breaking re-arm fires the
                 "continuous re-arms once / single-turn never re-arms" pair, breaking auto-send
                 fires the transcript-content test.
       depends_on: t-2, t-3, t-4

  t-6  Surface mic-denied guidance + permission request in ChatUiState
       files:    app/src/main/java/dev/equerry/app/ui/chat/ChatViewModel.kt
                 app/src/main/java/dev/equerry/app/ui/chat/ChatScreen.kt
                 app/src/test/java/dev/equerry/app/ui/chat/ChatViewModelVoiceTest.kt
       covers:   c-5
       contract: ChatViewModel.onMicDenied() sets a micGuidance field carrying the
                 MicPermission.Denied guidance text + an "open settings" action flag without
                 clearing the existing transcript; onMicGranted() clears it. If denial state
                 leaks the typed-chat error channel or wipes transcript, the
                 transcript-preserved / distinct-field assertion fails. (ChatScreen render of
                 the banner is the only manual bit — its content is asserted in this VM test.)
       depends_on: t-1

Wave 3
  t-7  Wire SpeechRecognizer + TextToSpeech adapters to the ports
       files:    app/src/main/java/dev/equerry/app/assistant/voice/AndroidSpeechRecognizerPort.kt
                 app/src/main/java/dev/equerry/app/assistant/voice/AndroidTextToSpeechPort.kt
       covers:   c-1, c-2, c-4
       contract: MANUAL only — these are thin android.speech.SpeechRecognizer /
                 android.speech.tts.TextToSpeech adapters with no decision logic (init, start,
                 forward RecognitionListener callbacks to the port, speak each utterance,
                 release on close). All behaviour they feed is contracted in t-3/t-4/t-5
                 against fakes of the same ports. Manual smoke: speak a phrase, confirm
                 transcription appears and reply is spoken. No branching lives here to break.
       depends_on: t-5

  t-8  Render ChatScreen from the assist session, driven by the controller
       files:    app/src/main/java/dev/equerry/app/assistant/EquerryVoiceInteractionSession.kt
       covers:   c-1, c-3
       contract: MANUAL only — swaps ProbeSessionScreen for ChatRoute/ChatScreen
                 (session_ui_reuse, locked), constructs VoiceFlowController with the Android
                 ports + ChatViewModel via the existing @EntryPoint pattern, starts listening
                 in onShow/onCreate. No new logic: turn-taking, mapping, speak-timing all live
                 in t-5's unit-tested controller. Manual smoke: assist gesture → ChatScreen
                 shown, listening starts. Probe dashboard moves off the assist path (still
                 reachable via the Probe log route).
       depends_on: t-5, t-6

  t-9  Minimal voice settings UI (turn-taking + speak-timing + mic)
       files:    app/src/main/java/dev/equerry/app/ui/settings/VoiceSettingsViewModel.kt
                 app/src/main/java/dev/equerry/app/ui/settings/VoiceSettingsScreen.kt
                 app/src/test/java/dev/equerry/app/ui/settings/VoiceSettingsViewModelTest.kt
       covers:   c-1, c-4, c-5
       contract: VoiceSettingsViewModel exposes the persisted TurnControl + SpeakTiming as
                 state and setTurnControl/setSpeakTiming write through VoiceSettingsStore; a
                 set-then-observe test asserts the new value round-trips. Mic toggle invokes
                 the same MicPermission path (request → granted/denied state) as the session,
                 asserted by reusing MicPermission so the guidance equals t-1's. The
                 VoiceSettingsScreen composable is the only manual bit.
       depends_on: t-2, t-1
```

## Coverage
- c-1 (assist launches → listening + STT start): t-1 (RECORD_AUDIO + state), t-2 (settings the
  controller reads on enter), t-5 (controller starts recognizer once), t-8 (session wires it),
  t-9 (settings entry)
- c-2 (utterance transcribed → shown as user question): t-3 (recognition→transcript mapping),
  t-5 (auto-fill + show), t-7 (recognizer adapter)
- c-3 (sent to CHAT provider → reply streams in): t-5 (auto-send via ChatViewModel),
  t-8 (session renders the streaming ChatScreen)
- c-4 (completed reply spoken via TTS): t-2 (speak_timing setting), t-4 (chunker),
  t-5 (speak on done), t-7 (TTS adapter), t-9 (setting)
- c-5 (no CHAT mapping / STT/TTS unavailable → clear guidance, never crash): t-1 (denial state),
  t-3 (recognizer-error → guidance), t-5 (error routing, never re-throw), t-6 (VM mic guidance),
  t-9 (settings mic path)

## Judgment calls
- Ports (SpeechRecognizerPort/TextToSpeechPort) as interfaces vs calling SpeechRecognizer
  directly in the controller: chose ports — rejected direct calls — because direct calls force
  the entire turn-taking state machine (t-5) onto Robolectric/device, making c-1..c-5 manual; the
  port seam makes all five contractable against fakes.
- SpeakChunker as a standalone unit (t-4) vs folding chunking into the controller: chose
  standalone — rejected folding — sentence-boundary detection is the most edge-case-prone logic
  and deserves an isolated per-mode contract independent of the recognizer/TTS fakes.
- Reuse phase-04 ChatViewModel for send/stream (t-5) vs a new voice send path: chose reuse —
  rejected new path — c-3's streaming + unmapped + ChatException behaviour is already contracted
  in ChatViewModelTest; a parallel path would duplicate and risk divergence on the c-5 unmapped
  case.
- Adapters t-7 as their own wave-3 task vs merging into the session t-8: chose split — keeps each
  manual-only shell minimal and individually swappable, and lets t-8 depend only on the
  controller, not on real Android STT/TTS being present.
- Mic guidance lives in ChatViewModel (t-6) vs a separate voice-only state holder: chose
  ChatViewModel — the session already renders ChatScreen (locked session_ui_reuse), so one state
  object drives both typed and spoken surfaces and the banner content is asserted in a VM test.
```

verification: 9 tasks across 3 waves, criteria covered 5/5
```
