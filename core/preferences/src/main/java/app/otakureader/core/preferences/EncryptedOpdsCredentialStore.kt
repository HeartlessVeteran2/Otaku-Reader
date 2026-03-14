package app.otakureader.core.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore-backed encrypted storage for OPDS server credentials.
 *
 * Credentials are stored per-server using the server ID as the key prefix.
 * Values are encrypted with AES-256-GCM (values) and AES-256-SIV (keys),
 * so nothing is written to disk in plaintext.
 */
@Singleton
class EncryptedOpdsCredentialStore @Inject constructor(
    private val context: Context
) {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val sharedPreferences: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Retrieves the stored username for the given server, or empty string. */
    suspend fun getUsername(serverId: Long): String = withContext(Dispatchers.IO) {
        sharedPreferences.getString(usernameKey(serverId), "") ?: ""
    }

    /** Retrieves the stored password for the given server, or empty string. */
    suspend fun getPassword(serverId: Long): String = withContext(Dispatchers.IO) {
        sharedPreferences.getString(passwordKey(serverId), "") ?: ""
    }

    /** Persists credentials for the given server ID. */
    suspend fun saveCredentials(serverId: Long, username: String, password: String) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putString(usernameKey(serverId), username)
                .putString(passwordKey(serverId), password)
                .commit()
        }
    }

    /** Removes stored credentials when a server is deleted. */
    suspend fun deleteCredentials(serverId: Long) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .remove(usernameKey(serverId))
                .remove(passwordKey(serverId))
                .commit()
        }
    }

    private fun usernameKey(serverId: Long) = "${KEY_PREFIX}${serverId}_username"
    private fun passwordKey(serverId: Long) = "${KEY_PREFIX}${serverId}_password"

    private companion object {
        const val FILE_NAME = "encrypted_opds_credentials"
        const val KEY_PREFIX = "opds_"
    }
}
