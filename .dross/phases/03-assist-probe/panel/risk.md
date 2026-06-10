# Risk-lens decomposition — 03-assist-probe

Bias: failure modes drive the graph. The Assist API hands us *untrusted, frequently-absent
input* — AssistStructure is null on many apps, the screenshot is blocked by FLAG_SECURE or
policy, node trees can be deep/wide, invocations fire concurrently and across process death,
and a locked privacy rule forbids ever persisting the bitmap. Each of these break-points is
owned and tested by exactly one task. The OS-bound surfaces (manifest role, gesture launch)
are isolated into a thin shell so every decision *derived from* OS input lives in a pure,
testable unit.

Phase 03-assist-probe — 7 tasks across 3 waves

Wave 1
  t-1  Define probe record model + extractors
       files:    app/src/main/java/dev/equerry/app/assistant/ProbeRecord.kt
                 app/src/test/java/dev/equerry/app/assistant/ProbeRecordTest.kt
       covers:   c-3, c-4
       desc:     Pure data class ProbeRecord(timestamp, packageName, structureArrived:Bool,
                 textNodeCount:Int, screenshotArrived:Bool, screenshotBlocked:Bool,
                 screenshotWidth:Int?, screenshotHeight:Int?). No Android types in the
                 fields — bitmap is NEVER a field (locked: screenshot_retention).
       contract: a ProbeRecord cannot be constructed holding a Bitmap/raw pixel data — the
                 type has no such field; test asserts the only image data are nullable Int
                 dimensions, failing if a bitmap/byte[] field is reintroduced.

  t-2  Count text nodes from AssistStructure
       files:    app/src/main/java/dev/equerry/app/assistant/AssistStructureProbe.kt
                 app/src/test/java/dev/equerry/app/assistant/AssistStructureProbeTest.kt
       covers:   c-3
       desc:     Function over an abstracted node tree (interface ProbeNode { text, children })
                 returning (arrived, textNodeCount). Walks all windows/roots, counts only
                 nodes whose text is non-null/non-blank. AssistStructure adapter maps the
                 real API onto ProbeNode so the walker is JVM-unit-testable.
       contract: null structure -> arrived=false, count=0; a tree with 3 text-bearing nodes
                 + 2 blank/null-text nodes returns count=3; a 4-level nested tree counts
                 descendants (recursion bug -> count off, test fails).

  t-3  Classify assist screenshot (arrived vs blocked)
       files:    app/src/main/java/dev/equerry/app/assistant/ScreenshotProbe.kt
                 app/src/test/java/dev/equerry/app/assistant/ScreenshotProbeTest.kt
       covers:   c-4
       desc:     Function mapping (bitmapWidth:Int?, bitmapHeight:Int?) onto
                 (arrived, blocked, width, height); reads dimensions only, drops the bitmap
                 reference immediately (locked: screenshot_retention). null dims = blocked.
       contract: null dimensions -> arrived=false, blocked=true, width/height null; a
                 1080x2400 input -> arrived=true, blocked=false, width=1080, height=2400;
                 the function signature accepts no Bitmap so pixels cannot leak past it.

  t-4  CSV serializer for probe records
       files:    app/src/main/java/dev/equerry/app/assistant/ProbeCsv.kt
                 app/src/test/java/dev/equerry/app/assistant/ProbeCsvTest.kt
       covers:   c-5
       desc:     Renders List<ProbeRecord> to RFC-4180 CSV (header + one row each). Quotes/
                 escapes fields — package names can contain commas, quotes, newlines.
       contract: empty list -> header row only (no crash, no trailing blank); a package name
                 containing a comma is wrapped in quotes; a field containing a double-quote
                 has it doubled ("a""b"); failing escaping corrupts column alignment and the
                 round-trip parse in the test fails.
       depends_on: t-1

Wave 2 (depends t-1)
  t-5  Persist probe records via DataStore (append)
       files:    app/src/main/java/dev/equerry/app/data/ProbeLogStore.kt
                 app/src/main/java/dev/equerry/app/di/PersistenceModule.kt
                 app/src/test/java/dev/equerry/app/data/ProbeLogStoreTest.kt
       covers:   c-3, c-4, c-5
       desc:     ProbeLogStore over the existing settings DataStore (key "probe_log_json"),
                 mirroring ProfileStore's StoredX-DTO pattern. append(record) reads-modify-
                 writes the JSON list inside one dataStore.edit; records() exposes a Flow;
                 clear() empties it. Provide it from PersistenceModule.
       contract: a record survives a fresh DataStore over the same file (process-death sim,
                 mirrors ProfileStoreTest round-trip); 50 concurrent append() calls all land
                 (list size == 50) because each mutates inside a single edit block — a
                 read-then-write outside edit loses writes and the test fails; empty store ->
                 records() emits emptyList.
       depends_on: t-1

