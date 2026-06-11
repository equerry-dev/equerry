Phase 08-remote-stt-tts — 6 tasks across 2 waves

Wave 1

  t-1  Flag STT/TTS capability + persist mappings
       files:    app/src/main/java/dev/equerry/app/providers/ProviderType.kt
                 app/src/main/java/dev/equerry/app/providers/CapabilitySlot.kt
                 app/src/main/java/dev/equerry/app/providers/ProviderProfile.kt
                 app/src/main/java/dev/equerry/app/providers/ProviderRepository.kt
                 app/src/test/java/dev/equerry/app/providers/ProviderRepositoryTest.kt
       covers:   c-1, c-2
       description:
                 Add supportsStt/supportsTts on ProviderType (true for OPENAI_COMPATIBLE only,
                 mirroring supportsImages); flip STT/TTS CapabilitySlot.active to true. Add optional
                 `ttsVoice: String?` to ProviderProfile + ProfileDraft and persist it via addProfile/
                 updateProfile. Add observeSttMapping/observeTtsMapping + setSttSlot/setTtsSlot to
                 ProviderRepository (mirroring the CHAT/VISION pair).
       contract: if setSttSlot stops writing through SlotMappingStore, the repo test asserting
                 observeSttMapping re-reads the persisted profile id after a fresh store instance
                 fails; if supportsStt is flagged on a non-OPENAI_COMPATIBLE type, the test asserting
                 only OPENAI_COMPATIBLE carries supportsStt/supportsTts fails.

  t-2  Remote STT: record-VAD-transcribe seam
       files:    app/src/main/java/dev/equerry/app/voice/RemoteSpeechToText.kt
                 app/src/main/java/dev/equerry/app/voice/SilenceDetector.kt
                 app/src/main/java/dev/equerry/app/providers/drivers/TranscribeClient.kt
                 app/src/test/java/dev/equerry/app/voice/RemoteSpeechToTextTest.kt
                 app/src/test/java/dev/equerry/app/voice/SilenceDetectorTest.kt
       covers:   c-3
       description:
                 RemoteSpeechToText implements SpeechToText: capture via an injected AudioRecord-style
                 source, run SilenceDetector (trailing-silence amplitude cutoff) to auto-stop, emit
                 EndOfSpeech on cutoff then a single Final from the multipart transcribe POST — no
                 Partial events. TranscribeClient POSTs the clip to the profile's transcribe endpoint
                 over the shared OkHttpClient; key in Authorization header only.
       contract: if the trailing-silence window stops triggering, SilenceDetectorTest (feed loud
                 samples then a silence run > the window, assert exactly one cutoff) fails; if the
                 transcribe round-trip is wired to emit a Partial, RemoteSpeechToTextTest asserting
                 the event order is [EndOfSpeech, Final] with no Partial fails; if the http 401 path
                 isn't mapped to SttError, the test asserting a 401 response yields Error(Unavailable)
                 fails.

  t-3  Remote TTS: POST-speech + Media3 playback seam
       files:    app/src/main/java/dev/equerry/app/voice/RemoteTextToSpeech.kt
                 app/src/main/java/dev/equerry/app/providers/drivers/SpeechClient.kt
                 app/src/test/java/dev/equerry/app/voice/RemoteTextToSpeechTest.kt
       covers:   c-4
       description:
                 RemoteTextToSpeech implements TextToSpeech over an injected gapless-player seam
                 (Media3 ExoPlayer in production): speak()/speakSentences() POST each clip via
                 SpeechClient (mp3, voice = profile.ttsVoice or provider default) and queue it in
                 order; awaitDone() suspends until the playlist drains. init() reports Ready/Failed.
                 Key in Authorization header only.
       contract: if speakSentences stops preserving order, RemoteTextToSpeechTest asserting the fake
                 player received clips in feed order fails; if awaitDone returns before the queued
                 clips drain, the test asserting awaitDone suspends until the fake player signals
                 playlist-complete fails; if the requested voice falls back wrongly, the test
                 asserting a blank ttsVoice omits the voice field (provider default) fails.

