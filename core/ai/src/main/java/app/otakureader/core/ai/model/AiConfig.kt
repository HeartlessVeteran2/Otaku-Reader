package app.otakureader.core.ai.model

/**
 * Configuration parameters for AI content generation.
 *
 * @property maxTokens Maximum number of tokens to generate (null = model default)
 * @property temperature Sampling temperature (0.0 = deterministic, 1.0 = creative, null = default)
 * @property topP Top-p nucleus sampling value (null = default)
 * @property topK Top-k sampling value (null = default)
 * @property requestTimeoutMillis Request timeout in milliseconds
 * @property enableSafetyFilters Whether safety filters are enabled
 */
data class AiConfig(
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val requestTimeoutMillis: Long = 30_000L,
    val enableSafetyFilters: Boolean = true
) {
    companion object {
        /** Conservative default settings. */
        val DEFAULT = AiConfig()

        /** Fast responses with lower quality. */
        val FAST = AiConfig(
            maxTokens = 256,
            temperature = 0.3,
            requestTimeoutMillis = 15_000L
        )

        /** Creative outputs with higher diversity. */
        val CREATIVE = AiConfig(
            temperature = 0.9,
            topP = 0.95,
            topK = 40
        )

        /** Precise outputs for factual tasks. */
        val PRECISE = AiConfig(
            temperature = 0.1,
            topP = 0.8,
            topK = 10
        )
    }
}
