# Equerry — Bring-Your-Own-Backend Android Assistant

> **Name: "Equerry" (locked).** An equerry is the trusted attendant who quietly handles
> logistics for someone important — exactly the product. Pronounced **"E-query"** (lean
> into the pun: an assistant is a thing you *query*). Spelling stays the real word —
> two R's, `Equerry` — which is what keeps it distinctive and trademark-clear; the pun
> lives in pronunciation and marketing ("Equerry — just ask"), not in respelling it to
> "eQuery" (that wanders into crowded query-tooling naming). Use "Equerry" verbatim in
> all UI copy, package IDs, and the listing.

This document is the build spec for an autonomous coding agent (Claude Code). It is
written to be executed mostly without supervision. Where a decision is irreversible,
costly, or a matter of taste, the agent must **stop and ask the human** — those points
are marked **[ASK HUMAN]**.

---

## 1. What this is

An open-source Android app that registers as the system **default digital assistant**
(the thing invoked by the power-button long-press / corner-swipe gesture) and routes
requests to **user-configured AI backends** instead of Gemini.

The differentiator: you bring your own keys and endpoints, and you can wire **different
providers to different jobs** — local Ollama for chat, Claude for vision/screen-reading,
a remote Whisper endpoint for speech-to-text, on-device TTS for speech-out, and so on.

It reads the current screen on request, drafts messages and emails, sets timers/alarms/
calendar events, and answers questions using whichever model the user pointed at "chat."

**Business model:** free, open source under **GPL-3.0** (chosen: keeps every fork open,
which *is* the privacy pitch — nobody can ship a closed clone with telemetry bolted on),
distributed on **Google Play + F-Droid**, funded by an optional tip jar /
"buy me a coffee" link. No subscription, no telemetry, no accounts.

---

## 2. The non-negotiable constraints (read before designing anything)

These are not preferences. They are the boundaries that the whole architecture must
respect. Violating them either breaks Play Store policy or breaks user trust.

1. **READ the screen, never TOUCH it.** The app must NOT use `AccessibilityService` to
   tap, swipe, or drive other apps. Google's policy (enforced Jan 2026) makes
   "AI that reads the screen and taps buttons for the user" a policy violation, and
   Android 17's Advanced Protection Mode auto-revokes the permission for non-accessibility
   apps. Screen *reading* uses the sanctioned **Assist API**, not Accessibility. Do not
   add an AccessibilityService for any reason.

2. **No silent side effects.** Sending an email, sending a message, posting anything, or
   any irreversible action requires an explicit user confirmation tap in-app. The model
   may *draft* and *stage*; the human *commits*. This is both a safety rule (models
   hallucinate recipients) and good design.

3. **No wake word.** Hotword activation ("Hey X") requires privileged APIs available only
   to preinstalled apps. Invocation is gesture/button only, exactly like the ChatGPT and
   Perplexity assistant integrations. Do not promise or attempt a custom wake word.

