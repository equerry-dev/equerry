# Phase 09-release-polish — RISK LENS draft

Every named failure mode is owned and tested by exactly one task. The graph is shaped so
that what can break — an unknowable default-assistant state, a user who declines/never
returns from the system screen, onboarding re-showing for an already-configured user, a
release build that leaks a debug flag or commits a signing secret, fastlane text that
fails F-Droid validation — each has a single owner with a test that fails when that
specific thing regresses.

```
Phase 09-release-polish — 9 tasks across 3 waves

Wave 1
  t-1  Default-assistant detector with safe fallback
       files:    app/src/main/java/dev/equerry/app/onboarding/AssistantRoleDetector.kt
                 app/src/test/java/dev/equerry/app/onboarding/AssistantRoleDetectorTest.kt
       covers:   c-1
       contract: if Settings.Secure "voice_interaction_service" holds another OEM component,
                 isDefault() returns false; if the key is absent/empty (unknowable on some
                 OEMs) it returns UNKNOWN (not a false true) — the OEM-empty-string case test
                 fails if detection ever reports "is default" without a component match.

  t-2  First-run completion-gate store
       files:    app/src/main/java/dev/equerry/app/data/OnboardingStore.kt
                 app/src/main/java/dev/equerry/app/di/PersistenceModule.kt
                 app/src/test/java/dev/equerry/app/data/OnboardingStoreTest.kt
       covers:   c-3
       contract: completed flag defaults false on a fresh DataStore and round-trips true after
                 setCompleted(true); the default-false test fails if the missing-key fallback
                 ever reads as completed (which would suppress onboarding for a new user).

  t-3  Hardened release build type
       files:    app/build.gradle.kts, app/proguard-rules.pro
       covers:   c-6
       contract: a Gradle/JVM assertion test over the release buildType fails if
                 isDebuggable is true, isMinifyEnabled is false, or versionName/applicationId
                 drift from "0.1.0"/"dev.equerry.app"; a debug-only leftover (debuggable=true)
                 flips the assertion red.

  t-4  Release signing config from env, never committed
       files:    app/build.gradle.kts, .gitignore, keystore.properties.example
       covers:   c-6
       contract: signingConfigs.release reads storeFile/storePassword/keyAlias/keyPassword
                 from System.getenv / a gitignored keystore.properties and is null-safe when
                 absent (unsigned build still assembles for F-Droid); a test/grep asserts no
                 literal store password or .jks path appears in build.gradle.kts or git-tracked
                 files — committing a secret string fails the no-secret-in-repo check.

  t-5  Fastlane metadata tree with lint
       files:    fastlane/metadata/android/en-US/title.txt
                 fastlane/metadata/android/en-US/short_description.txt
                 fastlane/metadata/android/en-US/full_description.txt
                 fastlane/metadata/android/en-US/changelogs/1.txt
                 app/src/test/java/dev/equerry/app/metadata/FastlaneMetadataTest.kt
       covers:   c-4
       contract: structural validation test reads the fastlane tree and fails if
                 short_description exceeds 80 chars, title exceeds 50, full_description exceeds
                 4000, the changelogs/<versionCode>.txt for versionCode=1 is missing/empty, or
                 a required donate link (Liberapay/GitHub) is absent — an over-length summary
                 makes FastlaneMetadataTest red.

Wave 2 (depends t-1, t-2)
  t-6  Onboarding state machine (steps + soft completion gate)
       files:    app/src/main/java/dev/equerry/app/onboarding/OnboardingViewModel.kt
                 app/src/test/java/dev/equerry/app/onboarding/OnboardingViewModelTest.kt
       covers:   c-2, c-3
       contract: state.completed flips true ONLY after observeChatMapping emits non-null;
                 dismissing at any step leaves completed=false and pendingSetup=true; calling
                 the default step's onReturn re-reads AssistantRoleDetector so a user who
                 declined (still not default) advances without dead-ending — the
                 "dismiss-before-CHAT-mapping" test fails if completed ever flips without a
                 CHAT mapping (premature-complete regression).
       depends:  t-1, t-2

  t-7  Fastlane screenshots + Play graphics assets
       files:    fastlane/metadata/android/en-US/images/phoneScreenshots/1.png
                 fastlane/metadata/android/en-US/images/phoneScreenshots/2.png
                 fastlane/metadata/android/en-US/images/icon.png
                 fastlane/metadata/android/en-US/images/featureGraphic.png
                 app/src/test/java/dev/equerry/app/metadata/FastlaneMetadataTest.kt
       covers:   c-4, c-5
       contract: the metadata-lint test asserts at least one phoneScreenshots/*.png and the
                 icon/featureGraphic files exist and are non-zero — a missing or empty
                 screenshot/graphic (which F-Droid and Play both reject) fails the existence
                 check. This is honestly a structural/file-presence check, not a pixel test.
       depends:  t-5

Wave 3 (depends t-6)
  t-8  Onboarding UI wired into nav + re-entry from settings
       files:    app/src/main/java/dev/equerry/app/onboarding/OnboardingScreen.kt
                 app/src/main/java/dev/equerry/app/MainActivity.kt
                 app/src/main/res/values/strings.xml
       covers:   c-1, c-2, c-3
       contract: startDestination resolves to Route.ONBOARDING when OnboardingStore.completed
                 is false and to Route.HOME when true (already-configured user is never
                 blocked); a Settings/Home entry navigates back to Route.ONBOARDING after
                 completion (re-enterable) — a NavHost test fails if a completed user lands on
                 onboarding or a configured user cannot re-open it.
       depends:  t-6

  t-9  Default-step intent + return-state refresh in UI
       files:    app/src/main/java/dev/equerry/app/onboarding/OnboardingScreen.kt
                 app/src/test/java/dev/equerry/app/onboarding/OnboardingViewModelTest.kt
       covers:   c-1
       contract: the "set as default" action launches Settings.ACTION_VOICE_INPUT_SETTINGS
                 (asserted via the captured Intent action), and an ON_RESUME refresh re-queries
                 the detector so the displayed default-state reflects a change made in system
                 Settings — the test fails if the step shows a stale "not default" after the
                 detector reports the component now matches, or if a non-VOICE_INPUT intent is
                 launched.
       depends:  t-6
```

