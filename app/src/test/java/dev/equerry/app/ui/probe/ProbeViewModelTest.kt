package dev.equerry.app.ui.probe

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.equerry.app.assistant.ProbeCsv
import dev.equerry.app.assistant.ProbeRecord
import dev.equerry.app.data.ProbeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ProbeViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: ProbeStore
    private lateinit var sessionVm: ProbeSessionViewModel
    private lateinit var logVm: ProbeLogViewModel

    private fun rec(i: Int) = ProbeRecord(
        packageName = "com.example.app$i",
        timestamp = i.toLong(),
        structureProvided = true,
        nodeCount = i,
        screenshotArrived = true,
        screenshotBlocked = false,
        screenshotWidth = 1080,
        screenshotHeight = 2400,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        scope = CoroutineScope(Dispatchers.IO + Job())
        val file = File(tmp.newFolder(), "probe.preferences_pb")
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }
        store = ProbeStore(ds)
        sessionVm = ProbeSessionViewModel(store)
        logVm = ProbeLogViewModel(store)
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }
        Dispatchers.resetMain()
    }

    @Test
    fun on_capture_sets_current_and_appends_to_log() = runBlocking {
        sessionVm.onCapture(rec(1))
        assertEquals(rec(1), sessionVm.currentCapture.value)
        assertEquals(listOf(rec(1)), logVm.records.first { it.isNotEmpty() })

        sessionVm.onCapture(rec(2))
        assertEquals(rec(2), sessionVm.currentCapture.value)
        // Second invocation appends rather than overwriting the first.
        assertEquals(listOf(rec(1), rec(2)), logVm.records.first { it.size == 2 })
    }

    @Test
    fun export_csv_matches_serializer_over_stored_records() = runBlocking {
        sessionVm.onCapture(rec(1))
        logVm.records.first { it.isNotEmpty() }
        sessionVm.onCapture(rec(2))
        val stored = logVm.records.first { it.size == 2 }

        assertEquals(ProbeCsv.toCsv(stored), logVm.exportCsv())
    }
}
