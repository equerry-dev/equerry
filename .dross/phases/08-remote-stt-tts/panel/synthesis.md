# Synthesis — Phase 08 remote-stt-tts

Judged from the three lens drafts (risk / mvp / verification) against the locked spec
and the real source tree. Path/seam claims were adjudicated against the existing files
(`ProviderType`, `CapabilitySlot`, `ProviderRepository`, `ProfileStore`, `SpeechToText`,
`TextToSpeech`, `VoiceFlowController`, `VoiceGuidance`, `SlotsViewModel`,
`VoiceComponentsEntryPoint`). Key grounding facts that drove the merge:

- `supportsImages`/`supportsTools` are computed `val` getters on `ProviderType`, not
  constructor flags — so the new flags are getters and the type test asserts getter values.
- `SttError` is a fixed sealed set with **no `Auth` variant**; a remote 401 must map onto
  the existing `SttError.Unavailable`. (risk's `AudioError.Auth`-as-SttEvent payload is the
  wrong type; mvp/verification's `Error(Unavailable)` is correct.)
- `ProviderRepository` already has the exact `observeChatMapping`/`setChatSlot` mirror and
  `deleteProfile` **already cascades over every `CapabilitySlot.entries`** — so STT/TTS
  cascade-on-delete is automatic once slots are active; it is a test, not new logic.
- `ProfileStore.StoredProfile` already proves the nullable-default + `ignoreUnknownKeys`
  back-compat pattern; `ProfileDraft` is the real form-input type.
- `VoiceFlowController` takes `stt`/`tts` as **constructor params** — it does not itself
  pick remote-vs-system. Selection belongs at construction (a selector), not inside the
  controller. The controller already routes `SttEvent.Error → _guidance`; consent/retry is
  a genuine new controller capability.
- There is **no `voice/remote/` subdirectory** today; existing peers (`SystemSpeechToText`,
  `ChatRequestBuilder`) sit flat in `voice/` and `providers/drivers/`.

## Scores

Scale: A (strong) / B (adequate) / C (weak), per dimension.

| Draft | Criteria coverage | Test-contract specificity | Granularity | Wave correctness |
|-------|-------------------|---------------------------|-------------|------------------|
| risk (10t/3w) | A — 6/6, every criterion owned, risk-per-task | A — sharpest leak/order/boundary contracts (named substrings, exact event sequences) | B — strong split, but t-1 and t-8 both touch flags/slots and the `voice/remote/` dir it assumes doesn't exist | B — Media3 (t-7) placed *after* its consumer t-6 in the same wave; otherwise sound |
| mvp (6t/2w) | B — 6/6 but c-5 leans entirely on one task (t-5) | B — good order/persist/voice-default contracts, lighter on the key-leak substring | C — t-1 bundles type-flag + slot-active + profile field + repo methods (4 layers); t-2/t-3 each fold builder+VAD+impl into one (only honest test is integration) | A — clean 2-wave dependency story, selector-before-fallback ordering correct |
| verification (12t/4w) | A — 6/6, mapped backward from the ideal test per criterion | A — every contract is a real JVM unit test against a fake; pure builders/VAD/mapper extracted | A — finest correct granularity; isolates the two framework shells (AudioRecord, Media3) at the seam edge exactly like `SystemSpeechToText`/`SystemTextToSpeech` | A — 4 waves with correct `pure → seam-impl → repo/selector → controller` layering |

**Skeleton: `verification`.** It alone derives granularity from a *real* unit test per
criterion, matches the codebase's existing seam idiom (pure builder + injected seam, mirroring
`ChatRequestBuilder` vs `SystemSpeechToText`), and keeps `SttError.Unavailable` (not an
invented `Auth`). Its file paths are the only set that all resolve against the real tree.
The graft below pulls risk's sharper leak/boundary contract wording, mvp's Media3-task
placement fix, and one missed slot/profile-UI surface that verification under-specified.

## Merged plan

Phase 08-remote-stt-tts — 11 tasks across 4 waves

### Wave 1

