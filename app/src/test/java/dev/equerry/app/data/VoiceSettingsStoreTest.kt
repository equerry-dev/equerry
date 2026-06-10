package dev.equerry.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VoiceSettingsStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newScope() = CoroutineScope(Dispatchers.IO + Job())

    private fun dataStore(scope: CoroutineScope, file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { file }

    private suspend fun CoroutineScope.close() = coroutineContext[Job]!!.cancelAndJoin()

    @Test
    fun fresh_store_emits_the_locked_defaults() = runBlocking {
        val file = File(tmp.root, "voice1.preferences_pb")
        val scope = newScope()
        val store = VoiceSettingsStore(dataStore(scope, file))

        assertEquals(TurnControl.CONTINUOUS, store.turnControl().first())
        assertEquals(SpeakTiming.WHOLE_REPLY, store.speakTiming().first())
        scope.close()
    }

    @Test
    fun turn_control_survives_a_reload() = runBlocking {
        val file = File(tmp.root, "voice2.preferences_pb")

        val scope1 = newScope()
        VoiceSettingsStore(dataStore(scope1, file)).setTurnControl(TurnControl.SINGLE_TURN)
        scope1.close()

        val scope2 = newScope()
        val loaded = VoiceSettingsStore(dataStore(scope2, file)).turnControl().first()
        scope2.close()

        assertEquals(TurnControl.SINGLE_TURN, loaded)
    }

    @Test
    fun speak_timing_survives_a_reload() = runBlocking {
        val file = File(tmp.root, "voice3.preferences_pb")

        val scope1 = newScope()
        VoiceSettingsStore(dataStore(scope1, file)).setSpeakTiming(SpeakTiming.SENTENCE_BY_SENTENCE)
        scope1.close()

        val scope2 = newScope()
        val loaded = VoiceSettingsStore(dataStore(scope2, file)).speakTiming().first()
        scope2.close()

        assertEquals(SpeakTiming.SENTENCE_BY_SENTENCE, loaded)
    }

    @Test
    fun a_garbled_value_falls_back_to_the_default() = runBlocking {
        val file = File(tmp.root, "voice4.preferences_pb")
        val scope = newScope()
        val ds = dataStore(scope, file)
        // Write a value that is not a valid TurnControl enum name.
        ds.edit { it[stringPreferencesKey("voice_turn_control")] = "NONSENSE" }

        val loaded = VoiceSettingsStore(ds).turnControl().first()
        scope.close()

        assertEquals(TurnControl.CONTINUOUS, loaded)
    }
}