Wave 2 (depends t-1, t-2, t-3)

  t-4  Filter pickers + voice field in slots/profile UI
       files:    app/src/main/java/dev/equerry/app/ui/slots/SlotsViewModel.kt
                 app/src/main/java/dev/equerry/app/ui/slots/SlotsScreen.kt
                 app/src/test/java/dev/equerry/app/ui/slots/SlotsViewModelTest.kt
       covers:   c-1, c-2
       description:
                 SlotsUiState gains sttProfile/visionProfile-style sttProfile + ttsProfile from the
                 new repo flows; mappedProfile + map() handle STT/TTS. The STT/TTS picker sheets
                 filter state.profiles to those whose type.supportsStt / supportsTts is true. The
                 provider create/edit form shows the optional voice free-text field only for TTS-
                 capable types.
       depends_on: t-1
       contract: if the STT picker stops filtering on supportsStt, the SlotsViewModelTest asserting
                 the STT-slot candidate list excludes an ANTHROPIC profile fails; if map(STT,..)
                 stops routing to setSttSlot, the test asserting the STT mapping flow updates fails.

  t-5  Select remote-vs-system STT/TTS per mapping
       files:    app/src/main/java/dev/equerry/app/voice/VoiceComponentsEntryPoint.kt
                 app/src/main/java/dev/equerry/app/voice/VoiceEngineSelector.kt
                 app/src/main/java/dev/equerry/app/ui/chat/ChatScreen.kt
                 app/src/main/java/dev/equerry/app/assistant/EquerryVoiceInteractionSession.kt
                 app/src/test/java/dev/equerry/app/voice/VoiceEngineSelectorTest.kt
       covers:   c-3, c-4, c-5
       description:
                 VoiceEngineSelector resolves the SpeechToText/TextToSpeech a turn uses: a mapped
                 STT/TTS profile yields the Remote impl built around that profile + its key; an
                 unmapped slot yields the System impl. Both construction sites (ChatRoute, session)
                 build their STT/TTS through the selector instead of hardcoding SystemSpeechToText/
                 SystemTextToSpeech.
       depends_on: t-1, t-2, t-3
       contract: if an unmapped STT slot stops resolving to SystemSpeechToText, the selector test
                 asserting null STT mapping → System impl fails (no-regression, c-5); if a mapped TTS
                 profile stops resolving to RemoteTextToSpeech, the test asserting a mapped profile →
                 Remote impl fails.

  t-6  Consented system-engine fallback on remote failure
       files:    app/src/main/java/dev/equerry/app/voice/VoiceFlowController.kt
                 app/src/main/java/dev/equerry/app/voice/VoiceGuidance.kt
                 app/src/test/java/dev/equerry/app/voice/VoiceFlowFailureTest.kt
       covers:   c-6
       description:
                 On a remote STT/TTS Error/init-Failed mid-turn, set guidance via a new
                 VoiceGuidanceFactory.remoteEngineFailed that carries a retryWithSystem action; expose
                 a retryWithSystem() on the controller that re-runs the current turn against the System
                 impl only after the user invokes it. Never auto-switch. Guidance message is built
                 key-free.
       depends_on: t-2, t-3, t-5
       contract: if the controller auto-switches to the system engine on remote failure, the test
                 asserting the system STT/TTS is NOT invoked until retryWithSystem() is called fails;
                 if a raw key leaks into the failure guidance, the test feeding a key-bearing error
                 body and asserting the guidance message contains no key substring fails (r-03).

## Coverage
- c-1: t-1, t-4
- c-2: t-1, t-4
- c-3: t-2, t-5
- c-4: t-3, t-5
- c-5: t-5
- c-6: t-6

## Judgment calls
- Folded "make STT/TTS slots mappable + persistent" into t-1 rather than a standalone persistence task: the SlotMappingStore/ProviderRepository machinery already persists CHAT/VISION across restart identically, so c-1/c-2 persistence is a mirror-method add, not new infrastructure — no separate restart task earns its place.
- Split slot/profile data (t-1) from slot/profile UI (t-4) because c-1/c-2 span data+repo+ui (>2 layers); kept everything else in each to avoid a third sliver task.
- Kept remote STT (t-2) and remote TTS (t-3) as two wave-1 tasks, not one: each is a full capture/playback subsystem with its own client + seam (>2 layers combined, distinct failure surfaces) — merging would exceed the size cap.
- Put engine-selection (t-5) and consented-fallback (t-6) as separate wave-2 tasks rather than one: selection is the no-regression wiring (c-5) at two construction sites; consent/guidance is the failure semantics (c-6). They break independently and have different test surfaces, but selection must land first so t-6 has a System impl to fall back to.
- Did NOT add a dedicated key-redaction task: redaction is asserted inside t-2/t-3 (header-only key) and t-6 (guidance message), reusing the existing ChatHttpClient redaction + KeyRedaction — a standalone task would have no surface of its own.
