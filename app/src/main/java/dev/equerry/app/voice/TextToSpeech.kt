package dev.equerry.app.voice

import kotlinx.coroutines.flow.Flow

/** Outcome of initializing the system TTS engine. */
sealed interface TtsInitResult {
    /** Engine ready — speech will be produced. */
    data object Ready : TtsInitResult

    /** An engine exists but reported a non-success init status. */
    data object Failed : TtsInitResult

    /** No TTS engine is available on the device at all. */
    data object MissingEngine : TtsInitResult
}

/**
 * System text-to-speech seam. Every [speak] call is a safe no-op until [init] has returned
 * [TtsInitResult.Ready], so a missing or failed engine degrades silently (c-5) rather than crashing.
 */
interface TextToSpeech {
    /** Initialize the engine, suspending until it reports readiness. */
    suspend fun init(): TtsInitResult

    /** Queue one utterance to be spoken. No-op unless the engine is Ready. */
    fun speak(utterance: String)

    /** Speak each emitted string as it arrives (sentence-by-sentence timing). No-op unless Ready. */
    suspend fun speakSentences(sentences: Flow<String>)

    /** Stop current and queued speech. */
    fun stop()

    /** Release engine resources. */
    fun shutdown()
}
