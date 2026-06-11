Phase 09-release-polish — 11 tasks across 3 waves

Wave 1
  t-1  Add onboarding-complete store
       files:    app/src/main/java/dev/equerry/app/data/OnboardingStore.kt
       covers:   c-3
       contract: If onboardingComplete() stops defaulting to false on a fresh store, or
                 setComplete(true) no longer survives a DataStore reload, OnboardingStoreTest
                 (mirroring VoiceSettingsStoreTest) fails.

  t-2  Define default-assistant detector interface
       files:    app/src/main/java/dev/equerry/app/assistant/DefaultAssistantDetector.kt
       covers:   c-1
       contract: If the interface no longer exposes a single boolean isDefaultAssistant()
                 entry point (the seam a ViewModel test fakes), OnboardingViewModelTest fails
                 to compile against the fake — i.e. the detection is not behind a fakeable seam.

  t-3  Validate fastlane metadata structure
       files:    app/src/test/java/dev/equerry/app/metadata/FastlaneMetadataTest.kt
       covers:   c-4
       contract: If title.txt/short_description.txt/full_description.txt/changelogs/1.txt are
                 missing, empty, or exceed F-Droid length caps (title<=50, short<=80 chars), or
                 the donate URLs are absent, FastlaneMetadataTest fails by reading the files from
                 the repo root. (Proxy: store prose/screenshots are not unit-testable; this is a
                 structural+length lint over the metadata tree.)

  t-4  Validate Play store-listing metadata
       files:    app/src/test/java/dev/equerry/app/metadata/PlayListingMetadataTest.kt
       covers:   c-5
       contract: If the Play-derived title/short/full description files are missing or exceed
                 Play caps (title<=30, short<=80, full<=4000 chars), or the graphics assets
                 (icon/feature graphic paths) are absent, PlayListingMetadataTest fails.
                 (Proxy: graphics bitmaps are not unit-testable; this asserts file presence +
                 text-length constraints over the same fastlane tree, per metadata_source.)

Wave 2
  t-5  Author fastlane metadata tree
       files:    fastlane/metadata/android/en-US/title.txt,
                 fastlane/metadata/android/en-US/short_description.txt,
                 fastlane/metadata/android/en-US/full_description.txt,
                 fastlane/metadata/android/en-US/changelogs/1.txt
       covers:   c-4, c-5
       contract: Satisfies t-3 and t-4 file-presence/length assertions; if any required file is
                 omitted or over length, FastlaneMetadataTest / PlayListingMetadataTest go red.
       depends:  t-3, t-4

  t-6  Add store graphics + donate metadata
       files:    fastlane/metadata/android/en-US/images/icon.png,
                 fastlane/metadata/android/en-US/images/phoneScreenshots/1.png,
                 fastlane/metadata/android/en-US/images/featureGraphic.png
       covers:   c-4, c-5
       contract: Satisfies the graphics-presence branch of FastlaneMetadataTest /
                 PlayListingMetadataTest; if icon.png or featureGraphic.png is removed those
                 tests fail. Donate links are asserted against .github/FUNDING.yml (Liberapay +
                 GitHub Sponsors, locked) by t-3, so dropping a funding entry also fails.
       depends:  t-3, t-4

  t-7  Implement default-assistant detector
       files:    app/src/main/java/dev/equerry/app/assistant/SettingsDefaultAssistantDetector.kt,
                 app/src/main/java/dev/equerry/app/di/RepositoryModule.kt
       covers:   c-1
       contract: If the Settings.Secure voice-interaction-service comparison no longer returns
                 true when the stored component equals dev.equerry.app's
                 EquerryVoiceInteractionService (and false otherwise), the Robolectric
                 SettingsDefaultAssistantDetectorTest (seeding Settings.Secure
                 "voice_interaction_service") fails.
       depends:  t-2

  t-8  Build onboarding state machine ViewModel
       files:    app/src/main/java/dev/equerry/app/ui/onboarding/OnboardingViewModel.kt
       covers:   c-1, c-2, c-3
       contract: OnboardingViewModelTest (with a fake DefaultAssistantDetector + in-memory
                 stores) fails if: (a) step order deviates from welcome -> set-default ->
                 map-CHAT -> done; (b) the set-default step is not skippable / dead-ends when
                 the detector reports not-default (c-1, locked deferrable); (c) state does not
                 flip to default=true after refresh() once the fake detector returns true
                 (return-from-system reflection, c-1); (d) completion is reported before a
                 non-null CHAT mapping exists, or NOT reported once observeChatMapping() emits
                 non-null (c-2 completion gate).
       depends:  t-1, t-2

