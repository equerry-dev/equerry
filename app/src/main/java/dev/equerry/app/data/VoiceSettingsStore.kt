package dev.equerry.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Post-reply listening behaviour (locked turn_control decision). */
enum class TurnControl {
    /** Auto-listen again for a follow-up until the session is dismissed (default). */
    CONTINUOUS,

    /** End the session after one question-and-answer. */
    SINGLE_TURN,
}

/** When the reply is spoken aloud (locked speak_timing decision). */
enum class SpeakTiming {
    /** Speak the finished reply as one utterance (default). */
    WHOLE_REPLY,

    /** Queue each sentence to TTS as the reply streams in. */
    SENTENCE_BY_SENTENCE,
}

/**
 * Persists the two voice settings on the shared "equerry_settings" DataStore. Defaults are the
 * locked decisions — [TurnControl.CONTINUOUS] and [SpeakTiming.WHOLE_REPLY]; a missing or
 * unrecognized stored value falls back to the default rather than throwing.
 */
class VoiceSettingsStore(private val dataStore: DataStore<Preferences>) {

    fun turnControl(): Flow<TurnControl> = dataStore.data.map { it[TURN_KEY].toTurnControl() }

    fun speakTiming(): Flow<SpeakTiming> = dataStore.data.map { it[SPEAK_KEY].toSpeakTiming() }

    suspend fun setTurnControl(value: TurnControl) {
        dataStore.edit { it[TURN_KEY] = value.name }
    }

    suspend fun setSpeakTiming(value: SpeakTiming) {
        dataStore.edit { it[SPEAK_KEY] = value.name }
    }

    private fun String?.toTurnControl(): TurnControl =
        TurnControl.entries.firstOrNull { it.name == this } ?: TurnControl.CONTINUOUS

    private fun String?.toSpeakTiming(): SpeakTiming =
        SpeakTiming.entries.firstOrNull { it.name == this } ?: SpeakTiming.WHOLE_REPLY

    private companion object {
        val TURN_KEY = stringPreferencesKey("voice_turn_control")
        val SPEAK_KEY = stringPreferencesKey("voice_speak_timing")
    }
}
