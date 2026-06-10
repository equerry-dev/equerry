Phase 06-tools-actions — 6 tasks across 3 waves

Wave 1
  t-1  Define tool specs and ToolCall token
       files:    app/src/main/java/dev/equerry/app/providers/drivers/ChatModels.kt
                 app/src/main/java/dev/equerry/app/providers/drivers/ChatRequestBuilder.kt
                 app/src/main/java/dev/equerry/app/providers/ProviderType.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/ChatRequestBuilderTest.kt
       covers:   c-1, c-4, c-5
       contract: if the five tool specs (set_timer/set_alarm/create_calendar_event/draft_email/
                 draft_sms) stop being injected into the OpenAI/Anthropic request body, the
                 ChatRequestBuilder "tools array" assertions fail; if ProviderType.supportsTools
                 flips for a known tool-incapable type, the capability-flag test fails.
  t-2  Parse tool calls from all stream parsers
       files:    app/src/main/java/dev/equerry/app/providers/drivers/SseTokenParser.kt
                 app/src/main/java/dev/equerry/app/providers/drivers/OllamaStreamParser.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/SseTokenParserTest.kt
                 app/src/test/java/dev/equerry/app/providers/drivers/OllamaStreamParserTest.kt
       covers:   c-1, c-5, c-6
       contract: a tool_call chunk in the OpenAI delta / Anthropic content_block / Ollama message
                 stream yields a ChatToken.ToolCall (name+args), not Delta text; two tool_calls in
                 one stream yield two ToolCall tokens; a tool_call with unparseable arguments JSON
                 yields TokenResult.Fail(ChatError.Malformed), not a crash.
  t-3  Build intent executors for the five tools
       files:    app/src/main/java/dev/equerry/app/tools/actions/ActionIntents.kt
                 app/src/test/java/dev/equerry/app/tools/actions/ActionIntentsTest.kt
       covers:   c-3, c-4
       contract: the timer/alarm builders produce AlarmClock.ACTION_SET_TIMER/ACTION_SET_ALARM
                 with the model's seconds/hour-minute extras; the calendar builder produces
                 ACTION_INSERT with title+begin/end-time extras; email -> ACTION_SENDTO mailto:
                 with recipient/subject/body; sms -> ACTION_SENDTO sms: with address+body — each
                 builder test fails if its action string or any populated extra is dropped.

Wave 2 (depends t-1, t-2, t-3)
  t-4  Dispatch tool calls and stage actions in ChatViewModel
       files:    app/src/main/java/dev/equerry/app/ui/chat/ChatViewModel.kt
                 app/src/test/java/dev/equerry/app/ui/chat/ChatViewModelTest.kt
       covers:   c-1, c-2, c-3, c-5, c-6
       contract: a stream carrying one timer ToolCall leaves a pending Start-now action in
                 ChatUiState (not fired) and posts no "sent" prose; a stream carrying two
                 ToolCalls leaves two independently-runnable pending entries (c-6); confirming the
                 timer action emits the timer intent and a deterministic "Timer started" note,
                 cancel discards it; a ToolCall against a supportsTools=false profile (or a
                 Malformed tool stream) sets the in-UI action-failed note with a Settings link and
                 never crashes (c-5).

Wave 3 (depends t-4)
  t-5  Render Start-now card, pending list, capability banner
       files:    app/src/main/java/dev/equerry/app/ui/chat/ChatScreen.kt
                 app/src/test/java/dev/equerry/app/ui/chat/ChatScreenTest.kt
       covers:   c-2, c-3, c-5, c-6
       contract: a single staged timer renders a Start/Cancel card whose Start calls the
                 view-model confirm; a multi-action state renders a pending-actions list with
                 per-row Start (timer/alarm) or Open (hand-off) tap targets; a tool-incapable
                 mapped provider renders the passive "Actions need a tool-capable provider" banner
                 with a Change-in-Settings link — each row/banner assertion fails if dropped.
  t-6  Add spoken "Start now?" yes/no confirm for timer/alarm
       files:    app/src/main/java/dev/equerry/app/voice/VoiceFlowController.kt
                 app/src/main/java/dev/equerry/app/voice/VoiceGuidance.kt
                 app/src/test/java/dev/equerry/app/voice/VoiceFlowControllerTest.kt
       covers:   c-2
       contract: in a voice session a staged timer/alarm speaks "Start now?" and only then arms a
                 narrow yes/no listen; a recognized "yes" fires the action, "no" discards it, and a
                 listen timeout leaves it staged as the tappable card — the test fails if the listen
                 is armed before the TTS prompt finishes or if email/SMS hand-offs ever take a
                 spoken yes (benign_voice_confirm).

## Coverage
- c-1 (tool call returned + acted on): t-1, t-2, t-4
- c-2 (timer/alarm Start-now card + spoken yes/no): t-4, t-5, t-6
- c-3 (calendar/email/SMS open system app pre-filled): t-3, t-4, t-5
- c-4 (all five tools end-to-end, details populated): t-1, t-3
- c-5 (unsupported/malformed → in-UI guidance, no crash): t-1, t-2, t-4, t-5
- c-6 (multi-action → pending list, each handled): t-2, t-4, t-5

## Judgment calls
- Merged tool-spec registration + ProviderType capability flag + ToolCall token into one wave-1 task (t-1): all three are the same "teach the request/model layer about tools" change, share ChatRequestBuilderTest, and splitting them would force an artificial dependency edge with no parallelism gain.
- Kept parser changes (t-2) separate from spec changes (t-1) despite both being driver-layer: they touch disjoint files (parsers vs builder) with no dependency, so both run in wave 1 for parallelism rather than chaining.
- Put all five intent builders in ONE file/task (t-3) rather than one-per-tool: each is a sub-10-minute pure intent-construction function with no abstraction the criteria demand; five tasks would violate the "too small, merge" rule.
- Chose to make ChatViewModel (t-4) own staging, confirm/cancel, multi-action list state, and the malformed/unsupported note — rejected a separate ActionDispatcher/coordinator class because no criterion needs that abstraction and the view-model already owns the send/collect loop where ToolCall tokens surface.
- Kept voice spoken-confirm (t-6) as its own wave-3 task instead of folding into t-4: it needs the staged-action state t-4 produces (depends on it) and lives entirely in the voice layer with its own test root, so merging would span 3 layers and break the two-layer cap.
- Did NOT add a separate task for the deterministic follow-up note or the action_followup behavior: it is one deterministic string emitted at confirm/open time inside t-4's dispatch, asserted by t-4's "Timer started" contract — no standalone task earns its place.
