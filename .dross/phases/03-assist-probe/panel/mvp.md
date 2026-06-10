Phase 03-assist-probe — 5 tasks across 3 waves

Wave 1
  t-1  Probe record model + structure analyzer
       files:    app/src/main/java/dev/equerry/app/assistant/ProbeRecord.kt
                 app/src/test/java/dev/equerry/app/assistant/AssistStructureAnalyzerTest.kt
       covers:   c-3, c-4
       contract: ProbeRecord is a serializable data class holding {timestamp, packageName,
                 structureProvided: Boolean, textNodeCount: Int, screenshotProvided: Boolean,
                 screenshotBlocked: Boolean, screenshotWidth: Int?, screenshotHeight: Int?}.
                 A pure analyzer fn countTextNodes(root: ViewNodeLike): Int walks a node tree
                 abstraction (interface mirroring AssistStructure.ViewNode: text + children)
                 and sums nodes whose text is non-null/non-empty.
                 No bitmap is ever a field (locked: screenshot_retention).
       contract: countTextNodes over a 3-deep tree with 4 text-bearing nodes among 7 returns 4;
                 an empty/no-text tree returns 0. If node recursion or the text predicate
                 breaks, AssistStructureAnalyzerTest fails.
       depends_on: []

  t-2  CSV serializer for probe records
       files:    app/src/main/java/dev/equerry/app/assistant/ProbeCsv.kt
                 app/src/test/java/dev/equerry/app/assistant/ProbeCsvTest.kt
       covers:   c-5
       contract: toCsv(records: List<ProbeRecord>): String emits a header row then one row per
                 record (timestamp, app, structure y/n, node count, screenshot arrived/blocked,
                 w, h); fields containing comma/quote are RFC-4180 quoted.
       contract: toCsv of two records yields 3 lines with the fixed header first; a packageName
                 containing a comma is wrapped in double-quotes. If column order or quoting
                 breaks, ProbeCsvTest fails.
       depends_on: []

Wave 2 (depends t-1)
  t-3  Persist probe records via DataStore
       files:    app/src/main/java/dev/equerry/app/data/ProbeLogStore.kt
                 app/src/main/java/dev/equerry/app/di/PersistenceModule.kt
                 app/src/test/java/dev/equerry/app/data/ProbeLogStoreTest.kt
       covers:   c-3, c-4, c-5
       contract: ProbeLogStore (mirrors ProfileStore: DataStore<Preferences> + JSON list under
                 one key) exposes records(): Flow<List<ProbeRecord>> and suspend append(r).
                 append adds one record without dropping prior ones (locked: probe_persistence).
                 Provided as a @Singleton in PersistenceModule.
       contract: append three records then read back via a fresh store over the same temp file
                 (simulated restart) returns all three in insertion order; empty store returns
                 emptyList. If the key/JSON round-trip or append-not-overwrite breaks,
                 ProbeLogStoreTest fails.
       depends_on: [t-1]

  t-4  Probe log screen + ViewModel with CSV export
       files:    app/src/main/java/dev/equerry/app/ui/probe/ProbeLogViewModel.kt
                 app/src/main/java/dev/equerry/app/ui/probe/ProbeLogScreen.kt
                 app/src/main/java/dev/equerry/app/MainActivity.kt
                 app/src/test/java/dev/equerry/app/ui/probe/ProbeLogViewModelTest.kt
       covers:   c-5
       contract: ProbeLogViewModel exposes state: StateFlow<List<ProbeRecord>> from
                 ProbeLogStore.records() and exportCsv(): String delegating to ProbeCsv.
                 ProbeLogScreen renders one row per record and an Export button that fires the
                 ACTION_SEND share-sheet with the CSV text. Reachable from HomeScreen via a new
                 Route.PROBE entry in EquerryNavHost.
       contract: with two records seeded in a fake/temp-file store, ProbeLogViewModelTest sees
                 state emit a 2-element list and exportCsv() returns a 3-line CSV (header + 2).
                 If wiring store→state or export→ProbeCsv breaks, ProbeLogViewModelTest fails.
       depends_on: [t-1, t-2, t-3]

Wave 3 (depends t-3)
  t-5  Register VoiceInteractionService + assist session dashboard
       files:    app/src/main/java/dev/equerry/app/assistant/EquerryInteractionService.kt
                 app/src/main/java/dev/equerry/app/assistant/EquerryInteractionSession.kt
                 app/src/main/AndroidManifest.xml
                 app/src/main/res/xml/interaction_service.xml
                 app/src/main/res/xml/recognition_service.xml
       covers:   c-1, c-2, c-3, c-4
       contract: EquerryInteractionSession.onHandleAssist captures the AssistStructure
                 (node count via t-1's analyzer over a ViewNode->ViewNodeLike adapter) and, in
                 onHandleScreenshot, records screenshot arrived/blocked + bitmap.width/height
                 (metadata only, no bitmap stored), builds a ProbeRecord and calls
                 ProbeLogStore.append; the session shows a Compose dashboard of the current
                 capture (locked: session_ui). Manifest declares the service with
                 BIND_VOICE_INTERACTION + the two xml metadata files.
       contract (testable): the ViewNode->ViewNodeLike adapter is unit-covered — given a stub
                 AssistStructure-shaped tree, the adapter+countTextNodes produce the expected
                 count (extend AssistStructureAnalyzerTest with the adapter case).
       contract (MANUAL, OS-bound — c-1/c-2): at verify time, on a device/emulator, Settings >
                 Apps > Default apps > Digital assistant app lists "Equerry"; selecting it and
                 performing the assist gesture launches Equerry's session dashboard (not Gemini),
                 and that dashboard shows the foreground app, AssistStructure arrived + node
                 count, and screenshot arrived/blocked + size for the invocation.
       depends_on: [t-3]

## Coverage
- c-1 → t-5 (manual verify)
- c-2 → t-5 (manual verify; dashboard content is real)
- c-3 → t-1 (node-count logic), t-3 (persist), t-5 (capture)
- c-4 → t-1 (record fields), t-3 (persist), t-5 (capture)
- c-5 → t-2 (CSV), t-3 (persist), t-4 (on-screen log + export)

## Judgment calls
- Merged "record model" and "node analyzer" into one task (t-1): both are pure data/logic in the
  same package, the analyzer's only consumer is the record builder, and split would be <10 min
  each. Rejected a separate model task.
- Pulled all testable logic (node counting, CSV, persistence, export wiring) out of the
  OS-bound VoiceInteractionService into t-1..t-4 so t-5 is the only manual-verify task.
  Rejected building capture inside the session directly (would make c-3/c-4 untestable).
- t-5 kept as one task despite touching 5 files: they are one layer (the assistant entry point)
  and all four files are inert without each other (service+session+manifest+xml must ship
  together to register at all). Splitting would create non-shippable intermediate states.
- Folded CSV export UI into the log screen ViewModel (t-4) rather than a separate export task —
  export is one method delegating to t-2 plus a share intent; a standalone task would be a
  single-file <10 min unit. Rejected an "export" task.
- Did NOT add a not-default-assistant guidance task (spec defers it) and no separate manifest
  task (manifest changes belong with the service that needs them, t-5).
- recognition_service.xml is included in t-5 because some OEM assistant pickers only surface a
  VoiceInteractionService that also advertises a recognition service; it ships with the same
  registration and has no independent testable surface.
