/**
 * Speech-to-text and text-to-speech abstractions over the SYSTEM provider (Android
 * SpeechRecognizer / TextToSpeech) and remote providers (Whisper / OpenAI TTS, deferred
 * past v1). STT/TTS default to SYSTEM so voice works with zero keys (spec §3).
 */
package dev.equerry.app.voice
