# Synthesis — 03-assist-probe (cold judge merge of risk / mvp / verification)

## Scores

Scale: weak / ok / strong.

| Draft | Criteria coverage | Test-contract specificity | Granularity | Wave correctness |
|-------|-------------------|---------------------------|-------------|------------------|
| risk | strong — all 5 owned; c-1/c-2 explicit MANUAL on t-7; logic pulled behind abstractions | strong — only draft with a concrete concurrency contract (50 concurrent appends inside one `edit`) | ok — 7 tasks, but node-count/screenshot split may be finer than the shared model warrants | ok — but no on-screen *log* screen separate from the session view; c-5 "exported for review" leans on the session dashboard only |
| mvp | ok — all 5 owned, but c-2's automated side is thin (no screen test); records `recognition_service.xml` no other draft has | ok — clear contracts, but no UI-level automated test; adapter contract noted but lighter | strong — 5 tasks, tightest defensible split, merges model+analyzer | ok — t-5 depends only on t-3, sensible; folds nav into the log screen task |
| verification | strong — every criterion has a unit OR screen test plus MANUAL where OS-bound; only draft with automated screen + nav coverage of c-2/c-5 | strong — contracts written test-first; Robolectric screen test + TestNavHostController nav test are uniquely concrete | ok — 7 tasks; separates session screen, log screen, nav route | strong — cleanest wave logic: nav route (t-7) in wave 2 because it needs screens not the OS session; t-3 pulled to depend on CSV so export delegates to the real serializer |

**Skeleton: `verification`.** It has the strongest test-contract layer (the only draft that gives c-2 and c-5 *automated* coverage via Robolectric screen tests + a nav test, instead of resting entirely on MANUAL), and the cleanest wave dependencies (nav route correctly in wave 2, ViewModel depending on the CSV serializer so `exportCsv()` is contract-tested against the real code). It keeps the same pure-logic-behind-abstractions spine the other two share, so grafting onto it costs little.

## Merged plan

Format: `wave → t-N  title  [origin]` then files / covers / contract / depends_on. Origin tags name which draft(s) the task (or the grafted detail) came from. No task below appears outside the three drafts.

### Wave 1

```
t-1  Define ProbeRecord + AssistAnalyzer                          [verification, =mvp.t-1, =risk.t-1+t-2]
     files:   app/src/main/java/dev/equerry/app/assistant/ProbeRecord.kt
              app/src/main/java/dev/equerry/app/assistant/AssistAnalyzer.kt
              app/src/test/java/dev/equerry/app/assistant/AssistAnalyzerTest.kt
     covers:  c-3, c-4
     contract: @Serializable data class ProbeRecord(packageName, timestamp,
              structureProvided, nodeCount, screenshotArrived, screenshotBlocked,
              screenshotWidth: Int?, screenshotHeight: Int?) — NO bitmap/byte[] field
              (locked: screenshot_retention; structurally enforced, not by convention
              [graft: risk.t-1]). AssistAnalyzer.countTextNodes over a ViewNode-like
              tree abstraction: null root -> structureProvided=false, count=0; a tree
              with 3 text-bearing + 2 blank/null-text nodes -> count=3; a 4-level
              nested tree counts all descendants (recursion bug -> count off, test
              fails) [graft: risk.t-2 sharper recursion + blank-node case].
              screenshotMeta(width: Int?, height: Int?): null dims -> arrived=false,
              blocked=true, dims null; 1080x2400 -> arrived=true, blocked=false,
              w=1080, h=2400 — signature takes no Bitmap so pixels can't leak past it
              [graft: risk.t-3 Int?-only signature]. Breakage of recursion, the
              blank-text predicate, or the blocked branch fails AssistAnalyzerTest.
     depends_on: []
```

```
t-2  Add ProbeStore (DataStore append-only list)                 [verification, =mvp.t-3, =risk.t-5]
     files:   app/src/main/java/dev/equerry/app/data/ProbeStore.kt
              app/src/main/java/dev/equerry/app/di/PersistenceModule.kt   [graft: risk.t-5 / mvp.t-3 — DI provider file]
              app/src/test/java/dev/equerry/app/data/ProbeStoreTest.kt
     covers:  c-5 (+ persistence backing c-3, c-4)
     contract: ProbeStore over the existing settings DataStore, JSON list under one key,
              mirroring ProfileStore. append(record) read-modify-writes inside ONE
              dataStore.edit; records(): Flow; provided @Singleton from PersistenceModule.
              Round-trip: append three then read via a fresh store over the same
              preferences_pb file (process-death sim) returns all three in insertion
              order; empty store -> emptyList. CONCURRENCY: 50 concurrent append() calls
              all land (size==50) — a read-then-write outside edit drops writes and the
              test fails [graft: risk.t-5 concurrency contract; the single strongest
              contract in any draft, retained verbatim].
     depends_on: [t-1]
```

