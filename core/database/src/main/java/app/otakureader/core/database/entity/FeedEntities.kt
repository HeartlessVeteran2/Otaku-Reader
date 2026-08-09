package app.otakureader.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Entity for feed items - latest chapters from various sources.
 */
/**
 * A chapter that has arrived, for the Feed tab.
 *
 * ### `(mangaId, chapterId)` is unique
 *
 * A manual library update and the periodic one are separate WorkManager unique-work names
 * (`library_update` and `library_update_periodic`), so they can run at the same time. Both can read
 * the stored chapter list before either inserts, both then see the same chapter as new, and both
 * try to record it.
 *
 * `chapters` already defends against exactly this with a unique `(mangaId, url)` index — the race
 * is not hypothetical, it is one the schema has already conceded. Without the same constraint here
 * a duplicated chapter is *persistent*: two rows in the feed, both consuming its limited window.
 * Serializing the workers would also fix it, but a database constraint cannot be undone by a
 * timing change somewhere else.
 */
@Entity(
    tableName = "feed_items",
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["timestamp"]),
        Index(value = ["mangaId"]),
        Index(value = ["mangaId", "chapterId"], unique = true)
    ]
)
data class FeedItemEntity(
    @PrimaryKey(autoGenerate = true)
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

@Entity(
    tableName = "feed_sources",
    indices = [Index(value = ["sourceId"], unique = true)]
)
data class FeedSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceId: Long,
    val sourceName: String,
    val isEnabled: Boolean = true,
    val itemCount: Int = 20,
    val order: Int = 0
)

@Entity(
    tableName = "feed_saved_searches",
    indices = [Index(value = ["sourceId"])]
)
data class FeedSavedSearchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceId: Long,
    val sourceName: String,
    val query: String,
    val filtersJson: String?, // Serialized Map<String, String>
    val order: Int = 0
)
