# Phase 06-tools-actions — synthesis

Judged the three drafts against the spec, the six criteria, the seven locked decisions, and the
actual source seams under `app/src/main/java/dev/equerry/app/...`. All referenced existing files are
real; `tools/actions/` holds only `package.kt`, so every action file is net-new in all three drafts.
Key seam facts that decided several calls below:

- `SseTokenParser.kt` is a stateless `object` with **separate** `parseOpenAiData` /
  `parseAnthropicData` functions — risk's t-3/t-4 split maps onto two real functions in one file.
- `OllamaStreamParser.kt` is a real **stateful** buffered parser. **Risk's plan never parses Ollama
  tool calls** (it has only Anthropic + OpenAI parse tasks); mvp and verification both include Ollama.
- OpenAI `tool_calls` arguments fragment across SSE events; the current `parseOpenAiData` is stateless
  and would need accumulation. Only risk names this fragmentation risk explicitly.
- `VoiceFlowController` is pure-Kotlin / Android-free and already references staged-action follow-ups
  from prior phases — the spoken-confirm task belongs there, as all three drafts place it.

## Scores

Scale 1–5. Dimensions: criteria coverage, test-contract specificity, granularity (right-sized tasks,
no artificial deps), wave correctness (parallel-safe waves, honest depends_on).

| Draft | Criteria coverage | Test-contract specificity | Granularity | Wave correctness |
| ----- | ----------------- | ------------------------- | ----------- | ---------------- |
| risk (11 tasks / 3 waves) | 5 — every criterion owned + a coverage table; but **omits Ollama tool parsing** | 5 — failure-mode contracts name exact wire formats, fragmentation, re-transcription | 3 — t-3/t-4 same-file split is justified; t-8/t-9 same-file sequential split is borderline over-fine | 4 — waves sound, but t-3/t-4 share `SseTokenParser.kt` so cannot truly parallelize as written |
| mvp (6 tasks / 3 waves) | 4 — all six covered incl. Ollama; c-2 voice path thinner | 3 — contracts present but coarser (t-4 bundles five behaviours into one ViewModel test) | 5 — well right-sized; merges defensible, no artificial deps | 5 — clean disjoint-file waves, honest depends |
| verification (9 tasks / 3 waves) | 5 — all six covered incl. Ollama; pure `ToolCall→ActionPlan` seam strengthens c-2/c-4/c-6 | 5 — contracts derived first, each crisp + JVM-unit-testable, named function signatures | 4 — capability flag as its own task (t-3) is thin; otherwise excellent seams | 5 — explicit per-task `depends`, parallel wave-1 seams genuinely disjoint |

