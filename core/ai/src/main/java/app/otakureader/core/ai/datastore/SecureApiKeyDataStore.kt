package app.otakureader.core.ai.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
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
 * - API keys are encrypted at rest using AES-256-GCM (values) and AES-256-SIV (keys)
 * - Keys are never included in auto-backup (android:allowBackup="false")
 * - MasterKey is stored in Android Keystore; raw key material never leaves the Keystore
 * - In-memory caching is avoided to prevent memory dumps from leaking keys
 * - Key creation timestamps are stored (in plaintext DataStore; timestamps are not sensitive)
 *   to enable age-based rotation recommendations
 *
 * **BYOK (Bring Your Own Key) Support:**
 * - Runtime API key updates via [saveApiKey]
 * - Key validation before storage
 * - Support for multiple AI providers
 *
 * **Key Rotation:**
 * - Use [rotateApiKey] to replace an existing key with a new one; the stored-at timestamp
 *   is updated automatically so the rotation age clock resets.
 * - [isKeyRotationRecommended] returns `true` when a key is older than [DEFAULT_KEY_MAX_AGE_DAYS]
 *   (default 90 days).  Surface this in the settings UI as a non-blocking recommendation.
 * - [getKeyStoredAt] returns the epoch-millisecond timestamp of the last save/rotation.
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
     * **C-10 fix:** Keys are read exclusively from [EncryptedSharedPreferences]. The
     * plaintext DataStore is checked only during a one-time migration window so that
     * existing users are not locked out. Once the migration key is absent from DataStore
     * the fallback path is never reached.
     *
     * @param provider The AI provider identifier (e.g., "gemini", "openai")
     * @return Flow emitting the API key or null if not set
     */
    fun getApiKey(provider: String): Flow<String?> {
        val legacyKey = stringPreferencesKey("${KEY_API_KEY_PREFIX}$provider")
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                // Primary source: EncryptedSharedPreferences (never plaintext on disk).
                val encrypted = encryptedSharedPreferences.getString("${KEY_API_KEY_PREFIX}$provider", null)
                if (!encrypted.isNullOrBlank()) return@map encrypted

                // One-time migration fallback: read from legacy plaintext DataStore.
                // This path is only reachable before migrateLegacyKeys() has run.
                preferences[legacyKey]
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
     * **C-10 fix:** The key is written *exclusively* to [EncryptedSharedPreferences].
     * The previous implementation also wrote to the plaintext DataStore, doubling the
     * attack surface. The DataStore write has been removed.
     *
     * The epoch-millisecond timestamp is recorded in the plaintext DataStore under
     * `key_stored_at_{provider}`. Timestamps are not sensitive and are used only for
     * key-rotation age checks.
     *
     * @param provider The AI provider identifier
     * @param apiKey The API key to store
     * @throws IllegalArgumentException if the API key is blank or invalid
     * @throws SecureStorageException if encryption or storage fails
     */
    suspend fun saveApiKey(provider: String, apiKey: String) {
        validateApiKey(apiKey)

        try {
            // C-10: Write exclusively to EncryptedSharedPreferences — never to plaintext DataStore.
            encryptedSharedPreferences.edit()
                .putString("${KEY_API_KEY_PREFIX}$provider", apiKey)
                .apply()
            // Record when the key was stored (timestamp is not sensitive).
            // Note: apply() above is asynchronous — the timestamp DataStore write below
            // may complete before the encrypted write is flushed to disk.  If the process
            // is killed between the two writes the timestamp will be absent on next start,
            // causing isKeyRotationRecommended to return false (a safe/conservative default).
            dataStore.edit { preferences ->
                preferences[keyStoredAtKey(provider)] = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            throw SecureStorageException("Failed to save API key for provider: $provider", e)
        }
    }

    /**
     * Remove a stored API key.
     *
     * Also removes any legacy plaintext entry from DataStore so that the migration
     * path does not resurrect a deleted key on the next app start.
     * The stored-at timestamp for the provider is also removed.
     *
     * @param provider The AI provider identifier
     */
    suspend fun removeApiKey(provider: String) {
        try {
            // Primary store: EncryptedSharedPreferences.
            encryptedSharedPreferences.edit()
                .remove("${KEY_API_KEY_PREFIX}$provider")
                .apply()

            // Also remove any legacy plaintext DataStore entry (migration cleanup)
            // and the stored-at timestamp.
            val legacyKey = stringPreferencesKey("${KEY_API_KEY_PREFIX}$provider")
            dataStore.edit { preferences ->
                preferences.remove(legacyKey)
                preferences.remove(keyStoredAtKey(provider))
            }
        } catch (e: Exception) {
            throw SecureStorageException("Failed to remove API key for provider: $provider", e)
        }
    }

    /**
     * Clear all stored API keys.
     *
     * Removes keys from both [EncryptedSharedPreferences] and any legacy plaintext
     * DataStore entries, as well as all stored-at timestamps.
     *
     * Use with caution — this removes all API keys from storage.
     */
    suspend fun clearAllApiKeys() {
        try {
            // Primary store.
            encryptedSharedPreferences.edit().clear().apply()

            // Legacy plaintext DataStore cleanup and timestamp removal.
            dataStore.edit { preferences ->
                preferences.asMap().keys
                    .filter {
                        it.name.startsWith(KEY_API_KEY_PREFIX) ||
                            it.name.startsWith(KEY_STORED_AT_PREFIX)
                    }
                    .forEach { preferences.remove(it) }
            }
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

    // ── Key rotation ──────────────────────────────────────────────────────────

    /**
     * Rotate an API key by replacing it with a new key value.
     *
     * The stored-at timestamp is updated so the rotation age clock resets.
     * This is functionally equivalent to [saveApiKey] but makes the intent
     * explicit at call sites.
     *
     * @param provider The AI provider identifier
     * @param newApiKey The replacement API key
     * @throws IllegalArgumentException if the new key is invalid
     * @throws SecureStorageException if the write fails
     */
    suspend fun rotateApiKey(provider: String, newApiKey: String) {
        saveApiKey(provider, newApiKey)
    }

    /**
     * Return the epoch-millisecond timestamp recorded when the key for [provider]
     * was last saved or rotated, or `null` if no key has been stored yet.
     */
    suspend fun getKeyStoredAt(provider: String): Long? {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }
            .map { it[keyStoredAtKey(provider)] }
            .first()
    }

    /**
     * Return `true` if the stored key for [provider] is older than [maxAgeDays] days.
     *
     * Returns `false` when no key has been stored (no timestamp available).
     * The default threshold is [DEFAULT_KEY_MAX_AGE_DAYS] (90 days) — a key older than
     * that should be rotated as a precautionary measure.
     *
     * @param provider The AI provider identifier
     * @param maxAgeDays Maximum acceptable key age in days (default: 90)
     */
    suspend fun isKeyRotationRecommended(
        provider: String,
        maxAgeDays: Long = DEFAULT_KEY_MAX_AGE_DAYS,
    ): Boolean {
        val storedAt = getKeyStoredAt(provider) ?: return false
        val ageMs = System.currentTimeMillis() - storedAt
        return (ageMs / MILLIS_PER_DAY) >= maxAgeDays
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
        private const val KEY_STORED_AT_PREFIX = "key_stored_at_"
        private const val MIN_KEY_LENGTH = 10
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
        private val PLACEHOLDER_PATTERN = Regex("YOUR_|PLACEHOLDER|EXAMPLE|TEST_|DEMO_|FAKE_", RegexOption.IGNORE_CASE)

        /** Default maximum key age in days before rotation is recommended. */
        const val DEFAULT_KEY_MAX_AGE_DAYS = 90L

        // Provider identifiers
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_OPENAI = "openai"
        const val PROVIDER_ANTHROPIC = "anthropic"
        const val PROVIDER_CUSTOM = "custom"

        private val Context.apiKeyDataStore: DataStore<Preferences> by preferencesDataStore(
            name = DATA_STORE_NAME
        )
    }

    private fun keyStoredAtKey(provider: String) =
        longPreferencesKey("${KEY_STORED_AT_PREFIX}$provider")
}

/**
 * Exception thrown when secure storage operations fail.
 */
class SecureStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
