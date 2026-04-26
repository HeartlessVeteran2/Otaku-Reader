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
     * Generate content from an image plus a text prompt (multimodal).
     *
     * Used by features that need the AI to actually see the page image —
     * currently OCR translation. Implementations are expected to route this
     * through a vision-capable model (e.g. Gemini 1.5 Flash on the free tier).
     *
     * The image bytes must be a JPEG or PNG that can be decoded by the host
     * platform's image-loading APIs. Callers should downscale to a reasonable
     * size (e.g. ≤1024 px on the long edge) before invoking this method to
     * keep request payloads small and respect free-tier quotas.
     *
     * In FOSS / no-op builds this returns a failure result.
     *
     * @param imageBytes JPEG/PNG-encoded image bytes.
     * @param prompt The text prompt accompanying the image.
     * @return The generated text response, or failure if the AI service is
     *   unavailable or rejects the request.
     */
    suspend fun generateContentWithImage(
        imageBytes: ByteArray,
        prompt: String,
    ): Result<String>

    /**
     * Check if the AI service is available and properly configured.
     *
     * @return true if the AI service is ready to use, false otherwise
     */
    suspend fun isAvailable(): Boolean

    /**
     * Initialize the AI service with an API key.
     *
     * @param apiKey The Gemini API key for authentication
     * @throws IllegalStateException if the API key is blank
     */
    suspend fun initialize(apiKey: String)

    /**
     * Clear the active API key and reset the AI client to an uninitialized state.
     *
     * After this call, [isAvailable] returns false until a new key is configured
     * and [initialize] is called again.
     */
    suspend fun clearApiKey()
}
