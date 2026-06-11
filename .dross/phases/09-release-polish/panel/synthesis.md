# Phase 09-release-polish — synthesis

Three drafts were judged: `risk.md` (9 tasks / 3 waves), `mvp.md` (5 tasks / 2 waves),
`verification.md` (11 tasks / 3 waves). I authored none of them. Below: scores, the merged
plan grafted from the strongest skeleton, and the genuine disagreements left un-papered.

Codebase reality checked before scoring:
- `ProviderRepository.observeChatMapping(): Flow<ProviderProfile?>` is real — the completion
  gate seam every draft leans on exists.
- `dev.equerry.app.assistant.EquerryVoiceInteractionService` is the real component the detector
  must compare against (verification names it correctly; risk/mvp leave it implicit).
- `MainActivity.Route` is an `object` with `startDestination = Route.HOME`; no `Route.ONBOARDING`
  yet. All routing tasks must add it.
- Screens live under `ui/` (`ui/slots`, `ui/voicesettings`, `ui/chat`). There is NO top-level
  `onboarding/` package. Verification's `ui/onboarding/` path matches the codebase; risk/mvp's
  top-level `onboarding/` does not — corrected in the merge.
- `app/src/main/java/dev/equerry/app/ui/probe/` is the real debug surface a hygiene check targets.
- `.github/FUNDING.yml` already exists (Liberapay + GitHub Sponsors, locked) — donate links are
  referenced, not authored.

## Scores

Scale: weak / ok / strong.

| Draft | Criteria coverage | Test-contract specificity | Granularity | Wave correctness |
|-------|-------------------|---------------------------|-------------|------------------|
| risk (9t/3w) | strong — all c-1..c-6, each failure mode owned once; tri-state UNKNOWN for c-1 is the sharpest c-1 read | strong — named keys, length caps, the "UNKNOWN not false-true" assertion, the "dismiss-before-CHAT-mapping" regression | strong — tasks stay 1–3 files, single seam each | ok — t-7 (screenshots) gated on t-5 only to avoid co-editing one test file; defensible but conservative |
| mvp (5t/2w) | strong — c-1..c-6 all hit; tightest map but c-1 return-refresh folded into UI task, under-specified | ok — round-trip + cap contracts present, but c-1 return-state refresh and intent action are not asserted; build contract lacks isMinify/version assertions | ok — deliberately coarse; t-3 UI task spans nav + banner + intent (borderline mega-task) | strong — minimal honest 2-wave split, no artificial serialization |
| verification (11t/3w) | strong — c-1..c-6 covered; honestly flags c-2 rests solely on t-8; correct service/route/package names | strong — contract-first (tests before files), fakeable-seam interface split, Robolectric Settings.Secure seeding, proguard keep-rule grep | ok→ over-split — detector interface (t-2) vs impl (t-7), and metadata test (t-3/t-4) vs files (t-5/t-6) split into 4 tasks where 2 honest tasks suffice | ok — tests-in-wave-1 before files-in-wave-2 is principled but inflates the graph; t-10 correctly independent in wave 1 |

