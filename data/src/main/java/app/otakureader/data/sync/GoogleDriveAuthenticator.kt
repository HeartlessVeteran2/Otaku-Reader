package app.otakureader.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.otakureader.core.preferences.EncryptedGoogleDriveTokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles Google OAuth 2.0 authentication for Drive API access.
 *
 * OAuth tokens (access token, refresh token) are stored securely in
 * [EncryptedGoogleDriveTokenStore] using Android Keystore-backed encryption.
 *
 * This is a prototype implementation. A production implementation should:
 * - Use Google Sign-In SDK or AppAuth library
 * - Handle token refresh automatically
 * - Implement proper OAuth flow with PKCE
 * - Handle auth errors and re-authentication
 *
 * For now, this provides the interface that a real implementation would fulfill.
 */
@Singleton
class GoogleDriveAuthenticator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptedTokenStore: EncryptedGoogleDriveTokenStore
) {

    private val Context.dataStore by preferencesDataStore(name = "google_drive_auth")

    /**
     * Check if user is currently authenticated.
     *
     * Returns true if a valid access token exists and has not expired.
     * This checks persisted tokens and validates token expiration.
     *
     * Note: This performs a blocking read from EncryptedSharedPreferences. The value
     * is typically cached so the performance impact is minimal.
     */
    fun isAuthenticated(): Boolean = runBlocking {
        val accessToken = encryptedTokenStore.getAccessToken()

        // No token means not authenticated
        if (accessToken.isNullOrBlank()) return@runBlocking false

        // Check token expiration if available
        val expiryTime = encryptedTokenStore.getTokenExpiry() ?: return@runBlocking false
        val currentTime = System.currentTimeMillis()

        // Token is valid if it hasn't expired (with 60-second buffer for clock skew)
        return@runBlocking expiryTime > (currentTime + 60_000)
    }

    /**
     * Initiate OAuth authentication flow.
     *
     * This should:
     * 1. Open browser or use Google Sign-In SDK
     * 2. Request Drive appDataFolder scope
     * 3. Handle OAuth callback
     * 4. Store access and refresh tokens
     *
     * @throws Exception if authentication fails
     */
    suspend fun authenticate() {
        // Prototype implementation
        // Real implementation would:
        // 1. Use Google Sign-In SDK or AppAuth
        // 2. Request scope: "https://www.googleapis.com/auth/drive.appdata"
        // 3. Handle OAuth flow
        // 4. Store tokens in DataStore
        throw NotImplementedError(
            "Google Drive authentication requires Google Sign-In SDK integration. " +
            "Add implementation using GoogleSignIn API with Drive.APPFOLDER scope."
        )
    }

    /**
     * Get current access token.
     *
     * Should handle token refresh if expired.
     *
     * @return Access token, or null if not authenticated
     */
    suspend fun getAccessToken(): String? {
        return encryptedTokenStore.getAccessToken()
    }

    /**
     * Clear stored credentials and log out.
     */
    suspend fun clearCredentials() {
        encryptedTokenStore.clearTokens()
        // Also clear any legacy DataStore entries if they exist
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * Store access and refresh tokens.
     *
     * @param accessToken OAuth access token
     * @param refreshToken OAuth refresh token
     * @param expiresIn Token expiration time in seconds
     */
    suspend fun storeTokens(
        accessToken: String,
        refreshToken: String,
        expiresIn: Long
    ) {
        val expiryTimeMillis = System.currentTimeMillis() + expiresIn * 1000
        encryptedTokenStore.storeTokens(accessToken, refreshToken, expiryTimeMillis)
    }

    /**
     * Refresh the access token using the refresh token.
     *
     * @return New access token, or null if refresh failed
     */
    suspend fun refreshAccessToken(): String? {
        val refreshToken = encryptedTokenStore.getRefreshToken() ?: return null

        // Prototype: Return null
        // Real implementation would call Google token endpoint:
        // POST https://oauth2.googleapis.com/token
        // with refresh_token grant
        return null
    }

    /**
     * One-time migration: reads any legacy plaintext OAuth tokens stored in DataStore,
     * copies them into the encrypted store (if encrypted store has no tokens yet), then removes
     * the plaintext entries. Safe to call on every app start — it is a no-op after the first
     * successful run. The legacy tokens are only removed after a confirmed successful write to
     * encrypted storage, preventing data loss if the encrypted write fails.
     */
    suspend fun migrateLegacyTokensIfNeeded() {
        val legacyAccessToken = context.dataStore.data.map { it[ACCESS_TOKEN_KEY] }.first()
        val legacyRefreshToken = context.dataStore.data.map { it[REFRESH_TOKEN_KEY] }.first()
        val legacyTokenExpiry = context.dataStore.data.map { it[TOKEN_EXPIRY_KEY] }.first()

        // Only migrate if we have legacy tokens
        if (!legacyAccessToken.isNullOrBlank() && !legacyRefreshToken.isNullOrBlank()) {
            // Only migrate if encrypted store is empty
            val currentAccessToken = encryptedTokenStore.getAccessToken()
            if (currentAccessToken.isNullOrBlank()) {
                val expiryTimeMillis = legacyTokenExpiry?.toLongOrNull()
                    ?: (System.currentTimeMillis() + 3600_000) // Default to 1 hour if missing

                // Write to encrypted store
                encryptedTokenStore.storeTokens(
                    legacyAccessToken,
                    legacyRefreshToken,
                    expiryTimeMillis
                )

                // Only remove the legacy tokens once the encrypted write has confirmed success
                val verifyAccessToken = encryptedTokenStore.getAccessToken()
                if (!verifyAccessToken.isNullOrBlank()) {
                    context.dataStore.edit { preferences ->
                        preferences.remove(ACCESS_TOKEN_KEY)
                        preferences.remove(REFRESH_TOKEN_KEY)
                        preferences.remove(TOKEN_EXPIRY_KEY)
                    }
                }
            } else {
                // Encrypted store already has tokens — just clean up the legacy entries
                context.dataStore.edit { preferences ->
                    preferences.remove(ACCESS_TOKEN_KEY)
                    preferences.remove(REFRESH_TOKEN_KEY)
                    preferences.remove(TOKEN_EXPIRY_KEY)
                }
            }
        }
    }

    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("google_drive_access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("google_drive_refresh_token")
        private val TOKEN_EXPIRY_KEY = stringPreferencesKey("google_drive_token_expiry")

        // OAuth configuration (for reference)
        const val CLIENT_ID = "YOUR_CLIENT_ID.apps.googleusercontent.com"
        const val REDIRECT_URI = "com.googleusercontent.apps.YOUR_CLIENT_ID:/oauth2redirect"
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
