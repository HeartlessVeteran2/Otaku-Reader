package app.otakureader.core.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Secure storage for sensitive API keys using Android Keystore-backed
 * [EncryptedSharedPreferences]. Keys are encrypted with AES-256-GCM and
 * preference keys are encrypted with AES-256-SIV, so raw values are never
 * written to disk in plaintext.
 */
class EncryptedApiKeyStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _geminiApiKey = MutableStateFlow(
        sharedPreferences.getString(KEY_GEMINI_API, "") ?: ""
    )

    /** Gemini API key as an observable [StateFlow]. */
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    /**
     * Persists the Gemini API key to encrypted storage and updates the observable state.
     * Uses [commit] (synchronous) to ensure the value is safely written before returning.
     */
    suspend fun setGeminiApiKey(value: String) {
        val commitSucceeded = withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putString(KEY_GEMINI_API, value)
                .commit()
        }
        if (commitSucceeded) {
            _geminiApiKey.value = value
        }
    }

    private companion object {
        const val FILE_NAME = "encrypted_api_keys"
        const val KEY_GEMINI_API = "gemini_api_key"
    }
}
