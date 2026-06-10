Phase 05-voice-roundtrip — 6 tasks across 2 waves

Wave 1

  t-1  Persist the two voice settings (DataStore)
       files:    app/src/main/java/dev/equerry/app/data/VoiceSettingsStore.kt
                 app/src/main/java/dev/equerry/app/di/PersistenceModule.kt
                 app/src/test/java/dev/equerry/app/data/VoiceSettingsStoreTest.kt
       covers:   turn_control, speak_timing (locked)
       desc:     New DataStore-backed store on the existing "equerry_settings" DataStore exposing
                 turnControl: Flow<TurnControl> (CONTINUOUS default) and speakTiming: Flow<SpeakTiming>
                 (WHOLE_REPLY default) with setters; @Provides @Singleton in PersistenceModule.
       contract: VoiceSettingsStoreTest: defaults read CONTINUOUS + WHOLE_REPLY before any write;
                 after setTurnControl(SINGLE_TURN) the flow re-emits SINGLE_TURN; mirrors
                 SlotMappingStoreTest's TemporaryFolder + PreferenceDataStoreFactory idiom.

  t-2  Add STT/TTS seams + system impls + RECORD_AUDIO
       files:    app/src/main/java/dev/equerry/app/voice/SpeechToText.kt
                 app/src/main/java/dev/equerry/app/voice/TextToSpeech.kt
                 app/src/main/java/dev/equerry/app/voice/SystemSpeechToText.kt
                 app/src/main/java/dev/equerry/app/voice/SystemTextToSpeech.kt
                 app/src/main/AndroidManifest.xml
       covers:   c-1, c-2, c-4 (capture/transcribe/speak surfaces)
       desc:     SpeechToText interface { fun listen(): Flow<SttEvent> } with SttEvent (Partial/Final/
                 EndOfSpeech/Error/Unavailable); TextToSpeech interface { suspend fun speak(text),
                 fun isAvailable() }. System impls wrap android.speech.SpeechRecognizer and
                 android.speech.tts.TextToSpeech. Declare <uses-permission RECORD_AUDIO>.
       contract: Framework impls (SystemSpeechToText/SystemTextToSpeech) are verified MANUALLY — no
                 unit test; the interfaces exist precisely so t-4 is testable against fakes. Manifest
                 regression caught by an existing manifest/instrumented check that RECORD_AUDIO is present.

  t-3  Add programmatic send + completion signal to ChatViewModel
       files:    app/src/main/java/dev/equerry/app/ui/chat/ChatViewModel.kt
                 app/src/test/java/dev/equerry/app/ui/chat/ChatViewModelTest.kt
       covers:   c-3, c-5 (reuse phase-04 send/stream/error/unmapped)
       desc:     Extract send() body into send(text: String) that takes the utterance directly (input
                 overload delegates to it); expose lastReply/done so a caller can read the completed
                 assistant text. No new round-trip logic — the existing stream/unmapped/error path is
                 reused verbatim for spoken questions.
       contract: ChatViewModelTest: send("hi") with a mapped provider produces the same 2-bubble
                 transcript as the typed path and exposes the finished assistant text "Hello";
                 send("hi") with no CHAT mapping sets unmapped and makes zero driver requests.