```
t-4  Add CSV serializer for probe records                        [verification, =mvp.t-2, =risk.t-4]
     files:   app/src/main/java/dev/equerry/app/assistant/ProbeCsv.kt
              app/src/test/java/dev/equerry/app/assistant/ProbeCsvTest.kt
     covers:  c-5
     contract: toCsv(records): RFC-4180 — fixed header
              (package,timestamp,structure,nodes,screenshot,width,height) then one row
              per record; blocked-screenshot record -> empty width/height cells,
              screenshot=false; a packageName containing a comma is double-quoted;
              a field containing a double-quote has it doubled ("a""b") so the
              round-trip parse in the test holds [graft: risk.t-4 quote-doubling +
              round-trip-parse assertion]; empty list -> header row only, no crash, no
              trailing blank [graft: risk.t-4 empty-list case]. Breakage of column
              order, header, or escaping fails ProbeCsvTest.
     depends_on: [t-1]
```

### Wave 2 (depends t-1, t-2, t-4)

```
t-3  Add ProbeSessionViewModel + ProbeLogViewModel               [verification]
     files:   app/src/main/java/dev/equerry/app/ui/probe/ProbeSessionViewModel.kt
              app/src/main/java/dev/equerry/app/ui/probe/ProbeLogViewModel.kt
              app/src/test/java/dev/equerry/app/ui/probe/ProbeViewModelTest.kt
     covers:  c-3, c-4, c-5
     contract: onCapture(record) exposes it as the current-capture StateFlow value AND
              it appears in ProbeLogViewModel.records backed by the same ProbeStore
              (fake/tmp DataStore). exportCsv() delegates to the real ProbeCsv (t-4) over
              stored records, so a serializer regression is caught here, not stubbed
              away. A second invocation appends (does not overwrite) the first
              [graft: risk.t-6 append-not-overwrite assertion]. Breakage of capture->store
              wiring or export delegation fails ProbeViewModelTest.
     depends_on: [t-1, t-2, t-4]
```

```
t-5  Build probe dashboard + log Compose screens                 [verification]
     files:   app/src/main/java/dev/equerry/app/ui/probe/ProbeSessionScreen.kt
              app/src/main/java/dev/equerry/app/ui/probe/ProbeLogScreen.kt
              app/src/test/java/dev/equerry/app/ui/probe/ProbeScreenTest.kt
     covers:  c-2, c-5
     contract: ProbeSessionScreen given a sample record shows the foreground package,
              "AssistStructure: yes (N nodes)", "Screenshot: arrived (WxH)"; a
              blocked-screenshot record shows "Screenshot: blocked". ProbeLogScreen given
              two records renders two rows + an "Export CSV" affordance. Robolectric
              onNodeWithText/assertIsDisplayed, mirroring ProviderListScreenTest. This is
              the only AUTOMATED coverage of c-2 dashboard content + c-5 log/export UI.
     depends_on: [t-1, t-3]
```

```
t-7  Wire probe log route into nav graph                         [verification]
     files:   app/src/main/java/dev/equerry/app/MainActivity.kt
              app/src/test/java/dev/equerry/app/NavigationTest.kt
     covers:  c-5
     contract: new Route.PROBE + composable; Home gains a "Probe log" entry whose click
              navigates to Route.PROBE. NavigationTest (TestNavHostController) asserts
              currentDestination?.route == Route.PROBE after the click. Breakage of route
              registration or the home entry fails NavigationTest.
     depends_on: [t-5]
```

### Wave 3 (depends t-3, t-5)

```
t-6  Register VoiceInteractionService/Session + manifest         [verification, =mvp.t-5, =risk.t-7]
     files:   app/src/main/java/dev/equerry/app/assistant/EquerryVoiceInteractionService.kt
              app/src/main/java/dev/equerry/app/assistant/EquerryVoiceInteractionSession.kt
              app/src/main/res/xml/voice_interaction_service.xml
              app/src/main/res/xml/recognition_service.xml           [graft: mvp.t-5 — OEM picker compat]
              app/src/main/AndroidManifest.xml
              app/src/main/res/values/strings.xml                    [graft: risk.t-7 — service label string]
     covers:  c-1, c-2, c-3, c-4
     description: VoiceInteractionService declared with BIND_VOICE_INTERACTION + the
              recognition/session metadata so Equerry appears in the Digital-assistant
              picker. Session onHandleAssist/onHandleScreenshot feed AssistAnalyzer (t-1)
              into ProbeSessionViewModel (t-3) and launch ProbeSessionScreen (t-5).
     AUTOMATED: extend AssistAnalyzerTest with the ViewNode -> ViewNodeLike adapter case —
              a stub AssistStructure-shaped tree through the adapter+countTextNodes yields
              the expected count [graft: mvp.t-5 adapter unit case; closes the one bit of
              t-6 logic that IS unit-testable]. A lint/assembleDebug check confirms the
              service + xml meta-data parse (broken meta-data fails manifest merge)
              [graft: risk.t-7 build-parse check].
     MANUAL (framework-bound, c-1/c-2):
       c-1: install debug build; Settings > Default apps > Digital assistant lists
            "Equerry"; selecting it makes it the active assistant.
       c-2: perform the assist gesture/long-press-home; Equerry's ProbeSessionScreen
            appears (NOT Gemini), showing the live capture (foreground app, structure
            arrived + node count, screenshot arrived/blocked + size).
     depends_on: [t-1, t-3, t-5]
```

