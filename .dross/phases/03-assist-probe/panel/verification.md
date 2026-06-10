# Phase 03-assist-probe — plan (lens: design-backward-from-test-contracts)

Method: for each criterion I wrote the ideal test contract first, then carved out the
smallest unit that makes that contract satisfiable. The load-bearing move is pulling all
probe logic (structure analysis, screenshot metadata, record shape, CSV) behind plain
data classes + pure functions that never need a live `AssistStructure`, `Bitmap`, or the
OS assistant role — so c-3/c-4/c-5 are JVM-unit-testable, and only the genuinely
framework-bound surfaces (c-1 registration, c-2 gesture launch) fall to MANUAL contracts.

Phase 03-assist-probe — 7 tasks across 3 waves

Wave 1
  t-1  Define ProbeRecord + AssistAnalyzer
       files:    app/src/main/java/dev/equerry/app/assistant/ProbeRecord.kt
                 app/src/main/java/dev/equerry/app/assistant/AssistAnalyzer.kt
                 app/src/test/java/dev/equerry/app/assistant/AssistAnalyzerTest.kt
       covers:   c-3, c-4
       contract: AssistAnalyzer.countTextNodes over a hand-built tree of nested
                 ViewNode-like stand-ins returns the exact leaf-text count (3-node tree
                 with one empty-text node -> count == 2); a null root yields
                 structureProvided=false, nodeCount=0. screenshotMeta(width,height) with
                 a present bitmap yields screenshotArrived=true + those dims; a blocked
                 (null) screenshot yields screenshotArrived=false and null dims. If the
                 leaf-vs-container counting rule or the blocked-screenshot branch breaks,
                 AssistAnalyzerTest fails. ProbeRecord is a @Serializable data class
                 carrying packageName, timestamp, structureProvided, nodeCount,
                 screenshotArrived, screenshotWidth?, screenshotHeight? — and NO bitmap
                 field (enforces screenshot_retention lock; reviewed by t-2 round-trip).

  t-2  Add ProbeStore (DataStore append-only list)
       files:    app/src/main/java/dev/equerry/app/data/ProbeStore.kt
                 app/src/test/java/dev/equerry/app/data/ProbeStoreTest.kt
       covers:   c-5
       contract: append(record) followed by a fresh ProbeStore over the same
                 preferences_pb file returns the record (round-trips across simulated
                 restart, mirroring ProfileStoreTest). Two appends yield a 2-element list
                 in insertion order; empty store yields emptyList. If the JSON list
                 encode/decode or append-not-overwrite semantics break, ProbeStoreTest
                 fails. (Depends on ProbeRecord type from t-1.)

  t-4  Add CSV serializer for probe records
       files:    app/src/main/java/dev/equerry/app/assistant/ProbeCsv.kt
                 app/src/test/java/dev/equerry/app/assistant/ProbeCsvTest.kt
       covers:   c-5
       contract: ProbeCsv.toCsv(listOf(record)) emits a header row
                 (package,timestamp,structure,nodes,screenshot,width,height) then one row
                 per record; a record with a blocked screenshot renders empty width/height
                 cells and screenshot=false; a packageName containing a comma is quoted so
                 the column count stays 7. If column order, header, or comma-escaping
                 breaks, ProbeCsvTest fails. (Depends on ProbeRecord from t-1.)

Wave 2 (depends t-1, t-2)
  t-3  Add ProbeSessionViewModel + ProbeLogViewModel
       files:    app/src/main/java/dev/equerry/app/ui/probe/ProbeSessionViewModel.kt
                 app/src/main/java/dev/equerry/app/ui/probe/ProbeLogViewModel.kt
                 app/src/test/java/dev/equerry/app/ui/probe/ProbeViewModelTest.kt
       covers:   c-3, c-4, c-5
       contract: recording a ProbeRecord through ProbeSessionViewModel.onCapture(record)
                 exposes it as the current StateFlow value AND it appears in
                 ProbeLogViewModel.records backed by the same ProbeStore (FakeProbeStore
                 or tmp DataStore, mirroring ProviderListViewModelTest). exportCsv()
                 returns the t-4 CSV string for the stored records. If the capture->store
                 wiring or the export delegation breaks, ProbeViewModelTest fails.
       depends_on: t-1, t-2, t-4

  t-5  Build probe dashboard + log Compose screens
       files:    app/src/main/java/dev/equerry/app/ui/probe/ProbeSessionScreen.kt
                 app/src/main/java/dev/equerry/app/ui/probe/ProbeLogScreen.kt
                 app/src/test/java/dev/equerry/app/ui/probe/ProbeScreenTest.kt
       covers:   c-2, c-5
       contract: ProbeSessionScreen given a sample ProbeRecord displays the foreground
                 package text, "AssistStructure: yes (N nodes)", and
                 "Screenshot: arrived (WxH)"; given a blocked-screenshot record it shows
                 "Screenshot: blocked". ProbeLogScreen given two records renders two rows
                 and surfaces an "Export CSV" affordance (onNodeWithText/ContentDescription
                 assertIsDisplayed, Robolectric, mirroring ProviderListScreenTest). If the
                 dashboard labelling or the log/export affordance breaks, ProbeScreenTest
                 fails.
       depends_on: t-1, t-3

  t-7  Wire probe log route into nav graph
       files:    app/src/main/java/dev/equerry/app/MainActivity.kt
                 app/src/test/java/dev/equerry/app/NavigationTest.kt
       covers:   c-5
       contract: a new Route.PROBE constant + composable; the Home screen gains a
                 "Probe log" entry whose click navigates to Route.PROBE. Extend
                 NavigationTest: clicking "Probe log" leaves currentDestination?.route ==
                 Route.PROBE (TestNavHostController, mirroring existing
                 home_navigates_to_* tests). If the route registration or the home entry
                 wiring breaks, NavigationTest fails.
       depends_on: t-5

