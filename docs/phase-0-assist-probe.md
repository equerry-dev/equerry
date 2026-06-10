# Phase-0 Assist Probe — Results Table

**Milestone v0.1 HARD GATE.** Does the sanctioned Assist API give Equerry enough to
work with — per real app, did `AssistStructure` arrive (and how many text nodes), and
did an assist screenshot arrive or get blocked?

- **Device:** Fairphone FP5
- **OS:** Android 15
- **Date:** 2026-06-10
- **Method:** Equerry set as the system default digital assistant; assist invoked
  (`KEYCODE_ASSIST`) over each foreground app. Captured by `EquerryVoiceInteractionSession`
  → `ProbeCapture` → `ProbeStore`. Screenshot dimensions only are recorded — never the
  bitmap (privacy / `screenshot_retention`).

## Results

| # | App | AssistStructure | Text nodes | Screenshot | Dimensions |
|---|-----|-----------------|------------|------------|------------|
| 1 | `com.android.settings` | yes | 37 | **blocked** | — |
| 2 | `com.android.settings` | yes | 37 | arrived | 1224×2700 |
| 3 | `com.android.chrome` | yes | 14 | arrived | 1224×2700 |
| 4 | `com.anthropic.claude` | yes | 4 | arrived | 1224×2700 |
| 5 | `com.fairphone.myfairphone` | yes | 14 | arrived | 1224×2700 |
| 6 | `bbc.mobile.news.uk` | yes | 13 | arrived | 1224×2700 |
| 7 | `com.discord` | yes | **0** | arrived | 1224×2700 |
| 8 | `ch.protonmail.android` | yes | 38 | arrived | 1224×2700 |
| 9 | `unknown` (window transition) | **no** | 0 | arrived | 1224×2700 |
| 10 | `co.uk.Nationwide.Mobile` (banking) | yes | **1** | **blocked** | — |
| 11 | `com.duckduckgo.mobile.android` | yes | 7 | arrived | 1224×2700 |

10 distinct apps probed (Settings captured twice); row 9 is an incidental
window-transition capture that usefully exercises the no-structure path.

## Findings

- **AssistStructure arrives broadly.** Every real foreground app returned a structure
  (node counts 0–38). The Assist API is a viable screen-context source — Equerry does
  not need AccessibilityService (rule r-01 holds).
- **Structure ≠ useful text.** `com.discord` returned a structure with **0 text nodes**
  (heavily custom-rendered UI). Node count, not mere presence, is the signal to watch
  for the screen-context feature (Phase 3).
- **Secure apps restrict assist.** `co.uk.Nationwide.Mobile` (banking) exposed only
  **1 node and blocked the screenshot** (`FLAG_SECURE`). Expect degraded/empty context
  on banking, password, and DRM screens — the feature must degrade gracefully.
- **Screenshots usually arrive** at full resolution (1224×2700) but are **not
  guaranteed** — blocked on the secure app and on one Settings capture (timing/transition).
  Treat the assist screenshot as best-effort, never assumed.
- **No-structure path works.** The `structureProvided=false` capture (row 9) confirms the
  probe records absence correctly rather than crashing or dropping the record.

## Implications for later phases

- **Screen-context (Phase 3):** gate on `nodeCount > 0`; fall back to OCR-over-screenshot
  only when a screenshot actually arrives, and show clear "no context available" UX for
  secure screens.
- **Privacy:** the no-bitmap-by-type design held end to end — only dimensions were ever
  persisted.

_Raw records live in the app DataStore (`equerry_settings.preferences_pb`,
`probe_records_json`) and are exportable as CSV from the in-app Probe log screen._
