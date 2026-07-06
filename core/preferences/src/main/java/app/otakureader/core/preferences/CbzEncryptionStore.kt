package app.otakureader.core.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore-backed encrypted storage for the CBZ archive encryption passphrase.
 *
 * Uses the same EncryptedSharedPreferences pattern as [CloudBackupCredentialsStore].
 * All reads and writes are synchronous — wrap in [kotlinx.coroutines.Dispatchers.IO]
 * if a strict threading policy is required.
 */
@Singleton
class CbzEncryptionStore @Inject constructor(@param:ApplicationContext private val context: Context) {


    // Created via EncryptedPrefsFactory so Keystore corruption recovers instead of
    // crash-looping the app (stored secrets are reset; the user re-authenticates).
    private val prefs: SharedPreferences by lazy {
        EncryptedPrefsFactory.create(context, "cbz_encryption")
    }

    fun getPassphrase(): String? = prefs.getString(KEY_PASSPHRASE, null)

    fun setPassphrase(passphrase: String) {
        prefs.edit().putString(KEY_PASSPHRASE, passphrase).apply()
    }

    fun clearPassphrase() {
        prefs.edit().remove(KEY_PASSPHRASE).apply()
    }

    private companion object {
        const val KEY_PASSPHRASE = "cbz_passphrase"
    }
}
