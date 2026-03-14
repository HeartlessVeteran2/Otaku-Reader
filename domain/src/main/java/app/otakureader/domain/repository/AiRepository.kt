package app.otakureader.domain.repository

/**
 * Repository for AI-powered features.
 *
 * Provides access to AI functionality such as content generation,
 * manga recommendations, and text analysis.
 */
interface AiRepository {

    /**
     * Generate content based on a text prompt.
     *
     * @param prompt The text prompt to send to the AI
     * @return The generated text response
     */
    suspend fun generateContent(prompt: String): Result<String>

    /**
     * Check if the AI service is available and properly configured.
     *
     * @return true if the AI service is ready to use, false otherwise
     */
    suspend fun isAvailable(): Boolean

    /**
     * Initialize the AI service with configuration.
     *
     * @param apiKey The API key for the AI service
     */
    suspend fun initialize(apiKey: String)
}