**Merged total: 7 tasks across 3 waves.**

## Disagreements

### D-1 — On-screen log: separate app screen vs. session-only dashboard
- **risk**: NO standalone log screen. The session content view *is* the dashboard, and "exported for review" (c-5) is served by the share-sheet from within the session. No nav route, no in-app log list.
- **mvp**: a separate ProbeLogScreen + ViewModel, reachable from Home, with the Export button (nav folded into the log task).
- **verification**: a separate ProbeLogScreen *and* a dedicated nav-route task (t-7) with an automated NavigationTest.
- **Provisional default**: keep the separate log screen + nav route (verification/mvp side; t-5 + t-7).
- **Why it matters**: c-5 says probe results "are shown in an on-screen log that can be exported for review" — results accumulate "across many separate invocations" (probe_persistence decision). A session-only dashboard shows *one* invocation; it cannot show the accumulated log without the app being re-invoked. The risk draft's reading under-serves c-5's "log" wording. This is the single biggest structural divergence and the reason the merged plan carries 7 tasks, not 5. Flag for human confirmation: if the spec author intended c-5's "log" to mean the session dashboard alone, drop t-7 and the ProbeLogScreen, collapsing toward risk's shape (~5 tasks).

### D-2 — Granularity of the pure probe logic (1 task vs 3)
- **risk**: three tasks — record model (t-1), node counter (t-2), screenshot classifier (t-3) — "one owner per risk surface".
- **mvp**: one task (model + analyzer merged); screenshot fields live in the record, classification is trivial.
- **verification**: one task (ProbeRecord + AssistAnalyzer together).
- **Provisional default**: ONE task (t-1), per verification/mvp.
- **Why it matters**: the three pure functions share the same ProbeRecord type and the same test file would naturally co-locate them; the risk split buys isolated ownership but creates two extra tasks whose contracts are each <10 min. The merge keeps risk's *contracts* (recursion/blank-node, Int?-only screenshot signature, blocked branch) without paying for three task envelopes. Matters because it sets task count and review overhead; the risk reviewer would object that a node-recursion regression and a screenshot-privacy regression now share one task — acceptable because both are covered by distinct assertions in AssistAnalyzerTest.

### D-3 — Does t-3 (ViewModel) depend on the CSV serializer?
- **risk**: t-6 ViewModel depends on t-1, t-2, t-3, t-5 (store + analyzers) — CSV (t-4) is consumed by the VM but risk lists exportCsv under the VM without a hard CSV dep ordering it before.
- **mvp**: t-4 (log VM) depends on [t-1, t-2, t-3] — includes the CSV task.
- **verification**: t-3 depends on [t-1, t-2, t-4] explicitly so exportCsv() is contract-tested against the real serializer.
- **Provisional default**: t-3 depends on t-4 (verification), and t-4 is therefore in wave 1.
- **Why it matters**: if the VM's export contract stubs CSV, a serializer regression slips through the VM test. Ordering t-4 before t-3 lets exportCsv() be asserted against real output. Cheap, strictly safer; no reviewer rejects it.

### D-4 — recognition_service.xml present or not
- **mvp**: includes `recognition_service.xml` — some OEM assistant pickers only surface a VoiceInteractionService that also advertises a recognition service.
- **risk / verification**: do not include it (verification names a `voice_interaction_service.xml` only; risk names `interaction_service.xml`).
- **Provisional default**: INCLUDE it (mvp side), grafted into t-6.
- **Why it matters**: c-1 ("appears in the picker") is the riskiest OS-bound criterion and the hardest to recover if the picker silently omits Equerry on some OEMs. The extra inert xml is low-cost insurance for the single hardest-to-test criterion. Flag: if the target test device surfaces the service without it, the file can be dropped — but defaulting it in protects c-1.

### D-5 — Automated UI/nav tests: worth the task weight?
- **verification**: yes — Robolectric ProbeScreenTest (c-2 content, c-5 export affordance) and a TestNavHostController NavigationTest (c-5 route).
- **risk / mvp**: no UI-level automated tests; c-2/c-5 UI rests on MANUAL + ViewModel logic tests.
- **Provisional default**: KEEP the automated UI + nav tests (verification).
- **Why it matters**: they convert c-2 (dashboard content) and c-5 (log + export) from MANUAL-only into partly automated coverage, which is exactly the verification lens's value-add and the reason it was chosen as skeleton. Risk/mvp reviewers would call them optional; retained because they are the only mechanical guard on the UI labelling the MANUAL step also checks.

---
synthesis: 7 tasks across 3 waves, 5 disagreements
