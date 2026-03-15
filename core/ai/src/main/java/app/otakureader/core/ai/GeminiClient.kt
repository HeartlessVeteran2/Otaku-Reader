package app.otakureader.core.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

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
     */
    private val hmacSalt: ByteArray = SecureRandom().generateSeed(32)

    /**
     * Initialize the Gemini client with an API key.
     *
     * This method is thread-safe: concurrent callers will not produce duplicate
     * models or time-of-check/time-of-use races.
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
                        "GeminiClient has already been initialized. " +
                            "Re-initialization with a different API key or model is not allowed."
                    )
                }
            }

            generativeModel = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey
            )
            // Store an HMAC rather than the raw API key to reduce secret exposure.
            configMac = configMacOf(apiKey, modelName)
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
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacSalt, "HmacSHA256"))
        // Feed key and model separately with a null-byte delimiter to prevent concatenation ambiguity.
        mac.update(apiKey.toByteArray(Charsets.UTF_8))
        mac.update(0.toByte())
        mac.update(modelName.toByteArray(Charsets.UTF_8))
        return mac.doFinal()
    }
}