```
t-1  Add supportsStt/supportsTts flags to ProviderType                    [verification + risk]
     files:    app/src/main/java/dev/equerry/app/providers/ProviderType.kt
               app/src/test/java/dev/equerry/app/providers/ProviderTypeTest.kt
     covers:   c-1, c-2
     contract: supportsStt/supportsTts are computed val getters true ONLY for
               OPENAI_COMPATIBLE (mirroring the supportsImages getter). Flip either flag
               onto ANTHROPIC/OLLAMA/OPENROUTER and the new ProviderTypeTest rows fail; if
               OPENAI_COMPATIBLE loses a flag, the same rows fail. (risk's bidirectional
               assertion: both "granted to a wrong type" and "lost from the right type" go red.)
```

```
t-2  Activate STT and TTS capability slots                               [verification]
     files:    app/src/main/java/dev/equerry/app/providers/CapabilitySlot.kt
               app/src/test/java/dev/equerry/app/providers/ProviderTypeTest.kt
     covers:   c-1, c-2
     contract: CapabilitySlot.STT.active and .TTS.active are true; the existing
               "rest are not active" assertion narrows to OCR+EMBEDDINGS only — if STT/TTS
               regress to inactive, that test fails.
```

```
t-3  Build transcribe HTTP request (pure)                                [verification + risk]
     files:    app/src/main/java/dev/equerry/app/providers/drivers/TranscribeRequestBuilder.kt
               app/src/test/java/dev/equerry/app/providers/drivers/TranscribeRequestBuilderTest.kt
     covers:   c-3, c-6
     contract: builder emits path "audio/transcriptions" and a multipart body carrying the
               profile.model field; the key appears ONLY in the Authorization header. The
               test asserts request path+query+body contain no "Bearer"/key substring while
               the Authorization header does (risk's r-03 substring assertion), mirroring
               ChatRequestBuilderTest. Fails if the key reaches path/query/body.
```

```
t-4  Build speech HTTP request (pure)                                    [verification + risk]
     files:    app/src/main/java/dev/equerry/app/providers/drivers/SpeechRequestBuilder.kt
               app/src/test/java/dev/equerry/app/providers/drivers/SpeechRequestBuilderTest.kt
     covers:   c-4, c-6
     contract: builder emits path "audio/speech" and a JSON body with model, input text, the
               selected voice, and response_format "mp3"; key only in Authorization header.
               Test fails if voice is dropped, format != mp3, or the key reaches body/path/query.
```

```
t-5  Silence VAD auto-stop (pure)                                        [verification + risk]
     files:    app/src/main/java/dev/equerry/app/voice/SilenceVad.kt
               app/src/test/java/dev/equerry/app/voice/SilenceVadTest.kt
     covers:   c-3
     contract: feeding loud frames then a trailing-silence run returns shouldStop=true
               EXACTLY at the window boundary — not one frame early, not on a single dip; a
               never-quiet sequence never trips. Grow or shrink the window by one frame and
               SilenceVadTest fails. (risk's "not one frame early / not on a single dip"
               sharpens verification's boundary assertion.)
```

```
t-6  Map transcribe/speech errors to key-free guidance (pure)           [verification + mvp]
     files:    app/src/main/java/dev/equerry/app/voice/RemoteAudioError.kt
               app/src/main/java/dev/equerry/app/voice/VoiceGuidance.kt
               app/src/test/java/dev/equerry/app/voice/VoiceGuidanceFactoryTest.kt
     covers:   c-6
     contract: a routable RemoteAudioError set is derived from status code / exception TYPE
               only — never copying a response body or exception message. A new
               VoiceGuidanceFactory.remoteEngineFailed(...) builds a fixed template message.
               Test feeds a 401 body containing "sk-secret-123" and asserts the guidance
               message does NOT contain that substring (r-03); 401/network/unavailable each
               yield a distinct message. (Extends the existing VoiceGuidanceFactory object
               rather than a new file — the factory already exists.)
```

### Wave 2 (depends t-3, t-4, t-5, t-6)

