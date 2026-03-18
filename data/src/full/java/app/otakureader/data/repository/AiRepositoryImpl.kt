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

private const val DEFAULT_GENERATE_CONTENT_TIMEOUT_MILLIS = 30_000L

/**
 * Implementation of [AiRepository] using Google's Gemini AI.
 *
 * This repository provides AI-powered features by interfacing with
 * the Gemini client from the core:ai module.
 *
 * This class lives in the `full` flavor source set so it (and its Gemini SDK
 * transitive dependency) are excluded from FOSS builds entirely.
 *
 * @property geminiClient Client for Gemini AI operations
 */
@Singleton
class AiRepositoryImpl @Inject constructor(
    private val geminiClient: GeminiClient
) : AiRepository {

    private val timeoutMillis: Long = DEFAULT_GENERATE_CONTENT_TIMEOUT_MILLIS

    /**
     * Generate content using the Gemini AI model.
     *
     * The request is wrapped in a [kotlinx.coroutines.withTimeout] of [timeoutMillis].
     * The Gemini SDK's `generateContent` is a suspending function that honours coroutine
     * cancellation, so the timeout will correctly abort the in-flight network request.
     *
     * Timeout behaviour: a [kotlinx.coroutines.TimeoutCancellationException] is caught
     * and converted to a [Result.failure] with a descriptive [IllegalStateException].
     * This is INTENTIONAL - timeouts are treated as domain errors (the AI service is
     * temporarily unavailable) rather than coroutine cancellations. Callers can distinguish
     * timeouts from other errors by checking the exception message. External cancellations
     * (any other [kotlinx.coroutines.CancellationException]) are re-thrown so the caller's
     * coroutine scope is properly cancelled.
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

            val response = withTimeout(timeoutMillis) {
                geminiClient.generateContent(prompt)
            }
            val generatedText = response.text ?: ""

            if (generatedText.isBlank()) {
                Result.failure(IllegalStateException("AI generated an empty response"))
            } else {
                Result.success(generatedText)
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("AI request timed out after ${timeoutMillis}ms", e))
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

    /**
     * Clear the active API key and reset the Gemini client to an uninitialized state.
     *
     * Callers should also remove the persisted key from [AiPreferences] so that the client
     * is not re-initialized on the next app start.
     */
    override suspend fun clearApiKey() {
        geminiClient.reset()
    }
}
