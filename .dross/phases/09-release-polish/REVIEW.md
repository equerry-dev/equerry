# Plan Review — 09-release-polish

Reviewed: 2026-06-11
Plan: 9 tasks across 3 waves

## BLOCKING
(none)

## FLAG
- [granularity / same-file parallel edits] t-3 and t-4 are both wave 1 and both edit
  `app/build.gradle.kts` (t-3 hardens the release buildType; t-4 adds the release
  signingConfig). With no depends_on between them they are scheduled to run in
  parallel, so two concurrent tasks will be editing the same file — guaranteed
  merge/clobber risk under the atomic-commit-per-task model.
  Suggestion: serialize them (give t-4 `depends_on = ["t-3"]`, or vice versa), or
  merge them into one "release build hardening + signing" task — both touch the same
  buildTypes block and cover the same criterion (c-6).

- [antipattern / same-file parallel edits] t-8 and t-9 are both wave 3 and both
  depend only on t-6, and both edit `app/src/main/java/.../ui/onboarding/OnboardingScreen.kt`.
  They will run in parallel and collide on that file. t-9 also re-opens
  `OnboardingViewModelTest.kt` (already authored in t-6) and arguably belongs with the
  intent/return-refresh logic that t-6 specs out.
  Suggestion: chain them (`t-9 depends_on = ["t-8"]`) so the screen is edited serially,
  or fold t-9's set-default intent + ON_RESUME refresh into t-8 (it is the same screen
  and the same c-1 concern). As split they inflate task count without buying parallelism.

- [antipattern / shared-test-file coupling] t-5 and t-7 both write to
  `FastlaneMetadataTest.kt` (t-7 "extends" it), which is the only reason t-7 must wait
  for t-5 — the screenshot/graphic PNG files themselves have no dependency on the text
  tree. The dependency is an artifact of squeezing two concerns into one test file.
  Suggestion: acceptable as-is (the shared file makes the wave-2 ordering real), but
  consider a separate `FastlaneAssetsTest.kt` for the file-presence checks so t-7 could
  drop to wave 1 and run alongside t-5.

- [granularity / merge candidate] t-2 (OnboardingStore) is a single ~40-line store
  plus a DI provider and a 2-case test, closely mirroring the existing VoiceSettingsStore.
  It is well under 10 minutes of real work and is consumed only by t-6.
  Suggestion: defensible to keep separate for a clean wave-1 unit, but it is a merge
  candidate into t-6 if task-count trimming is wanted.

## NOTE
- [coverage] All six criteria are covered: c-1 → t-1/t-6/t-8/t-9; c-2 → t-6/t-8;
  c-3 → t-2/t-6/t-8; c-4 → t-5/t-7; c-5 → t-5/t-7; c-6 → t-3/t-4. No gaps.

- [locked-decisions] No conflicts with the four locked decisions. The soft-gate /
  CHAT-mapping completion (completion_gate), the deferrable set-default step
  (onboarding_shape), the single canonical fastlane tree feeding both stores
  (metadata_source), and env-var/gitignored signing with debuggable=false
  (release_signing) are each honored explicitly in the matching tasks.

- [forbidden-actions] No rule violations. runtime.mode is "native" (not docker), so the
  gradle commands are correct; r-03 (no secrets in repo) is actively enforced by t-4's
  test_contract; r-01/r-02 are not in scope for this phase.

- [test-contract quality] Strong, specific contracts throughout — they name the exact
  surface that breaks and the regression each guards (e.g. UNKNOWN tri-state in t-1, the
  missing-key=completed fallback in t-2, premature-complete in t-6, the no-secret grep in
  t-4). The contracts also honestly scope what JVM unit tests cannot prove (t-7 calls out
  bitmaps as not JVM-testable and uses a file-presence proxy). This is above-average rigor.

- [path verification] Referenced existing paths confirmed: RepositoryModule.kt,
  PersistenceModule.kt, build.gradle.kts (release block already present with
  minify/shrink, versionCode=1/versionName=0.1.0 matching t-3's assertions),
  proguard-rules.pro, .gitignore, .github/FUNDING.yml (lists github: equerry-dev +
  liberapay: equerry, matching t-5's donate-link contract), MainActivity.kt (Route object
  + injectable NavHost the t-8 startDestination switch will extend), and the assistant
  component EquerryVoiceInteractionService.kt (manifest-registered, matching t-1/t-3).
  New files (OnboardingStore/ViewModel/Screen, DefaultAssistantDetector, fastlane tree,
  keystore.properties.example) are all created by their own tasks before being consumed.

## Summary
Solid, well-covered plan with notably specific test contracts; the only real issue is
three pairs of parallel-scheduled tasks editing the same file (build.gradle.kts;
OnboardingScreen.kt; FastlaneMetadataTest.kt) — add ordering deps or merge before
running under atomic-per-task commits.
