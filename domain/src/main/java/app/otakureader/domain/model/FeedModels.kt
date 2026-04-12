package app.otakureader.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant

/**
 * Represents a feed item - latest updates from a source.
 * Used in the Feed feature to show recent chapters across sources.
 */
@Immutable
data class FeedItem(
    val id: Long = 0,
    val mangaId: Long,
    val mangaTitle: String,
    val mangaThumbnailUrl: String?,
    val chapterId: Long,
    val chapterName: String,
    val chapterNumber: Float,
    val sourceId: Long,
    val sourceName: String,
    val timestamp: Instant,
    val isRead: Boolean = false
)

/**
 * Feed source configuration - which sources to include in feed.
 */
@Immutable
data class FeedSource(
    val sourceId: Long,
    val sourceName: String,
    val isEnabled: Boolean = true,
    val itemCount: Int = 20, // Number of items to fetch per source
    val order: Int = 0 // Display order in feed
)

/**
 * Saved search for feed - tracks a specific search query.
 */
@Immutable
data class FeedSavedSearch(
    val id: Long = 0,
    val sourceId: Long,
    val sourceName: String,
    val query: String,
    val filters: Map<String, String> = emptyMap(),
    val order: Int = 0
)
