Phase 08-remote-stt-tts — 12 tasks across 4 waves

Lens: design backward from the test contract. For each criterion the ideal test was written
first, then the smallest task that makes that test compile and pass. The bias is to extract
pure, Android-free units behind the existing framework seams (RecognizerHandle / TtsEngine
style) so every contract below is a real JVM unit test against a fake — never an integration
hope. The two framework-bound shells (AudioRecord capture, Media3 player) are isolated to the
seam edge and left to manual integration, exactly as SystemRecognizerHandle / AndroidTtsEngine
are today.

Wave 1
  t-1  Add supportsStt/supportsTts flags to ProviderType
       files:    app/src/main/java/dev/equerry/app/providers/ProviderType.kt
                 app/src/test/java/dev/equerry/app/providers/ProviderTypeTest.kt
       covers:   c-1, c-2
       contract: supportsStt/supportsTts are true ONLY for OPENAI_COMPATIBLE — flip the flag
                 onto ANTHROPIC/OLLAMA/OPENROUTER and the new ProviderTypeTest rows fail
                 (mirrors the existing image_capability row test).

  t-2  Activate STT and TTS capability slots
       files:    app/src/main/java/dev/equerry/app/providers/CapabilitySlot.kt
                 app/src/test/java/dev/equerry/app/providers/ProviderTypeTest.kt
       covers:   c-1, c-2
       contract: CapabilitySlot.STT.active and .TTS.active are true; the existing
                 "rest are not active" assertion in ProviderTypeTest is narrowed to OCR+EMBEDDINGS
                 only — if STT/TTS regress to inactive, that test fails.

  t-3  Build transcribe HTTP request (pure)
       files:    app/src/main/java/dev/equerry/app/providers/drivers/TranscribeRequestBuilder.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/TranscribeRequestBuilderTest.kt
       covers:   c-3, c-6
       contract: builder emits path "audio/transcriptions", a multipart body carrying the
                 profile.model field, and the key ONLY in the Authorization header — a test
                 asserting the built path/headers fails if the key leaks into path/body (r-03),
                 mirroring ChatRequestBuilderTest.

  t-4  Build speech HTTP request (pure)
       files:    app/src/main/java/dev/equerry/app/providers/drivers/SpeechRequestBuilder.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/SpeechRequestBuilderTest.kt
       covers:   c-4, c-6
       contract: builder emits path "audio/speech", a JSON body with model, input text, the
                 selected voice, and response_format "mp3"; key only in Authorization header —
                 test fails if voice is dropped, format != mp3, or the key reaches body/path.

  t-5  Silence VAD auto-stop (pure)
       files:    app/src/main/java/dev/equerry/app/voice/SilenceVad.kt
                 app/src/test/java/dev/equerry/app/voice/SilenceVadTest.kt
       covers:   c-3
       contract: feeding a frame-amplitude sequence that rises above then stays below the
                 threshold for the trailing-silence window returns shouldStop=true exactly at
                 the window boundary; a sequence that never goes quiet never trips — shrink or
                 grow the window by one frame and SilenceVadTest fails.

  t-6  Map transcribe/speech errors to guidance (pure)
       files:    app/src/main/java/dev/equerry/app/voice/RemoteAudioError.kt
                 app/src/main/java/dev/equerry/app/voice/VoiceGuidance.kt
                 app/src/test/java/dev/equerry/app/voice/VoiceGuidanceFactoryTest.kt
       covers:   c-6
       contract: a 401 maps to an auth-failure RemoteAudioError whose VoiceGuidance message is
                 a fixed template containing NO request/response text; a test feeding a key-bearing
                 fake response asserts the key string is absent from the guidance (r-03), and that
                 401/network/unavailable each yield a distinct message.

Wave 2 (depends t-3, t-4, t-5, t-6)
  t-7  RemoteSpeechToText over recorder+transport seams
       files:    app/src/main/java/dev/equerry/app/voice/RemoteSpeechToText.kt
                 app/src/test/java/dev/equerry/app/voice/RemoteSpeechToTextTest.kt
       covers:   c-3, c-6
       depends:  t-3, t-5, t-6
       contract: driving a fake recorder (amplitude frames) + fake transport, listen() emits
                 EXACTLY [EndOfSpeech, Final("...")] and no Partial when the VAD trips and the
                 transcript returns; a transport 401 yields [EndOfSpeech, Error(Unavailable/Auth)]
                 and the flow completes (no hang) — assert against RemoteSpeechToTextTest, mirroring
                 SystemSpeechToTextTest's fake-handle pattern.

  t-8  RemoteTextToSpeech over transport+player seams
       files:    app/src/main/java/dev/equerry/app/voice/RemoteTextToSpeech.kt
                 app/src/test/java/dev/equerry/app/voice/RemoteTextToSpeechTest.kt
       covers:   c-4, c-6
       depends:  t-4, t-6
       contract: speak("hi") POSTs once via the fake transport and enqueues one clip on the fake
                 player; speakSentences(flow) POSTs per sentence and enqueues clips IN ORDER;
                 awaitDone() suspends until the fake player drains and resumes after — a test that
                 completes the player late proves awaitDone gated the mic re-arm. A transport failure
                 surfaces RemoteAudioError without throwing into the caller (c-6).

  t-9  Voice/format field on draft + validator
       files:    app/src/main/java/dev/equerry/app/providers/ProviderProfile.kt
                 app/src/main/java/dev/equerry/app/data/ProfileStore.kt
                 app/src/test/java/dev/equerry/app/data/ProfileStoreTest.kt
       covers:   c-2, c-4
       depends:  t-1
       contract: a ProviderProfile saved with ttsVoice="alloy" round-trips through ProfileStore
                 encode→decode unchanged, and a profile written WITHOUT the field (legacy JSON)
                 still decodes (ttsVoice == null) — ProfileStoreTest fails if the new field breaks
                 backward-compatible decode or drops the voice on persistence.

