# Plan Review — 05-voice-roundtrip

Reviewed: 2026-06-10
Plan: 11 tasks across 4 waves

## BLOCKING
- [wave-order] t-7 (wave 2) declares `depends_on = ["t-6"]`, but t-6 is also wave 2.
  A task may not depend on another task in the same wave — waves are the parallel-execution
  unit, so a same-wave dependency means t-7 can start before t-6's `SpeakChunker.kt` exists,
  and its test_contract line 3 ("SENTENCE_BY_SENTENCE each completed sentence is queued...
  matching t-6 output") references t-6's behaviour directly. t-7 cannot be authored in
  parallel with t-6.
  Suggestion: move t-7 to wave 3 (with t-8/t-11 shifted to wave 4 and t-10 to wave 5), or
  move t-6 to wave 1 (it only depends on t-4, which is wave 1 — see FLAG below) so t-7's
  wave-2 placement becomes valid.

## FLAG
- [wave-order] t-6 `depends_on = ["t-4"]` only, and t-4 is wave 1. Nothing else t-6 needs is
  produced in wave 2, so t-6 has no reason to sit in wave 2 — it could drop to wave 1 (it is a
  pure function over the speak_timing enum; it does not need t-4's runtime store, only the
  enum type, which t-4 defines). Dropping it to wave 1 also resolves the BLOCKING same-wave
  dependency for t-7.
  Suggestion: move t-6 to wave 1. If the enum type genuinely lives in t-4's file, keep the
  t-4 dependency but it is still a wave-1→wave-1 edge, which is fine only if t-4 is treated as
  producing the type before t-6 in the same wave; cleaner to extract the enums where t-6 can
  reach them.

- [granularity] t-8 and t-11 are both wave 3 and both edit the SAME file
  `app/src/main/java/dev/equerry/app/voice/VoiceFlowController.kt`. Their own descriptions
  admit this ("Edits VoiceFlowController.kt after t-7... sequential, same file as t-8" / "same
  file as t-11"). Two tasks editing one file in the same wave cannot run in parallel without a
  merge collision — they are implicitly sequential, which contradicts the wave-3 placement.
  Suggestion: either sequence them across waves (t-8 wave 3, t-11 wave 4) or merge the
  concurrency-safety and failure-routing edits into one VoiceFlowController hardening task with
  two test files. The current shape claims parallelism the file boundary won't allow.

- [granularity] t-9 spans 3 layers in 4 files (data/settings store consumption via VM +
  Compose UI screen + MainActivity navigation route wiring) AND folds in the mic-permission
  settings-path grant. The MainActivity VOICE-route addition is unrelated to the
  settings-store VM and could be split or folded into t-10's MainActivity touch.
  Suggestion: consider splitting the MainActivity route/Home-entry wiring out, or confirm the
  4-file/3-layer scope is intentional for one commit.

- [test-contract] t-10 line 1 is "Framework session verified MANUALLY (documented in contract
  comment...)". This is acceptable for the genuinely framework-bound `VoiceInteractionSession`
  (consistent with the existing session's "verified manually" convention), but as written it
  is a vague contract — it names no automated surface that breaks. The other two lines on t-10
  are specific (good); the manual line should at minimum assert what the comment must contain.
  Suggestion: keep the manual line but tie c-1's automated guarantee to the
  "permission-checked-before-arm" assertion already present, and ensure the criteria coverage
  for c-1/c-3 does not rest on the manual line alone.

- [antipattern: missing-file] t-10 references `ChatRoute`/`ChatScreen` (confirmed present) and
  switches the session content from `ProbeSessionScreen` to it — fine. But t-10 also says
  "pull VoiceFlowController/ChatViewModel via a new @EntryPoint". `ChatViewModel` is a
  `@HiltViewModel` (confirmed), not a singleton — exposing it through a `SingletonComponent`
  EntryPoint the way `ProbeStore` is exposed will not work (a HiltViewModel isn't a singleton
  binding). The plan asserts "same pattern as ProbeStoreEntryPoint" but ChatViewModel's scope
  differs from ProbeStore's.
  Suggestion: clarify how ChatViewModel is obtained in the session (ViewModelStoreOwner /
  ViewModelProvider against the session's own `ViewModelStore`, which it already implements),
  not via a SingletonComponent EntryPoint.

## NOTE
- [coverage] All five criteria (c-1..c-5) appear in at least one task's `covers`. Coverage is
  complete; c-5 in particular is covered defensively across t-1/t-2/t-3/t-5/t-9/t-11/t-10.
- [strength] Locked-decision fidelity is strong: session_ui_reuse (t-10 ChatScreen swap + Probe
  stays on its log route), turn_control (t-7 auto-stop/auto-send, t-8 continuous re-arm,
  no tap-to-stop), speak_timing (t-4 setting + t-6 chunker), and mic_permission (t-1's single
  Denied guidance, asserted byte-identical across entry points in t-1 and reused in t-9) are
  each mapped to concrete tasks. No locked-decision conflicts found.
- [strength] Test contracts are unusually specific where it matters — STT error-code mapping
  (t-2), TTS init/no-op safety (t-3), SpeakChunker boundary behaviour with literal expected
  output (t-6), and the failure-routing matrix (t-11) each name the exact surface that breaks.
- [strength] Failure isolation is well-factored: concurrency races (t-8) and failure routing
  (t-11) are kept as their own tasks rather than buried in t-7, and STT/TTS error mapping is
  unit-tested at the JVM seam rather than left to manual-only verification.
- [forbidden-actions] No rule violation. No AccessibilityService (r-01) — STT uses
  SpeechRecognizer, not Accessibility. No side-effecting auto-actions (r-02) — the voice flow
  only renders/speaks replies. Key redaction (r-03) is explicitly preserved: t-5 reuses the
  phase-04 key-redaction path verbatim and t-11 asserts "key-free error shown".

## Summary
Coverage, locked-decision fidelity, and forbidding-rule compliance are clean, but the wave graph
has one blocking same-wave dependency (t-7→t-6) plus same-file parallelism conflicts
(t-8/t-11 on VoiceFlowController.kt) and a likely-incorrect EntryPoint plan for the HiltViewModel
in t-10 — fix the wave ordering and clarify t-10's ViewModel acquisition before proceeding.

## Re-review (2026-06-10)

Re-reviewed the amended plan independently (now 11 tasks across 6 waves; prior was 4 waves).

### Prior BLOCKING — RESOLVED
- t-7 was wave 2 depending on t-6 (also wave 2). Amended: t-7 is now **wave 3**, t-6 stays
  **wave 2**. The edge t-7→t-6 is now wave-3→wave-2 (strictly lower). Resolved.

### Full edge re-audit (every depends_on points strictly lower) — all valid
- t-1..t-5: wave 1, no deps.
- t-6 (w2) → [t-4 w1] ✓
- t-9 (w2) → [t-1 w1, t-4 w1] ✓
- t-7 (w3) → [t-1,t-2,t-3,t-4 (w1), t-5 (w1), t-6 (w2)] — all ≤ w2 ✓
- t-8 (w4) → [t-7 w3] ✓
- t-11 (w5) → [t-7 w3, t-8 w4] ✓
- t-10 (w6) → [t-7 w3, t-8 w4, t-11 w5] ✓
No same-wave or higher-wave dependency remains.

### Same-file parallelism — RESOLVED
- VoiceFlowController.kt is touched by t-7 (w3, creates), t-8 (w4), t-11 (w5) — now three
  distinct waves with real edges (t-8→t-7, t-11→t-7+t-8). The prior t-8/t-11 same-wave
  collision is gone. t-10 (w6) does NOT edit VoiceFlowController.kt (it edits
  EquerryVoiceInteractionSession.kt), so no late collision either.
- Other waves checked: wave-1 files all distinct; wave-2 t-6 (SpeakChunker.kt) vs t-9
  (VoiceSettingsScreen/VM + MainActivity) — no overlap. No new same-file-same-wave conflict.

### t-10 DI mechanism — RESOLVED
- Amended t-10 now explicitly states ChatViewModel is obtained "NOT via a SingletonComponent
  EntryPoint (it is @HiltViewModel, not a singleton binding) but through a ViewModelProvider
  backed by the session's own ViewModelStore ... using Hilt's ViewModel factory." The wrong
  mechanism asserted in the prior review is corrected. VoiceFlowController (@Singleton) is
  still pulled via @EntryPoint, which is the correct scope for it.

### Regression check — clean
- Coverage: c-1 (t-1/t-4/t-7/t-9/t-10), c-2 (t-2/t-7), c-3 (t-5/t-7/t-10),
  c-4 (t-3/t-4/t-6/t-7/t-9), c-5 (t-1/t-2/t-3/t-5/t-9/t-11/t-10). All c-1..c-5 still covered.
- Locked decisions all still mapped: session_ui_reuse (t-10), turn_control (t-7/t-8),
  speak_timing (t-4/t-6/t-7), mic_permission (t-1/t-9). No conflict introduced.
- Rules: r-01 (no Accessibility — STT via SpeechRecognizer), r-02 (no side-effecting
  auto-actions), r-03 (key redaction preserved in t-5/t-11) all intact.

### NEW issues introduced by the amendment
- None. The wave re-numbering (collapsing nothing, only splitting t-7/t-8/t-11/t-10 across
  waves 3–6) introduced no new edges that violate the lower-wave rule and no new same-file
  collisions.

### Verdict
Prior BLOCKING resolved, no new blocking/flags — plan is wave-graph-clean and cleared to proceed.
