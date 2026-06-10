# Plan Review — 04-chat-drivers

Reviewed: 2026-06-10
Plan: 10 tasks across 4 waves

## BLOCKING
(none)

All five criteria (c-1..c-5) appear in at least one task's `covers`. No task description, file, or
test contract contradicts a locked decision — streaming-as-Flow (reply_streaming) drives t-6/t-7/t-9,
the dedicated chat screen + nav route (chat_surface) is t-10, the singleton in-memory holder with
clear()/no-persistence (session_boundary) is t-4, and the three optional profile params with
blank=default (request_params) are t-5/t-8/t-10. No forbidden action: runtime.mode is "native" (not
docker), all build steps use `./gradlew`, and r-03 (never log/URL-embed keys) is actively enforced by
t-1/t-3/t-8/t-9 contracts. okhttp 4.12.0 is already in libs.versions.toml so t-9 pinning mockwebserver
to the same version is correct; mockwebserver is genuinely absent today, so t-9 adding it is right.

## FLAG
- [granularity] t-10 touches 5 files and spans 3 layers (ViewModel/UI + nav wiring in MainActivity +
  provider-edit form). It bundles two distinct deliverables: the chat surface (ChatViewModel/ChatScreen/
  nav route, covering c-1/c-3/c-5) and the provider-form request-param inputs (ProviderEdit VM/Screen,
  covering c-1's params persistence). The latter only depends on t-5, not on t-9, so it is being held in
  wave 4 unnecessarily.
  Suggestion: consider splitting the ProviderEdit form wiring into its own task that depends only on t-5
  (could run in wave 2/3), leaving t-10 as the chat screen + nav.

- [wave-order] t-5 has no `depends_on` and sits in wave 1, but its third test_contract asserts on
  `ProviderRepositoryTest` (addProfile->observeProfiles round-trip) while `ProviderRepository.kt` is not
  in t-5's `files` list. Either the round-trip is already satisfied by the existing repository (in which
  case the contract is testing untouched code) or the repository needs an edit the plan doesn't schedule.
  Suggestion: confirm whether ProviderRepository needs changes to carry the three new fields; if so add it
  to t-5's files, if not reword the contract to name the store/profile round-trip it actually exercises.

- [granularity] t-6 and t-7 are both single-file pure-parser tasks in the same wave with the same
  dependencies (t-1,t-2,t-3) and the same `covers` (c-2,c-4). They are split by wire format (SSE vs
  Ollama NDJSON). This is defensible (two genuinely different formats) but borders on a granularity-
  inflation split — each is a small pure function.
  Suggestion: acceptable as-is for parallelism; only merge if you'd rather review token parsing as one
  unit. No action required if intentional.

- [test-contract] t-3's `KeyRedactionTest.keyNeverSurvivesRedaction` covers redaction, but the
  ChatHttpClient half of the task asserts only `key_never_in_logged_request`. There is no contract that
  the logging interceptor is actually installed / bodies are never logged (the description promises "never
  logs bodies"). A client that simply never logs at all would pass `key_never_in_logged_request`.
  Suggestion: add a contract that a non-key body field IS observable in the log sink (proving logging is
  on) while the key is not — otherwise the redaction path can silently regress to "logging disabled".

## NOTE
- [strength] Test contracts are unusually specific and failure-oriented — they name the exact assertion
  and the surface that breaks (e.g. "if `[DONE]` is parsed as JSON, SseTokenParserTest's malformed-line
  assertion fails", "if the key is present in the request path, ChatDriverTest's recorded-request
  assertion fails"). This is the opposite of the "tests pass" antipattern and makes verification auditable.

- [strength] The r-03 key-safety constraint is defended in depth across the layer boundaries it can leak
  at: model construction (t-1), error mapping (t-2), redaction util + HTTP client (t-3), request body/path
  (t-8), and recorded-request e2e (t-9). No single point of trust.

- [strength] Wave structure is sound: pure leaf types/parsers/builders (waves 1-2) feed the driver
  integration (wave 3) which feeds the UI (wave 4). The dependency edges (t-9 needs t-6/t-7/t-8; t-10
  needs t-9) reflect real data flow, and the wave-1 set is genuinely parallel.

- [files] Every referenced existing file was confirmed present (ProviderProfile.kt, ProfileValidator.kt,
  ProfileStore.kt, MainActivity.kt, RepositoryModule.kt, ProviderEdit{ViewModel,Screen}.kt). All new
  driver files land under the existing providers/drivers/ package. The nav pattern t-10 cites
  (providersContent/slotsContent/probeContent) exists verbatim in MainActivity.

## Summary
A strong, well-sequenced plan with no blocking issues — tighten t-10's granularity, confirm t-5's
ProviderRepository contract maps to a file it actually touches, and prove the logging interceptor is
live, then proceed.