**Skeleton: verification.** It scores highest combined and is the only draft built test-contract-first
with framework-free seams (returned `Intent`s, a pure `ToolCall→ActionPlan` mapper, a pure
`YesNoGrammar`) that make every criterion unit-assertable without Robolectric. It covers Ollama (risk's
gap) and right-sizes the intent builders (avoids risk's t-8/t-9 over-split). Graft from runners-up:
risk's sharper per-wire-format parser contract and its explicit malformed/incapable ViewModel
contract; mvp's reminder that the deterministic note needs no standalone task.

## Merged plan

```
Phase 06-tools-actions — 9 tasks across 3 waves

Wave 1  (pure seams — disjoint files, no cross-task deps)

  t-1  Add ToolCall token + tool-call parsers (OpenAI, Anthropic, Ollama)   [verification, contract grafted from risk]
       files:    app/src/main/java/dev/equerry/app/providers/drivers/ChatModels.kt
                 app/src/main/java/dev/equerry/app/providers/drivers/SseTokenParser.kt
                 app/src/main/java/dev/equerry/app/providers/drivers/OllamaStreamParser.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/SseTokenParserTest.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/OllamaStreamParserTest.kt
       covers:   c-1, c-5
       depends_on: —
       contract: ChatToken gains a ToolCall(name, argsJson, id) variant so the collect path can
                 distinguish prose from an action — drop it and ChatModels/parser tests fail to
                 compile. Per wire format (risk's sharper split, same file): an Anthropic
                 content_block_start(tool_use)+input_json_delta*+content_block_stop sequence yields ONE
                 ToolCall with reassembled JSON args, not Delta/Skip; an OpenAI multi-event
                 choices[].delta.tool_calls[].function.arguments stream ACCUMULATES fragments across
                 events into one assembled ToolCall at finish_reason "tool_calls" (note: parseOpenAiData
                 is currently stateless — this task adds the accumulation seam); an Ollama message
                 tool_call line yields a ToolCall. Any of the three with non-JSON / truncated args
                 yields TokenResult.Fail(ChatError.Malformed) — never a crash, never a half-parsed
                 ToolCall, never prose.

  t-2  Register five tool specs in request body                            [verification+risk]
       files:    app/src/main/java/dev/equerry/app/tools/ToolSpecs.kt
                 app/src/main/java/dev/equerry/app/providers/drivers/ChatRequestBuilder.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/ChatRequestBuilderTest.kt
                 app/src/test/java/dev/equerry/app/tools/ToolSpecsTest.kt
       covers:   c-1, c-4
       depends_on: —
       contract: ToolSpecs is the single source for EXACTLY five tools (set_timer, set_alarm,
                 create_calendar_event, draft_email, draft_sms) each with its locked param set — drop,
                 rename, or add a sixth and the "exactly five, each with its params" count/name
                 assertion fails (tool_scope locked). The Anthropic body carries a top-level tools
                 array (input_schema shape) with all five; the OpenAI body carries tools of
                 type:"function" with all five; the OLLAMA body, judged tool-incapable best-effort,
                 carries NO tools array. An assertion that no tool spec / header / arg ever contains
                 the configured key string guards r-03.

  t-3  Add per-type tool-capability flag                                   [verification]
       files:    app/src/main/java/dev/equerry/app/providers/ProviderType.kt
                 app/src/test/java/dev/equerry/app/providers/ProviderTypeTest.kt
       covers:   c-5
       depends_on: —
       contract: ProviderType exposes supportsTools; ANTHROPIC, OPENAI_COMPATIBLE, OPENROUTER report
                 true; OLLAMA reports its best-effort value (judged incapable). Flip any row and the
                 table-test fails. (unsupported_provider_ux: capability judged at driver level,
                 best-effort.)

  t-4  Pure intent-builders for the five actions                          [verification, side-effect split grafted from risk]
       files:    app/src/main/java/dev/equerry/app/tools/actions/TimerAlarmIntents.kt
                 app/src/main/java/dev/equerry/app/tools/actions/HandoffIntents.kt
                 app/src/test/java/dev/equerry/app/tools/actions/TimerAlarmIntentsTest.kt
                 app/src/test/java/dev/equerry/app/tools/actions/HandoffIntentsTest.kt
       covers:   c-3, c-4
       depends_on: —
       contract: builders RETURN Intents (never startActivity), so action-string + extras are direct
                 assertions. setTimerIntent(300) -> ACTION_SET_TIMER, EXTRA_LENGTH=300, EXTRA_SKIP_UI
                 =false; setAlarmIntent -> ACTION_SET_ALARM with hour/minute; calendarIntent ->
                 ACTION_INSERT on Events.CONTENT_URI with TITLE + BEGIN/END extras; emailIntent ->
                 ACTION_SENDTO mailto: with subject/body; smsIntent -> ACTION_SENDTO sms: with body.
                 No builder emits ACTION_SEND/ACTION_EDIT or any auto-commit action — the "stages,
                 never commits (r-02)" assertion fails if it does. (Two files keep the r-02 boundary
                 — benign on-device vs outward hand-off — visible per benign_voice_confirm.)

  t-5  Pure ToolCall -> ActionPlan mapper                                  [verification]
       files:    app/src/main/java/dev/equerry/app/tools/actions/Action.kt
                 app/src/main/java/dev/equerry/app/tools/actions/ActionPlanner.kt
                 app/src/test/java/dev/equerry/app/tools/actions/ActionPlannerTest.kt
       covers:   c-2, c-4, c-5, c-6
       depends_on: —
       contract: planner(listOf(timerCall)) -> a single Staged(Timer) entry, NOT fired; planner of one
                 email call -> a single Handoff(Email) with recipient/subject/body threaded from args;
                 planner of [timer, email, alarm] -> a 3-entry plan preserving type + order (c-6); a
                 call with a missing/invalid required arg -> a Malformed entry, not a thrown exception
                 (feeds c-5).

  t-6  Pure yes/no grammar recogniser                                     [verification]
       files:    app/src/main/java/dev/equerry/app/voice/YesNoGrammar.kt
                 app/src/test/java/dev/equerry/app/voice/YesNoGrammarTest.kt
       covers:   c-2
       depends_on: —
       contract: classify("yes")/("yeah")/("start it") -> Yes; ("no")/("cancel")/("stop") -> No;
                 ("what time is it")/("maybe") -> Unrecognised. A narrow grammar — an off-topic or
                 ambiguous utterance NEVER collapses to Yes, so it can't fire a timer (mis-recognition
                 safeguard, timer_alarm_confirm).

Wave 2  (consume the wave-1 seams)

  t-7  Dispatch tool calls + stage/pending state + guidance in ChatViewModel   [verification skeleton, contract grafted from risk; note absorbed per mvp]
       files:    app/src/main/java/dev/equerry/app/ui/chat/ChatViewModel.kt
                 app/src/main/java/dev/equerry/app/tools/actions/ActionRunner.kt
                 app/src/test/java/dev/equerry/app/ui/chat/ChatViewModelTest.kt
       covers:   c-1, c-2, c-5, c-6
       depends_on: t-1, t-3, t-4, t-5
       contract: a stream yielding one ToolCall produces a staged/launchable action, NOT assistant
                 prose (c-1); a single timer ToolCall leaves exactly one staged Start-now action and
                 does NOT auto-fire; confirm() invokes the runner once and posts a deterministic note
                 ("Timer started — 5:00", "Opened Messages to Mum (you send it)") that NEVER says
                 "sent" (action_followup — asserted on text; no second model call); cancel discards. A
                 stream of two ToolCalls -> a 2-entry pendingActions list, each independently runnable,
                 skipping entry 0 leaves entry 1 runnable (multi_action, c-6). A ToolCall whose args
                 fail the planner, OR a Malformed parse, OR a send to a supportsTools==false provider ->
                 a one-line in-UI note with a Settings link + the passive banner flag set; never crashes,
                 never drops the request (c-5). (The deterministic note is emitted inline here — no
                 standalone task, per mvp.)

  t-8  Action cards, pending list, capability banner in ChatScreen        [verification+mvp+risk]
       files:    app/src/main/java/dev/equerry/app/ui/chat/ChatScreen.kt
                 app/src/test/java/dev/equerry/app/ui/chat/ChatScreenTest.kt
       covers:   c-2, c-3, c-5, c-6
       depends_on: t-7
       contract: a staged timer state renders a Start/Cancel card — tapping Start invokes onConfirm,
                 Cancel invokes onCancel and the timer NEVER fires without the tap (r-02 tap-gate for
                 timer/alarm); a multi-action state renders a pending-actions list with per-row Start
                 (timer/alarm) or Open (hand-off) and a skip control; the tool-incapable flag renders
                 the passive "Actions need a tool-capable provider" banner with a Change-in-Settings
                 link; a failed-action note renders its Settings link. Each row/banner/link assertion
                 fails if dropped.

Wave 3  (spoken confirm layers onto the staged-action flow)

  t-9  Spoken "Start now?" + yes/no confirm in voice flow                 [verification+risk]
       files:    app/src/main/java/dev/equerry/app/voice/VoiceFlowController.kt
                 app/src/main/java/dev/equerry/app/voice/VoiceGuidance.kt
                 app/src/test/java/dev/equerry/app/voice/VoiceFlowControllerTest.kt
       covers:   c-2
       depends_on: t-6, t-7
       contract: when a turn stages a timer/alarm in a voice session, the controller speaks
                 "Start now?" and only THEN arms a narrow yes/no listen — if the listen is armed before
                 tts.awaitDone() the "TTS prompt finishes before mic arms (prompt not re-transcribed as
                 yes)" assertion fails. A "yes" through YesNoGrammar fires the action EXACTLY once; a
                 "no" discards it; a listen timeout leaves it staged as the tappable card (never
                 auto-fires); a non-yes/no transcript does NOT fire. Email/SMS hand-offs are NEVER
                 voice-fired (benign_voice_confirm).
```

Locked-decision check: no task in the merged plan violates a locked decision. Each draft was audited;
no draft proposes a confirm_first param (timer_alarm_confirm), an in-app preview card for hand-offs
(handoff_execution), a second model round-trip with a tool result (action_followup), a sixth tool
(tool_scope), or voice-commit of email/SMS (benign_voice_confirm). All clean.

## Disagreements

1. **Ollama tool-call parsing — included vs omitted.**
   mvp (t-2) and verification (t-1) parse Ollama tool calls; **risk parses only Anthropic (t-3) and
   OpenAI (t-4) and silently omits Ollama.** Provisional default: **INCLUDE Ollama parsing** (taken
   from verification/mvp; `OllamaStreamParser.kt` is a real shipped parser). Why it matters: Ollama is
   a locked v1 provider type; if a user maps a tool-capable Ollama model and its tool-call lines parse
   as prose, c-1 silently breaks for that provider. Risk's own `tool_scope`/capability logic marks
   Ollama best-effort-incapable (no tools array sent), which *mitigates* but does not justify dropping
   the parse path — a model that emits tool calls anyway must still not surface them as prose. This is
   the single most consequential divergence and the main reason verification beat risk on coverage.

2. **Parser granularity — one parse task vs split by wire format.**
   verification/mvp put all parsers in one task; **risk splits Anthropic (t-3) and OpenAI (t-4) into
   two**, arguing the formats fail differently (block-assembly vs fragmented-argument deltas) and a
   shared task lets one format's bug hide behind the other's green test. Provisional default: **one
   parser task (t-1)** but with risk's per-wire-format contract clauses folded in as named, separately
   asserted bullets. Why it matters: the two formats genuinely live in the same `SseTokenParser.kt`
   (separate functions), so a split wouldn't actually parallelize across files — it would be two
   sequential same-file tasks. Folding the contracts keeps risk's anti-hiding guarantee (each format
   independently asserted) without the artificial task boundary. Note: this default leans on the
   contract, not the task count — if execution finds the OpenAI fragment-accumulation seam large, t-1
   is the natural place to re-split.

