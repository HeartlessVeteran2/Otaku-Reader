package app.otakureader.core.ainoop

import app.otakureader.domain.model.search.CachedSmartSearch
import app.otakureader.domain.repository.SmartSearchCacheRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoOpSmartSearchCacheRepository @Inject constructor() : SmartSearchCacheRepository {

    override fun getCachedSearch(queryHash: String): Flow<CachedSmartSearch?> = flowOf(null)

    override suspend fun cacheSearch(cachedSearch: CachedSmartSearch) { /* no-op */ }

    override suspend fun getRecentSearches(limit: Int): List<CachedSmartSearch> = emptyList()

    override suspend fun clearAllCache() { /* no-op */ }

    override suspend fun clearOldCache(maxAgeMs: Long) { /* no-op */ }
}