Wave 2 (depends t-1, t-2, t-3, t-5)
  t-6  Probe ViewModel: capture, persist, expose, export
       files:    app/src/main/java/dev/equerry/app/assistant/ProbeViewModel.kt
                 app/src/test/java/dev/equerry/app/assistant/ProbeViewModelTest.kt
       covers:   c-3, c-4, c-5
       desc:     ViewModel assembling a ProbeRecord from t-2/t-3 outputs + foreground package,
                 calling ProbeLogStore.append, exposing current-capture state + full-log
                 StateFlow, and producing CSV text (t-4) for export. Injectable so JVM tests
                 drive it with fakes (mirrors SlotsViewModelTest setup).
       contract: recordInvocation(pkg, null-structure, null-screenshot) appends a record with
                 structureArrived=false, screenshotBlocked=true, and current-capture state
                 reflects it; exportCsv() returns CSV whose row count == appended records + 1
                 header; a second invocation appends (does not overwrite) the first.
       depends_on: t-1, t-2, t-3, t-5

Wave 3 (depends t-6)
  t-7  Wire VoiceInteractionService/Session + dashboard UI; register in manifest
       files:    app/src/main/java/dev/equerry/app/assistant/EquerryVoiceInteractionService.kt
                 app/src/main/java/dev/equerry/app/assistant/EquerryVoiceInteractionSession.kt
                 app/src/main/java/dev/equerry/app/assistant/ProbeSessionScreen.kt
                 app/src/main/res/xml/interaction_service.xml
                 app/src/main/AndroidManifest.xml
                 app/src/main/res/values/strings.xml
       covers:   c-1, c-2, c-3, c-4, c-5
       desc:     VoiceInteractionService + Session that on onHandleAssist/onHandleScreenshot
                 feed AssistStructure + screenshot into the t-2/t-3 adapters via t-6, render
                 the Compose probe dashboard (foreground app, structure arrived + node count,
                 screenshot arrived/blocked + size) as the session content view, and offer a
                 CSV share-sheet export. Manifest declares the service with
                 BIND_VOICE_INTERACTION + the recognition/interaction-service meta-data.
       contract: MANUAL (OS-bound, c-1/c-2): on a device, Equerry appears in Settings >
                 Digital assistant app picker; once selected, the assist gesture/long-press
                 launches the Equerry probe dashboard (a visible Compose screen) and NOT
                 Gemini. AUTOMATED: the manifest/role glue is verified indirectly — the
                 session's capture path delegates to t-6 (covered by t-6's tests); a lint/
                 build check confirms the service + interaction_service.xml meta-data parse
                 (broken meta-data fails :app:assembleDebug / manifest merge).
       depends_on: t-6

## Coverage
- c-1 (assistant picker + becomes active): t-7 (MANUAL verify-time contract — OS role binding)
- c-2 (gesture launches Equerry session UI, not Gemini): t-7 (MANUAL verify-time contract)
- c-3 (record AssistStructure y/n + text-node count): t-1, t-2, t-5, t-6, t-7
- c-4 (record screenshot arrived/blocked + metadata): t-1, t-3, t-5, t-6, t-7
- c-5 (on-screen log + CSV export): t-4, t-5, t-6, t-7

Every criterion is owned by at least one automated-testable task except the two OS-bound
ones (c-1, c-2), which carry explicit MANUAL contracts on t-7 with all derivable logic
pulled into t-2/t-3/t-5/t-6 behind abstractions.

## Judgment calls
- Split node-counting (t-2) from screenshot-classification (t-3) into separate tasks: each
  is a distinct failure surface (null tree + recursion vs blocked-vs-arrived + privacy),
  and the risk lens demands one owner per risk; rejected merging into one "capture" unit.
- ProbeNode abstraction in t-2 instead of testing against android.app.assist.AssistStructure
  directly: AssistStructure is a sealed framework type that can't be built in a JVM unit
  test, so the recursion/null logic would be untestable; rejected an instrumented-only test
  because the spec's testable logic must run in fast unit tests.
- Screenshot metadata derived from Int? dimensions, never a Bitmap, even in the function
  signature (t-3): makes the locked never-persist-bitmap rule structurally enforced rather
  than convention; rejected passing the Bitmap in and "remembering not to store it".
- t-5 mutates inside a single dataStore.edit and is tested under 50 concurrent appends:
  append-per-invocation (locked) means real concurrent assist invocations race; a naive
  read-then-write would silently drop records. Rejected a separate "concurrency" task —
  the risk is intrinsic to append and belongs with its owner.
- t-1, t-2, t-3, t-4 are all wave-1 (t-4 depends only on t-1's model): none needs another's
  runtime output, so they parallelize; only the store (t-5), the assembler VM (t-6) and the
  OS shell (t-7) are forced into later waves. Rejected chaining the pure units serially.
- One service+session+UI+manifest task (t-7) despite spanning 5+ files: it is a single
  indivisible OS-binding shell with no internal logic worth a separate owner (all logic
  lives in t-1..t-6); splitting it would create tasks that can't be tested independently of
  the manifest. Rejected a separate "manifest-only" task as untestable in isolation.
