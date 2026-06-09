package dev.equerry.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * [SecretStore] over a [SharedPreferences] instance. In production [create] builds it
 * over Keystore-backed [EncryptedSharedPreferences] (project rule r-03), so both the
 * alias and the API key are encrypted at rest — the file never holds either in
 * cleartext. The prefs are injected so the alias/CRUD logic is testable on the JVM
 * without the Android Keystore; the real encrypted-prefs path is covered by an
 * androidTest (Robolectric cannot host the Keystore).
 */
class EncryptedSecretStore(private val prefs: SharedPreferences) : SecretStore {

    override fun putKey(profileId: String, key: String) {
        prefs.edit().putString(alias(profileId), key).apply()
    }

    override fun getKey(profileId: String): String? = prefs.getString(alias(profileId), null)

    override fun removeKey(profileId: String) {
        prefs.edit().remove(alias(profileId)).apply()
    }

    private fun alias(profileId: String) = "$KEY_PREFIX$profileId"

    companion object {
        const val FILE_NAME = "equerry_secrets"
        const val KEY_PREFIX = "provider_key_"

        /** Production store backed by Keystore-encrypted SharedPreferences (rule r-03). */
        fun create(context: Context): EncryptedSecretStore {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return EncryptedSecretStore(prefs)
        }
    }
}
