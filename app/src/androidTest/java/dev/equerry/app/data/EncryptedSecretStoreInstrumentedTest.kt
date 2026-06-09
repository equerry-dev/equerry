package dev.equerry.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device/CI coverage of the REAL Keystore-encrypted store (rule r-03, criterion c-2):
 * round-trips a key and proves neither the alias nor the secret is written in cleartext.
 * Lives in androidTest because EncryptedSharedPreferences needs the Android Keystore,
 * which Robolectric does not provide. Run with `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedSecretStoreInstrumentedTest {

    private lateinit var context: Context
    private lateinit var store: SecretStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Start from a clean backing file so a stale master key can't fail create().
        context.getSharedPreferences(EncryptedSecretStore.FILE_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = EncryptedSecretStore.create(context)
    }

    @Test
    fun round_trips_and_removes_a_key() {
        store.putKey("p1", "sk-real-123")
        assertEquals("sk-real-123", store.getKey("p1"))
        store.removeKey("p1")
        assertNull(store.getKey("p1"))
    }

    @Test
    fun backing_file_holds_neither_alias_nor_secret_in_cleartext() {
        store.putKey("p1", "sk-plaintext-marker")

        val raw = context.getSharedPreferences(EncryptedSecretStore.FILE_NAME, Context.MODE_PRIVATE)
        assertFalse("alias must not be stored in cleartext", raw.contains("provider_key_p1"))
        val rawValues = raw.all.values.map { it.toString() }
        assertTrue(
            "secret must not appear in cleartext on disk",
            rawValues.none { it.contains("sk-plaintext-marker") },
        )
    }
}