**Skeleton: `risk` (9 tasks / 3 waves).** It has the strongest criteria coverage with one
owner per failure mode, the sharpest contracts (tri-state detection, premature-complete
regression, no-secret-in-repo grep), and clean granularity — and it is the only draft that
splits the two distinct c-6 failure modes (debug-flag leak vs committed signing secret) into
separately-testable tasks. mvp is too coarse on c-1's return-refresh; verification is correct
and the most rigorous on contracts but over-split (11 tasks, with interface/impl and
test/file seams that don't each earn a task).

## Merged plan

Skeleton = risk's 9 tasks / 3 waves. Grafts applied:
- **[verification]** correct paths: detector under `ui`-adjacent `assistant/` package (it is the
  real package and houses `EquerryVoiceInteractionService`); onboarding UI under `ui/onboarding/`.
- **[verification]** sharper detector contract: name the concrete component
  `EquerryVoiceInteractionService` and assert via Robolectric Settings.Secure seeding.
- **[verification]** sharper c-6 hygiene contract: grep `ui/probe` debug surface out of
  release-reachable paths AND assert proguard keep-rules for Hilt / serialization / the
  voice-interaction service entry points (so minify cannot strip the assistant).
- **[mvp]** explicit DataStore reuse: `OnboardingStore` rides the existing `equerry_settings`
  DataStore following the `VoiceSettingsStore` pattern (real file confirmed).
- **[mvp+verification]** persistent setup banner on HOME while CHAT is unmapped (risk implies
  "persistent setup entry/banner" in the spec gate but doesn't own it in a task) — folded into
  the nav task's contract, not a new task.

Format: id · title · files · covers · contract · depends_on · origin.

### Wave 1

```
t-1  Default-assistant detector with tri-state fallback        [risk + verification]
     files:   app/src/main/java/dev/equerry/app/assistant/DefaultAssistantDetector.kt
              app/src/main/java/dev/equerry/app/di/RepositoryModule.kt
              app/src/test/java/dev/equerry/app/assistant/DefaultAssistantDetectorTest.kt
     covers:  c-1
     contract: Robolectric test seeds Settings.Secure "voice_interaction_service".
              isDefault() returns TRUE only when the stored component equals
              dev.equerry.app's EquerryVoiceInteractionService; FALSE when it holds a
              different OEM component; UNKNOWN when the key is absent/empty (never a
              false TRUE). The OEM-empty-string case fails if detection ever reports
              "is default" without a component match. Exposes ACTION_VOICE_INPUT_SETTINGS.
     depends: —
     origin:  risk skeleton (t-1) + verification's concrete service name & Robolectric seam
              (vt-7) + verification's fakeable single-entry-point shape (vt-2, folded — see D-2)

t-2  First-run onboarding-complete store                        [risk + mvp]
     files:   app/src/main/java/dev/equerry/app/data/OnboardingStore.kt
              app/src/main/java/dev/equerry/app/di/PersistenceModule.kt
              app/src/test/java/dev/equerry/app/data/OnboardingStoreTest.kt
     covers:  c-3
     contract: completed flag defaults FALSE on a fresh DataStore (riding the existing
              equerry_settings store, VoiceSettingsStore pattern) and round-trips TRUE
              after setCompleted(true). The default-false test fails if the missing-key
              fallback ever reads as completed (which would suppress onboarding for a new
              user). Mirrors VoiceSettingsStoreTest.
     depends: —
     origin:  risk skeleton (t-2) + mvp's explicit equerry_settings/VoiceSettingsStore reuse

t-3  Hardened release build type                                [risk]
     files:   app/build.gradle.kts
     covers:  c-6
     contract: a Gradle/JVM assertion test over the release buildType fails if
              isDebuggable is true, isMinifyEnabled is false, or versionName/applicationId
              drift from "0.1.0"/"dev.equerry.app" (versionCode=1). A debug-only leftover
              (debuggable=true) flips the assertion red.
     depends: —
     origin:  risk (t-3); version/applicationId assertion sharpened with verification's
              versionCode=1 (vt-10)

t-4  Release signing config from env, never committed           [risk]
     files:   app/build.gradle.kts
              .gitignore
              keystore.properties.example
     covers:  c-6
     contract: signingConfigs.release reads storeFile/storePassword/keyAlias/keyPassword
              from System.getenv / a gitignored keystore.properties and is null-safe when
              absent (unsigned build still assembles for F-Droid). A test/grep asserts no
              literal store password or .jks path appears in build.gradle.kts or any
              git-tracked file — committing a secret fails the no-secret-in-repo check (r-03).
     depends: —
     origin:  risk (t-4)

t-5  Fastlane metadata text tree with lint                      [risk + verification]
     files:   fastlane/metadata/android/en-US/title.txt
              fastlane/metadata/android/en-US/short_description.txt
              fastlane/metadata/android/en-US/full_description.txt
              fastlane/metadata/android/en-US/changelogs/1.txt
              app/src/test/java/dev/equerry/app/metadata/FastlaneMetadataTest.kt
     covers:  c-4, c-5
     contract: FastlaneMetadataTest reads the tree and fails if title > 30 (Play cap,
              the binding constraint since one tree feeds both stores), short_description
              > 80, full_description > 4000, the changelogs/<versionCode>.txt for
              versionCode=1 is missing/empty, or a required donate link is absent
              (asserted against the existing .github/FUNDING.yml: Liberapay + GitHub
              Sponsors, locked). One canonical tree serves c-4 and c-5 (metadata_source
              locked). An over-length summary makes the test red.
     depends: —
     origin:  risk (t-5); Play title<=30 cap & FUNDING.yml donate assertion from
              verification (vt-3/vt-4/vt-6) — see D-3 for the c-4/c-5 split decision
```

### Wave 2 (depends t-1, t-2)

```
t-6  Onboarding state machine (steps + soft completion gate)    [risk + verification]
     files:   app/src/main/java/dev/equerry/app/ui/onboarding/OnboardingViewModel.kt
              app/src/test/java/dev/equerry/app/ui/onboarding/OnboardingViewModelTest.kt
     covers:  c-1, c-2, c-3
     contract: OnboardingViewModelTest (fake DefaultAssistantDetector + in-memory stores)
              fails if (a) step order deviates from welcome -> set-default -> map-CHAT ->
              done; (b) the set-default step is non-deferrable / dead-ends when the
              detector reports not-default or UNKNOWN (locked deferrable, c-1); (c) state
              does not flip default=true after refresh()/onReturn once the fake detector
              returns true (return-from-system reflection, c-1); (d) completed flips
              before observeChatMapping() emits non-null, or fails to flip once it does
              (premature-complete regression / c-2 gate). Dismiss at any step leaves
              completed=false and pendingSetup=true.
     depends: t-1, t-2
     origin:  risk (t-6) + verification's explicit (a)-(d) step-order/refresh assertions (vt-8)

t-7  Fastlane screenshots + Play graphics assets                [risk + verification]
     files:   fastlane/metadata/android/en-US/images/phoneScreenshots/1.png
              fastlane/metadata/android/en-US/images/phoneScreenshots/2.png
              fastlane/metadata/android/en-US/images/icon.png
              fastlane/metadata/android/en-US/images/featureGraphic.png
              app/src/test/java/dev/equerry/app/metadata/FastlaneMetadataTest.kt
     covers:  c-4, c-5
     contract: the metadata-lint test asserts at least one phoneScreenshots/*.png plus
              icon.png and featureGraphic.png exist and are non-zero — a missing/empty
              screenshot or graphic (both F-Droid and Play reject these) fails the
              existence check. Honestly a structural/file-presence check; bitmaps are not
              JVM-unit-testable.
     depends: t-5
     origin:  risk (t-7); verification confirms the file-presence-proxy honesty (vt-6)
```

### Wave 3 (depends t-6)

```
t-8  Onboarding UI wired into nav + re-entry + setup banner     [risk + mvp]
     files:   app/src/main/java/dev/equerry/app/ui/onboarding/OnboardingScreen.kt
              app/src/main/java/dev/equerry/app/MainActivity.kt
              app/src/main/res/values/strings.xml
     covers:  c-1, c-2, c-3
     contract: startDestination resolves to Route.ONBOARDING when OnboardingStore.completed
              is false and Route.HOME when true (already-configured user never blocked); a
              Settings/Home entry navigates back to Route.ONBOARDING after completion
              (re-enterable); a persistent setup banner shows on HOME while CHAT is unmapped
              and is absent once mapped. NavHost test fails if a completed user lands on
              onboarding, a configured user cannot re-open it, or the banner is wrong.
     depends: t-6
     origin:  risk (t-8) + mvp's HOME setup-banner visibility contract (mt-3)

t-9  Default-step intent + return-state refresh in UI          [risk]
     files:   app/src/main/java/dev/equerry/app/ui/onboarding/OnboardingScreen.kt
              app/src/test/java/dev/equerry/app/ui/onboarding/OnboardingViewModelTest.kt
     covers:  c-1
     contract: the "set as default" action launches ACTION_VOICE_INPUT_SETTINGS (asserted
              via the captured Intent action), and an ON_RESUME refresh re-queries the
              detector so the displayed default-state reflects a change made in system
              Settings. The test fails if the step shows a stale "not default" after the
              detector reports a match, or if a non-VOICE_INPUT intent is launched.
     depends: t-6
     origin:  risk (t-9)
```

### Coverage (every criterion covered)

- c-1 -> t-1 (detect + UNKNOWN), t-6 (deferrable step + return refresh logic), t-8 (route/start), t-9 (intent + ON_RESUME refresh)
- c-2 -> t-6 (completion gate on observeChatMapping), t-8 (UI add/map step)
- c-3 -> t-2 (persisted flag), t-6 (soft gate, dismissible), t-8 (start-destination switch + re-entry)
- c-4 -> t-5 (text + lint), t-7 (screenshots/graphics presence)
- c-5 -> t-5 (shared canonical tree, Play caps), t-7 (graphics)
- c-6 -> t-3 (hardened buildType), t-4 (env signing, no secret in repo)

## Disagreements

### D-1 — Onboarding granularity: 2 tasks (mvp) vs 3 tasks (risk/verification)
- **mvp**: store+detector in ONE task (t-1), ViewModel (t-2), UI/nav (t-3) — 3 logic tasks
  but folds detection-store together and folds intent+refresh into the UI task.
- **risk**: detector (t-1), store (t-2), ViewModel (t-6), nav/re-entry (t-8), intent+refresh
  (t-9) — 5 onboarding tasks; splits intent/return-refresh out as its own owner.
- **verification**: detector interface (t-2) + impl (t-7) + store (t-1) + ViewModel (t-8) +
  nav (t-9) — also fine-grained, and additionally splits the detector into interface vs impl.
- **Provisional default: risk's split, minus the intent/refresh task is KEPT as t-9.** Five
  onboarding owners (t-1, t-2, t-6, t-8, t-9).
- **Why it matters**: the stale-default-after-return bug (c-1's "reflects the changed state
  when they return") is a distinct lifecycle surface from start-destination routing. mvp buries
  it inside a 3-file UI task where no contract asserts the ON_RESUME re-query — that is exactly
  the regression c-1 names. Keeping t-9 separate gives it a failing test. The cost is one extra
  task; the benefit is c-1's hardest clause has an owner.

### D-2 — Detector: concrete class (risk/mvp) vs interface + impl split (verification)
- **risk/mvp**: a single concrete `DefaultAssistantDetector` class.
- **verification**: an interface (t-2) the ViewModel test fakes, plus a Settings.Secure impl
  (t-7), so OnboardingViewModelTest needs no Robolectric plumbing.
- **Provisional default: single class in t-1, BUT it must be a fakeable seam (interface or open
  class) so t-6's ViewModel test injects a fake.** Verification's *goal* (fakeable seam) is
  adopted; its *two-task split* is rejected.
- **Why it matters**: the fakeable seam is non-negotiable — without it, c-1/c-2/c-3 ViewModel
  logic can only be tested under instrumentation, which the gradle-hang memory makes costly.
  But interface-and-impl-as-two-tasks is over-granular for a single ~40-line detector; one task
  delivering `interface + Settings.Secure impl + DI binding` keeps the seam without inflating
  the graph. If the impl grows (multiple OEM strategies), revisit the split.

### D-3 — F-Droid + Play metadata: one task (mvp/risk-text) vs two (verification)
- **mvp & risk**: ONE canonical fastlane tree task covering c-4 and c-5 (metadata_source
  locked: one tree feeds both stores).
- **verification**: separate FastlaneMetadataTest (c-4) and PlayListingMetadataTest (c-5) over
  the same tree, plus contract-first (tests authored in wave 1, files in wave 2).
- **Provisional default: ONE text task (t-5) + ONE graphics task (t-7), both covering c-4+c-5;
  a single FastlaneMetadataTest.** Verification's separate Play test is rejected; its strongest
  caps are grafted in (Play title<=30 as the binding limit, FUNDING.yml donate assertion).
- **Why it matters**: the locked `metadata_source` decision says ONE canonical tree, F-Droid
  native + Play derived — two test files over one tree assert the same files twice and invite
  drift in which cap is authoritative. Using the *tighter* of the two stores' caps (Play's
  title<=30) in a single test covers both honestly. The text/graphics split (t-5/t-7) is kept
  because graphics are a file-presence proxy on a different artifact type, and sequencing them
  avoids two wave-mates editing the same test file (risk's stated reason). Verification's
  contract-first ordering (tests before files) is NOT adopted — it doubles the task count for a
  structural lint whose test and data are trivially co-authorable in one task.

### D-4 — Release build: split (risk/verification) vs single (mvp)
- **mvp**: ONE release task (t-5) — signingConfig + isDebuggable + version + proguard.
- **risk**: split into hardened buildType (t-3) and env-signing/no-secret (t-4).
- **verification**: split into build-config+version (t-10) and debug-leftover-stripping+proguard
  keeps (t-11), with the hygiene grep over the `ui/probe` surface.
- **Provisional default: risk's two-task split (t-3 buildType hardening, t-4 env-signing).**
- **Why it matters**: spec lists two distinct c-6 failure modes the locked release_signing
  decision separates — "debuggable=false / no debug leftovers" vs "signing key via env, never
  committed (r-03)". Merging them lets a leaked debug flag hide behind a green signing test (or
  vice versa). Risk's split is chosen over verification's because risk's t-4 carries the
  explicit no-secret-in-repo grep, which is the r-03-critical assertion. **Open sub-question NOT
  resolved here**: verification's t-11 adds a *proguard keep-rule + probe-surface hygiene* check
  that neither risk task fully owns. This is genuinely missing from the chosen skeleton — see D-5.

### D-5 — Debug-surface hygiene + proguard keeps: owned (verification) vs absent (risk/mvp)
- **verification**: t-11 greps `ui/probe` out of release-reachable paths AND asserts proguard
  keep-rules for Hilt/serialization/the voice-interaction service (so minify doesn't strip the
  assistant entry point — a real risk once isMinifyEnabled=true per t-3).
- **risk/mvp**: proguard file is edited (risk t-3/t-4 list proguard-rules.pro) but no task
  *asserts* keep-rules survive, and no task greps the probe debug surface.
- **Provisional default: graft verification's keep-rule + probe-hygiene assertion INTO t-3's
  contract** (rather than add a 10th task), since t-3 already owns isMinifyEnabled=true and that
  is precisely what makes missing keep-rules dangerous.
- **Why it matters**: this is the one substantive gap in the risk skeleton. Turning on minify
  (t-3) without keep-rules for the VoiceInteractionService can strip the assistant entry point —
  the app's entire reason to exist — and no test in risk/mvp would catch it. Folding it into t-3
  keeps the task count at 9 while closing a real release regression. If t-3's contract grows
  unwieldy, promote it to a 10th task mirroring verification's t-11.
