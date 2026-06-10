# Phase 06-tools-actions — Verification-lens plan

Designed backward from test contracts. Every criterion is reduced to a crisp JVM-unit-testable
contract first; each task is the smallest change that makes its contract satisfiable. Where a
behaviour would be hard to test (Android `Intent` launch, voice yes/no), I split out a **pure seam**
(intent-builder returning an `Intent`; a yes/no grammar function; a `ToolCall→ActionPlan` mapper) so
the load-bearing logic is asserted without the framework.

## Test contracts, derived first (the spine of the plan)

- **c-1** (provider returns a structured tool call, Equerry acts not just prose): a `tool_use` /
  `tool_calls` chunk in each provider's stream parses to a `ChatToken.ToolCall`, NOT a
  `Delta` of the JSON text; and the request body the builder emits contains the five tool specs.
- **c-2** (timer/alarm stage "Start now?", fire on tap/spoken-yes, Cancel/no discards): a timer/alarm
  `ToolCall` maps to a `Staged` action (not auto-fired); confirming fires the timer intent once;
  cancelling fires nothing; in a voice session a "yes" utterance through the yes/no grammar fires it
  and "no"/timeout leaves it staged.
- **c-3** (calendar/email/SMS open the system app pre-filled, never send): the calendar intent-builder
  returns an `ACTION_INSERT` Intent with title+begin/end extras; the email builder a `mailto:` /
  `ACTION_SENDTO` Intent with recipient/subject/body; the SMS builder an `sms:` Intent with
  recipient+body — and none of them carries a "send" side-effect (assert action string + extras).
- **c-4** (each of five tools works end-to-end with model details populated): the
  `ToolCall→ActionPlan` mapper, given a well-formed call for each of the five tool names, produces the
  correct typed action with the model-proposed fields threaded through (timer duration, alarm time,
  event title+time, email recipient/subject/body, sms recipient/body).
- **c-5** (incapable provider or malformed tool call → clear guidance, no crash, nothing dropped): a
  tool-incapable provider type reports `supportsTools == false` and ChatViewModel surfaces the passive
  banner; a malformed/garbled tool-call payload parses to `ToolResult.Fail`/a `Malformed`-style guidance
  note (with Settings link) rather than throwing or being silently swallowed.
- **c-6** (one request → several actions, each handled per type): a stream carrying multiple tool calls
  maps to a multi-entry `ActionPlan`; ChatViewModel posts a pending-actions list where each entry runs
  independently (timer entry stages/fires, hand-off entry opens) and a skipped entry leaves the others
  runnable.

---

## The plan

