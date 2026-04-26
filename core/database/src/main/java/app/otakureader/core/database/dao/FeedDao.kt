package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.otakureader.core.database.entity.FeedItemEntity
import app.otakureader.core.database.entity.FeedSavedSearchEntity
import app.otakureader.core.database.entity.FeedSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    // Feed Sources
    @Query("SELECT * FROM feed_sources ORDER BY `order` ASC")
    fun getFeedSources(): Flow<List<FeedSourceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedSource(source: FeedSourceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedSources(sources: List<FeedSourceEntity>)

    @Update
    suspend fun updateFeedSource(source: FeedSourceEntity)

    @Delete
    suspend fun deleteFeedSource(source: FeedSourceEntity)

    @Query("DELETE FROM feed_sources WHERE sourceId = :sourceId")
    suspend fun deleteFeedSourceById(sourceId: Long)

    @Query("UPDATE feed_sources SET isEnabled = :enabled WHERE sourceId = :sourceId")
    suspend fun setFeedSourceEnabled(sourceId: Long, enabled: Boolean)

    @Query("UPDATE feed_sources SET `order` = :order WHERE sourceId = :sourceId")
    suspend fun updateFeedSourceOrder(sourceId: Long, order: Int)

    @Query("UPDATE feed_sources SET itemCount = :count WHERE sourceId = :sourceId")
    suspend fun updateFeedSourceItemCount(sourceId: Long, count: Int)

    // Feed Items
    @Query("SELECT * FROM feed_items ORDER BY timestamp DESC LIMIT :limit")
    fun getFeedItems(limit: Int = 100): Flow<List<FeedItemEntity>>

    @Query("SELECT * FROM feed_items WHERE sourceId = :sourceId ORDER BY timestamp DESC LIMIT :limit")
    fun getFeedItemsForSource(sourceId: Long, limit: Int = 20): Flow<List<FeedItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedItem(item: FeedItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedItems(items: List<FeedItemEntity>)

    @Query("UPDATE feed_items SET isRead = 1 WHERE id = :feedItemId")
    suspend fun markFeedItemAsRead(feedItemId: Long)

    @Query("DELETE FROM feed_items")
    suspend fun clearAllFeedItems()

    @Query("DELETE FROM feed_items WHERE timestamp < :olderThan")
    suspend fun clearOldFeedItems(olderThan: java.time.Instant)

    // Saved Searches
    @Query("SELECT * FROM feed_saved_searches ORDER BY `order` ASC")
    fun getSavedSearches(): Flow<List<FeedSavedSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedSearch(search: FeedSavedSearchEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedSearches(searches: List<FeedSavedSearchEntity>)

    @Update
    suspend fun updateSavedSearch(search: FeedSavedSearchEntity)

    @Delete
    suspend fun deleteSavedSearch(search: FeedSavedSearchEntity)

    @Query("DELETE FROM feed_saved_searches WHERE id = :searchId")
    suspend fun deleteSavedSearchById(searchId: Long)

    @Query("UPDATE feed_saved_searches SET `order` = :order WHERE id = :searchId")
    suspend fun updateSavedSearchOrder(searchId: Long, order: Int)
}
