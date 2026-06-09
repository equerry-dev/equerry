package dev.equerry.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.equerry.app.providers.CapabilitySlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SlotMappingStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newScope() = CoroutineScope(Dispatchers.IO + Job())

    private fun dataStore(scope: CoroutineScope, file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { file }

    private suspend fun CoroutineScope.close() = coroutineContext[Job]!!.cancelAndJoin()

    @Test
    fun chat_mapping_survives_a_reload() = runBlocking {
        val file = File(tmp.root, "slots.preferences_pb")

        val scope1 = newScope()
        SlotMappingStore(dataStore(scope1, file)).setMapping(CapabilitySlot.CHAT, "id-1")
        scope1.close()

        val scope2 = newScope()
        val loaded = SlotMappingStore(dataStore(scope2, file)).mapping(CapabilitySlot.CHAT).first()
        scope2.close()

        assertEquals("id-1", loaded)
    }

    @Test
    fun cleared_mapping_returns_null() = runBlocking {
        val file = File(tmp.root, "slots2.preferences_pb")
        val scope = newScope()
        val store = SlotMappingStore(dataStore(scope, file))

        store.setMapping(CapabilitySlot.CHAT, "id-1")
        store.setMapping(CapabilitySlot.CHAT, null)
        val loaded = store.mapping(CapabilitySlot.CHAT).first()
        scope.close()

        assertNull(loaded)
    }
}
