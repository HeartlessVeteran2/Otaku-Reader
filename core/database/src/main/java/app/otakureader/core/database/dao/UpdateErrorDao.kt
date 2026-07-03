package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.otakureader.core.database.entity.UpdateErrorEntity
import app.otakureader.core.database.entity.UpdateErrorWithMangaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UpdateErrorDao {
    @Transaction
    @Query("SELECT * FROM update_errors ORDER BY timestamp DESC")
    fun observeErrors(): Flow<List<UpdateErrorWithMangaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(error: UpdateErrorEntity)

    @Query("DELETE FROM update_errors WHERE mangaId = :mangaId")
    suspend fun deleteByMangaId(mangaId: Long)

    @Query("DELETE FROM update_errors")
    suspend fun deleteAll()
}
