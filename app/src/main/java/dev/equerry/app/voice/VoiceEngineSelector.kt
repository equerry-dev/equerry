package dev.equerry.app.voice

import android.content.Context
import dev.equerry.app.providers.ProviderProfile
import dev.equerry.app.providers.ProviderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * Decides which [SpeechToText] / [TextToSpeech] a voice turn uses, per the STT/TTS capability-slot
 * mapping: a mapped profile yields the Remote engine built around that profile + its key; an unmapped
 * slot yields the System engine — so the zero-config path is unchanged (criterion c-5). The actual
 * engine constructors are injected, so the decision is pure and unit-testable; production wiring is
 * via [fromContext].
 *
 * [sttEngine] / [ttsEngine] return thin switching engines that re-resolve through this selector each
 * turn, so the construction sites (ChatScreen, EquerryVoiceInteractionSession) can build them eagerly
 * while still picking up a remote mapping (or a change to it) without rebuilding the controller.
 */
class VoiceEngineSelector(
    private val repository: ProviderRepository,
    private val systemStt: () -> SpeechToText,
    private val remoteStt: (ProviderProfile, String) -> SpeechToText,
    private val systemTts: () -> TextToSpeech,
    private val remoteTts: (ProviderProfile, String) -> TextToSpeech,
) {

    /** The STT engine for this turn: the mapped profile's Remote engine, else the System engine. */
    suspend fun resolveStt(): SpeechToText {
        val profile = repository.observeSttMapping().first()
        return if (profile != null) remoteStt(profile, repository.keyFor(profile.id).orEmpty()) else systemStt()
    }

    /** The TTS engine for this turn: the mapped profile's Remote engine, else the System engine. */
    suspend fun resolveTts(): TextToSpeech {
        val profile = repository.observeTtsMapping().first()
        return if (profile != null) remoteTts(profile, repository.keyFor(profile.id).orEmpty()) else systemTts()
    }

    /** A [SpeechToText] that re-resolves the engine on every [SpeechToText.listen] call. */
    fun sttEngine(): SpeechToText = SelectingSpeechToText(::resolveStt)

    /** A [TextToSpeech] that resolves its engine on [TextToSpeech.init] and delegates thereafter. */
    fun ttsEngine(): TextToSpeech = SelectingTextToSpeech(::resolveTts)

    /** A fresh System STT engine — the consented fallback the controller retries on (failover_consented). */
    fun systemSttEngine(): SpeechToText = systemStt()

    /** A fresh System TTS engine — the consented fallback the controller retries on (failover_consented). */
    fun systemTtsEngine(): TextToSpeech = systemTts()

    /** Whether the STT slot currently maps to a remote provider — gates offering the consented retry. */
    suspend fun isSttMapped(): Boolean = repository.observeSttMapping().first() != null

    /** Whether the TTS slot currently maps to a remote provider — gates offering the consented retry. */
    suspend fun isTtsMapped(): Boolean = repository.observeTtsMapping().first() != null

    companion object {
        /** Production wiring: System engines via their fromContext factories, Remote via fromProfile. */
        fun fromContext(context: Context, repository: ProviderRepository): VoiceEngineSelector {
            val app = context.applicationContext
            return VoiceEngineSelector(
                repository = repository,
                systemStt = { SystemSpeechToText.fromContext(app) },
                remoteStt = { profile, key -> RemoteSpeechToText.fromProfile(profile, key) },
                systemTts = { SystemTextToSpeech.fromContext(app) },
                remoteTts = { profile, key -> RemoteTextToSpeech.fromProfile(app, profile, key) },
            )
        }
    }
}

/** Resolves a fresh [SpeechToText] per listen, so each turn picks up the current STT mapping. */
private class SelectingSpeechToText(private val resolve: suspend () -> SpeechToText) : SpeechToText {
    override fun listen(): Flow<SttEvent> = flow { emitAll(resolve().listen()) }
}

/** Resolves the [TextToSpeech] on init and forwards every later call to it. */
private class SelectingTextToSpeech(private val resolve: suspend () -> TextToSpeech) : TextToSpeech {
    private var delegate: TextToSpeech? = null

    override suspend fun init(): TtsInitResult {
        val resolved = resolve()
        delegate = resolved
        return resolved.init()
    }

    override fun speak(utterance: String) = delegate?.speak(utterance) ?: Unit
    override suspend fun speakSentences(sentences: Flow<String>) {
        delegate?.speakSentences(sentences)
    }
    override suspend fun awaitDone() {
        delegate?.awaitDone()
    }
    override fun stop() = delegate?.stop() ?: Unit
    override fun shutdown() = delegate?.shutdown() ?: Unit
}