```
t-7  RemoteSpeechToText over recorder + transport seams                  [verification + risk]
     files:    app/src/main/java/dev/equerry/app/voice/RemoteSpeechToText.kt
               app/src/test/java/dev/equerry/app/voice/RemoteSpeechToTextTest.kt
     covers:   c-3, c-6
     depends:  t-3, t-5, t-6
     contract: driving a fake recorder (amplitude frames) + fake transport, listen() emits
               EXACTLY [EndOfSpeech, Final("...")] and ZERO Partial when the VAD trips and the
               transcript returns; a transport 401 yields a terminal Error mapped to
               SttError.Unavailable (the existing sealed set has no Auth variant) and the flow
               completes without hanging — mirroring SystemSpeechToTextTest's fake-handle pattern.
               AudioRecord stays at the seam edge (manual integration), like SystemRecognizerHandle.
```

```
t-8  RemoteTextToSpeech over transport + player seams                    [verification + mvp]
     files:    app/src/main/java/dev/equerry/app/voice/RemoteTextToSpeech.kt
               app/src/test/java/dev/equerry/app/voice/RemoteTextToSpeechTest.kt
     covers:   c-4, c-6
     depends:  t-4, t-6
     contract: speak("hi") POSTs once via the fake transport and enqueues one clip on the fake
               player; speakSentences(flow) POSTs per sentence (reusing SpeakChunker timing) and
               enqueues clips IN ORDER; awaitDone() suspends until the fake player drains and
               resumes only after — a test completing the player late proves awaitDone gated the
               mic re-arm. init() reports Ready/Failed and speak() is a guarded no-op on a failed
               init (a dead player never throws, c-5). A transport 500 surfaces RemoteAudioError
               without throwing into the caller (c-6). Blank ttsVoice omits the voice field
               (provider default). Media3 ExoPlayer stays at the seam edge (manual integration).
```

```
t-9  Add Media3 dependency for audio playback                           [risk]
     files:    gradle/libs.versions.toml
               app/build.gradle.kts
     covers:   c-4
     depends:  (none — gradle catalog edit; lands before/with t-8's production player)
     contract: add androidx.media3 exoplayer + common to the version catalog and the app
               module so RemoteTextToSpeech's production AudioPlayer can decode/play mp3 clips.
               If the dependency is missing/misversioned, :app:compileDebugKotlin fails to
               resolve the androidx.media3 imports. (Standalone so a version miss fails a
               clearly-attributed compile step, not a TTS logic test.)
```

```
t-10 Voice field on profile + back-compat store                         [verification + risk]
     files:    app/src/main/java/dev/equerry/app/providers/ProviderProfile.kt
               app/src/main/java/dev/equerry/app/data/ProfileStore.kt
               app/src/test/java/dev/equerry/app/data/ProfileStoreTest.kt
     covers:   c-2, c-4
     depends:  t-1
     contract: add optional ttsVoice: String? to ProviderProfile + ProfileDraft + the
               StoredProfile DTO (nullable default, leaning on the existing
               ignoreUnknownKeys + nullable-default pattern). A profile saved with
               ttsVoice="alloy" round-trips encode→decode unchanged; a legacy JSON written
               WITHOUT the field still decodes (ttsVoice == null, no exception).
               ProfileStoreTest fails if the new field breaks back-compat or drops the voice.
```

### Wave 3 (depends t-7, t-8, t-9, t-10)

```
t-11 STT/TTS mappings + filtered pickers + voice UI                      [mvp + verification + risk]
     files:    app/src/main/java/dev/equerry/app/providers/ProviderRepository.kt
               app/src/main/java/dev/equerry/app/ui/slots/SlotsViewModel.kt
               app/src/main/java/dev/equerry/app/ui/slots/SlotsScreen.kt
               app/src/main/java/dev/equerry/app/ui/providers/ProviderEditViewModel.kt
               app/src/test/java/dev/equerry/app/providers/ProviderRepositoryTest.kt
               app/src/test/java/dev/equerry/app/ui/slots/SlotsViewModelTest.kt
     covers:   c-1, c-2, c-5
     depends:  t-1, t-2, t-10
     contract: add observeSttMapping/observeTtsMapping + setSttSlot/setTtsSlot to
               ProviderRepository (mirroring the CHAT/VISION pair); SlotsUiState gains
               sttProfile/ttsProfile and map()/clear()/mappedProfile() route STT/TTS. Picker
               sheets filter state.profiles to type.supportsStt / supportsTts; the profile
               edit form shows the optional ttsVoice free-text+presets field only for
               TTS-capable types.
               Tests: (a) setSttSlot persists via SlotMappingStore and observeSttMapping
               re-resolves the profile from a fresh repository over the same store
               (restart-survival, c-1); (b) an Ollama/Anthropic profile is excluded from the
               STT candidate list (filtering); (c) deleting a mapped profile clears its STT/TTS
               mapping (the existing deleteProfile cascade over CapabilitySlot.entries now
               covers STT/TTS). Fails on lost persistence, unfiltered picker, or stale mapping.
```

