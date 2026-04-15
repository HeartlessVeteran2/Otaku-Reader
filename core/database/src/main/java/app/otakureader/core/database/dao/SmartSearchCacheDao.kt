package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.otakureader.core.database.entity.SmartSearchCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartSearchCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: SmartSearchCacheEntity)

    @Query("SELECT * FROM smart_search_cache WHERE queryHash = :queryHash")
    fun getByHash(queryHash: String): Flow<SmartSearchCacheEntity?>

    @Query("SELECT * FROM smart_search_cache ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<SmartSearchCacheEntity>

    @Query("DELETE FROM smart_search_cache")
    suspend fun clearAll()

    @Query("DELETE FROM smart_search_cache WHERE timestamp < :olderThan")
    suspend fun clearOlderThan(olderThan: Long)

    @Query("DELETE FROM smart_search_cache WHERE queryHash = :queryHash")
    suspend fun deleteByHash(queryHash: String)
}
