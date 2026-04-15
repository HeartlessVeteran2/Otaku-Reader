package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.otakureader.core.database.entity.CategorizationResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategorizationResultDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: CategorizationResultEntity)
    
    @Update
    suspend fun update(result: CategorizationResultEntity)
    
    @Query("SELECT * FROM categorization_results WHERE mangaId = :mangaId")
    suspend fun getByMangaId(mangaId: Long): CategorizationResultEntity?
    
    @Query("SELECT * FROM categorization_results WHERE mangaId = :mangaId")
    fun getByMangaIdFlow(mangaId: Long): Flow<CategorizationResultEntity?>
    
    @Query("DELETE FROM categorization_results WHERE mangaId = :mangaId")
    suspend fun deleteByMangaId(mangaId: Long)
    
    @Query("SELECT * FROM categorization_results WHERE wasAutoApplied = 0 AND wasReviewed = 0 ORDER BY timestamp DESC")
    fun getPendingSuggestions(): Flow<List<CategorizationResultEntity>>
    
    @Query("UPDATE categorization_results SET wasReviewed = 1 WHERE mangaId = :mangaId")
    suspend fun markAsReviewed(mangaId: Long)
    
    @Query("DELETE FROM categorization_results WHERE mangaId NOT IN (SELECT id FROM manga WHERE favorite = 1)")
    suspend fun cleanupRemovedManga()
}
