package dev.equerry.app.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM coverage of [EncryptedSecretStore]'s alias/CRUD contract against a plain
 * Robolectric SharedPreferences (no Keystore). The real Keystore-encrypted path —
 * round-trip + no-cleartext-on-disk — is covered by EncryptedSecretStoreInstrumentedTest
 * in androidTest, because Robolectric cannot host AndroidKeyStore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EncryptedSecretStoreTest {

    private lateinit var store: SecretStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_secrets", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = EncryptedSecretStore(prefs)
    }

    @Test
    fun put_then_get_round_trips_a_key() {
        store.putKey("p1", "sk-secret-123")
        assertEquals("sk-secret-123", store.getKey("p1"))
    }

    @Test
    fun get_returns_null_after_remove() {
        store.putKey("p1", "sk-secret-123")
        store.removeKey("p1")
        assertNull(store.getKey("p1"))
    }

    @Test
    fun keys_are_isolated_per_profile() {
        store.putKey("p1", "key-one")
        store.putKey("p2", "key-two")
        assertEquals("key-one", store.getKey("p1"))
        assertEquals("key-two", store.getKey("p2"))

        store.removeKey("p1")
        assertNull(store.getKey("p1"))
        assertEquals("key-two", store.getKey("p2"))
    }
}
