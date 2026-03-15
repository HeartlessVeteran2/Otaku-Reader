package app.otakureader.data.repository

import app.otakureader.core.ai.GeminiClient
import app.otakureader.domain.repository.AiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

private const val GENERATE_CONTENT_TIMEOUT_MILLIS = 30_000L

/**
 * Implementation of [AiRepository] using Google's Gemini AI.
 *
 * This repository provides AI-powered features by interfacing with
 * the Gemini client from the core:ai module.
 */
@Singleton
class AiRepositoryImpl @Inject constructor(
    private val geminiClient: GeminiClient
) : AiRepository {

    /**
     * Generate content using the Gemini AI model.
     *
     * @param prompt The text prompt to send to the AI
     * @return Result containing the generated text on success, or an error on failure
     */
    override suspend fun generateContent(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!geminiClient.isInitialized()) {
                return@withContext Result.failure(
                    IllegalStateException("AI service is not initialized. Please configure an API key.")
                )
            }

            val response = withTimeout(GENERATE_CONTENT_TIMEOUT_MILLIS) {
                geminiClient.generateContent(prompt)
            }
            val generatedText = response.text ?: ""

            if (generatedText.isBlank()) {
                Result.failure(IllegalStateException("AI generated an empty response"))
            } else {
                Result.success(generatedText)
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("AI request timed out after ${GENERATE_CONTENT_TIMEOUT_MILLIS}ms", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if the AI service is properly configured and available.
     *
     * @return true if the service is initialized and ready to use
     */
    override suspend fun isAvailable(): Boolean {
        return geminiClient.isInitialized()
    }

    /**
     * Initialize the AI service with an API key.
     *
     * @param apiKey The Gemini API key for authentication
     */
    override suspend fun initialize(apiKey: String) {
        geminiClient.initialize(apiKey)
    }
}
