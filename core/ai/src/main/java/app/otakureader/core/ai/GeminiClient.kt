package app.otakureader.core.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for interacting with Google's Gemini AI API.
 *
 * This class provides a wrapper around the Gemini Generative AI SDK,
 * offering methods to generate AI-powered content and responses.
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
     * Hash of the (apiKey, modelName) pair used during initialization.
     * Stored instead of the raw API key to avoid persisting secrets in memory.
     * Uses SHA-256 to avoid false-positive collisions from String.hashCode().
     */
    @Volatile
    private var configHash: String = ""

    private val initLock = Any()

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
                val newConfigHash = configHashOf(apiKey, modelName)
                if (configHash == newConfigHash) {
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
            // Store a hash rather than the raw API key to reduce secret exposure.
            configHash = configHashOf(apiKey, modelName)
        }
    }

    /**
     * Generate content based on a text prompt.
     *
     * @param prompt The text prompt to send to the AI
     * @return The generated response from Gemini
     * @throws IllegalStateException if the client is not initialized
     */
    suspend fun generateContent(prompt: String): GenerateContentResponse {
        val model = generativeModel
            ?: error("GeminiClient must be initialized with an API key before use")
        return model.generateContent(prompt)
    }

    /**
     * Check if the client has been initialized with an API key.
     *
     * @return true if initialized, false otherwise
     */
    fun isInitialized(): Boolean = generativeModel != null

    private fun configHashOf(apiKey: String, modelName: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        // Feed key and model separately with a null-byte delimiter to avoid collisions.
        // Avoid concatenating the API key into a String to minimize secret exposure.
        digest.update(apiKey.toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(modelName.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
