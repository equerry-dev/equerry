package dev.equerry.app.ui.voicesettings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.equerry.app.data.SpeakTiming
import dev.equerry.app.data.TurnControl
import dev.equerry.app.data.VoiceSettingsStore
import dev.equerry.app.voice.MicPermission
import dev.equerry.app.voice.MicPermissionEntryPoint
import dev.equerry.app.voice.MicPermissionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSettingsViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: VoiceSettingsStore

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        scope = CoroutineScope(Dispatchers.IO + Job())
        val file = File(tmp.newFolder(), "voice.preferences_pb")
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }
        store = VoiceSettingsStore(ds)
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        Dispatchers.resetMain()
    }

    @Test
    fun seeds_from_the_persisted_values_not_the_defaults() = runBlocking {
        // Persist a non-default value BEFORE the VM exists.
        store.setTurnControl(TurnControl.SINGLE_TURN)

        val vm = VoiceSettingsViewModel(store)

        // A VM that ignored the store and hardcoded the default would never reach SINGLE_TURN.
        val seeded = withTimeout(5_000) { vm.state.first { it.turnControl == TurnControl.SINGLE_TURN } }
        assertEquals(TurnControl.SINGLE_TURN, seeded.turnControl)
    }

    @Test
    fun toggling_turn_control_persists_and_reemits() = runBlocking {
        val vm = VoiceSettingsViewModel(store)

        vm.setTurnControl(TurnControl.SINGLE_TURN)

        val updated = withTimeout(5_000) { vm.state.first { it.turnControl == TurnControl.SINGLE_TURN } }
        assertEquals(TurnControl.SINGLE_TURN, updated.turnControl)
        assertEquals(TurnControl.SINGLE_TURN, store.turnControl().first())
    }

    @Test
    fun toggling_speak_timing_persists_sentence_by_sentence() = runBlocking {
        val vm = VoiceSettingsViewModel(store)

        vm.setSpeakTiming(SpeakTiming.SENTENCE_BY_SENTENCE)

        val updated = withTimeout(5_000) { vm.state.first { it.speakTiming == SpeakTiming.SENTENCE_BY_SENTENCE } }
        assertEquals(SpeakTiming.SENTENCE_BY_SENTENCE, updated.speakTiming)
        assertEquals(SpeakTiming.SENTENCE_BY_SENTENCE, store.speakTiming().first())
    }

    @Test
    fun mic_denied_guidance_matches_the_in_session_path() {
        val vm = VoiceSettingsViewModel(store)

        val settingsDenied = vm.micState(granted = false)
        val inSessionDenied = MicPermission.evaluate(granted = false, MicPermissionEntryPoint.IN_SESSION)

        // Settings-path guidance must be identical to the in-session prompt (locked mic_permission).
        assertEquals(inSessionDenied, settingsDenied)
        assertTrue(settingsDenied is MicPermissionState.Denied)
    }
}
