package dev.equerry.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OnboardingStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newScope() = CoroutineScope(Dispatchers.IO + Job())

    private fun dataStore(scope: CoroutineScope, file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { file }

    private suspend fun CoroutineScope.close() = coroutineContext[Job]!!.cancelAndJoin()

    @Test
    fun fresh_store_is_not_completed() = runBlocking {
        val file = File(tmp.root, "onboarding1.preferences_pb")
        val scope = newScope()
        val store = OnboardingStore(dataStore(scope, file))

        // A missing key must read as false — defaulting to completed would suppress
        // onboarding for a brand-new install (c-3).
        assertFalse(store.completed().first())
        scope.close()
    }

    @Test
    fun completed_survives_a_reload() = runBlocking {
        val file = File(tmp.root, "onboarding2.preferences_pb")

        val scope1 = newScope()
        OnboardingStore(dataStore(scope1, file)).setCompleted(true)
        scope1.close()

        val scope2 = newScope()
        val loaded = OnboardingStore(dataStore(scope2, file)).completed().first()
        scope2.close()

        assertTrue(loaded)
    }
}
