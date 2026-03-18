package app.otakureader.core.ai.model

/**
 * Sealed class representing AI-specific exceptions.
 */
sealed class AiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** AI client has not been initialized with an API key. */
    class NotInitialized : AiException("AI client is not initialized. Configure an API key first.")

    /** The AI request timed out. */
    class Timeout(cause: Throwable? = null) : AiException("AI request timed out", cause)

    /** A network error occurred while contacting the AI service. */
    class NetworkError(message: String = "Network error", cause: Throwable? = null) : AiException(message, cause)

    /** The request was invalid (e.g. blank prompt). */
    class InvalidRequest(message: String) : AiException(message)
}
