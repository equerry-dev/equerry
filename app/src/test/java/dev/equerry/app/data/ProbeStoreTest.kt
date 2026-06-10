package dev.equerry.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.equerry.app.assistant.ProbeRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProbeStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newScope() = CoroutineScope(Dispatchers.IO + Job())

    private fun dataStore(scope: CoroutineScope, file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { file }

    private suspend fun CoroutineScope.close() = coroutineContext[Job]!!.cancelAndJoin()

    private fun sample(i: Int) = ProbeRecord(
        packageName = "com.example.app$i",
        timestamp = i.toLong(),
        structureProvided = true,
        nodeCount = i,
        screenshotArrived = true,
        screenshotBlocked = false,
        screenshotWidth = 1080,
        screenshotHeight = 2400,
    )

    @Test
    fun appended_records_round_trip_across_a_reload_in_order() = runBlocking {
        val file = File(tmp.root, "probe.preferences_pb")
        val expected = listOf(sample(1), sample(2), sample(3))

        val scope1 = newScope()
        val store1 = ProbeStore(dataStore(scope1, file))
        expected.forEach { store1.append(it) }
        scope1.close()

        // A fresh DataStore over the same file simulates an app restart.
        val scope2 = newScope()
        val loaded = ProbeStore(dataStore(scope2, file)).records().first()
        scope2.close()

        assertEquals(expected, loaded)
    }

    @Test
    fun empty_store_returns_empty_list() = runBlocking {
        val file = File(tmp.root, "empty.preferences_pb")
        val scope = newScope()
        val loaded = ProbeStore(dataStore(scope, file)).records().first()
        scope.close()
        assertEquals(emptyList<ProbeRecord>(), loaded)
    }

    @Test
    fun concurrent_appends_all_land() = runBlocking {
        val file = File(tmp.root, "concurrent.preferences_pb")
        val scope = newScope()
        val store = ProbeStore(dataStore(scope, file))

        (1..50).map { i ->
            launch(Dispatchers.Default) { store.append(sample(i)) }
        }.joinAll()

        val loaded = store.records().first()
        scope.close()
        assertEquals(50, loaded.size)
    }
}
