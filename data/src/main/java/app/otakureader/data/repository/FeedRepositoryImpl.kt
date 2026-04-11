package app.otakureader.data.repository

import app.otakureader.core.database.dao.FeedDao
import app.otakureader.core.database.entity.FeedItemEntity
import app.otakureader.core.database.entity.FeedSavedSearchEntity
import app.otakureader.core.database.entity.FeedSourceEntity
import app.otakureader.domain.model.FeedItem
import app.otakureader.domain.model.FeedSavedSearch
import app.otakureader.domain.model.FeedSource
import app.otakureader.domain.repository.FeedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Separator used to encode filter map entries as a flat string in the database. */
private const val ENTRY_SEP = "\u001E" // ASCII Record Separator (RS)
private const val KV_SEP = "\u001F"    // ASCII Unit Separator (US)

@Singleton
class FeedRepositoryImpl @Inject constructor(
    private val feedDao: FeedDao
) : FeedRepository {

    // Feed Sources

    override fun getFeedSources(): Flow<List<FeedSource>> =
        feedDao.getFeedSources().map { entities -> entities.map { it.toDomain() } }

    override suspend fun addFeedSource(sourceId: Long, sourceName: String) {
        feedDao.insertFeedSource(FeedSourceEntity(sourceId = sourceId, sourceName = sourceName))
    }

    override suspend fun removeFeedSource(sourceId: Long) {
        feedDao.deleteFeedSourceById(sourceId)
    }

    override suspend fun toggleFeedSource(sourceId: Long, enabled: Boolean) {
        feedDao.setFeedSourceEnabled(sourceId, enabled)
    }

    override suspend fun updateFeedSourceOrder(sourceId: Long, order: Int) {
        feedDao.updateFeedSourceOrder(sourceId, order)
    }

    override suspend fun updateFeedSourceItemCount(sourceId: Long, count: Int) {
        feedDao.updateFeedSourceItemCount(sourceId, count)
    }

    // Feed Content

    override fun getFeedItems(limit: Int): Flow<List<FeedItem>> =
        feedDao.getFeedItems(limit).map { entities -> entities.map { it.toDomain() } }

    override fun getFeedItemsForSource(sourceId: Long, limit: Int): Flow<List<FeedItem>> =
        feedDao.getFeedItemsForSource(sourceId, limit).map { entities -> entities.map { it.toDomain() } }

    override suspend fun refreshFeed() {
        // Actual network refresh is driven by FeedRefreshWorker via WorkManager.
        // Callers that need a background refresh should enqueue the worker directly.
    }

    override suspend fun markFeedItemAsRead(feedItemId: Long) {
        feedDao.markFeedItemAsRead(feedItemId)
    }

    override suspend fun clearFeedHistory() {
        feedDao.clearAllFeedItems()
    }

    // Saved Searches

    override fun getSavedSearches(): Flow<List<FeedSavedSearch>> =
        feedDao.getSavedSearches().map { entities -> entities.map { it.toDomain() } }

    override suspend fun addSavedSearch(
        sourceId: Long,
        sourceName: String,
        query: String,
        filters: Map<String, String>
    ) {
        feedDao.insertSavedSearch(
            FeedSavedSearchEntity(
                sourceId = sourceId,
                sourceName = sourceName,
                query = query,
                filtersJson = encodeFilters(filters)
            )
        )
    }

    override suspend fun removeSavedSearch(searchId: Long) {
        feedDao.deleteSavedSearchById(searchId)
    }

    override suspend fun updateSavedSearchOrder(searchId: Long, order: Int) {
        feedDao.updateSavedSearchOrder(searchId, order)
    }
}

// Filter encoding helpers using control-character separators that are safe
// from any user-supplied key/value content.

private fun encodeFilters(filters: Map<String, String>): String? {
    if (filters.isEmpty()) return null
    return filters.entries.joinToString(separator = ENTRY_SEP) { (k, v) -> "$k$KV_SEP$v" }
}

private fun decodeFilters(encoded: String?): Map<String, String> {
    if (encoded.isNullOrBlank()) return emptyMap()
    return try {
        encoded.split(ENTRY_SEP).associate { entry ->
            val sepIdx = entry.indexOf(KV_SEP)
            if (sepIdx < 0) return emptyMap()
            entry.substring(0, sepIdx) to entry.substring(sepIdx + 1)
        }
    } catch (_: Exception) {
        emptyMap()
    }
}

// Extension mappers

private fun FeedSourceEntity.toDomain(): FeedSource = FeedSource(
    sourceId = sourceId,
    sourceName = sourceName,
    isEnabled = isEnabled,
    itemCount = itemCount,
    order = order
)

private fun FeedItemEntity.toDomain(): FeedItem = FeedItem(
    id = id,
    mangaId = mangaId,
    mangaTitle = mangaTitle,
    mangaThumbnailUrl = mangaThumbnailUrl,
    chapterId = chapterId,
    chapterName = chapterName,
    chapterNumber = chapterNumber,
    sourceId = sourceId,
    sourceName = sourceName,
    timestamp = timestamp,
    isRead = isRead
)

private fun FeedSavedSearchEntity.toDomain(): FeedSavedSearch = FeedSavedSearch(
    id = id,
    sourceId = sourceId,
    sourceName = sourceName,
    query = query,
    filters = decodeFilters(filtersJson),
    order = order
)

