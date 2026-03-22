package app.otakureader.domain.model.search

import app.otakureader.domain.model.MangaStatus

/**
 * M-19: Shared MatchMode enum extracted from [SearchIntent.GenreSearch] and
 * [SearchIntent.DescriptionSearch] where it was duplicated. Both classes now
 * reference this single definition.
 */
enum class MatchMode {
    /** Match any of the provided values (logical OR). */
    ANY,
    /** Match all of the provided values (logical AND). */
    ALL
}

/**
 * Sealed class representing different search intents extracted from natural language queries.
 */
sealed interface SearchIntent {
    /**
     * Search by manga title.
     */
    data class TitleSearch(
        val title: String,
        val fuzzyMatch: Boolean = true
    ) : SearchIntent

    /**
     * Search by genre/tag.
     */
    data class GenreSearch(
        val genres: List<String>,
        // M-19: References the shared top-level MatchMode instead of a duplicate inner enum.
        val matchMode: MatchMode = MatchMode.ANY
    ) : SearchIntent

    /**
     * Search by author name.
     */
    data class AuthorSearch(
        val author: String,
        val includeArtist: Boolean = true
    ) : SearchIntent

    /**
     * Search by description keywords.
     */
    data class DescriptionSearch(
        val keywords: List<String>,
        // M-19: References the shared top-level MatchMode instead of a duplicate inner enum.
        val matchMode: MatchMode = MatchMode.ANY
    ) : SearchIntent

    /**
     * Search by mood/atmosphere.
     */
    data class MoodSearch(
        val mood: Mood
    ) : SearchIntent {
        enum class Mood {
            DARK,
            LIGHTHEARTED,
            ACTION_PACKED,
            ROMANTIC,
            MYSTERIOUS,
            COMEDY,
            DRAMATIC,
            HORROR,
            ADVENTURE,
            SLICE_OF_LIFE,
            PSYCHOLOGICAL,
            THRILLING,
            HEARTWARMING,
            EPIC,
            TRAGIC
        }
    }

    /**
     * Search by manga status.
     */
    data class StatusSearch(
        val status: MangaStatus
    ) : SearchIntent

    /**
     * Search by popularity/rating.
     */
    data class PopularitySearch(
        val minRating: Float? = null,
        val minPopularity: Int? = null
    ) : SearchIntent

    /**
     * Search by content rating/maturity.
     */
    data class ContentRatingSearch(
        val allowNsfw: Boolean = false,
        val contentWarnings: List<String> = emptyList()
    ) : SearchIntent

    /**
     * Search by publication year/decade.
     */
    data class YearSearch(
        val year: Int? = null,
        val decade: Int? = null,
        val yearRange: IntRange? = null
    ) : SearchIntent

    /**
     * Composite search combining multiple intents with AND logic.
     */
    data class CompositeSearch(
        val intents: List<SearchIntent>
    ) : SearchIntent
}

/**
 * Data class representing the result of parsing a natural language query.
 */
data class ParsedSearchQuery(
    val originalQuery: String,
    val intents: List<SearchIntent>,
    val confidence: Float,
    val suggestedSourceIds: List<String>? = null,
    val isAmbiguous: Boolean = false,
    val clarificationPrompt: String? = null
)

/**
 * Data class representing a cached smart search.
 */
data class CachedSmartSearch(
    val queryHash: String,
    val originalQuery: String,
    val parsedQuery: ParsedSearchQuery,
    val timestamp: Long = System.currentTimeMillis()
)