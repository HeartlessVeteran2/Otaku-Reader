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

    /**
     * Aggregated tracker statistics for the Statistics screen, computed in a single query:
     * distinct tracked manga, mean of non-zero scores (normalized 0–10 across every tracker
     * service; 0 = unscored, excluded via the CASE so it can't drag the mean down — null when
     * nothing is scored yet), and distinct tracker services in use.
     */
    @Query(
        """
        SELECT
            COUNT(DISTINCT manga_id) AS trackedMangaCount,
            AVG(CASE WHEN score > 0 THEN score END) AS meanScore,
            COUNT(DISTINCT tracker_id) AS serviceCount
        FROM track_entries
        """
    )
    fun getTrackerStats(): Flow<TrackerStats>
}

/** Projection for [TrackEntryDao.getTrackerStats] — not a table, just the aggregate row. */
data class TrackerStats(
    val trackedMangaCount: Int,
    val meanScore: Float?,
    val serviceCount: Int,
)
