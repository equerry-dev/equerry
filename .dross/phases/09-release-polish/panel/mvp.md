Phase 09-release-polish — 5 tasks across 2 waves

Wave 1
  t-1  Add default-assistant detection + onboarding state
       files:    app/src/main/java/dev/equerry/app/data/OnboardingStore.kt
                 app/src/main/java/dev/equerry/app/onboarding/DefaultAssistantDetector.kt
                 app/src/main/java/dev/equerry/app/di/PersistenceModule.kt
       covers:   c-1, c-2, c-3
       description: OnboardingStore persists a completed flag on the shared "equerry_settings"
                 DataStore (VoiceSettingsStore pattern); DefaultAssistantDetector reads
                 Settings.Secure (voice_interaction_service) vs the app's component and exposes
                 the ACTION_VOICE_INPUT_SETTINGS intent. Provide both in PersistenceModule.
       contract: if completed survives reads but never auto-flips (it is set explicitly), the
                 OnboardingStore round-trip test fails; if the detector reports "default" when
                 Settings.Secure holds a different component, the detector unit test fails.

  t-4  Add fastlane metadata tree for F-Droid + Play
       files:    fastlane/metadata/android/en-US/title.txt
                 fastlane/metadata/android/en-US/short_description.txt
                 fastlane/metadata/android/en-US/full_description.txt
                 fastlane/metadata/android/en-US/changelogs/1.txt
                 fastlane/metadata/android/en-US/images/phoneScreenshots/.gitkeep
                 app/src/test/java/dev/equerry/app/MetadataLintTest.kt
       covers:   c-4, c-5
       description: Single canonical fastlane tree (locked metadata_source) consumed by F-Droid
                 natively and reused for the Play listing; donate links via the existing
                 .github/FUNDING.yml. MetadataLintTest asserts the tree's structural validity.
       contract: if title.txt exceeds 30 chars or short_description.txt exceeds 80 chars (store
                 limits), or any required file (title/short/full/changelogs/1) is missing or empty,
                 MetadataLintTest fails.

  t-5  Configure signable release build
       files:    app/build.gradle.kts
                 app/proguard-rules.pro
       covers:   c-6
       description: Add a release signingConfig sourced from env/Gradle properties (never
                 committed, r-03/release_signing), set isDebuggable=false explicitly, bump
                 versionName naming, and add proguard log-strip rules so no debug logging leaks.
       contract: if the release buildType lacks a signingConfig wired to the env-property path, or
                 isDebuggable is left true, the build-config validation test (parses
                 build.gradle.kts / asserts release block) fails.

Wave 2
  t-2  Build onboarding wizard ViewModel
       files:    app/src/main/java/dev/equerry/app/onboarding/OnboardingViewModel.kt
       covers:   c-2, c-3
       description: ViewModel combines DefaultAssistantDetector state, repository.observeChatMapping(),
                 and OnboardingStore.completed into wizard step state; marks complete only when a
                 non-null CHAT mapping exists (soft gate); exposes shouldShow (first run until done)
                 and a re-enter entry point.
       depends_on: t-1
       contract: if completion is marked while the CHAT mapping is null, OnboardingViewModelTest
                 fails; if shouldShow stays true after completed flips true (already-configured user
                 blocked), the test fails; if the default-assistant step is non-deferrable (can't
                 advance when undetected), the deferral test fails.

  t-3  Build wizard UI and wire nav + setup banner
       files:    app/src/main/java/dev/equerry/app/onboarding/OnboardingScreen.kt
                 app/src/main/java/dev/equerry/app/MainActivity.kt
                 app/src/main/res/values/strings.xml
       covers:   c-1, c-2, c-3
       description: Wizard screens (welcome -> set default -> add+map CHAT -> done) reusing the
                 provider/slots routes; add Route.ONBOARDING with first-run start + a Settings
                 re-enter entry; render a persistent setup banner on HOME while CHAT is unmapped.
       depends_on: t-2
       contract: if the onboarding route is unreachable from Settings (re-entry), NavigationTest
                 fails; if the HOME setup banner is absent when CHAT is unmapped, the banner
                 navigation/visibility test fails.

## Coverage
c-1 -> t-1, t-3
c-2 -> t-1, t-2, t-3
c-3 -> t-1, t-2, t-3
c-4 -> t-4
c-5 -> t-4
c-6 -> t-5

## Judgment calls
- Onboarding logic split into store/detector (t-1) + ViewModel (t-2) + UI/nav (t-3): rejected a single mega-task (4+ files, 2 layers, breaches granularity) and rejected per-step tasks (speculative); this is the fewest tasks that keep each within bounds and isolate the testable VM seam.
- F-Droid + Play metadata collapsed into one task (t-4): locked metadata_source mandates a single canonical fastlane tree, so splitting c-4/c-5 would duplicate the deliverable; one task with a structural metadata-lint contract covers both honestly (prose/screenshots are not JVM-unit-testable).
- Donate links reuse existing .github/FUNDING.yml rather than a new task: it already exists with locked Liberapay + GitHub Sponsors, so no work beyond referencing it in metadata.
- Release build kept as its own small task (t-5): touches build.gradle.kts + proguard only, distinct layer from onboarding/metadata, no dependency — stays wave 1.
- Wave assignment: t-4 and t-5 have no dependency on onboarding, so they sit in wave 1 alongside t-1 rather than being artificially deferred; only t-2 (needs t-1's store/detector) and t-3 (needs t-2's VM) are wave 2.
