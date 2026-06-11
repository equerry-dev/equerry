# Plan Review — 07-screen-context

Reviewed: 2026-06-11
Plan: 9 tasks across 5 waves

## BLOCKING
(none)

All six criteria are covered: c-1 (t-8, t-9), c-2 (t-1, t-2, t-3, t-5, t-7), c-3 (t-4, t-5, t-7), c-4 (t-9), c-5 (t-3, t-5, t-6, t-7), c-6 (t-7, t-9). No task contradicts a locked decision, and no forbidden action (e.g. an AccessibilityService — r-01) is implied; t-9 explicitly asserts against one. Runtime is native Gradle, so no docker/pnpm-style violations are possible.

## FLAG
- [granularity / merge-candidate] t-2 ("supportsImages flag on ProviderType") is one file, ~one boolean per enum constant, mirroring the existing `supportsTools` field — well under 10 minutes. It is a wave-1 sibling of t-1, which also touches the drivers/providers area and is the only consumer of the flag's intent. Splitting it out inflates the task count without isolating real risk.
  Suggestion: consider merging t-2 into t-1 (image-bearing message + capability flag land together) or into t-3 (slot/capability wiring); keep its specific test_contract line either way.

- [granularity / squashed-task] t-9 bundles three distinct concerns into one file-task: (a) changing `onHandleScreenshot` to retain the actual bitmap transiently and correlate it with `onHandleAssist` — today `onHandleScreenshot` deliberately keeps dimensions only and discards the bitmap (EquerryVoiceInteractionSession.kt:211-214), so this is the load-bearing, security-sensitive change for c-6/c-4; (b) adding the Compose "Ask about this screen" button; (c) supplying the screenContext provider to VoiceFlowController. These are the c-4 and c-6 criteria's primary enforcement point, yet the bitmap-retention change shares a task with UI plumbing.
  Suggestion: consider splitting the transient bitmap-capture + AssistAnalyzer.extractText correlation (the never-persist seam) from the button/voice-provider wiring, so the c-6 retention contract is verified in isolation.

- [test-contract / unverifiable-in-CI] t-9's third contract degrades to "manual verification (assist gesture -> tap -> answer renders) fails", and t-4's second contract is an instrumented/on-device test ("native engine not JVM-unit-testable"). Both are legitimate (UI gesture and native Tesseract genuinely resist JVM unit tests), but a plan leaning on manual/instrumented gates risks those criteria never being exercised by the normal `./gradlew testDebugUnitTest` gate.
  Suggestion: confirm an instrumented (androidTest) target exists/runs in the verify step for t-4's fixture-bitmap test; for t-9, ensure the askAboutScreen call and bitmap-non-persistence are unit-covered (the contract already delegates the call to t-7 — keep that), leaving only the literal tap as manual.

## NOTE
- [test-contract / strength] Contracts are unusually specific and failure-framed throughout — e.g. t-7 names the exact surfaces ("retries once with Assist text and no image", "ChatSession.turns holds the screen TEXT and never the image bytes"), and t-1 distinguishes the OpenAI `image_url` data-URI shape from the Anthropic `type:image` block. This is the opposite of the "tests pass" antipattern and made coverage easy to audit.
- [accuracy / strength] Plan claims verified against the repo hold up: the delete-cascade already iterates `CapabilitySlot.entries` (t-3's "Delete-cascade already covers all slots"), `supportsTools` exists as the stated precedent for `supportsImages`, the session already exposes a Compose surface via `onCreateContentView` for t-9's button, and AssistAnalyzer is a pure `ViewNodeLike`-based object that `extractText` (t-5) drops into cleanly. All three "missing" files (OcrEngine.kt, ScreenContext.kt, ScreenQueryGrammar.kt) are created by their own tasks before use.
- [decisions / strength] All four locked decisions (fallback_engine FOSS-OCR, screen_in_history text-only retention, vision_capability best-effort+degrade, blank_screen honest-note) map onto concrete tasks (t-4, t-7, t-2+t-5+t-7, t-5+t-7) rather than being asserted and forgotten.
- [wave-order] Wave ordering is tight: every cross-wave dependency is real (t-6→t-3 for setVisionSlot, t-7→t-1/t-4/t-5 for message+OCR+planner, t-8→t-7 for askAboutScreen, t-9→t-8 for the voice provider). No task could trivially drop a wave for parallelism. t-5's single declared dep on t-2 (not t-3) is correct because the planner takes mappings as inputs rather than reaching into ProviderRepository.

## Summary
A genuinely strong, well-grounded plan with full criterion coverage and specific contracts; the only worthwhile adjustments are merging the trivial t-2 and reconsidering whether t-9 squashes the security-critical bitmap-retention change in with UI wiring.