Wave 3
  t-9  Wire onboarding into nav + first-run gate
       files:    app/src/main/java/dev/equerry/app/MainActivity.kt,
                 app/src/main/java/dev/equerry/app/ui/onboarding/OnboardingScreen.kt
       covers:   c-3
       contract: NavigationTest-style Robolectric test fails if startDestination is not
                 Route.ONBOARDING when OnboardingStore.complete=false, is not Route.HOME when
                 complete=true (never blocks a configured user), and if a Settings/Home entry
                 does not navigate to Route.ONBOARDING (re-enterable, c-3).
       depends:  t-8

  t-10 Add release build config + version naming
       files:    app/build.gradle.kts
       covers:   c-6
       contract: ReleaseBuildConfigTest (Robolectric, reading BuildConfig + manifest) fails if
                 the release signingConfig is not driven by env/Gradle properties (release_signing,
                 locked: no committed keystore), if debuggable is true in release, or if
                 versionName != "0.1.0" / versionCode != 1. Backed by a structural assertion that
                 build.gradle.kts references the signing env vars and sets isDebuggable=false.
       depends:  empty

  t-11 Strip debug-only leftovers + proguard keeps
       files:    app/proguard-rules.pro,
                 app/src/test/java/dev/equerry/app/ReleaseHygieneTest.kt
       covers:   c-6
       contract: ReleaseHygieneTest fails if any source under app/src/main references the probe
                 debug surface in a release-reachable path, or if a release-only string/logging
                 leftover is present; and proguard-rules.pro must retain the keep rules for the
                 serialization/Hilt/voice-interaction service classes (asserted by grepping the
                 rules file) so minify does not strip the assistant entry points.
       depends:  t-10

## Coverage
- c-1 -> t-2, t-7, t-8
- c-2 -> t-8 (completion gate), t-5 (metadata not involved — primary is t-8)
- c-3 -> t-1, t-8, t-9
- c-4 -> t-3, t-5, t-6
- c-5 -> t-4, t-5, t-6
- c-6 -> t-10, t-11

(c-2 is satisfied solely by t-8's completion-gate contract tied to observeChatMapping; no metadata task covers it.)

## Judgment calls
- Detection behind an interface (t-2) split from its Settings.Secure impl (t-7): chose a fakeable
  seam so OnboardingViewModelTest never needs Robolectric Settings plumbing; rejected calling
  Settings.Secure directly inside the ViewModel because that buries c-1 logic where only an
  instrumented test could reach it.
- All step-order / completion-gate logic lives in OnboardingViewModel (t-8), not the Composable:
  chose a JVM-testable state machine so c-1/c-2/c-3 have imaginable unit tests; rejected encoding
  step order inside OnboardingScreen where only a brittle Compose-UI test could assert it.
- Metadata gets validation tests authored BEFORE the files (t-3/t-4 in wave 1, files in wave 2):
  chose contract-first so the prose/graphics artifacts have an executable definition of "validates"
  (c-4 "that validates"); rejected writing raw resource files with no test, which the lens forbids.
- Graphics/screenshots reduced to file-presence + length proxies (t-3/t-4/t-6): chose the smallest
  honest structural assertion since bitmaps/prose are inherently un-unit-testable, and said so;
  rejected pretending pixel content is verifiable.
- Release config split into build-config (t-10) and hygiene/proguard (t-11): chose two tasks because
  signing+versioning (gradle) and debug-leftover stripping+keep-rules (proguard/source) are distinct
  surfaces with distinct failing tests; rejected one mega-task touching gradle+proguard+source.
- First-run gate (t-9) kept in wave 3 behind the ViewModel (t-8): the nav startDestination decision
  strictly needs OnboardingStore (t-1) + the ViewModel's complete signal; rejected folding it into
  t-8 because it crosses into the nav/Composable layer (2nd layer) and has its own NavigationTest.
- t-10 placed in wave 1 (no deps): release gradle config needs nothing from onboarding; kept it
  independent rather than artificially serializing it after UI work.
