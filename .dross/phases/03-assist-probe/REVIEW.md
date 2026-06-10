# Plan Review — 03-assist-probe

Reviewed: 2026-06-10
Plan: 5 tasks across 3 waves

## BLOCKING
(none)

All five criteria are covered (c-1→t-3, c-2→t-4, c-3→t-2/t-3, c-4→t-2/t-3,
c-5→t-1/t-2/t-5). No task contradicts a locked decision. No rule violations:
runtime.mode is "native" so gradle commands are correct, and the assistant
role is built on the sanctioned Assist API / VoiceInteractionService, not an
AccessibilityService (r-01 respected).

## FLAG
- [granularity] t-3 touches 7 files and spans 3 layers (service registration +
  manifest/res-xml plumbing + JVM-testable ProbeRecorder capture logic). The
  framework-binding plumbing (the three services, two res/xml files, manifest)
  and the testable ProbeRecorder are separable concerns with different verify
  paths — the former is MANUAL-only, the latter has a real unit contract.
  Suggestion: consider splitting t-3 into "role registration + manifest/xml"
  (manual-verified, no unit test) and "ProbeRecorder capture glue" (the
  ProbeRecorderTest contract). The split also lets t-4 depend only on the
  registration half.

- [wave-order] t-4 declares depends_on = ["t-3", "t-2"], but t-4's actual work
  is the Compose dashboard in the session window. Its stated need on t-3 is the
  EquerryVoiceInteractionSession host. However t-4 ALSO edits
  EquerryVoiceInteractionSession.kt — the same file t-3 creates — so the two
  tasks write the same file in different waves. That is a real ordering need
  (t-4 must follow t-3), but it signals the session class is being built across
  two tasks. Suggestion: confirm t-3 leaves a clear seam (e.g. an empty
  onCreateContentView / setContentView hook) for t-4 to fill, or fold the
  dashboard hosting into t-3 and keep ProbeDashboard.kt as the only t-4 file.

- [wave-order] t-5 depends_on = ["t-1", "t-2"] only — it needs the store and the
  CSV renderer, neither of which is in wave 2. t-5 has no dependency on t-3 or
  t-4, so it could run in wave 2 alongside t-3 for more parallelism instead of
  sitting in wave 3. Suggestion: move t-5 to wave 2 (its deps t-1/t-2 are both
  wave 1), shrinking the critical path.

- [test-contract] t-3's first contract ("ProbeRecorder.record(...) persists
  exactly one ProbeRecord whose nodeCount == summary count and screenshot
  fields == meta") is good, but the MANUAL line is the only coverage for c-1
  (appears in picker / becomes active assistant). c-1 therefore has zero
  automated verification. That is inherent to the OS-bound role and acceptable,
  but flag it so verify-time treats c-1 as a manual gate, not a passed unit
  test. Suggestion: ensure the verify step explicitly records the manual c-1
  check rather than inferring it from t-3 passing.

## NOTE
- [strengths] Screenshot-retention discipline is carried correctly through the
  whole chain: t-1 ("Never stores a bitmap — screenshot is fact + dimensions
  only") and t-2 (blocked marker / dimensions only) both honor the locked
  screenshot_retention decision and the privacy core_value. Good fidelity to a
  hard constraint.

- [strengths] The pure-helpers/glue separation (t-2 extracts node counting, CSV
  rendering, and screenshot meta as JVM-testable units behind an AssistNode
  abstraction) is the right shape for an Android phase — it pulls real logic out
  of the framework-bound services so c-3/c-4/c-5 get genuine unit contracts
  instead of leaning on instrumentation.

- [strengths] Plan correctly mirrors the existing ProfileStore pattern (verified
  in app/.../data/ProfileStore.kt) and reuses the already-provided
  PersistenceModule DataStore — no new dependency, matching the locked
  probe_persistence rationale. All referenced existing files (ProfileStore,
  PersistenceModule, MainActivity, AndroidManifest) exist in the repo; the
  manifest even carries a TODO marker exactly where t-3 will register the
  services.

## Summary
Solid, well-decomposed plan with full criteria coverage and no blockers; the
main improvements are splitting the overloaded t-3, pulling t-5 forward to
wave 2, and treating the EquerryVoiceInteractionSession.kt shared edit between
t-3 and t-4 as a deliberate seam.
