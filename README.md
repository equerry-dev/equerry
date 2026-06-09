# Equerry

> Pronounced **"E-query"** — just ask.

A privacy-first, open-source Android assistant that registers as the system **default
digital assistant** and routes requests to **AI backends you configure** instead of Gemini.
Bring your own keys and endpoints, and wire **different providers to different jobs** —
local Ollama for chat, a strong multimodal model for screen-reading, on-device speech for
voice — via the Capability-Slot system.

- **Reads the screen, never touches it** — uses the sanctioned Assist API, never
  AccessibilityService.
- **No silent side effects** — the model drafts and stages; you tap to confirm anything
  that sends.
- **Privacy is the product** — no accounts, no telemetry, no first-party data collection.
  Your data flows only to the providers you set up.

## Status

Bootstrapping (Phase 0 — Assist probe). See `project.md` for the full build spec and phase
plan.

## Tech stack

Kotlin · Jetpack Compose (Material3) · Hilt · Coroutines/Flow · Retrofit + OkHttp ·
kotlinx.serialization · DataStore + EncryptedSharedPreferences. `minSdk 29`, `targetSdk 36`.

## Building

Requires the Android SDK (`platforms;android-36`, `build-tools;36.0.0`) and a **JDK 17–21**.
The pinned Gradle (8.13) does **not** run on JDK 22+, so point `JAVA_HOME` at a JDK 21:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"   # or your JDK 17–21 path
./gradlew :app:assembleDebug      # build the debug APK
./gradlew testDebugUnitTest       # run JVM unit tests
./gradlew :app:lintDebug          # Android lint
```

`local.properties` (gitignored) must contain `sdk.dir=<path to Android SDK>`.

## License

[GPL-3.0](./LICENSE). Every fork stays open — that is the privacy guarantee. External
contributions require a CLA (to preserve dual-licensing / sale optionality); see
`project.md` §9.
