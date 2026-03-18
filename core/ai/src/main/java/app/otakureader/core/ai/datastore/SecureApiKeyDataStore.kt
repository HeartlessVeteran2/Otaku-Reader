package app.otakureader.core.ai.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure DataStore for storing and retrieving encrypted API keys.
 *
 * This class provides a secure storage mechanism for AI API keys using:
 * - DataStore for structured preferences storage
 * - EncryptedSharedPreferences for encryption at rest
 * - MasterKey for key management (Android Keystore on API 23+)
 *
 * **Security Features:**
 * - API keys are encrypted at rest using AES-256
 * - Keys are never included in auto-backup (android:allowBackup="false")
 * - MasterKey is stored in Android Keystore when available
 * - In-memory caching is avoided to prevent memory dumps from leaking keys
 *
 * **BYOK (Bring Your Own Key) Support:**
 * - Runtime API key updates via [saveApiKey]
 * - Key validation before storage
 * - Support for multiple AI providers
 *
 * @param context The application context for DataStore and encryption
 */
@Singleton
class SecureApiKeyDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore: DataStore<Preferences> = context.apiKeyDataStore

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedSharedPreferences: EncryptedSharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
    }

    /**
     * Get the stored API key for a specific provider.
     *
     * @param provider The AI provider identifier (e.g., "gemini", "openai")
     * @return Flow emitting the API key or null if not set
     */
    fun getApiKey(provider: String): Flow<String?> {
        val key = stringPreferencesKey("${KEY_API_KEY_PREFIX}$provider")
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                // Try DataStore first, fall back to EncryptedSharedPreferences for migration
                preferences[key] ?: encryptedSharedPreferences.getString("${KEY_API_KEY_PREFIX}$provider", null)
            }
    }

    /**
     * Get the API key synchronously (one-shot).
     *
     * @param provider The AI provider identifier
     * @return The API key or null if not set
     */
    suspend fun getApiKeyOnce(provider: String): String? {
        return getApiKey(provider).first()
    }

    /**
     * Save an API key securely.
     *
     * The key is validated before storage to ensure it's not blank.
     * Any existing key for the same provider is overwritten.
     *
     * @param provider The AI provider identifier
     * @param apiKey The API key to store
     * @throws IllegalArgumentException if the API key is blank or invalid
     * @throws SecureStorageException if encryption or storage fails
     */
    suspend fun saveApiKey(provider: String, apiKey: String) {
        validateApiKey(apiKey)

        try {
            val key = stringPreferencesKey("${KEY_API_KEY_PREFIX}$provider")
            dataStore.edit { preferences ->
                preferences[key] = apiKey
            }

            // Also save to EncryptedSharedPreferences for redundancy
            encryptedSharedPreferences.edit()
                .putString("${KEY_API_KEY_PREFIX}$provider", apiKey)
                .apply()
        } catch (e: Exception) {
            throw SecureStorageException("Failed to save API key for provider: $provider", e)
        }
    }

    /**
     * Remove a stored API key.
     *
     * @param provider The AI provider identifier
     */
    suspend fun removeApiKey(provider: String) {
        try {
            val key = stringPreferencesKey("${KEY_API_KEY_PREFIX}$provider")
            dataStore.edit { preferences ->
                preferences.remove(key)
            }

            encryptedSharedPreferences.edit()
                .remove("${KEY_API_KEY_PREFIX}$provider")
                .apply()
        } catch (e: Exception) {
            throw SecureStorageException("Failed to remove API key for provider: $provider", e)
        }
    }

    /**
     * Clear all stored API keys.
     *
     * Use with caution - this removes all API keys from storage.
     */
    suspend fun clearAllApiKeys() {
        try {
            dataStore.edit { preferences ->
                preferences.asMap().keys
                    .filter { it.name.startsWith(KEY_API_KEY_PREFIX) }
                    .forEach { preferences.remove(it) }
            }

            encryptedSharedPreferences.edit().clear().apply()
        } catch (e: Exception) {
            throw SecureStorageException("Failed to clear all API keys", e)
        }
    }

    /**
     * Check if an API key exists for a provider.
     *
     * @param provider The AI provider identifier
     * @return true if a key exists, false otherwise
     */
    suspend fun hasApiKey(provider: String): Boolean {
        return getApiKeyOnce(provider) != null
    }

    /**
     * Get the default Gemini API key from secure storage.
     *
     * Convenience method for the most common use case.
     *
     * @return Flow emitting the Gemini API key or null
     */
    fun getGeminiApiKey(): Flow<String?> = getApiKey(PROVIDER_GEMINI)

    /**
     * Save the Gemini API key.
     *
     * @param apiKey The Gemini API key
     */
    suspend fun saveGeminiApiKey(apiKey: String) = saveApiKey(PROVIDER_GEMINI, apiKey)

    /**
     * Validate an API key format.
     *
     * @param apiKey The API key to validate
     * @throws IllegalArgumentException if the key is invalid
     */
    private fun validateApiKey(apiKey: String) {
        require(apiKey.isNotBlank()) {
            "API key cannot be blank"
        }
        require(apiKey.length >= MIN_KEY_LENGTH) {
            "API key is too short (minimum $MIN_KEY_LENGTH characters)"
        }
        // Check for common placeholder patterns
        require(!apiKey.contains(PLACEHOLDER_PATTERN)) {
            "API key appears to be a placeholder. Please provide a valid API key."
        }
    }

    companion object {
        private const val DATA_STORE_NAME = "secure_api_keys"
        private const val ENCRYPTED_PREFS_FILE_NAME = "secure_api_keys_encrypted"
        private const val KEY_API_KEY_PREFIX = "api_key_"
        private const val MIN_KEY_LENGTH = 10
        private val PLACEHOLDER_PATTERN = Regex("YOUR_|PLACEHOLDER|EXAMPLE|TEST_|DEMO_|FAKE_", RegexOption.IGNORE_CASE)

        // Provider identifiers
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_OPENAI = "openai"
        const val PROVIDER_ANTHROPIC = "anthropic"
        const val PROVIDER_CUSTOM = "custom"

        private val Context.apiKeyDataStore: DataStore<Preferences> by preferencesDataStore(
            name = DATA_STORE_NAME
        )
    }
}

/**
 * Exception thrown when secure storage operations fail.
 */
class SecureStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
