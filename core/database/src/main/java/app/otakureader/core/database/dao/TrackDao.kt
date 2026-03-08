package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.otakureader.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE mangaId = :mangaId")
    fun getTracksForManga(mangaId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE mangaId = :mangaId AND serviceId = :serviceId LIMIT 1")
    suspend fun getTrack(mangaId: Long, serviceId: Int): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteById(trackId: Long)
}
