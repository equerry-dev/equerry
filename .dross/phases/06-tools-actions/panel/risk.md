# Phase 06-tools-actions — risk lens

Failure modes drive this graph. Every sharp risk is owned by exactly one task and dies in that
task's test contract. The risks I built around:

- a stream that carries a tool call vs prose vs a *malformed* tool call (3-way fork)
- per-provider wire formats: Anthropic `tool_use` blocks, OpenAI/OpenRouter `tool_calls` deltas
  (fragmented across SSE chunks), Ollama tool support varying by model
- intents with no handler app (`ActivityNotFoundException`) and only-one-app-can-open-at-a-time
- voice "yes/no" mis-recognition; the TTS "Start now?" prompt being re-transcribed as a "yes"
- r-02 (never auto-send/commit irreversible actions); r-03 (never log keys / tool args)

```
Phase 06-tools-actions — 11 tasks across 3 waves

Wave 1
  t-1  Add ToolCall token + capability flag
       files:    app/src/main/java/dev/equerry/app/providers/drivers/ChatModels.kt
                 app/src/main/java/dev/equerry/app/providers/ProviderType.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/ChatModelsTest.kt
       covers:   c-1, c-5
       contract: if ChatToken loses its ToolCall(name, argsJson, id) variant the model can't
                 exhaustively distinguish prose from an action, ChatModelsTest fails to compile;
                 if OLLAMA's supportsTools flag flips true (varies by model, judged incapable
                 best-effort) the ProviderType capability assertion in ChatModelsTest fails.

  t-2  Define the five tool specs
       files:    app/src/main/java/dev/equerry/app/tools/actions/ToolSpecs.kt
                 app/src/test/java/dev/equerry/app/tools/actions/ToolSpecsTest.kt
       covers:   c-1, c-4
       contract: if the registry drops one of the five (set_timer, set_alarm,
                 create_calendar_event, draft_email, draft_sms) or renames a required param,
                 ToolSpecsTest's "exactly five tools, each with its locked param set" assertion
                 fails; if a spec is added beyond the five (tool_scope locked) the same count
                 assertion fails.

  t-3  Parse Anthropic tool_use blocks to ToolCall
       files:    app/src/main/java/dev/equerry/app/providers/drivers/SseTokenParser.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/SseTokenParserTest.kt
       covers:   c-1, c-5
       contract: if the Anthropic branch mis-reads input_json_delta accumulation, a
                 content_block_start(tool_use)+input_json_delta*+content_block_stop sequence
                 in SseTokenParserTest yields Delta text or Skip instead of one ToolCall with
                 reassembled JSON args; a tool_use block with truncated/non-JSON args yields
                 Fail(Malformed), not a crash or a half-parsed ToolCall.

  t-4  Parse OpenAI tool_calls deltas to ToolCall
       files:    app/src/main/java/dev/equerry/app/providers/drivers/SseTokenParser.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/SseTokenParserTest.kt
       covers:   c-1, c-5
       contract: if the OpenAI branch fails to accumulate choices[].delta.tool_calls[].function
                 .arguments fragments across SSE events, a multi-chunk tool_calls stream in
                 SseTokenParserTest yields fragmented Deltas instead of one assembled ToolCall at
                 finish-reason "tool_calls"; arguments that never parse as JSON yield
                 Fail(Malformed), not a ToolCall with garbage args.

Wave 2 (depends t-1, t-2)
  t-5  Register tool specs in request bodies
       files:    app/src/main/java/dev/equerry/app/providers/drivers/ChatRequestBuilder.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/ChatRequestBuilderTest.kt
       covers:   c-1, c-4
       contract: if the Anthropic body omits the top-level "tools" array (input_schema shape) or
                 the OpenAI body omits "tools" with type:"function", ChatRequestBuilderTest's
                 per-type "tools array present with all five function names" assertion fails;
                 if Ollama (incapable) still gets a tools array the "ollama body carries no tools"
                 assertion fails; an assertion that no tool spec, header, or arg ever contains the
                 key string guards r-03.

Wave 2 (depends t-1)
  t-6  Build timer/alarm action intents
       files:    app/src/main/java/dev/equerry/app/tools/actions/AlarmActions.kt
                 app/src/test/java/dev/equerry/app/tools/actions/AlarmActionsTest.kt
       covers:   c-2, c-4
       contract: if the timer builder mis-maps duration, a set_timer ToolCall of 5 minutes
                 produces an ACTION_SET_TIMER intent whose EXTRA_LENGTH != 300 in
                 AlarmActionsTest; if set_alarm drops hour/minute the ACTION_SET_ALARM extras
                 assertion fails; a malformed/negative duration yields a typed BuildFailure, not
                 an exception.

  t-7  Build calendar/email/SMS hand-off intents
       files:    app/src/main/java/dev/equerry/app/tools/actions/HandoffActions.kt
                 app/src/test/java/dev/equerry/app/tools/actions/HandoffActionsTest.kt
       covers:   c-3, c-4
       contract: if create_calendar_event uses ACTION_EDIT/ACTION_SEND instead of ACTION_INSERT,
                 or draft_email/draft_sms use anything that auto-sends rather than mailto:/smsto:
                 compose (ACTION_SENDTO), HandoffActionsTest's "intent action stages, never
                 commits (r-02)" assertion fails; if the title/time or recipient/subject/body
                 extras are dropped, the per-tool extras assertion fails.

Wave 3 (depends t-3, t-4, t-5, t-6, t-7)
  t-8  Dispatch tool calls + malformed/incapable guidance in ChatViewModel
       files:    app/src/main/java/dev/equerry/app/ui/chat/ChatViewModel.kt
                 app/src/test/java/dev/equerry/app/ui/chat/ChatViewModelTest.kt
       covers:   c-1, c-5, c-6
       contract: if the collector treats a ToolCall as prose, a stream of one ToolCall in
                 ChatViewModelTest produces a staged/launchable action (not assistant text);
                 if a ToolCall whose args fail the builder isn't caught, the "malformed tool call
                 -> one-line note + Settings link, no crash, request not dropped" assertion fails;
                 if a tool-incapable mapped provider doesn't surface the banner the "incapable
                 provider banner present before send" assertion fails; the deterministic
                 follow-up note must never say "sent" (action_followup) — asserted on text.

  t-9  Multi-action staging + single-action direct-launch
       files:    app/src/main/java/dev/equerry/app/ui/chat/ChatViewModel.kt
                 app/src/test/java/dev/equerry/app/ui/chat/ChatViewModelTest.kt
       covers:   c-6
       contract: if a single-tool-call turn doesn't direct-launch/stage exactly one action, or a
                 multi-tool-call turn collapses to only the first instead of a pending-actions
                 list, ChatViewModelTest's "N tool calls -> N independently runnable entries,
                 runnable in any order, each skippable" assertion fails (only-one-app-can-open
                 risk); covers multi_action locked decision.

  t-10 Voice "Start now?" prompt + yes/no confirm
       files:    app/src/main/java/dev/equerry/app/voice/VoiceFlowController.kt
                 app/src/main/java/dev/equerry/app/voice/VoiceGuidance.kt
                 app/src/test/java/dev/equerry/app/voice/VoiceFlowControllerTest.kt
       covers:   c-2
       contract: if a staged timer/alarm in a voice session doesn't speak "Start now?" then listen
                 with a narrow yes/no grammar, the test fails; if listening starts before
                 tts.awaitDone() the "TTS prompt finishes before mic arms (prompt not
                 re-transcribed as yes)" assertion fails; spoken "yes" fires, "no"/timeout leaves
                 it staged as a tappable card (never auto-fires); a non-yes/no transcript
                 ("maybe") does NOT fire — guards mis-recognition.

  t-11 Wire Start/Cancel card, pending-actions list, banner
       files:    app/src/main/java/dev/equerry/app/ui/chat/ChatScreen.kt
                 app/src/test/java/dev/equerry/app/ui/chat/ChatScreenTest.kt
       covers:   c-2, c-3, c-5, c-6
       contract: if the Start-now card's Cancel still fires the timer, or Start fires without a
                 tap, ChatScreenTest's tap-gated-fire assertions fail (r-02 for timer/alarm); if
                 the pending-actions list omits per-entry Start/Open or the skip control, the
                 multi-action render assertion fails; if the incapable-provider banner or the
                 failed-action Settings note is absent, the c-5 render assertions fail.
```

