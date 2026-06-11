Phase 08-remote-stt-tts — 10 tasks across 3 waves

Lens: failure modes drive the graph. Each task owns exactly one way this feature
can break — bad input, mic contention, decode failure, network/auth, key leakage,
TTS feeding back into STT, or a silent privacy-violating engine switch — and the
test named in its contract is the one that goes red when that risk regresses.

Wave 1

  t-1  Add supportsStt/supportsTts capability flags
       files:    app/src/main/java/dev/equerry/app/providers/ProviderType.kt
       desc:     Add supportsStt and supportsTts (true only for OPENAI_COMPATIBLE,
                 mirroring supportsImages) so STT/TTS pickers can filter on type.
       covers:   c-1, c-2
       contract: if either flag is granted to ANTHROPIC/OLLAMA/OPENROUTER, a
                 ProviderTypeTest asserting only OPENAI_COMPATIBLE carries each flag
                 fails; if OPENAI_COMPATIBLE loses a flag, the same test fails.

  t-2  Audio HTTP error mapper + redaction guard
       files:    app/src/main/java/dev/equerry/app/voice/remote/AudioError.kt,
                 app/src/main/java/dev/equerry/app/voice/remote/AudioErrorMapper.kt,
                 app/src/test/java/dev/equerry/app/voice/remote/AudioErrorMapperTest.kt
       desc:     A routable AudioError set (Auth/Network/Http/Unavailable/Decode) and
                 a mapper that derives it from status code / exception TYPE only,
                 never copying a response body or exception message into the result.
       covers:   c-6
       contract: if the mapper ever copies a response body/exception text into
                 AudioError, the test feeding a 401 body containing "sk-secret-123"
                 asserting the mapped message does NOT contain that substring fails;
                 if 401/403 stops mapping to Auth, the status-code test fails.

  t-3  Silence-detecting audio capture (VAD)
       files:    app/src/main/java/dev/equerry/app/voice/remote/SilenceDetector.kt,
                 app/src/main/java/dev/equerry/app/voice/remote/AudioCapture.kt,
                 app/src/test/java/dev/equerry/app/voice/remote/SilenceDetectorTest.kt
       desc:     Pure amplitude/energy SilenceDetector that trips after a trailing-
                 silence window, plus an AudioCapture seam (interface + AudioRecord
                 impl behind a factory) that streams PCM frames and auto-stops on the
                 detector. The pure detector is unit-tested; AudioRecord is the seam.
       covers:   c-3
       contract: if the trailing-silence cutoff is mistuned, a SilenceDetectorTest
                 feeding loud frames then N silent frames asserting it trips at the
                 window boundary (and NOT one frame early or on a single dip) fails.

  t-4  Remote transcribe/speech request builders
       files:    app/src/main/java/dev/equerry/app/voice/remote/AudioRequestBuilder.kt,
                 app/src/test/java/dev/equerry/app/voice/remote/AudioRequestBuilderTest.kt
       desc:     Build the multipart transcribe POST (audio clip + model field) and the
                 JSON speech POST (model/voice/input, response_format=mp3); key lives in
                 the Authorization header only, never in path/query/body — mirroring
                 ChatRequestBuilder/r-03.
       covers:   c-3, c-4, c-6
       contract: if a builder puts the key in the URL or a form/body field, the test
                 asserting the built request's path+query+body contain no "Bearer"/key
                 substring while the Authorization header does fails.

