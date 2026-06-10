# Plan Review — 06-tools-actions

Reviewed: 2026-06-10
Plan: 9 tasks across 3 waves

## BLOCKING
(none)

## FLAG
- [granularity] t-7 ("Dispatch tool calls + staging + guidance in ChatViewModel") is the heavy
  task: it touches ChatViewModel + ActionRunner + tests, and its description spans three concerns
  — stream-dispatch of ToolCalls, staged/pending UI-state management, AND malformed/incapable
  guidance routing + deterministic note emission. ActionRunner is also created here (Android
  Intent-firing via startActivity), which is a different layer from the pure ViewModel state logic.
  Its 5-bullet test_contract covers c-1, c-2, c-5, c-6 in one task. This is the plan's biggest
  squash risk.
  Suggestion: consider splitting ActionRunner (the Android startActivity side-effector) from the
  ChatViewModel state/dispatch logic, or at minimum confirm ActionRunner is thin enough to ride along.

- [antipattern/files] t-1 says it will "add the accumulation seam to the currently-stateless
  parseOpenAiData", but the OpenAI parse path is a stateless function reference
  (`SseTokenParser::parseOpenAiData`) invoked per-event by `sseChatFlow` in ChatDriver.kt. That
  transport constructs no per-stream parser instance — it passes a bare function. Accumulating
  tool_call argument fragments ACROSS SSE events requires per-stream state, which today's transport
  does not provide. t-1's files list omits ChatDriver.kt, so the seam may have nowhere to live.
  (Contrast: the Ollama path already instantiates a stateful `OllamaStreamParser()` per call, and
  Anthropic accumulates within a single event window, so only OpenAI is exposed here.)
  Suggestion: t-1 should either add ChatDriver.kt to its files (to thread a per-stream OpenAI
  accumulator through `sseChatFlow`) or explicitly state how cross-event accumulation is held
  without touching the transport. As written the test_contract bullet ("accumulates fragments
  across events into one assembled ToolCall at finish_reason 'tool_calls'") is not satisfiable by
  editing only the stateless `parseOpenAiData`.

- [wave-order] t-8 (ChatScreen) depends_on [t-7] only and t-9 (voice) depends_on [t-6, t-7].
  t-8 and t-9 both consume the staged/pending action UI-state that t-7 introduces, but neither
  needs the other — they are correctly parallel in waves 2 and 3. However, t-9 is placed in wave 3
  while its only hard producers (t-6, t-7) are in waves 1 and 2; nothing in wave 3 depends on t-8
  (wave 2). t-9 could run in wave 2 alongside t-8 since both only need t-7 (+t-6 for t-9). The
  extra wave is not load-bearing.
  Suggestion: consider collapsing t-9 into wave 2 (it has no dependency on t-8); the 3-wave
  structure adds a serialization point that the dependency graph doesn't require.

## NOTE
- [coverage] All six criteria (c-1..c-6) appear in at least one task's `covers`. Mapping is
  complete: c-1→t-1/t-2/t-7, c-2→t-5/t-6/t-7/t-8/t-9, c-3→t-4/t-8, c-4→t-2/t-4/t-5,
  c-5→t-1/t-3/t-5/t-7/t-8, c-6→t-5/t-7/t-8.

- [locked-decisions] No task contradicts a locked decision. tool_scope (exactly five tools) is
  pinned by t-2's count+name assertion; handoff_execution (ACTION_INSERT / ACTION_SENDTO, never
  ACTION_SEND/ACTION_EDIT) is enforced by t-4's "no auto-commit action" assertion; action_followup
  (note never says "sent", no second model call) is checked in t-7; benign_voice_confirm (email/SMS
  never voice-fired) is checked in t-9. Strong fidelity to the spec's locked decisions.

- [forbidden-actions] No r-01/r-02/r-03 violations. r-02 (stage-then-tap) is actively defended in
  t-4 (intents only, never startActivity), t-7, and t-8 (tap-gate). r-03 (keys never in body/URL/
  log) gets an explicit guard test in t-2 ("no tool spec, header, or request field ever contains
  the configured key string"), which is a good touch given the new tools payload. No global rules
  file exists (~/.claude/dross/rules.toml absent), so only project rules apply.

- [test-contract] Test contracts are specific and name the surface that breaks (e.g. "drop it and
  ChatModels/parser tests fail to compile", "flip any row and the table-test fails", "NEVER
  contains 'sent'"). No vague "tests pass" / "covered by integration" contracts found.

- [strength] The pure/impure split is disciplined: parsers, intent-builders, the ToolCall→ActionPlan
  mapper, and the yes/no grammar are all isolated as pure, directly-assertable units (t-1, t-4, t-5,
  t-6) before any Android side-effect or ViewModel wiring (t-7, t-8, t-9). This makes the wave-1
  tasks genuinely parallelizable and the r-02 boundary unit-testable.

## Summary
Coverage, locked-decision fidelity, and rule-compliance are all solid; the plan is sound to proceed
once t-1 names where the OpenAI cross-event tool_call accumulator actually lives (the current
transport is stateless) and t-7's squashed scope is acknowledged or split.
