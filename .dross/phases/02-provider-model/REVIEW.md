# Plan Review — 02-provider-model

Reviewed: 2026-06-09
Plan: 10 tasks across 6 waves

## BLOCKING
(none)

## FLAG
- [coverage / locked-decision] The `model_input` locked decision requires "a free-text field
  with a dropdown of hardcoded common model ids per provider type", and c-1 requires the
  "type-appropriate ... model fields" to be present. t-1 carries `modelPresets` in
  ProviderType metadata, but NO task's test_contract asserts the model input/dropdown is
  actually rendered or that presets reach the UI. t-8's contract only checks the key-field
  show/hide and the inline error; t-6's only checks base-URL prefill + key gating. The model
  field could be silently dropped and every test would still pass.
  Suggestion: have t-6 and/or t-8 add a contract clause asserting the model field renders with
  the per-type presets available (e.g. ANTHROPIC drafts surface its preset model ids).

- [test-specificity] t-5 contract line 3 — "observeProfiles() emits the updated list after
  addProfile, updateProfile, and deleteProfile" — is the weakest of the three; it names the
  surface (observeProfiles) but "emits the updated list" doesn't pin what would break (order?
  identity? the mutated field on update?). The other two lines in t-5 are sharp; this one is
  soft by comparison.
  Suggestion: name the observable property that must change per op (e.g. updateProfile's new
  label is reflected; deleteProfile's id is absent).

- [granularity / wave-order] t-2 (Gradle deps + ToolchainSmokeTest) is a deliberate
  single-owner build-file task in wave 1, and t-1 (pure-Kotlin domain, no Android deps) is
  also wave 1. t-1 does not depend on t-2 — correct, they're parallel. But t-4 depends on
  [t-1, t-2] and t-3 depends on [t-2] only. The wave assignment is sound; flagging only that
  t-2 is effectively a "set up dependencies" task. It is rescued from the usual antipattern by
  naming concrete files (libs.versions.toml, app/build.gradle.kts) and a real
  ToolchainSmokeTest gate, so this is a soft flag, not blocking.
  Suggestion: none required — noting the shape so future plans keep the concrete-file + smoke
  -test rescue.

- [granularity] t-1 touches 6 files and spans two concerns (domain enums/model + validator)
  but they are one cohesive pure-Kotlin layer with paired tests; t-4 touches 5 files
  (ProfileStore, SlotMappingStore, PersistenceModule + 2 tests). Both sit at the 5+ file
  flag threshold. Neither is a true split candidate — the files are tightly coupled and share
  a test surface — but they are at the granularity ceiling.
  Suggestion: leave as-is; recorded for threshold awareness.

## NOTE
- [forbidden-actions] No rules.toml violation. runtime.mode is "native" and every task uses
  Kotlin/Gradle; the r-03 secret-storage rule is actively honored by t-3 (EncryptedSecretStore)
  and t-5 (key-split, no-plaintext-in-profile assertion). r-01 (no AccessibilityService) and
  r-02 (confirm side-effects) are out of scope for this phase. Global rules file
  (~/.claude/dross/rules.toml) does not exist; only project rules apply.

- [strengths] The key-split architecture is enforced in tests, not just prose: ProviderProfile
  is declared with "deliberately NO key field" (t-1), and t-4/t-5 contracts assert the key
  string never appears in the ProfileStore payload. This makes c-2 / r-03 a test-gated
  invariant rather than a hope.

- [strengths] t-3 names the real risk (security-crypto's Tink/Keystore path may not run under
  Robolectric) and pre-specifies a concrete fallback (connected androidTest for the real impl,
  JVM gate keeps the contract + no-plaintext guarantee). That is honest risk-surfacing rather
  than an optimistic "tests pass".

- [strengths] Wave structure is clean: domain (w1) -> persistence (w2) -> repository (w3) ->
  viewmodels (w4) -> compose screens (w5) -> navigation (w6), with explicit depends_on edges
  that match the data-flow dependencies. t-6/t-7 and t-8/t-9 correctly parallelize within
  their waves. No task sits in a later wave than its dependencies require.

## Summary
Solid, test-gated plan with full criteria coverage and honest risk-handling; the one substantive
gap is that the locked model-dropdown/preset behaviour (c-1 / model_input decision) is never
asserted by any UI test and could regress silently.
