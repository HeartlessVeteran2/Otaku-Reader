package app.otakureader.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Represents an AI-recommended manga with confidence score and reasoning.
 *
 * @property manga The recommended manga object
 * @property confidenceScore AI confidence score (0.0 to 1.0) for this recommendation
 * @property reasoning Human-readable explanation of why this manga is recommended
 * @property basedOnMangaIds IDs of user's manga that influenced this recommendation
 * @property basedOnGenres Genres from user's history that influenced this recommendation
 * @property recommendationType Type of recommendation (similar, discovery, trending, etc.)
 * @property generatedAt Timestamp when the recommendation was generated
 */
@Immutable
@Serializable
data class RecommendedManga(
    val manga: Manga,
    val confidenceScore: Float,
    val reasoning: String,
    val basedOnMangaIds: List<Long> = emptyList(),
    val basedOnGenres: List<String> = emptyList(),
    val recommendationType: RecommendationType = RecommendationType.SIMILAR,
    val generatedAt: Long = System.currentTimeMillis()
) {
    init {
        require(confidenceScore in 0.0f..1.0f) {
            "Confidence score must be between 0.0 and 1.0, was $confidenceScore"
        }
    }

    companion object {
        /**
         * Minimum confidence threshold for displaying recommendations.
         */
        const val MIN_CONFIDENCE_THRESHOLD = 0.5f

        /**
         * Cache duration in milliseconds (24 hours).
         */
        const val CACHE_DURATION_MS = 24L * 60 * 60 * 1000
    }
}

/**
 * Result wrapper for AI recommendations with metadata.
 *
 * @property recommendations Ranked list of recommended manga (max 10)
 * @property refreshedAt When the recommendations were last refreshed
 * @property expiresAt When the cache expires (24 hours after refresh)
 * @property isCached Whether these results came from cache
 * @property analysisSummary Brief summary of the user's reading patterns used for recommendations
 */
@Immutable
@Serializable
data class RecommendedMangaResult(
    val recommendations: List<RecommendedManga>,
    val refreshedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + RecommendedManga.CACHE_DURATION_MS,
    val isCached: Boolean = false,
    val analysisSummary: String? = null
) {
    companion object {
        const val MAX_RECOMMENDATIONS = 10
    }
}

/**
 * Input data for AI recommendation analysis.
 *
 * @property readingHistory List of manga with reading engagement data
 * @property favoriteManga User's favorited manga
 * @property ratedManga Manga with user ratings
 * @property timeSpentPerManga Map of manga ID to time spent in milliseconds
 * @property totalReadingTimeMs Total time spent reading across all manga
 * @property preferredGenres User's preferred genres based on history
 * @property favoriteAuthors User's favorite authors based on reading patterns
 */
@Immutable
@Serializable
data class RecommendationAnalysisInput(
    val readingHistory: List<MangaReadingHistory>,
    val favoriteManga: List<Manga> = emptyList(),
    val ratedManga: List<RatedManga> = emptyList(),
    val timeSpentPerManga: Map<Long, Long> = emptyMap(),
    val totalReadingTimeMs: Long = 0L,
    val preferredGenres: List<GenrePreference> = emptyList(),
    val favoriteAuthors: List<String> = emptyList()
)

/**
 * Reading history entry for a specific manga.
 *
 * @property manga The manga
 * @property chaptersRead Number of chapters read
 * @property timeSpentMs Total time spent reading in milliseconds
 * @property isCompleted Whether the user completed the series
 * @property completionPercentage Percentage of chapters read (0.0 to 1.0)
 * @property lastReadAt Timestamp of last read
 * @property isFavorite Whether the manga is favorited
 */
@Immutable
@Serializable
data class MangaReadingHistory(
    val manga: Manga,
    val chaptersRead: Int = 0,
    val timeSpentMs: Long = 0L,
    val isCompleted: Boolean = false,
    val completionPercentage: Float = 0f,
    val lastReadAt: Long? = null,
    val isFavorite: Boolean = false
)

/**
 * Rated manga entry.
 *
 * @property manga The manga
 * @property rating User rating (typically 1-5 or 1-10)
 */
@Immutable
@Serializable
data class RatedManga(
    val manga: Manga,
    val rating: Int
)
