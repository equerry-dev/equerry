package dev.equerry.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.equerry.app.providers.ProviderProfile
import dev.equerry.app.providers.ProviderType
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

class ProfileStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newScope() = CoroutineScope(Dispatchers.IO + Job())

    private fun dataStore(scope: CoroutineScope, file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { file }

    private suspend fun CoroutineScope.close() = coroutineContext[Job]!!.cancelAndJoin()

    @Test
    fun saved_profiles_round_trip_across_a_reload() = runBlocking {
        val file = File(tmp.root, "profiles.preferences_pb")
        val profiles = listOf(
            ProviderProfile("id-1", "Home Ollama", ProviderType.OLLAMA, "http://localhost:11434", "llama3.2"),
            ProviderProfile("id-2", "Work Claude", ProviderType.ANTHROPIC, "https://api.anthropic.com", "claude-sonnet-4-6"),
        )

        val scope1 = newScope()
        ProfileStore(dataStore(scope1, file)).save(profiles)
        scope1.close()

        // A fresh DataStore over the same file simulates an app restart.
        val scope2 = newScope()
        val loaded = ProfileStore(dataStore(scope2, file)).profiles().first()
        scope2.close()

        assertEquals(profiles, loaded)
    }

    @Test
    fun empty_store_returns_empty_list() = runBlocking {
        val file = File(tmp.root, "empty.preferences_pb")
        val scope = newScope()
        val loaded = ProfileStore(dataStore(scope, file)).profiles().first()
        scope.close()
        assertEquals(emptyList<ProviderProfile>(), loaded)
    }
}
