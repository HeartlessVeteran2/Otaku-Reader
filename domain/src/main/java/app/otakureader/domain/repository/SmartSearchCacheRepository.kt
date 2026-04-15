package app.otakureader.domain.repository

import app.otakureader.domain.model.search.CachedSmartSearch
import kotlinx.coroutines.flow.Flow

/**
 * Repository for caching smart search results.
 */
interface SmartSearchCacheRepository {
    /**
     * Get a cached search by its query hash.
     */
    fun getCachedSearch(queryHash: String): Flow<CachedSmartSearch?>

    /**
     * Cache a search result.
     */
    suspend fun cacheSearch(cachedSearch: CachedSmartSearch)

    /**
     * Get recent cached searches.
     */
    suspend fun getRecentSearches(limit: Int): List<CachedSmartSearch>

    /**
     * Clear all cached searches.
     */
    suspend fun clearAllCache()

    /**
     * Clear old cache entries (older than specified max age).
     */
    suspend fun clearOldCache(maxAgeMs: Long)
}