Wave 2 (depends t-1, t-2, t-3, t-4)

  t-5  Remote SpeechToText impl (record→POST→Final)
       files:    app/src/main/java/dev/equerry/app/voice/remote/RemoteSpeechToText.kt,
                 app/src/test/java/dev/equerry/app/voice/remote/RemoteSpeechToTextTest.kt
       desc:     SpeechToText that records via AudioCapture, POSTs the clip via
                 AudioRequestBuilder over OkHttp, emits EndOfSpeech when the VAD trips
                 then a single Final, mapping transport/HTTP failures to SttEvent.Error
                 via AudioErrorMapper. No Partial events — preserves the SttEvent contract.
       covers:   c-3, c-6
       contract: if a Partial leaks or the Final/EndOfSpeech order breaks, a test
                 (MockWebServer returns a transcript) asserting the emitted sequence is
                 exactly [EndOfSpeech, Final("…")] with zero Partial fails; if a 401
                 response isn't surfaced, the test asserting the flow emits
                 Error(AudioError.Auth) fails.
       depends:  t-3, t-4, t-2

  t-6  Remote TextToSpeech impl + ordered playback
       files:    app/src/main/java/dev/equerry/app/voice/remote/RemoteTextToSpeech.kt,
                 app/src/main/java/dev/equerry/app/voice/remote/AudioPlayer.kt,
                 app/src/test/java/dev/equerry/app/voice/remote/RemoteTextToSpeechTest.kt
       desc:     TextToSpeech that POSTs each utterance for audio bytes and enqueues
                 clips on an AudioPlayer seam (Media3/ExoPlayer impl behind a factory;
                 fake in tests); awaitDone() suspends until the queue drains. speak() is
                 a guarded no-op on a failed init so a dead player never throws (c-5).
       covers:   c-4, c-6
       contract: if playback reorders or awaitDone returns before the queue drains, a
                 test queueing "one" then "two" against a fake player asserting play
                 order is [one,two] and awaitDone suspends until the last clip completes
                 fails; if a POST 500 throws instead of mapping, the test asserting
                 speak() reports AudioError.Http(500) without throwing fails.
       depends:  t-4, t-2

  t-7  Add Media3 dependency for audio playback
       files:    gradle/libs.versions.toml, app/build.gradle.kts
       desc:     Add androidx.media3 exoplayer + common to the version catalog and the
                 app module so RemoteTextToSpeech's production AudioPlayer can decode/
                 play mp3 clips.
       covers:   c-4
       contract: if the media3 dependency is missing/misversioned, :app:compileDebugKotlin
                 fails to resolve the AudioPlayer impl's androidx.media3 imports.
       depends:  t-6

  t-8  Filter STT/TTS pickers + persist mappings
       files:    app/src/main/java/dev/equerry/app/providers/ProviderRepository.kt,
                 app/src/main/java/dev/equerry/app/providers/CapabilitySlot.kt,
                 app/src/main/java/dev/equerry/app/ui/slots/SlotsViewModel.kt,
                 app/src/test/java/dev/equerry/app/providers/ProviderRepositoryTest.kt
       desc:     Flip STT/TTS CapabilitySlot.active=true; add observeSttMapping/
                 observeTtsMapping + setSttSlot/setTtsSlot to the repo (reusing
                 SlotMappingStore so mappings persist); extend SlotsViewModel.map/clear
                 to route STT/TTS. Slot pickers filter to profiles whose type carries
                 the matching flag (t-1).
       covers:   c-1, c-2
       contract: if a mapping doesn't persist, a ProviderRepositoryTest setting the STT
                 slot then re-reading observeSttMapping from a fresh repo over the same
                 store asserting it still resolves the profile fails; if the picker
                 stops filtering, the test asserting an Ollama profile is excluded from
                 the STT candidate list fails.
       depends:  t-1

  t-9  Voice voice/preset field on TTS profiles
       files:    app/src/main/java/dev/equerry/app/providers/ProviderProfile.kt,
                 app/src/main/java/dev/equerry/app/data/ProfileStore.kt,
                 app/src/main/java/dev/equerry/app/ui/providers/ProviderEditViewModel.kt,
                 app/src/test/java/dev/equerry/app/data/ProfileStoreTest.kt
       desc:     Add an optional ttsVoice field to ProviderProfile + StoredProfile DTO
                 (nullable default so older profiles still decode), surfaced as a free-
                 text+presets field only for TTS-capable types in the edit form,
                 defaulting to the provider default.
       covers:   c-2, c-4
       contract: if the new field breaks back-compat, a ProfileStoreTest decoding a
                 profile JSON written WITHOUT ttsVoice asserting it loads with
                 ttsVoice=null (no exception) fails.
       depends:  t-1

