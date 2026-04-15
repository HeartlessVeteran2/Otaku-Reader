package app.otakureader.data.repository

import app.otakureader.core.database.dao.SmartSearchCacheDao
import app.otakureader.core.database.entity.SmartSearchCacheEntity
import app.otakureader.domain.model.search.CachedSmartSearch
import app.otakureader.domain.model.search.ParsedSearchQuery
import app.otakureader.domain.repository.SmartSearchCacheRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartSearchCacheRepositoryImpl @Inject constructor(
    private val cacheDao: SmartSearchCacheDao,
    private val json: Json
) : SmartSearchCacheRepository {

    override fun getCachedSearch(queryHash: String): Flow<CachedSmartSearch?> {
        return cacheDao.getByHash(queryHash).map { entity ->
            entity?.toDomainModel()
        }
    }

    override suspend fun cacheSearch(cachedSearch: CachedSmartSearch) {
        val entity = SmartSearchCacheEntity(
            queryHash = cachedSearch.queryHash,
            originalQuery = cachedSearch.originalQuery,
            parsedQueryJson = json.encodeToString(cachedSearch.parsedQuery),
            timestamp = cachedSearch.timestamp
        )
        cacheDao.insert(entity)
    }

    override suspend fun getRecentSearches(limit: Int): List<CachedSmartSearch> {
        return cacheDao.getRecent(limit).map { it.toDomainModel() }
    }

    override suspend fun clearAllCache() {
        cacheDao.clearAll()
    }

    override suspend fun clearOldCache(maxAgeMs: Long) {
        val cutoffTime = System.currentTimeMillis() - maxAgeMs
        cacheDao.clearOlderThan(cutoffTime)
    }

    private fun SmartSearchCacheEntity.toDomainModel(): CachedSmartSearch {
        return CachedSmartSearch(
            queryHash = queryHash,
            originalQuery = originalQuery,
            parsedQuery = json.decodeFromString<ParsedSearchQuery>(parsedQueryJson),
            timestamp = timestamp
        )
    }
}