3. **ViewModel dispatch — one task vs dispatch/multi-action split.**
   **risk splits the ViewModel into t-8 (3-way prose/tool/malformed fork + banner) and t-9 (one-vs-many
   launch policy)**, same file, sequenced; verification/mvp keep it one task. Provisional default:
   **one ViewModel task (t-7)** covering dispatch, staging, multi-action list, confirm/cancel, and the
   malformed/incapable note. Why it matters: both risk subtasks touch the same file with no parallelism
   gain, and the merged t-7 contract already asserts the two risk surfaces (3-way fork + banner; and
   N-calls -> N independent entries) as distinct clauses — so the anti-hiding benefit is preserved
   without a second same-file task. If the ViewModel diff proves large at execution time, splitting t-7
   along risk's seam is the documented fallback.

4. **Capability flag — its own task vs bundled into the request-body task.**
   verification (t-3) makes the per-type `supportsTools` flag a standalone task; **mvp folds it into the
   request-body task (its t-1)**; risk folds it into its ToolCall-token task (its t-1). Provisional
   default: **keep it standalone (t-3)**, following the skeleton. Why it matters: it's a thin pure-enum
   table-test with genuinely zero dependency on the other wave-1 seams, so a standalone task maximizes
   wave-1 parallelism and gives c-5's capability assertion one clear home. The cost is one extra small
   task; the alternative (mvp's bundle) is also defensible and would trim the count to 8 — this is the
   lowest-stakes divergence.
```

synthesis: 9 tasks across 3 waves, 4 disagreements