```
Phase 06-tools-actions — 9 tasks across 3 waves

Wave 1  (pure seams — no cross-task deps)
  t-1  Add ToolCall token + tool-call parsers
       files:    app/src/main/java/dev/equerry/app/providers/drivers/ChatModels.kt
                 app/src/main/java/dev/equerry/app/providers/drivers/SseTokenParser.kt
                 app/src/main/java/dev/equerry/app/providers/drivers/OllamaStreamParser.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/SseTokenParserTest.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/OllamaStreamParserTest.kt
       covers:   c-1, c-5
       contract: an OpenAI `choices[].delta.tool_calls` chunk and an Anthropic
                 `content_block_start`/`input_json_delta` block each parse to a
                 ChatToken.ToolCall(name,args), NOT a Delta of the raw JSON; a tool-call
                 payload with non-JSON args returns TokenResult.Fail(ChatError.Malformed),
                 so a malformed call never reaches the UI as prose.

  t-2  Register five tool specs in request body
       files:    app/src/main/java/dev/equerry/app/tools/ToolSpecs.kt
                 app/src/main/java/dev/equerry/app/providers/drivers/ChatRequestBuilder.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/ChatRequestBuilderTest.kt
                 app/src/test/java/dev/equerry/app/tools/ToolSpecsTest.kt
       covers:   c-1, c-4
       contract: the OpenAI body carries a `tools` array of exactly the five tool names
                 (set_timer, set_alarm, create_calendar_event, draft_email, draft_sms) with
                 their declared params; the Anthropic body carries the same five under its
                 `tools` shape; drop one spec and the count/name assertion fails.

  t-3  Add per-type tool-capability flag
       files:    app/src/main/java/dev/equerry/app/providers/ProviderType.kt
                 app/src/test/java/dev/equerry/app/providers/ProviderTypeTest.kt
       covers:   c-5
       contract: ProviderType exposes supportsTools; ANTHROPIC and OPENAI_COMPATIBLE and
                 OPENROUTER report true and OLLAMA reports its best-effort value — flip any
                 entry and the table-test row fails.

  t-4  Pure intent-builders for the five actions
       files:    app/src/main/java/dev/equerry/app/tools/actions/TimerAlarmIntents.kt
                 app/src/main/java/dev/equerry/app/tools/actions/HandoffIntents.kt
                 app/src/test/java/dev/equerry/app/tools/actions/TimerAlarmIntentsTest.kt
                 app/src/test/java/dev/equerry/app/tools/actions/HandoffIntentsTest.kt
       covers:   c-3, c-4
       contract: setTimerIntent(300) returns an AlarmClock ACTION_SET_TIMER Intent with
                 EXTRA_LENGTH=300 and EXTRA_SKIP_UI=false; calendarIntent(title,start,end)
                 returns ACTION_INSERT on Events.CONTENT_URI with TITLE + BEGIN/END extras;
                 emailIntent(to,subject,body) returns ACTION_SENDTO with a mailto: uri and
                 subject/body extras; smsIntent(to,body) returns ACTION_SENDTO sms: with
                 the body extra — and no builder emits a "send"/ACTION_SEND side-effect.

  t-5  Pure ToolCall → ActionPlan mapper
       files:    app/src/main/java/dev/equerry/app/tools/actions/Action.kt
                 app/src/main/java/dev/equerry/app/tools/actions/ActionPlanner.kt
                 app/src/test/java/dev/equerry/app/tools/actions/ActionPlannerTest.kt
       covers:   c-2, c-4, c-5, c-6
       contract: planner(listOf(timerCall)) yields a single Staged(Timer) entry (not fired);
                 planner of one email call yields a single Handoff(Email) with recipient/
                 subject/body threaded from the args; planner of [timer, email, alarm] yields
                 a 3-entry plan preserving type+order; a call with a missing required arg
                 yields a Malformed entry, not a thrown exception.

  t-6  Pure yes/no grammar recogniser
       files:    app/src/main/java/dev/equerry/app/voice/YesNoGrammar.kt
                 app/src/test/java/dev/equerry/app/voice/YesNoGrammarTest.kt
       covers:   c-2
       contract: classify("yes")/("yeah")/("start it") -> Yes; ("no")/("cancel")/("stop")
                 -> No; ("what time is it") -> Unrecognised — a narrow grammar, so an
                 off-topic utterance never collapses to Yes and can't fire a timer.

Wave 2  (consume the wave-1 seams)
  t-7  Dispatch tool calls + stage/pending state in ChatViewModel
       files:    app/src/main/java/dev/equerry/app/ui/chat/ChatViewModel.kt
                 app/src/main/java/dev/equerry/app/tools/actions/ActionRunner.kt
                 app/src/test/java/dev/equerry/app/ui/chat/ChatViewModelTest.kt
       covers:   c-1, c-2, c-5, c-6
       depends:  t-1, t-3, t-5
       contract: a stream yielding one timer ToolCall leaves ChatUiState with a single staged
                 action and no auto-fire; confirm() invokes the runner once and posts a
                 deterministic note ("Timer started — 5:00", never "sent"); a stream of two
                 tool calls produces a 2-entry pendingActions list, and skipping entry 0
                 leaves entry 1 runnable; a send to a supportsTools==false provider sets the
                 banner flag and never drops the request.

  t-8  Action cards, pending list, banner in ChatScreen
       files:    app/src/main/java/dev/equerry/app/ui/chat/ChatScreen.kt
                 app/src/test/java/dev/equerry/app/ui/chat/ChatScreenTest.kt
       covers:   c-2, c-5, c-6
       depends:  t-7
       contract: a staged timer state renders a Start-now card with tappable Start and Cancel;
                 tapping Start invokes onConfirm and Cancel invokes onCancel; a 2-entry pending
                 state renders two rows each with its own Open/Start action; the
                 tool-incapable flag renders the "Actions need a tool-capable provider" banner
                 with a Settings link.

Wave 3  (spoken confirm layers onto the staged-action flow)
  t-9  Spoken "Start now?" + yes/no confirm in voice flow
       files:    app/src/main/java/dev/equerry/app/voice/VoiceFlowController.kt
                 app/src/main/java/dev/equerry/app/voice/VoiceGuidance.kt
                 app/src/test/java/dev/equerry/app/voice/VoiceFlowControllerTest.kt
       covers:   c-2
       depends:  t-6, t-7
       contract: when a turn's reply stages a timer in a voice session, the controller speaks
                 "Start now?" and only THEN listens; a "yes" through YesNoGrammar fires the
                 timer exactly once; a "no" discards it and a listen timeout leaves it staged
                 as a tappable card (never fires); email/SMS hand-offs are never voice-fired.
```