Wave 2

  t-4  Voice flow controller: listen→send→speak + turn loop
       files:    app/src/main/java/dev/equerry/app/voice/VoiceFlowController.kt
                 app/src/test/java/dev/equerry/app/voice/VoiceFlowControllerTest.kt
       covers:   c-1, c-2, c-3, c-4, c-5
       desc:     Orchestrates the round-trip over t-2 seams + t-1 settings + t-3 ChatViewModel:
                 start listening (state=Listening), on Final transcript auto-send via ChatViewModel,
                 on reply-complete speak it (WHOLE_REPLY) or per-sentence (SENTENCE), then CONTINUOUS
                 re-listens vs SINGLE_TURN stops. Exposes micState incl. PermissionDenied + Unavailable
                 guidance. Pure logic over fakes; no framework classes referenced directly.
       depends:  t-1, t-2, t-3
       contract: VoiceFlowControllerTest (fake STT/TTS/ChatViewModel seam): (a) STT EndOfSpeech →
                 auto-send fires exactly once per utterance; (b) on reply-complete with WHOLE_REPLY the
                 fake TTS receives one speak() of the full reply, with SENTENCE it receives one per
                 sentence; (c) CONTINUOUS re-enters Listening after speak, SINGLE_TURN does not;
                 (d) STT Error/Unavailable and unmapped-provider both set a guidance state and never
                 throw / never call TTS.

  t-5  Render ChatScreen in the assist session, driven by voice flow
       files:    app/src/main/java/dev/equerry/app/assistant/EquerryVoiceInteractionSession.kt
       covers:   c-1, c-2, c-3, c-4, c-5, session_ui_reuse, mic_permission (in-session prompt)
       desc:     Replace ProbeSessionScreen with ChatRoute/ChatScreen wired to a VoiceFlowController;
                 on session start request RECORD_AUDIO and start listening (c-1); on denial show the
                 shared guidance state from t-4. Probe capture stays (still feeds ProbeStore) but is no
                 longer the rendered surface. Keep AssistStructure capture untouched.
       depends:  t-4
       contract: Framework-bound (VoiceInteractionSession) — verified MANUALLY per the codebase note:
                 assist gesture → session shows ChatScreen, listens, transcribes, streams reply, speaks.
                 All branchable logic lives in t-4's tested VoiceFlowController; this task only wires it.

  t-6  Minimal settings UI for the two voice toggles
       files:    app/src/main/java/dev/equerry/app/ui/settings/VoiceSettingsScreen.kt
                 app/src/main/java/dev/equerry/app/ui/settings/VoiceSettingsViewModel.kt
                 app/src/main/java/dev/equerry/app/MainActivity.kt
                 app/src/test/java/dev/equerry/app/ui/settings/VoiceSettingsViewModelTest.kt
       covers:   turn_control, speak_timing (locked — minimal UI), mic_permission (settings path)
       desc:     Leanest screen: two segmented/toggle controls bound to t-1's store via a ViewModel,
                 plus a mic-permission request entry (settings path of mic_permission). Add a Route +
                 HomeScreen entry in MainActivity. No general settings framework — just these toggles.
       depends:  t-1
       contract: VoiceSettingsViewModelTest: toggling turn-control calls store.setTurnControl(SINGLE_TURN)
                 and the exposed state reflects it; toggling speak-timing calls setSpeakTiming(SENTENCE).
                 Compose-render of the screen is covered by a lightweight VoiceSettingsScreen smoke test
                 asserting both controls render with the current values.

## Coverage
  c-1 (launch → listening + STT start)         → t-2, t-4, t-5
  c-2 (utterance transcribed, shown as question)→ t-2, t-3, t-4, t-5
  c-3 (send to CHAT provider, streams in)       → t-3, t-4, t-5
  c-4 (completed reply spoken via TTS)          → t-2, t-4, t-5
  c-5 (no mapping / STT|TTS unavailable → guidance, no crash) → t-3, t-4, t-5
  Locked turn_control   → t-1 (persist), t-4 (loop behaviour), t-6 (UI)
  Locked speak_timing   → t-1 (persist), t-4 (timing behaviour), t-6 (UI)
  Locked session_ui_reuse → t-5
  Locked mic_permission → t-5 (in-session prompt), t-6 (settings path); shared denied state lives in t-4

## Judgment calls
  - Reuse ChatViewModel via a programmatic send(text) (t-3) — chose; rejected a new VoiceChatViewModel; the phase-04 stream/error/unmapped path is exactly c-3/c-5 and duplicating it adds an untested second copy.
  - Voice round-trip logic in a plain VoiceFlowController over interface seams (t-4) — chose; rejected putting the loop in the framework session; framework code is manual-only, so the testable turn/timing/guidance logic must sit behind an interface seam (codebase memory).
  - One VoiceSettingsStore for both locked settings (t-1) — chose; rejected two stores; both are simple enum prefs on the same DataStore, one file < the merge threshold.
  - Settings UI as one tiny screen + ViewModel (t-6) — chose minimal toggles; rejected a general settings framework (none exists, none required by criteria); locked decisions demand the two toggles and nothing more.
  - mic_permission split across t-5 (in-session) and t-6 (settings) — chose; rejected a third permission-helper task; the *shared* denied/guidance state is one state in t-4, so each entry point only needs a request call, not its own task.
  - STT/TTS system impls left untested (t-2) — chose manual verification; rejected wrapping SpeechRecognizer/TextToSpeech in test doubles for their own sake; they are thin framework adapters, and t-4 tests the only branching logic against fakes.
