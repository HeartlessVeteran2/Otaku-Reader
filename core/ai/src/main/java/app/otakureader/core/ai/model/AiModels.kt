package app.otakureader.core.ai.model

/**
 * Data models for AI-related functionality.
 *
 * This package contains domain models representing AI requests,
 * responses, and related data structures.
 */

/**
 * Represents a request to generate AI content.
 *
 * @property prompt The user's text prompt
 * @property maxTokens Optional maximum number of tokens to generate
 * @property temperature Optional temperature for response randomness (0.0 to 1.0)
 */
data class AiRequest(
    val prompt: String,
    val maxTokens: Int? = null,
    val temperature: Double? = null
)

/**
 * Represents a response from the AI service.
 *
 * @property content The generated text content
 * @property finishReason The reason the generation finished
 * @property safetyRatings Optional safety ratings for the response
 */
data class AiResponse(
    val content: String,
    val finishReason: String? = null,
    val safetyRatings: List<SafetyRating>? = null
)

/**
 * Represents a safety rating for AI-generated content.
 *
 * @property category The safety category
 * @property probability The probability level
 */
data class SafetyRating(
    val category: String,
    val probability: String
)