Wave 3 (depends t-3, t-5)
  t-6  Register VoiceInteractionService/Session + manifest
       files:    app/src/main/java/dev/equerry/app/assistant/EquerryVoiceInteractionService.kt
                 app/src/main/java/dev/equerry/app/assistant/EquerryVoiceInteractionSession.kt
                 app/src/main/res/xml/voice_interaction_service.xml
                 app/src/main/res/xml/interaction_service_settings_unused.txt
                 app/src/main/AndroidManifest.xml
       covers:   c-1, c-2, c-3, c-4
       description: VoiceInteractionService declared with
                 BIND_VOICE_INTERACTION + the recognition/session metadata so Equerry
                 appears in the Digital-assistant picker. Session's onHandleAssist /
                 onHandleScreenshot feed AssistAnalyzer (t-1) into ProbeSessionViewModel
                 (t-3) and launch ProbeSessionScreen (t-5).
       depends_on: t-1, t-3, t-5
       MANUAL contract (framework-bound, cannot unit-test):
         c-1: install debug build; Settings > Default apps > Digital assistant lists
              "Equerry"; selecting it makes it active (returns from picker as selected).
         c-2: perform the assist gesture/long-press-home; Equerry's ProbeSessionScreen
              appears (not Gemini), showing the live capture.
         c-3/c-4 at integration: invoke over a known app; the on-screen dashboard and the
              persisted log row show structure y/n + node count and screenshot
              arrived/blocked + dims. (The analysis+record logic these depend on is
              unit-covered by t-1/t-2/t-3; this manual step only confirms the OS actually
              delivers the callbacks.)

## Coverage
- c-1: t-6 (MANUAL)
- c-2: t-5 (screen content), t-6 (MANUAL gesture launch)
- c-3: t-1 (analyzer), t-3 (capture->state), t-6 (MANUAL integration)
- c-4: t-1 (screenshot meta), t-3 (capture->state), t-6 (MANUAL integration)
- c-5: t-2 (persistence), t-3 (record+export wiring), t-4 (CSV), t-5 (log+export UI), t-7 (route)

Every criterion (c-1..c-5) has at least one task; every framework-bound criterion has a
named MANUAL contract plus unit coverage of the logic behind it.

## Judgment calls
- Split probe logic (t-1 AssistAnalyzer) from the OS Session (t-6) so c-3/c-4 node-count
  and screenshot-metadata rules are pure JVM unit tests; rejected putting analysis inside
  the VoiceInteractionSession, which would have made c-3/c-4 testable only by MANUAL.
- ProbeRecord deliberately has no bitmap field and the analyzer only takes width/height;
  rejected passing a Bitmap through the record — that would violate the locked
  screenshot_retention decision and leave nothing CSV-serializable.
- t-4 CSV is its own task (pure function) rather than folded into the export UI; rejected
  embedding CSV building in the screen/ViewModel because comma/quote-escaping deserves a
  dedicated contract and is the most likely silent breakage.
- t-2 ProbeStore is append-only (mirrors the locked "one record per invocation"); rejected
  reusing ProfileStore's save(whole-list) shape, which invites read-modify-write races
  across separate invocations.
- t-7 (nav route) kept separate from t-5 (screens) and placed in wave 2 alongside it: the
  route needs the screen composables to exist (depends t-5) but not the OS Session, so it
  does not wait on wave 3.
- t-3 placed in wave 2 depending on t-4 as well as t-1/t-2 so exportCsv() can delegate to
  the real serializer in its contract; rejected stubbing CSV in the ViewModel test, which
  would have let a serializer regression pass.
