package app.otakureader.core.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
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
     * The Gemini generative model instance.
     * Configured with the API key and model settings.
     * Note: The API key should be provided via dependency injection or BuildConfig.
     */
    private lateinit var generativeModel: GenerativeModel

    private var currentApiKey: String? = null
    private var currentModelName: String? = null

    /**
     * Initialize the Gemini client with an API key.
     *
     * @param apiKey The Gemini API key for authentication
     * @param modelName The model name to use (default: "gemini-pro")
     */
    fun initialize(apiKey: String, modelName: String = "gemini-pro") {
        require(apiKey.isNotBlank()) {
            "Gemini API key must not be blank."
        }

        if (::generativeModel.isInitialized) {
            if (currentApiKey == apiKey && currentModelName == modelName) {
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
        currentApiKey = apiKey
        currentModelName = modelName
    }

    /**
     * Generate content based on a text prompt.
     *
     * @param prompt The text prompt to send to the AI
     * @return The generated response from Gemini
     * @throws IllegalStateException if the client is not initialized
     */
    suspend fun generateContent(prompt: String): GenerateContentResponse {
        check(::generativeModel.isInitialized) {
            "GeminiClient must be initialized with an API key before use"
        }
        return generativeModel.generateContent(prompt)
    }

    /**
     * Check if the client has been initialized with an API key.
     *
     * @return true if initialized, false otherwise
     */
    fun isInitialized(): Boolean = ::generativeModel.isInitialized
}
