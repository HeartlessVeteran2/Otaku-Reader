package app.otakureader.domain.model

/**
 * AI-derived quality score for a single manga source.
 *
 * Scores range from 0.0 (poor) to 1.0 (excellent).
 *
 * @property sourceId Identifier of the source being scored.
 * @property mangaId The manga for which this score was calculated.
 * @property contentQualityScore Translation/scan quality (0–1).
 * @property updateFrequencyScore How frequently the source updates this manga (0–1).
 * @property reliabilityScore Estimated uptime / error rate (0–1).
 * @property overallScore Weighted aggregate of the individual scores (0–1).
 * @property recommendation Short AI-generated justification for the ranking.
 * @property analyzedAt Unix timestamp (ms) when the analysis was performed.
 */
data class SourceScore(
    val sourceId: String,
    val mangaId: Long,
    val contentQualityScore: Float,
    val updateFrequencyScore: Float,
    val reliabilityScore: Float,
    val overallScore: Float,
    val recommendation: String,
    val analyzedAt: Long = System.currentTimeMillis(),
)
