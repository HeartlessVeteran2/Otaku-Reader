package app.otakureader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for caching smart search results.
 */
@Entity(tableName = "smart_search_cache")
data class SmartSearchCacheEntity(
    @PrimaryKey
    val queryHash: String,
    val originalQuery: String,
    val parsedQueryJson: String, // JSON representation of ParsedSearchQuery
    val timestamp: Long = System.currentTimeMillis()
)