### Wave 4 (depends t-11)

```
t-12 Resolve remote-vs-system engine per mapping (pure selector)        [mvp + verification]
     files:    app/src/main/java/dev/equerry/app/voice/VoiceEngineSelector.kt
               app/src/main/java/dev/equerry/app/voice/VoiceComponentsEntryPoint.kt
               app/src/main/java/dev/equerry/app/ui/chat/ChatScreen.kt
               app/src/main/java/dev/equerry/app/assistant/EquerryVoiceInteractionSession.kt
               app/src/test/java/dev/equerry/app/voice/VoiceEngineSelectorTest.kt
     covers:   c-3, c-4, c-5
     depends:  t-7, t-8, t-11
     contract: VoiceEngineSelector resolves the SpeechToText/TextToSpeech a turn uses: a mapped
               STT/TTS profile yields the Remote impl built around that profile + its key (via
               keyFor); an unmapped slot yields the System impl. Both construction sites
               (ChatRoute/ChatScreen, EquerryVoiceInteractionSession) build their engines through
               the selector instead of hardcoding SystemSpeechToText/SystemTextToSpeech.
               VoiceEngineSelectorTest asserts: null mapping → System impl (c-5 no-regression,
               by capturing which factory ran AND that the Remote impl is never constructed);
               mapped profile → Remote impl. A mapped-STT path asserts the spoken transcript
               reaches chat.send via the remote transcript, not system STT.
```

```
t-13 Consented system-engine fallback in VoiceFlowController            [verification + mvp + risk]
     files:    app/src/main/java/dev/equerry/app/voice/VoiceFlowController.kt
               app/src/test/java/dev/equerry/app/voice/VoiceFlowFailureTest.kt
     covers:   c-6
     depends:  t-12
     contract: on a remote STT/TTS Error / init-Failed mid-turn, set guidance via
               VoiceGuidanceFactory.remoteEngineFailed (t-6) carrying a retryWithSystem action,
               and expose retryWithSystem() on the controller that re-runs the current turn on
               the System impl ONLY after the user invokes it. Never auto-switch (locked
               failover_consented). VoiceFlowFailureTest, driving a remote engine that errors,
               asserts: (a) one failure guidance is shown; (b) the system-engine spy is NOT
               invoked until retryWithSystem() fires; (c) it IS invoked after it fires.
               Fails on any silent fallback (a system call recorded without consent) or a
               missing retry action.
```

