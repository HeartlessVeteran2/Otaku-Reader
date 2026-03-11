package app.otakureader.core.preferences

import android.content.Context
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
     */
    suspend fun setGeminiApiKey(value: String) {
        val commitSucceeded = withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putString(KEY_GEMINI_API, value)
                .commit()
        }
        if (commitSucceeded) {
            _geminiApiKey.value = value
            // Mark as initialized so a concurrent init() won't overwrite with the stale value.
            initialized.set(true)
        }
    }

    private companion object {
        const val FILE_NAME = "encrypted_api_keys"
        const val KEY_GEMINI_API = "gemini_api_key"
    }
}