4. **`FLAG_SECURE` is a hard wall.** Banking apps, DRM screens, and password managers set
   this flag; when they do, screen capture returns nothing. Handle this gracefully ("I
   can't see this screen — it's protected by the app") and never attempt a workaround.

5. **Keys are secrets.** API keys and endpoint credentials are stored encrypted (Android
   Keystore-backed `EncryptedSharedPreferences` or equivalent). They are never logged,
   never put in URLs/query strings, never sent anywhere except the provider they belong
   to.

6. **Privacy is the product.** No analytics SDKs, no crash reporters that phone home
   without opt-in, no data collection. Screen content and conversation content leave the
   device only to the user's own configured providers, and only with consent. This must
   be true in code, not just in the privacy policy.

### Explicit non-goals (do NOT build these)

- Any AccessibilityService-based automation or app-driving ("agent that operates my
  phone"). Permanently out of scope.
- On-device model inference (running the LLM on the phone). Out of scope for v1 — the
  phone is a thin client to remote/self-hosted models. (May revisit later; not now.)
- A custom wake word / always-listening mode.
- Multi-user accounts, cloud sync, a hosted backend of our own.

---

## 3. The provider model (this is the heart of the app)

The core abstraction is a **Provider Profile** decoupled from a **Capability Slot**.

### Provider Profile
A saved backend the user can talk to. Fields:
- `id` (generated)
- `displayName` (e.g. "Helicon Ollama", "Claude", "Work OpenAI")
- `type` — enum: `OPENAI_COMPATIBLE`, `ANTHROPIC`, `OLLAMA`, `WHISPER_HTTP`,
  `OPENAI_TTS`, `SYSTEM` (on-device Android STT/TTS), `CUSTOM_HTTP`
- `baseUrl`
- `apiKey` (encrypted; optional for local/self-hosted)
- `defaultModel` (e.g. `claude-...`, `llama3.x`, `whisper-1`)
- `extraHeaders` (optional map — for self-hosted auth proxies, etc.)
- `timeoutSeconds`

### Capability Slots
Each job the app needs is a slot that points at *one* Provider Profile (+ optionally a
model override). The user assigns providers to slots independently. Slots:

| Slot | Purpose | Typical provider |
|------|---------|------------------|
| `CHAT` | Main conversational model + tool-calling | Ollama / Claude / OpenAI |
| `VISION` | Screen-reading & image understanding (needs a multimodal model) | Claude / GPT-class |
| `STT` | Speech → text | System (Android `SpeechRecognizer`) or remote Whisper |
| `TTS` | Text → speech | System (Android `TextToSpeech`) or remote (e.g. OpenAI TTS) |
| `OCR` | Text extraction from images when no vision model is set | On-device ML Kit, or remote |
| `EMBEDDINGS` | (future) memory / RAG | Ollama / OpenAI |

Rules:
- A single Provider Profile can serve multiple slots (Claude can be both `CHAT` and
  `VISION`). The user maps freely.
- `STT` and `TTS` default to the on-device `SYSTEM` provider so the app works out of the
  box with zero keys for voice.
- `VISION` is optional; if unset, screen-reading falls back to `OCR` (extract text) +
  `CHAT` (reason over text). If `VISION` is set, send the screenshot directly.
- Every slot must degrade gracefully when unconfigured, with a clear in-app message
  telling the user which slot to set up.

### Why this matters
The user explicitly wants to split jobs across backends for cost/privacy/quality:
cheap-or-local model for chat, a strong multimodal model only when a screenshot is
actually being analysed, on-device STT/TTS to keep voice free and offline. The slot
system is what makes that possible and is the main thing reviewers/users will praise.

---

## 4. Architecture

**Language:** Kotlin. **UI:** Jetpack Compose. **Min SDK:** 29. **Target SDK:** 36
(Play requires API 36 targeting by Aug 2026 — start there). **DI:** Hilt.
**Async:** Coroutines + Flow. **Networking:** Retrofit + OkHttp (or Ktor client —
**[ASK HUMAN]** if a preference; default Retrofit). **Persistence:** DataStore for
settings, `EncryptedSharedPreferences` for secrets, Room only if conversation history
storage grows beyond trivial.

### Module / package layout
```
app/
  assistant/         # VoiceInteractionService, session, screen-context capture
  providers/         # Provider Profile model, drivers, capability-slot routing
    drivers/         # one driver per `type` (openai, anthropic, ollama, whisper, ...)
  tools/             # tool-calling: schema, dispatcher, individual tool impls
    actions/         # intent-based actions (timer, alarm, calendar, draft email...)
  voice/             # STT + TTS abstractions over system + remote providers
  ui/                # Compose screens: chat, settings, provider config, slot mapping
  data/              # DataStore, encrypted secret store, conversation history
  di/                # Hilt modules
  core/              # shared models, Result types, error handling
```

### Driver interface (sketch — agent to finalize)
Each capability is an interface; each provider `type` implements the relevant ones.
```kotlin
interface ChatDriver {
    suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>?,
        model: String,
    ): ChatResult   // text + optional tool calls
}

interface VisionDriver {
    suspend fun describe(
        prompt: String,
        image: Bitmap,
        model: String,
    ): String
}

interface SttDriver { suspend fun transcribe(audio: AudioClip): String }
interface TtsDriver { suspend fun speak(text: String) }
```
Note the OpenAI-compatible and Anthropic chat APIs differ in tool-calling shape and
message format — the drivers normalize both to the app's internal `ChatMessage` /
`ToolSpec` / `ToolCall` types. Keep that translation inside the driver, never leak it
upward.

### The assistant loop (Phase 1–3)
1. Gesture fires the `VoiceInteractionSession`.
2. Capture context: optionally grab `AssistStructure` + screenshot (Phase 3).
3. Get input: voice via `STT` slot, or text box.
4. Build the message list (+ screen context if the user asked about the screen).
5. Call `CHAT` driver with the tool schema.
6. If the response contains tool calls → dispatch them (see §5) → feed results back →
   loop until the model returns a final answer.
7. Speak via `TTS` slot and/or render in the session UI.
8. Any staged side-effecting action waits for a confirmation tap.

---

## 5. Tools & actions (the "hands")

Tool-calling drives real Android behaviour through **public intents only** — no special
permissions, no Accessibility. Implement these as the v1 tool set:

| Tool | Mechanism | Side-effect? |
|------|-----------|--------------|
| `set_timer` | `AlarmClock.ACTION_SET_TIMER` | no (fires immediately, reversible) |
| `set_alarm` | `AlarmClock.ACTION_SET_ALARM` | no |
| `create_calendar_event` | `CalendarContract` insert intent | no (opens prefilled editor) |
| `draft_email` | `ACTION_SENDTO` / `Intent.ACTION_SEND` prefilled, NOT sent | **stage → confirm** |
| `draft_message` | SMS/messaging intent prefilled, NOT sent | **stage → confirm** |
| `open_app` / `launch_url` | launch intent / `ACTION_VIEW` | no |
| `web_search` | routed to a configured tool endpoint or the CHAT provider's own search if it has one | no |
| `read_screen` | Assist API capture → VISION or OCR+CHAT | no |

The **dispatcher** maps a `ToolCall` name+args to one of these implementations, executes
it, and returns a structured result string the model can reason about. Unknown tool →
return a clean error to the model, never crash.

**Confirmation pattern:** side-effecting tools return a *staged action* object that the
session UI renders as a card with the filled-in content and a Confirm / Edit / Cancel
control. Nothing is sent until Confirm. For email/SMS, "Confirm" hands off to the
system compose UI (so the actual send happens in the user's mail/SMS app, which is also
the safest place for it).

---

## 6. Screen context (the headline feature — de-risk FIRST)

Uses the **Assist API**, available to the registered default assistant. On invocation the
system can provide:
- `AssistStructure` — a tree of on-screen views (text, hints, bounds). Quality varies by
  app; Compose/Canvas/WebView/game screens can be sparse.
- An **assist screenshot** bitmap — the reliable fallback.

Strategy: if a `VISION` provider is configured, prefer sending the **screenshot** to it
(robust, app-agnostic). Otherwise, extract text from `AssistStructure` (+ `OCR` slot on
the screenshot if needed) and reason with the `CHAT` provider.

**Limits to handle gracefully:** `FLAG_SECURE` screens return nothing; some apps null out
assist data; the user's device-level "use screen context / screenshot" toggle must be on.
Detect each and explain, don't fail silently.

### PHASE 0 — prove this before building anything else
Build a throwaway probe: register as assistant, and on invocation dump to a log/file:
- whether `AssistStructure` arrived and how many text nodes it has,
- whether a screenshot arrived (or was blocked),
- the raw extracted text.
Then walk it across ~10 real apps (a browser, Gmail, a Compose-heavy app, a game, a
banking app, a chat app, the home screen). Produce a small results table: per app —
structure-text-quality (good/sparse/empty), screenshot (yes/blocked). This single
afternoon decides how much to lean on VISION vs structure. **Do not start Phase 1 until
this table exists and the human has seen it.**

---

## 7. Build phases & acceptance criteria

The agent should work phase by phase, committing at each green checkpoint, and pausing
for human review at the marked gates.

**Phase 0 — Assist probe** *(gate: human reviews results table)*
- App installs, can be set as default assistant, gesture invokes it.
- Probe logs structure + screenshot availability across test apps.
- ✅ Done when: the results table is produced and reviewed.

**Phase 1 — Chat + voice round-trip**
- Provider Profile + Capability Slot data model and encrypted key storage.
- Settings UI: add/edit provider, map slots.
- `CHAT` driver for at least `OLLAMA` and `ANTHROPIC` + `OPENAI_COMPATIBLE`.
- Gesture → STT (system) → CHAT → TTS (system) → spoken/rendered reply.
- ✅ Done when: a user with one configured chat provider can hold a spoken Q&A.

**Phase 2 — Tools & actions**
- Tool schema + dispatcher + the §5 tool set.
- Staged-action confirmation UI for email/message.
- ✅ Done when: "set a 10-minute timer," "draft an email to X saying Y" (stops at
  confirm), and "add lunch to my calendar tomorrow" all work end to end.

**Phase 3 — Screen context**
- Assist capture wired to VISION (screenshot) with OCR+CHAT fallback.
- `FLAG_SECURE` / empty-data handling with clear messaging.
- ✅ Done when: "what's on my screen / summarise this / what does this error mean" works
  on the apps the Phase-0 table marked as readable.

**Phase 4 — Polish & release**
- First-run onboarding (explain assistant role, walk through adding first provider).
- In-app prominent disclosure for screen-context data use (Play requirement).
- Provider connection test buttons; helpful errors; empty-slot guidance.
- Tip-jar link **[ASK HUMAN]** (which platform).
- F-Droid metadata + reproducible build check; Play listing + data-safety form.
- ✅ Done when: clean install onboards a non-technical user to a working chat assistant
  with zero documentation.

---

## 8. Play Store / F-Droid compliance checklist

- Target **API 36** from the start.
- **Assistant role** is legitimately requested via the digital-assistant default-app flow
  — fine.
- **No `AccessibilityService`.** (See §2.1.)
- Sensitive permissions (if any added, e.g. SMS for messaging) require the **Permissions
  Declaration Form** and must tie to core functionality. Prefer intent-based handoff to
  avoid needing these at all — default to NOT requesting SMS/contacts/call-log
  permissions; use the system compose UIs and the Contact Picker instead.
- **Prominent in-app disclosure + affirmative consent** before any screen content is sent
  to a provider — shown in normal app flow, not buried in settings or the privacy policy.
- **Data safety form:** declare that conversation/screen data is sent to user-configured
  third parties; declare no first-party collection.
- F-Droid: no proprietary dependencies, no non-free SDKs, reproducible build.

---

## 9. Decisions the human must make — collect these up front

- **Name: "Equerry" (decided).** Pronounced "E-query." Project home: **equerry.dev**
  (owned). Package ID **`dev.equerry.app` (decided)** — stands on its own rather than
  under the studio namespace, to keep the project independently brandable/transferable.
  Note: package IDs are permanent once published to Play.
- **License: GPL-3.0 (decided).** Keeps every fork open. Does NOT prevent a future sale —
  an acquirer buys the brand, domain, Play listing + install base, and the copyright
  (sole-authored, so fully owned). What GPL blocks is a buy-to-close-it play, not a
  legitimate acquisition. **To preserve dual-licensing / sale optionality: require a CLA
  (contributor assigns rights to the owner) from the first external contribution onward.**
  Without a CLA, a single outside patch permanently locks the project to GPL and forecloses
  dual-licensing. Have the CLA ready *before* accepting the first PR — cheap now, costly to
  retrofit.
- **[ASK HUMAN]** Tip-jar platform (Ko-fi, GitHub Sponsors, Liberapay for F-Droid crowd).
- **[ASK HUMAN]** Retrofit vs Ktor for networking (default Retrofit if no preference).
- **[ASK HUMAN]** Which provider `type`s to ship in v1 beyond the core three
  (Ollama / Anthropic / OpenAI-compatible). OpenRouter is a cheap add since it's
  OpenAI-compatible.
- **[ASK HUMAN]** Bundle a remote-Whisper STT driver in v1, or ship system-STT only and
  add Whisper later?

---

## 10. Glossary (for the agent)

- **Default digital assistant** — the app the OS routes the assist gesture to; set in
  Settings → Apps → Default apps → Digital assistant app.
- **`VoiceInteractionService` / `VoiceInteractionSession`** — the Android framework
  entry points an assistant app implements to receive invocation and render its UI.
- **Assist API** — `onProvideAssistData` / `AssistStructure` / `AssistContent` + assist
  screenshot; the sanctioned way an assistant reads current-screen context.
- **`FLAG_SECURE`** — window flag apps set to block screenshots/screen capture.
- **Capability Slot** — an app-internal job (CHAT/VISION/STT/TTS/OCR) the user maps to a
  Provider Profile.
- **Provider Profile** — a saved backend (type + URL + key + model).
- **Staged action** — a side-effecting action drafted by the model and held for explicit
  user confirmation before execution.

---

## 11. First actions for the agent

1. Read this whole file. Surface the §9 **[ASK HUMAN]** questions to the human in one
   batch before writing code.
2. Scaffold the Gradle project (Kotlin, Compose, Hilt, target SDK 36) and the §4 module
   layout.
3. Build the **Phase 0** assist probe and stop at the gate with the results table.
4. Do not proceed past a phase gate without the green criteria met.
