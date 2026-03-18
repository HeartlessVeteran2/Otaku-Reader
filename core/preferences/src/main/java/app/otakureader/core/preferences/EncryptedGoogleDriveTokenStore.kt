package app.otakureader.core.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore-backed encrypted storage for Google Drive OAuth tokens.
 *
 * Access tokens, refresh tokens, and expiry timestamps are encrypted with
 * AES-256-GCM (values) and AES-256-SIV (keys) so nothing is written to disk
 * in plaintext.
 *
 * This replaces the plain DataStore storage previously used in
 * GoogleDriveAuthenticator to address the security vulnerability of storing
 * OAuth credentials in plaintext.
 */
@Singleton
class EncryptedGoogleDriveTokenStore @Inject constructor(
    @ApplicationContext private val context: Context
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

    /** Retrieves the stored access token, or null if not set. */
    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
    }

    /** Retrieves the stored refresh token, or null if not set. */
    suspend fun getRefreshToken(): String? = withContext(Dispatchers.IO) {
        sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
    }

    /** Retrieves the token expiry timestamp (milliseconds since epoch), or null if not set. */
    suspend fun getTokenExpiry(): Long? = withContext(Dispatchers.IO) {
        val expiryString = sharedPreferences.getString(KEY_TOKEN_EXPIRY, null)
        expiryString?.toLongOrNull()
    }

    /**
     * Persists OAuth tokens and expiry time.
     *
     * @param accessToken OAuth access token
     * @param refreshToken OAuth refresh token
     * @param expiryTimeMillis Token expiry time in milliseconds since epoch
     */
    suspend fun storeTokens(
        accessToken: String,
        refreshToken: String,
        expiryTimeMillis: Long
    ) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_TOKEN_EXPIRY, expiryTimeMillis.toString())
                .commit()
        }
    }

    /** Removes all stored tokens (used when logging out). */
    suspend fun clearTokens() {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .clear()
                .commit()
        }
    }

    private companion object {
        const val FILE_NAME = "encrypted_google_drive_tokens"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_TOKEN_EXPIRY = "token_expiry"
    }
}
