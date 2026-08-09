package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.otakureader.core.database.entity.RecommendationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecommendationDao {

    @Query("SELECT * FROM manga_feature_cache ORDER BY score DESC LIMIT 20")
    fun getRecommendations(): Flow<List<RecommendationEntity>>

    @Query("SELECT * FROM manga_feature_cache ORDER BY score DESC LIMIT 20")
    suspend fun getRecommendationsOnce(): List<RecommendationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(recommendations: List<RecommendationEntity>)

    @Query("DELETE FROM manga_feature_cache")
    suspend fun deleteAll()
}