Wave 3 (depends t-5, t-6, t-8)

  t-10 Route + consented fallback in VoiceFlowController
       files:    app/src/main/java/dev/equerry/app/voice/VoiceFlowController.kt,
                 app/src/main/java/dev/equerry/app/voice/VoiceGuidance.kt,
                 app/src/main/java/dev/equerry/app/assistant/EquerryVoiceInteractionSession.kt,
                 app/src/main/java/dev/equerry/app/ui/chat/ChatScreen.kt,
                 app/src/test/java/dev/equerry/app/voice/VoiceFlowRemoteEngineTest.kt
       desc:     Select RemoteSpeechToText/RemoteTextToSpeech when the STT/TTS slot is
                 mapped, else the system engines (no regression). On a remote STT/TTS
                 failure, show one guidance surface AND an explicit "retry with system
                 engine" action; the turn uses the system engine only after the user
                 consents — never silently. Construction sites supply both engines +
                 mapping lookups.
       covers:   c-3, c-4, c-5, c-6
       contract: if the controller ever silently falls back, a VoiceFlowRemoteEngineTest
                 driving a remote STT Error asserting (a) guidance is shown, (b) the
                 system STT fake is NOT invoked until the consent action fires, and (c)
                 it IS invoked after fires. A separate test with both slots unmapped
                 asserts the system engines are used and the remote ones never
                 constructed (c-5). A mapped-STT test asserts the spoken text reaches
                 chat.send via the remote transcript, not system STT.
       depends:  t-5, t-6, t-8

## Coverage

  c-1  (config+map STT, survives restart)        → t-1, t-8
  c-2  (config+map TTS, survives restart)        → t-1, t-8, t-9
  c-3  (spoken utterance via remote STT)         → t-3, t-4, t-5, t-10
  c-4  (CHAT reply spoken via remote TTS)        → t-4, t-6, t-7, t-9, t-10
  c-5  (unmapped → system engines, no regression)→ t-10
  c-6  (remote failure → guidance, no key leak)  → t-2, t-4, t-5, t-6, t-10

Every criterion is owned by at least one task; the failure each criterion implies
(restart loss, wrong engine, key leak, silent fallback) is the named test in the
owning task's contract.

## Judgment calls

- Split the audio error mapper (t-2) and request builders (t-4) out of the STT/TTS
  impls (t-5/t-6) — chose isolating the two r-03 leak surfaces (error text, key
  placement) into their own pure, MockWebServer-free unit tests over folding them
  in; a key-leak regression then fails a tiny focused test, not a flaky network one.
- Made the VAD SilenceDetector a pure class (t-3) separate from AudioRecord — chose
  testing the silence-cutoff boundary deterministically over an instrumented mic
  test, because the cutoff timing is the single highest-value STT risk and must be
  unit-pinnable.
- Put consented fallback IN t-10 with the engine routing, not a separate task —
  chose co-owning "which engine runs" and "never switch without consent" because the
  silent-fallback privacy risk (locked failover_consented) lives entirely in the
  selection logic; splitting it would leave the rule un-owned by the router.
- Split Media3 dependency (t-7) from the TTS impl (t-6) as its own wave-2 task —
  chose a standalone gradle task gated by a compile contract over bundling the
  build-file edit into t-6, so a dependency/version miss fails a clearly-attributed
  compile step instead of masquerading as a TTS logic bug.
- Gave TTS voice (t-9) its own task touching profile+store+form — chose isolating
  the ProfileStore back-compat risk (older JSON must still decode) into one task with
  a decode-old-JSON contract over scattering the new field across t-8.
- Did NOT add a separate task for mic contention between STT capture and TTS
  playback — chose to own it inside t-10's awaitDone-before-rearm assertion (the
  existing controller already regates the mic on awaitDone; the remote TTS just has
  to honour the same suspend), rather than a task that would duplicate t-6's
  awaitDone contract.

risk: 10 tasks across 3 waves, criteria covered 6/6
