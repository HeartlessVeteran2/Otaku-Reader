package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.otakureader.core.database.entity.TrackEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackEntryDao {

    @Query("SELECT * FROM track_entries WHERE manga_id = :mangaId")
    fun getByMangaId(mangaId: Long): Flow<List<TrackEntryEntity>>

    @Query("SELECT * FROM track_entries WHERE manga_id = :mangaId AND tracker_id = :trackerId LIMIT 1")
    suspend fun getByMangaAndTracker(mangaId: Long, trackerId: Int): TrackEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: TrackEntryEntity): Long

    @Query("DELETE FROM track_entries WHERE manga_id = :mangaId AND tracker_id = :trackerId")
    suspend fun deleteByMangaAndTracker(mangaId: Long, trackerId: Int)

    /** Number of distinct manga with at least one tracker entry, for the Statistics screen. */
    @Query("SELECT COUNT(DISTINCT manga_id) FROM track_entries")
    fun getTrackedMangaCount(): Flow<Int>

    /**
     * Mean of all non-zero tracker scores (normalized 0–10 across every tracker service).
     * Null when no entry has a score yet.
     */
    @Query("SELECT AVG(score) FROM track_entries WHERE score > 0")
    fun getMeanScore(): Flow<Float?>

    /** Number of distinct tracker services with at least one entry, for the Statistics screen. */
    @Query("SELECT COUNT(DISTINCT tracker_id) FROM track_entries")
    fun getTrackerServiceCount(): Flow<Int>
}