> Wave count is 4 (skeleton's structure), but t-9 (Media3) is pulled into Wave 2 and the
> three slot/picker/profile-UI fragments are consolidated into one Wave-3 task (t-11), so the
> total is 13 tasks, not the skeleton's 12. Coverage stays 6/6.

### Coverage

```
c-1  (config+map STT, survives restart)         → t-1, t-2, t-11
c-2  (config+map TTS, survives restart)         → t-1, t-2, t-10, t-11
c-3  (spoken utterance via remote STT)          → t-3, t-5, t-7, t-12
c-4  (CHAT reply spoken via remote TTS)         → t-4, t-8, t-9, t-10, t-12
c-5  (unmapped → system engines, no regression) → t-11, t-12
c-6  (remote failure → guidance, no key leak)   → t-3, t-4, t-6, t-7, t-8, t-13
```

## Disagreements

### D-1 — Where does engine selection live: a separate selector, or inside the controller?
- **risk** rejects a selector: it folds "which engine runs" *and* "never switch without
  consent" into one Wave-3 task (t-10) inside `VoiceFlowController`, arguing the
  silent-fallback privacy risk lives entirely in the selection logic and splitting it
  un-owns the rule.
- **mvp** and **verification** both extract a pure `VoiceEngineSelector` (their t-5 / t-11)
  *separate* from the consented-fallback task, arguing the no-regression unmapped→system
  path (c-5) must be a unit assertion on a single decision point, not duplicated UI glue.
- **Provisional default: split (selector t-12 + controller fallback t-13).** The real
  `VoiceFlowController` takes `stt`/`tts` as constructor params and never picks an engine
  itself — so selection genuinely happens at construction, and the two construction sites
  (ChatScreen, EquerryVoiceInteractionSession) both need the same decision. A pure selector
  makes c-5 a JVM unit test; folding it into the controller would force c-5 to ride on a
  fatter controller test. **Why it matters:** if the merge instead followed risk and put
  selection in the controller, the no-regression guarantee (c-5) would have no isolated test
  and both wiring sites would risk drifting. The risk lens's actual concern — that the
  consent rule stays owned — is preserved: t-13 solely owns "never switch without consent."

### D-2 — One slot/profile-UI task, or split data from UI (and split the voice field out)?
- **risk** maximally splits: separate tasks for repo+slots persistence (t-8), the ttsVoice
  profile field (t-9), with picker filtering folded into t-8.
- **mvp** splits data (t-1) from UI (t-4) on the >2-layer rule but keeps each cohesive.
- **verification** splits the back-compat ttsVoice store concern (t-9) from the
  repo-mapping task (t-10), and *under-specifies* the picker-filtering + voice-field-in-form
  UI surface (it lives implicitly across t-1/t-9 with no dedicated UI assertion).
- **Provisional default: ttsVoice store/back-compat stays its own task (t-10, from
  verification/risk); repo mappings + filtered pickers + voice-in-form consolidate into one
  Wave-3 task (t-11, from mvp).** The back-compat-decode risk is high-value and deserves its
  own focused `ProfileStoreTest` (verification/risk agree). But the repo mirror-methods, the
  `SlotsViewModel` STT/TTS routing, and the form's TTS-only voice field are all thin
  mirror-adds on proven CHAT/VISION machinery; three sliver tasks don't earn their place.
  **Why it matters:** keeping the decode-old-JSON contract isolated means a back-compat
  regression fails a tiny named test, not a fat UI test — while still surfacing the picker
  *filtering* assertion that verification had no home for (the c-1/c-5 "Ollama excluded from
  STT candidates" test now has an owner).

### D-3 — Media3 gradle dependency: its own task, or absorbed into the TTS impl?
- **risk** makes it a standalone Wave-2 task (t-7) gated by a compile contract, so a version
  miss fails a clearly-attributed compile step, not a masquerading TTS logic test.
- **mvp** and **verification** absorb the dependency into the RemoteTextToSpeech work and
  keep Media3 at the seam edge with no separate build task.
- **Provisional default: standalone task (t-9), adopting risk's reasoning.** A build-catalog
  edit and a Kotlin logic change have different failure surfaces and different reviewers'
  attention; risk's compile-contract framing is the cleanest. **Ordering fix over risk:**
  risk placed t-7 *after* its consumer t-6 in the same wave; here t-9 carries no dependency
  on t-8 and is ordered to land first within Wave 2, so the production `AudioPlayer` impl in
  t-8 compiles against an already-present catalog entry. **Why it matters:** mis-ordering the
  dependency behind its consumer (risk's original) means t-8's production code can't compile
  until t-9 lands, inverting the intended gate.

### D-4 — File placement: a new `voice/remote/` package, or flat alongside existing peers?
- **risk** puts every new audio file under a new `app/.../voice/remote/` subdirectory
  (AudioError, AudioCapture, RemoteSpeechToText, AudioPlayer, …).
- **mvp** and **verification** keep new files flat in `voice/` and `providers/drivers/`,
  matching where `SystemSpeechToText`, `SystemTextToSpeech`, and `ChatRequestBuilder` already
  live.
- **Provisional default: flat placement (mvp/verification).** The `voice/remote/` directory
  does not exist in the tree, and the established convention is flat — request builders in
  `providers/drivers/` next to `ChatRequestBuilder`, engine impls in `voice/` next to the
  System impls. **Why it matters:** adopting risk's package would split the STT/TTS family
  across two locations and break the "remote impl sits beside its system twin" symmetry the
  selector (t-12) and tests rely on for discoverability.