## Coverage

- c-1 (chat -> structured tool call, acted on): t-1, t-2, t-3, t-4, t-5, t-8
- c-2 (timer/alarm "Start now?" staging, tap-or-spoken-yes fires, cancel/no discards): t-6, t-10, t-11
- c-3 (calendar/email/SMS open pre-filled system app, never send — r-02): t-7, t-11
- c-4 (all five tools end-to-end with populated details): t-2, t-5, t-6, t-7
- c-5 (incapable/malformed -> clear in-UI guidance, no crash, not dropped): t-1, t-3, t-4, t-8, t-11
- c-6 (multi-action -> each handled, not just first): t-8, t-9, t-11

Every criterion c-1..c-6 is owned by at least one task and tested there.

## Judgment calls

- Split tool-call PARSING into two tasks (t-3 Anthropic, t-4 OpenAI) instead of one parser task:
  rejected merging because the two wire formats fail differently (block-assembly vs fragmented
  arguments deltas) and a shared task would let one format's bug hide behind the other's passing test.
- Made parsing (t-3/t-4) depend on t-1 only via the shared ToolCall type, so both run in wave 1
  alongside t-1/t-2; rejected pushing them to wave 2 — they need the type's existence, not request
  registration, so holding them back would lose parallelism (wave rule).
- Ollama tool support is treated as best-effort INCAPABLE (no tools array, capability flag false)
  rather than per-model probing: chose the simpler, safe-by-default path the unsupported_provider_ux
  decision allows; rejected model-name sniffing as fragile and out of scope.
- Split ViewModel dispatch (t-8) from multi-action staging (t-9) though both touch ChatViewModel.kt:
  the 3-way fork (prose/tool/malformed) + incapable banner is one risk surface; the
  one-vs-many launch policy (only-one-app-can-open) is a distinct risk. Separate test contracts,
  same file, sequenced — t-9 depends on t-8's dispatch landing.
- Built action intents as two tasks by side-effect class — t-6 benign on-device (timer/alarm, may
  voice-confirm) vs t-7 outward hand-offs (calendar/email/SMS, r-02, never voice-confirm) — rather
  than one "actions" task: the r-02 boundary (benign_voice_confirm decision) is exactly the line
  between them, so each side owns its own "never commits" contract.
- Voice confirm (t-10) lives in VoiceFlowController, not the action builders: the re-transcription
  risk (TTS prompt heard as "yes") and the narrow-grammar mis-recognition risk are voice-flow
  concerns; the builders stay Android-intent-pure and the controller gates firing on a real spoken
  yes after awaitDone().
- UI wiring (t-11) is one task spanning card + list + banner rather than three: all three are
  ChatScreen render/tap concerns over state t-8/t-9 already produce, none needs another's output,
  and one Robolectric test file covers the tap-gating (r-02) and render assertions together.
