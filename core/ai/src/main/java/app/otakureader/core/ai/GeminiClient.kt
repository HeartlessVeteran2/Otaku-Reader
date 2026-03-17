package app.otakureader.core.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** HMAC algorithm used for config MAC. */
private const val HMAC_ALGORITHM = "HmacSHA256"

/**
 * Client for interacting with Google's Gemini AI API.
 *
 * This class provides a wrapper around the Gemini Generative AI SDK,
 * offering methods to generate AI-powered content and responses.
 *
 * **Initialization contract**: [initialize] must be called before [generateContent]. If
 * [generateContent] is called before initialization completes, it will throw an
 * [IllegalStateException]. Callers should ensure initialization is complete before
 * invoking content generation (e.g., check [isInitialized] or use a lifecycle-aware
 * initialization gate).
 */
@Singleton
class GeminiClient @Inject constructor() {

    /**
     * The Gemini generative model instance. Volatile to ensure visibility across threads.
     * Null until [initialize] has been called successfully.
     */
    @Volatile
    private var generativeModel: GenerativeModel? = null

    /**
     * HMAC-SHA256 of the (apiKey, modelName) pair, keyed by [hmacSalt].
     * Stored instead of the raw API key to avoid persisting secrets in memory.
     * Using HMAC with a per-process random salt means the stored value cannot be used
     * for offline brute-force attacks to recover the API key.
     */
    @Volatile
    private var configMac: ByteArray = ByteArray(0)

    private val initLock = Any()

    /**
     * Per-process random salt for HMAC. Generated once at instantiation time and never
     * exposed outside this object. Because the salt is only in process memory, an
     * attacker observing [configMac] cannot reverse it to obtain the API key.
     *
     * Uses [SecureRandom.nextBytes] for consistent entropy across platforms.
     */
    private val hmacSalt: ByteArray = ByteArray(32).apply {
        SecureRandom().nextBytes(this)
    }

    /**
     * Initialize the Gemini client with an API key.
     *
     * This method is thread-safe: concurrent callers will not produce duplicate
     * models or time-of-check/time-of-use races.
     *
     * If the client is already initialized with the same configuration, this is a no-op.
     * If the client is already initialized with a **different** configuration, call [reset]
     * before calling this method; otherwise an [IllegalStateException] is thrown.
     *
     * @param apiKey The Gemini API key for authentication
     * @param modelName The model name to use (default: "gemini-pro")
     */
    fun initialize(apiKey: String, modelName: String = "gemini-pro") {
        require(apiKey.isNotBlank()) {
            "Gemini API key must not be blank."
        }

        synchronized(initLock) {
            if (generativeModel != null) {
                val newConfigMac = configMacOf(apiKey, modelName)
                if (configMac.contentEquals(newConfigMac)) {
                    // Already initialized with the same configuration; nothing to do.
                    return
                } else {
                    throw IllegalStateException(
                        "GeminiClient has already been initialized with a different configuration. " +
                            "Call reset() before re-initializing with a new API key or model."
                    )
                }
            }

            generativeModel = GenerativeModel(
                modelName = modelName,
                // SECURITY NOTE: The raw API key is passed to the SDK here. The Gemini SDK
                // may retain this key internally (heap, caches, logs). This is an SDK
                // limitation - we cannot avoid passing the raw key. The key is stored
                // encrypted at rest (see AiPreferences), but will be in memory during use.
                apiKey = apiKey
            )
            // Store an HMAC rather than the raw API key to reduce secret exposure.
            configMac = configMacOf(apiKey, modelName)
        }
    }

    /**
     * Reset the Gemini client, clearing the current model and configuration.
     *
     * After calling this method, [isInitialized] returns false and [initialize] may
     * be called again with the same or a different API key/model combination.
     *
     * This is the correct way to support key rotation: call [reset] followed by
     * [initialize] with the new key.
     *
     * **Security**: Zeroes the [configMac] buffer before reassignment to prevent the
     * HMAC of the API key from lingering in memory until GC/reallocation.
     *
     * **Note**: The Gemini SDK's [GenerativeModel] may retain the raw API key
     * internally in its own data structures. This implementation cannot force the SDK
     * to clear its internal state. Nulling [generativeModel] makes it eligible for GC,
     * but the SDK does not expose a shutdown/close/clear method for deterministic cleanup.
     *
     * This method is thread-safe.
     */
    fun reset() {
        synchronized(initLock) {
            // SECURITY: Zero the HMAC buffer before dropping the reference to minimize
            // the window during which secret-derived material remains in memory.
            configMac.fill(0)
            generativeModel = null
            configMac = ByteArray(0)
        }
    }

