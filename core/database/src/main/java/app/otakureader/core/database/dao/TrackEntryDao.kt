package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.otakureader.core.database.entity.TrackEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackEntryDao {

    @Query("SELECT * FROM track_entries WHERE manga_id = :mangaId")
    fun getByMangaId(mangaId: Long): Flow<List<TrackEntryEntity>>

    @Query("SELECT * FROM track_entries WHERE manga_id = :mangaId AND tracker_id = :trackerId LIMIT 1")
    suspend fun getByMangaAndTracker(mangaId: Long, trackerId: Int): TrackEntryEntity?

    /**
     * Every tracker link in the library, for the backup writer. One query for the whole library
     * rather than a [getByMangaId] per manga, matching how reading history is collected there.
     */
    @Query("SELECT * FROM track_entries")
    fun getAllEntries(): Flow<List<TrackEntryEntity>>

    /**
     * Inserts [entry], or updates the existing row for its `(manga_id, tracker_id)` pair.
     *
     * UPDATE-then-INSERT rather than `OnConflictStrategy.REPLACE` (#1276). REPLACE **deletes** the
     * conflicting row before inserting, and `id` is `autoGenerate`, so every sync handed the row a
     * new id. Nothing currently references a track entry's id, so this was churn rather than data
     * loss — but the rule is the same one that cost this project real data twice, in `ChapterDao`
     * (#1254) and `MangaDao` (#1269), and a table that quietly reassigns its own primary key is a
     * trap for whoever adds the first foreign key to it.
     *
     * A full overwrite is correct here: every field comes from the tracker, so there is no
     * user-owned column to preserve and no field-by-field merge to get wrong. That is what makes
     * this the refresh case rather than the create-if-absent one.
     */
    @Transaction
    suspend fun upsert(entry: TrackEntryEntity): Long {
        getByMangaAndTracker(entry.mangaId, entry.trackerId)?.let { existing ->
            update(entry.copy(id = existing.id))
            return existing.id
        }
        val rowId = insertIfAbsent(entry)
        if (rowId != -1L) return rowId
        // Lost the race between the read and the insert: somebody else created the row, so this
        // call becomes the update it would have been had it arrived a moment later.
        val now = getByMangaAndTracker(entry.mangaId, entry.trackerId) ?: return 0L
        update(entry.copy(id = now.id))
        return now.id
    }

    /** Returns the new row id, or -1 when `(manga_id, tracker_id)` is already taken. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entry: TrackEntryEntity): Long

    @Update
    suspend fun update(entry: TrackEntryEntity)

    @Query("DELETE FROM track_entries WHERE manga_id = :mangaId AND tracker_id = :trackerId")
    suspend fun deleteByMangaAndTracker(mangaId: Long, trackerId: Int)

    /**
     * Distinct manga IDs that have at least one tracker entry, for the Library screen's
     * tracked-badge lookup — one query for the whole library instead of a per-manga
     * [getByMangaId] check.
     */
    @Query("SELECT DISTINCT manga_id FROM track_entries")
    fun getMangaIdsWithTrackEntries(): Flow<List<Long>>

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
