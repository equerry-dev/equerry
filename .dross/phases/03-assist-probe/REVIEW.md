# Plan Review — 03-assist-probe

Reviewed: 2026-06-10
Plan: 7 tasks across 3 waves

## BLOCKING
(none)

## FLAG
- [wave-order] t-2 and t-4 are both declared `wave = 1` yet each has
  `depends_on = ["t-1"]`, and t-1 is also `wave = 1`. A task cannot run in the same
  wave as a dependency it consumes — both need t-1's `ProbeRecord` type. As written
  the `wave` field contradicts the dependency graph (the whole of wave 1 cannot run
  in parallel).
  Suggestion: Move t-2 and t-4 to wave 2 and cascade the rest (current wave-2
  t-3/t-5/t-7 → wave 3, t-6 → wave 4), or drop wave numbers and let `depends_on`
  drive ordering. Pick one ordering model and keep it internally consistent.

- [locked-decisions] export_format is locked as "Export the results table as CSV
  **via the Android share-sheet**." t-4 produces the CSV string and t-3 exposes
  `exportCsv()`, but no task owns the share-sheet (`Intent.ACTION_SEND` /
  `ShareCompat`) wiring. t-5 only renders an "Export CSV" affordance and its
  contract asserts the affordance is *displayed*, not that it fires a share intent.
  The share-sheet half of the locked decision is unimplemented.
  Suggestion: Assign the share-sheet launch (intent construction + invocation from
  the Export affordance) explicitly to t-5 or t-3, with a contract asserting the
  intent is built with the CSV payload and correct MIME type.

- [granularity] t-6 touches 6 files, spans 3 layers (service/session classes + 3
  manifest/XML resources + strings + viewmodel/screen wire-up) and covers 4
  criteria (c-1..c-4). It bundles "make the app a selectable assistant" (manifest +
  VIS registration, c-1) with "session feeds the analyzer and launches the screen"
  (c-2/c-3/c-4) — two separable concerns with different verify paths.
  Suggestion: Consider splitting into (a) VoiceInteractionService/Session
  registration + manifest/XML (c-1, mostly manifest-merge + manual) and (b) session
  onHandleAssist/onHandleScreenshot wiring into analyzer/viewmodel/screen
  (c-2/c-3/c-4, has unit-testable surface).

- [test-contract] t-6's only automated contract for its core capture path is
  "extend AssistAnalyzerTest with the ViewNode -> ViewNodeLike adapter case." The
  session callbacks (`onHandleAssist`/`onHandleScreenshot`) feeding the viewmodel
  are not unit-asserted; c-2/c-3/c-4 for the live session path rest on the two
  MANUAL contracts. The adapter test verifies tree conversion but not that the
  session wires that output into ProbeSessionViewModel.
  Suggestion: Add a contract that the session's capture handler (or an extracted
  testable capture function) builds a ProbeRecord from a stub structure/screenshot
  and pushes it to the viewmodel — so the wiring, not just the adapter, has a gate.

## NOTE
- [strengths] screenshot_retention is enforced structurally, not by convention:
  t-1's `ProbeRecord` has no bitmap/byte[] field and `screenshotMeta` takes no
  Bitmap, with the contract stating "pixels can't leak past it." Enforcing a
  privacy decision at the type level is the right move for a privacy-is-the-product
  app.

- [strengths] Test contracts are specific and name the failure mode rather than
  asserting "tests pass": t-2's "read-then-write outside the edit block drops
  writes," t-4's RFC-4180 quote-doubling round-trip, t-1's recursion / 4-level
  nesting case.

- [strengths] t-3's exportCsv contract deliberately delegates to the real
  `ProbeCsv` ("so a serializer regression is caught here rather than stubbed away"),
  avoiding the common trap of mocking the collaborator and testing nothing.

- [coverage] All five criteria covered: c-1→t-6; c-2→t-5,t-6; c-3→t-1,t-3,t-6;
  c-4→t-1,t-3,t-6; c-5→t-2,t-3,t-4,t-5,t-7. No gaps.

- [forbidden-actions] No rule violations. r-01 (no AccessibilityService) is
  respected — the plan uses VoiceInteractionService/Assist API only. r-02/r-03 are
  not engaged (no side-effecting actions, no keys). runtime.mode is "native" with
  gradle commands; no pnpm/docker concern. Global rules.toml does not exist.

- [verified] All referenced existing files exist: MainActivity.kt (has `Route` +
  `EquerryNavHost` that t-7 extends), NavigationTest.kt (t-7's contract matches its
  TestNavHostController pattern), PersistenceModule.kt (t-2 extends it, mirroring
  the ProfileStore/SlotMappingStore provider pattern), ProfileStore.kt (the pattern
  t-2 mirrors), strings.xml, and AndroidManifest.xml (carries a TODO placeholder
  exactly where t-6 registers the VIS). ProviderListScreenTest (t-5's mirror)
  exists. New files are each created by their own task before downstream use.

- [granularity] t-1 bundles ProbeRecord + AssistAnalyzer in one task — fine, not an
  inflated split; the record is a trivial data class and the analyzer is small.

## Summary
Solid, specific plan with strong type-level privacy enforcement and concrete
contracts; the fixable gaps are the wave/dependency inconsistency, an unowned
share-sheet step that a locked decision requires, and t-6 being an over-stuffed
multi-layer task with thin automated coverage of its session-wiring path.