## Coverage

| Criterion | Delivered by |
| --------- | ------------ |
| c-1 | t-1 (ToolCall token + parsers), t-2 (tool specs in body), t-7 (dispatch — acts not prose) |
| c-2 | t-5 (timer/alarm → Staged), t-6 (yes/no grammar), t-7 (stage state + confirm fires once), t-8 (Start/Cancel card), t-9 (spoken Start-now? + yes/no) |
| c-3 | t-4 (calendar ACTION_INSERT / email mailto / sms intents, no send) |
| c-4 | t-2 (five specs declared), t-4 (each builder populates fields), t-5 (each tool name maps + threads args) |
| c-5 | t-1 (malformed call → Fail, not prose), t-3 (capability flag), t-7 (banner flag + no dropped request), t-8 (banner UI) |
| c-6 | t-5 (multi-entry plan), t-7 (pending list, independent run/skip), t-8 (multi-row UI) |

All of c-1..c-6 accounted for.

## Judgment calls

- Chose a pure `ToolCall→ActionPlan` mapper (t-5) as a separate seam from both the parser and the
  ViewModel; rejected mapping tool calls directly inside ChatViewModel, because the fire-vs-stage-vs-
  handoff-vs-multi decision (c-2/c-4/c-6) is the hardest logic and deserves a framework-free table-test
  rather than a Robolectric ViewModel test.
- Chose to extend `ChatToken` with a `ToolCall` variant and surface it through the existing
  `TokenResult` parsers (t-1); rejected a side-channel callback, so the same `.collect` path already in
  ChatViewModel sees tool calls and the "not Delta text" assertion is a one-line parser test.
- Chose intent-builders that **return** `Intent` objects (t-4) instead of launching them; rejected
  builders that take a `Context` and call `startActivity`, because a returned Intent is asserted on
  action-string+extras under Robolectric, and the no-`ACTION_SEND` invariant (r-02, c-3) becomes a
  direct assertion rather than an integration observation.
- Chose a standalone `YesNoGrammar` function (t-6) over inlining keyword checks in the controller;
  rejected the inline form because the "narrow grammar never mis-fires on an off-topic utterance"
  safeguard from `timer_alarm_confirm` is exactly the thing to unit-test in isolation.
- Chose to keep tool-spec data in a new `tools/ToolSpecs.kt` consumed by `ChatRequestBuilder` (t-2)
  rather than hand-inlining JSON per provider; rejected per-provider duplication so the "exactly five
  tools" count assertion has one source of truth and adding a sixth tool later is a one-list edit.
- Put t-3 (capability flag) in wave 1 even though it feeds the c-5 banner; it strictly needs no other
  task's output (pure enum table-test), so dropping it to wave 1 maximises parallelism.
- Kept t-9 (spoken confirm) in its own wave-3 task depending on t-7's staged-action state and t-6's
  grammar; rejected folding it into t-7 because it crosses the voice layer and the
  "speak-then-listen / timeout-leaves-staged" ordering needs its own VoiceFlowController test.
```
