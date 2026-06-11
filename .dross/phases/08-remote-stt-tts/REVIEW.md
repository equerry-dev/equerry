# Plan Review — 08-remote-stt-tts

Reviewed: 2026-06-11
Plan: 13 tasks across 4 waves

## BLOCKING
(none)

All six criteria are covered, no task contradicts a locked decision, and no task
implies a rules.toml violation. Coverage matrix:

- c-1 → t-1, t-2, t-11
- c-2 → t-1, t-2, t-10, t-11
- c-3 → t-3, t-5, t-7, t-12
- c-4 → t-4, t-8, t-9, t-10, t-12
- c-5 → t-11, t-12 (also asserted in t-8)
- c-6 → t-3, t-4, t-6, t-7, t-8, t-13

## FLAG

- [wave-order] t-10 declares `depends_on = ["t-1"]`, but t-10 only adds an optional
  `ttsVoice: String?` to ProviderProfile / ProfileDraft / StoredProfile — it touches none
  of the `supportsStt/supportsTts` flags that t-1 adds to ProviderType. The dependency is
  spurious. t-10 is otherwise a wave-2 task whose only real dependency could be dropped,
  letting it run in wave 1 alongside the other pure/data tasks.
  Suggestion: Drop the `depends_on = ["t-1"]` and consider moving t-10 to wave 1 for
  parallelism; if there is a genuine ordering reason (e.g. a shared test fixture), state it.

- [locked-decisions / test-contract] t-7's contract maps a transport 401 to
  `SttError.Unavailable` and "the flow completes". The existing `VoiceFlowController.start()`
  routes any `SttEvent.Error` straight to `VoiceGuidanceFactory.sttError(...)`, which has NO
  retry-with-system action. The locked `failover_consented` decision requires an explicit
  consented "retry with system engine" surface on remote failure. t-13 is supposed to add
  that, but t-13 keys off "a remote STT/TTS Error or init-Failed" — yet t-7 collapses the
  remote 401 into the generic `SttError.Unavailable`, which is indistinguishable from a
  genuine system-STT unavailability. If the controller can't tell "remote provider failed"
  from "system recognizer unavailable", it can't decide whether to show the consented-retry
  surface (remote) vs the plain sttError surface (system).
  Suggestion: Have t-7 emit a *remote-distinguishable* error (a dedicated remote error type,
  or carry the `RemoteAudioError` from t-6 through the event) so t-13 can branch on it. Make
  the t-7 ↔ t-13 contract about *which* surface fires explicit, not just "completes without
  hanging."

- [granularity / squash] t-11 touches 6 files spanning 3 layers — repository
  (ProviderRepository), view-model/state (SlotsViewModel), and UI (SlotsScreen +
  ProviderEditViewModel) — and bundles three distinct concerns: (a) STT/TTS slot
  mapping + restart survival, (b) picker filtering by capability flag, (c) the optional
  ttsVoice edit-form field. It is the largest task in the plan and a clear split candidate.
  Suggestion: Consider splitting into a repository+viewmodel mapping task (covers c-1/c-2
  restart survival + filtering) and a UI task (picker sheets + ttsVoice form field). The
  restart-survival contract and the form-field contract are independently verifiable.

- [granularity / merge] t-1 and t-2 are both wave-1, both `covers = ["c-1","c-2"]`, both
  edit the same test file (ProviderTypeTest.kt), and each is a sub-10-minute one-or-two-line
  change (add two computed getters; flip two `active` booleans + narrow one assertion). They
  are an artificial split.
  Suggestion: Merge t-1 and t-2 into a single "activate STT/TTS capability + provider flags"
  task. They share a test file and a wave with no dependency between them.

- [test-contract] t-9's contract — "if the androidx.media3 dependency is missing or
  misversioned, `:app:compileDebugKotlin` fails to resolve the media3 imports" — describes a
  compile failure of *downstream* code (t-8's production player), not a property of the
  dependency-add task itself. At the point t-9 lands, nothing imports media3 yet, so the
  contract can't be exercised by t-9 in isolation; it only "fails" once t-8 exists.
  Suggestion: Either fold the media3 dependency add into t-8 (the only consumer), or give t-9
  a self-contained verification (e.g. a trivial throwaway reference or a catalog-presence
  assertion). As written it is a "configure Y" task whose gate lives in another task.

## NOTE

- [strength] The r-03 (never-log-keys) discipline is threaded through every surface that
  could leak: t-3/t-4 assert the key reaches only the Authorization header and never the
  path/query/body; t-6 asserts a 401 body carrying a fake secret never appears in the
  guidance string. This is exactly the right place to enforce the hard rule — at the pure
  builder and the error-mapper — and the contracts name the specific substring assertion that
  breaks. Strong, specific, and aligned with the existing KeyRedaction pattern.

- [strength] The plan correctly preserves the locked seam contracts: t-7 asserts
  `listen()` emits exactly `[EndOfSpeech, Final]` with zero `Partial` (matching the
  `stt_capture_and_contract` decision and the existing SttEvent shape), and t-8 asserts
  `awaitDone()` resumes only after the player drains, explicitly tying it to the mic-regate
  that stops STT capturing Equerry's own speech. These contracts name the real failure
  surface, not "tests pass."

- [strength] t-10 mirrors the proven back-compat pattern already in ProfileStore
  (nullable-default field + `ignoreUnknownKeys`) and its contract pins the exact risk:
  legacy JSON without the field still decodes to `ttsVoice == null` without throwing. This
  is the correct, verified-against-existing-code approach the granularity FLAGs elsewhere
  don't undermine.

- [observation] t-13's contract is a model of specificity for a consent gate: "the
  system-engine spy is NOT invoked until retryWithSystem() fires" and "any silent fallback
  (a system call recorded without consent) fails the test" directly encode the locked
  `failover_consented` "never silently switch" requirement as an assertion. Good. (See the
  FLAG above re: making the *remote-vs-system* error distinction reach this branch.)

- [observation] Both `VoiceFlowController` construction sites (ChatScreen.kt and
  EquerryVoiceInteractionSession.kt) currently hard-wire `SystemSpeechToText.fromContext` /
  `SystemTextToSpeech.fromContext`. t-12 lists both sites in `files`, so the rewire is
  accounted for — but note the controller today takes a single `stt`/`tts` at construction,
  while the spec needs per-turn resolution (mapping can change; consented fallback re-runs the
  turn on the system engine). The plan assigns this to t-12 (selector) + t-13 (controller),
  which is the right division; just confirm during execution that the controller ends up able
  to hold/resolve both engines rather than one frozen at construction.

## Summary
A genuinely solid, well-sequenced plan with excellent r-03 and seam-contract discipline; no
blockers, but tighten the t-1/t-2 merge, the t-11 split, the spurious t-10 dependency, and —
most importantly — make the remote-vs-system STT error distinction explicit so the locked
consented-fallback (t-13) can actually branch on it.
