package app.otakureader.core.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Secure storage for sensitive API keys using Android Keystore-backed
 * [EncryptedSharedPreferences]. Keys are encrypted with AES-256-GCM and
 * preference keys are encrypted with AES-256-SIV, so raw values are never
 * written to disk in plaintext.
 *
 * Both [MasterKey] creation and [EncryptedSharedPreferences] creation are
 * deferred via `by lazy` so that Keystore/disk work does not execute on the
 * main thread during Hilt singleton initialisation. Callers must invoke
 * [init] from a coroutine before reading [geminiApiKey] to ensure the
 * persisted value is loaded off the main thread. [init] is idempotent — the
 * disk read only happens on the first invocation.
 *
 * **Key Rotation:**
 * Non-sensitive metadata (the epoch-millisecond timestamp of the last
 * save/rotation) is stored in a separate, non-encrypted [SharedPreferences]
 * file (`${FILE_NAME}_meta`).  Use [getGeminiKeyStoredAt] to retrieve the
 * timestamp and [isGeminiKeyRotationRecommended] to check whether the key
 * is older than [DEFAULT_KEY_MAX_AGE_DAYS] days.  To rotate, call
 * [setGeminiApiKey] with the replacement key value.
 */
class EncryptedApiKeyStore(private val context: Context) {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val sharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Non-encrypted preferences for non-sensitive key metadata (e.g. timestamps). */
    private val metaPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("${FILE_NAME}_meta", Context.MODE_PRIVATE)
    }

    private val initialized = AtomicBoolean(false)
    private val _geminiApiKey = MutableStateFlow("")

    /** Gemini API key as an observable [StateFlow]. Empty until [init] is called. */
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    /**
     * Reads the stored API key from disk on [Dispatchers.IO] and populates [geminiApiKey].
     * Safe to call from multiple coroutines — the disk read is performed only once.
     */
    suspend fun init() {
        if (initialized.compareAndSet(false, true)) {
            val stored = withContext(Dispatchers.IO) {
                sharedPreferences.getString(KEY_GEMINI_API, "") ?: ""
            }
            _geminiApiKey.value = stored
        }
    }

    /**
     * Persists the Gemini API key to encrypted storage and updates the observable state.
     * Uses [commit] (synchronous) to ensure the value is safely written before returning.
     * The in-memory state is only updated when the write succeeds.
     *
     * The current epoch-millisecond timestamp is also recorded in the meta-preferences
     * file so that [isGeminiKeyRotationRecommended] can report accurate key age.
     * The timestamp is written with [apply] (async) because missing a timestamp on
     * a process kill is acceptable — the next successful [setGeminiApiKey] call will
     * record a fresh timestamp.
     */
    suspend fun setGeminiApiKey(value: String) {
        val commitSucceeded = withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putString(KEY_GEMINI_API, value)
                .commit()
        }
        if (commitSucceeded) {
            // Timestamp is non-sensitive metadata; apply() is fine here (async write is acceptable).
            metaPrefs.edit()
                .putLong(KEY_GEMINI_STORED_AT, System.currentTimeMillis())
                .apply()
            _geminiApiKey.value = value
            // Mark as initialized so a concurrent init() won't overwrite with the stale value.
            initialized.set(true)
        }
    }

    // ── Key rotation ──────────────────────────────────────────────────────────

    /**
     * Return the epoch-millisecond timestamp recorded when [setGeminiApiKey] last
     * succeeded, or `null` if no key has been stored via this class.
     */
    fun getGeminiKeyStoredAt(): Long? {
        val value = metaPrefs.getLong(KEY_GEMINI_STORED_AT, TIMESTAMP_ABSENT)
        return if (value == TIMESTAMP_ABSENT) null else value
    }

    /**
     * Return `true` if the Gemini API key is older than [maxAgeDays] days.
     *
     * Returns `false` when no timestamp is available (key was stored before
     * rotation-tracking was introduced, or no key has been set).
     *
     * @param maxAgeDays Maximum acceptable key age in days (default: [DEFAULT_KEY_MAX_AGE_DAYS])
     */
    fun isGeminiKeyRotationRecommended(maxAgeDays: Long = DEFAULT_KEY_MAX_AGE_DAYS): Boolean {
        val storedAt = getGeminiKeyStoredAt() ?: return false
        val ageMs = System.currentTimeMillis() - storedAt
        return (ageMs / MILLIS_PER_DAY) >= maxAgeDays
    }

    private companion object {
        const val FILE_NAME = "encrypted_api_keys"
        const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_GEMINI_STORED_AT = "gemini_key_stored_at"
        private const val TIMESTAMP_ABSENT = -1L
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

        /** Default maximum key age in days before rotation is recommended. */
        const val DEFAULT_KEY_MAX_AGE_DAYS = 90L
    }
}