## Coverage

- c-1 (detect default; route to system screen; reflect on return): t-1 (detection + UNKNOWN fallback), t-8 (route/start), t-9 (launch intent + return refresh)
- c-2 (guide CHAT mapping; complete only with working mapping): t-6 (completion gate on observeChatMapping), t-8 (UI for add/map step)
- c-3 (runs on first run until done; never blocks configured user; re-enterable): t-2 (persisted completed flag), t-6 (soft gate, dismissible), t-8 (start-destination switch + re-entry)
- c-4 (F-Droid fastlane metadata that validates): t-5 (text + lint), t-7 (screenshots/graphics + existence check)
- c-5 (Play store-listing metadata as versioned assets): t-5 (shared canonical text tree), t-7 (graphics)
- c-6 (signed-configurable release; release naming; no debug leftovers): t-3 (hardened buildType assertions), t-4 (env-based signing, no secret in repo)

## Judgment calls

- Split build hardening (t-3) from signing config (t-4): each is a distinct failure mode — a leaked debug flag vs a committed signing secret — so each gets its own owner and test; merging them would let one regression hide behind the other's green.
- Detection (t-1) returns a tri-state (true/false/UNKNOWN) rather than a boolean: chose this because the gathered context says the default-assistant state is unknowable on some OEMs (Settings.Secure key may be empty); a boolean would force a false "is default", silently dead-ending c-1. Rejected the RoleManager ROLE_ASSISTANT path — there is no public legacy-assistant role, so Settings.Secure comparison is the verified approach.
- Completion gate (t-6) keys off the existing ProviderRepository.observeChatMapping() rather than a new onboarding-local mapping check: chose reuse so "complete" and the live CHAT slot can never disagree; rejected a separate onboarding boolean that could drift from the real mapping.
- t-9 split from t-8: the launch-intent + ON_RESUME-refresh risk (stale default-state after the user returns) is a different surface from the start-destination/re-entry routing risk, so each is owned and testable alone. Rejected folding both into one UI task because a 5+ file UI task spanning routing and lifecycle would bury the return-state regression.
- Screenshots/graphics (t-7) depend on t-5 because both write into the same lint test and the same fastlane tree; sequencing them avoids two tasks editing FastlaneMetadataTest.kt in the same wave. Contract is honestly stated as a file-presence/structural check — bitmaps are not JVM-unit-testable.
- t-3/t-4/t-5 sit in wave 1 (not gated on onboarding) because release config and metadata share no code with the onboarding feature; keeping them parallel maximizes throughput without violating wave correctness.