Wave 3 (depends t-7, t-8, t-9)
  t-10 STT/TTS slot mapping in repository
       files:    app/src/main/java/dev/equerry/app/providers/ProviderRepository.kt
                 app/src/test/java/dev/equerry/app/providers/ProviderRepositoryTest.kt
       covers:   c-1, c-2, c-5
       depends:  t-2
       contract: setSttSlot(id)/setTtsSlot(id) persist via SlotMappingStore and observeSttMapping/
                 observeTtsMapping re-emit that profile; after a simulated restart (fresh repository
                 over the same fake stores) the mapping is still present, and deleteProfile cascades
                 to clear an STT/TTS mapping pointing at it — ProviderRepositoryTest fails on any of
                 these, mirroring the existing CHAT/VISION mapping tests.

  t-11 Resolve mapped remote vs system engine (pure selector)
       files:    app/src/main/java/dev/equerry/app/voice/VoiceEngineSelector.kt
                 app/src/test/java/dev/equerry/app/voice/VoiceEngineSelectorTest.kt
       covers:   c-3, c-4, c-5
       depends:  t-7, t-8
       contract: given an STT mapping → selector returns a RemoteSpeechToText built for that
                 profile; given NO mapping → it returns the system SpeechToText (factory invoked
                 with the system builder) — same for TTS. VoiceEngineSelectorTest asserts the
                 unmapped path picks the system engine (c-5 no-regression) and the mapped path picks
                 remote, by capturing which factory ran.

Wave 4 (depends t-10, t-11)
  t-12 Consented system-engine fallback in VoiceFlowController
       files:    app/src/main/java/dev/equerry/app/voice/VoiceFlowController.kt
                 app/src/test/java/dev/equerry/app/voice/VoiceFlowFailureTest.kt
       covers:   c-6
       depends:  t-11
       contract: with a remote STT/TTS fake that errors, the controller surfaces ONE failure
                 guidance AND exposes a "retry with system engine" action; invoking that action
                 re-runs the turn on the system engine, while NOT invoking it never touches the
                 system engine — VoiceFlowFailureTest fails if it silently falls back (a system-
                 engine spy records a call without consent) or if no retry action is offered.

## Coverage
  c-1  → t-1, t-2, t-10
  c-2  → t-1, t-2, t-9, t-10
  c-3  → t-3, t-5, t-7, t-11
  c-4  → t-4, t-8, t-9, t-11
  c-5  → t-10, t-11
  c-6  → t-3, t-4, t-6, t-7, t-8, t-12

## Judgment calls
- Split the engines into pure builders (t-3/t-4), a pure VAD (t-5), and seam-backed impls
  (t-7/t-8) rather than one RemoteSpeechToText class doing record+POST+parse: chose three small
  units so each has a real JVM test (mirroring ChatRequestBuilder vs SystemSpeechToText), rejected
  the monolith because its only honest test would be an instrumented integration run.
- Modelled capture/playback as injected seams (recorder factory, transport, player) like the
  existing RecognizerHandle/TtsEngine, NOT direct AudioRecord/Media3 in the testable class: chose
  testability over fewer files; the framework shells (AudioRecord, ExoPlayer) sit at the edge and
  are manual-integration-only, consistent with SystemRecognizerHandle/AndroidTtsEngine.
- Made the mapped-vs-system choice a pure VoiceEngineSelector (t-11) instead of branching inside
  the two ChatScreen/EquerryVoiceInteractionSession wiring sites: chose one tested decision point
  so c-5 (no-regression to system when unmapped) is a unit assertion, not duplicated UI glue;
  rejected inlining because the unmapped fallback would then be untestable.
- Put consented failover in VoiceFlowController against the existing VoiceFlowFailureTest (t-12)
  rather than a new orchestrator: chose to extend the proven pure controller and its fake-driven
  test, rejected a parallel failover class that would re-implement the turn loop.
- Reused profile.model as the audio model (locked decision) and added only ttsVoice to the
  profile/store (t-9); rejected a separate audio-model field as contradicting the locked
  "model field doubles as the audio model" decision.
