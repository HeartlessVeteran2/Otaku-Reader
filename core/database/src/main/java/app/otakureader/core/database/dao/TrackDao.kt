package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.otakureader.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE mangaId = :mangaId")
    fun getTracksByMangaId(mangaId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE mangaId = :mangaId AND serviceId = :serviceId")
    suspend fun getTrackByMangaAndService(mangaId: Long, serviceId: Int): TrackEntity?

    @Query("SELECT * FROM tracks WHERE mangaId = :mangaId AND serviceId = :serviceId")
    fun getTrackByMangaAndServiceFlow(mangaId: Long, serviceId: Int): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrackById(id: Long): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: TrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<TrackEntity>)

    @Update
    suspend fun update(track: TrackEntity)

    @Delete
    suspend fun delete(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE mangaId = :mangaId")
    suspend fun deleteByMangaId(mangaId: Long)

    @Query("DELETE FROM tracks WHERE mangaId = :mangaId AND serviceId = :serviceId")
    suspend fun deleteByMangaAndService(mangaId: Long, serviceId: Int)
}