    /**
     * Reinitialize the Gemini client with a new API key and/or model.
     *
     * Atomically clears the current state and initializes with the new configuration
     * in a single [synchronized] block, preventing other threads from observing a
     * partially-reset state between the clear and the new init.
     *
     * Use this for key rotation (e.g. when the user updates their API key in settings).
     *
     * **Error handling**: If initialization fails, the client remains in a reset state
     * (uninitialized). The previous configuration is not restored, as the old API key
     * may no longer be valid. Callers should handle failures by either retrying with
     * corrected parameters or accepting that the client is uninitialized.
     *
     * This method is thread-safe.
     *
     * @param apiKey The new Gemini API key for authentication
     * @param modelName The model name to use (default: "gemini-pro")
     * @throws IllegalArgumentException if the API key is blank
     * @throws Exception if model initialization fails (SDK-specific exceptions)
     */
    fun reinitialize(apiKey: String, modelName: String = "gemini-pro") {
        synchronized(initLock) {
            require(apiKey.isNotBlank()) {
                "Gemini API key must not be blank."
            }

            // Zero and clear the old state first
            configMac.fill(0)
            generativeModel = null
            configMac = ByteArray(0)

            try {
                // Attempt to create the new model
                val newModel = GenerativeModel(
                    modelName = modelName,
                    // SECURITY NOTE: The raw API key is passed to the SDK here. See initialize().
                    apiKey = apiKey
                )
                generativeModel = newModel
                configMac = configMacOf(apiKey, modelName)
            } catch (e: Exception) {
                // On failure, the client remains reset (generativeModel = null, configMac = empty).
                // We do NOT restore the old model because:
                // 1. The old API key may be invalid/revoked (why the user is changing it)
                // 2. Leaving the client uninitialized is safer than using potentially bad state
                throw e
            }
        }
    }

    /**
     * Generate content based on a text prompt.
     *
     * The Gemini SDK's [GenerativeModel.generateContent] is a suspending function and
     * honours coroutine cancellation, so [kotlinx.coroutines.withTimeout] in callers
     * will correctly abort the underlying request.
     *
     * **Note**: This method throws [IllegalStateException] if called before [initialize]
     * has returned successfully. Callers that may invoke this during app startup should
     * guard with [isInitialized] or await a lifecycle signal.
     *
     * @param prompt The text prompt to send to the AI
     * @return The generated response from Gemini
     * @throws IllegalStateException if the client is not initialized
     */
    suspend fun generateContent(prompt: String): GenerateContentResponse {
        val model = generativeModel
            ?: error(
                "GeminiClient is not yet initialized. " +
                    "Call initialize() with a valid API key before generating content."
            )
        return model.generateContent(prompt)
    }

    /**
     * Check if the client has been initialized with an API key.
     *
     * @return true if initialized, false otherwise
     */
    fun isInitialized(): Boolean = generativeModel != null

    /**
     * Compute an HMAC-SHA256 of [apiKey] and [modelName] keyed by [hmacSalt].
     *
     * Feeding the key and model name separately with a null-byte delimiter prevents
     * concatenation ambiguity (e.g. `"ab"+"cd"` vs `"a"+"bcd"` producing the same bytes).
     * Using HMAC (rather than a plain digest) means the output is bound to the per-process
     * [hmacSalt] and cannot be inverted or brute-forced to recover the API key by an
     * attacker who only observes [configMac].
     */
    private fun configMacOf(apiKey: String, modelName: String): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(hmacSalt, HMAC_ALGORITHM))
        // Feed key and model separately with a null-byte delimiter to prevent concatenation ambiguity.
        mac.update(apiKey.toByteArray(Charsets.UTF_8))
        mac.update(0.toByte())
        mac.update(modelName.toByteArray(Charsets.UTF_8))
        return mac.doFinal()
    }
}
