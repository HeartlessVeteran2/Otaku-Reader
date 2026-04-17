package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import app.otakureader.core.database.entity.ChapterWithHistoryEntity
import app.otakureader.core.database.entity.HistoryWithMangaEntity
import app.otakureader.core.database.entity.LastReadInfo
import app.otakureader.core.database.entity.ReadingHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingHistoryDao {

    /**
     * Inserts a new reading-history entry or, on a chapter_id conflict, accumulates the reading
     * time rather than replacing the row.  This preserves the total time spent reading a chapter
     * across multiple sessions while still updating the "last read" timestamp.
     */
    @Transaction
    suspend fun upsert(chapterId: Long, readAt: Long, readDurationMs: Long) {
        val updated = updateHistory(chapterId, readAt, readDurationMs)
        if (updated == 0) {
            insertHistory(chapterId, readAt, readDurationMs)
        }
    }

    @Query(
        """
        UPDATE reading_history
        SET read_at = MAX(read_at, :readAt),
            read_duration_ms = read_duration_ms + :readDurationMs
        WHERE chapter_id = :chapterId
        """
    )
    suspend fun updateHistory(chapterId: Long, readAt: Long, readDurationMs: Long): Int

    @Query(
        """
        INSERT INTO reading_history (chapter_id, read_at, read_duration_ms)
        VALUES (:chapterId, :readAt, :readDurationMs)
        """
    )
    suspend fun insertHistory(chapterId: Long, readAt: Long, readDurationMs: Long)

    /**
     * Atomically sets (overwrites) the reading-history entry for the given chapter without
     * accumulating duration.  Unlike `upsert`, this is used for the restore path where the
     * exact backed-up values must be written verbatim.
     *
     * Uses the same UPDATE-then-INSERT transaction pattern as [upsert] to preserve the existing
     * row's `id` (avoiding DELETE-trigger side-effects that `INSERT OR REPLACE` would cause on a
     * table with an auto-generated primary key).
     *
     * **WARNING**: This method intentionally differs from [upsert] by NOT accumulating duration.
     * It should ONLY be used in backup restore logic. Using it elsewhere would silently lose
     * accumulated reading time and alter tracking semantics. For normal reading session tracking,
     * always use [upsert] instead.
     */
    @Transaction
    suspend fun replaceHistory(chapterId: Long, readAt: Long, readDurationMs: Long) {
        val updated = overwriteHistory(chapterId, readAt, readDurationMs)
        if (updated == 0) {
            insertHistory(chapterId, readAt, readDurationMs)
        }
    }

    @Query(
        """
        UPDATE reading_history
        SET read_at = :readAt,
            read_duration_ms = :readDurationMs
        WHERE chapter_id = :chapterId
        """
    )
    suspend fun overwriteHistory(chapterId: Long, readAt: Long, readDurationMs: Long): Int

    @Query("SELECT * FROM reading_history ORDER BY read_at DESC")
    fun observeHistory(): Flow<List<ReadingHistoryEntity>>

    /**
     * Returns chapters joined with their reading history **and** parent manga metadata, ordered
     * by most-recently read.  Used by the History screen to display the cover thumbnail and manga
     * title alongside each chapter row without extra queries.
     */
    @Query(
        """
        SELECT ch.id,
               ch.mangaId,
               ch.url,
               ch.name,
               ch.scanlator,
               ch.read,
               ch.bookmark,
               ch.lastPageRead,
               ch.chapterNumber,
               ch.dateFetch,
               ch.dateUpload,
               rh.read_at,
               rh.read_duration_ms,
               m.title  AS manga_title,
               m.thumbnailUrl AS manga_thumbnail
        FROM   chapters        ch
        INNER JOIN reading_history rh ON ch.id        = rh.chapter_id
        INNER JOIN manga           m  ON ch.mangaId   = m.id
        ORDER  BY rh.read_at DESC
        """
    )
    fun observeHistoryWithMangaInfo(): Flow<List<HistoryWithMangaEntity>>

    /**
     * Returns chapters joined with their reading history, ordered by most-recently read.
     */
    @Transaction
    @Query(
        "SELECT chapters.* FROM chapters " +
            "INNER JOIN reading_history ON chapters.id = reading_history.chapter_id " +
            "ORDER BY reading_history.read_at DESC"
    )
    fun observeHistoryWithChapters(): Flow<List<ChapterWithHistoryEntity>>

    /**
     * Returns the most-recently-read chapter's IDs and its manga title in a single query.
     * Used by [app.otakureader.shortcut.AppShortcutManager] to keep the Continue Reading
     * shortcut in sync without an extra DB lookup.
     */
    @Query(
        "SELECT c.mangaId AS mangaId, c.id AS chapterId, m.title AS mangaTitle " +
            "FROM chapters c " +
            "INNER JOIN reading_history rh ON c.id = rh.chapter_id " +
            "INNER JOIN manga m ON c.mangaId = m.id " +
            "ORDER BY rh.read_at DESC " +
            "LIMIT 1"
    )
    fun observeLastReadWithMangaTitle(): Flow<LastReadInfo?>

    @Query("SELECT COALESCE(SUM(read_duration_ms), 0) FROM reading_history")
    fun getTotalReadingTimeMs(): Flow<Long>

    @Query("SELECT COUNT(*) FROM reading_history")
    fun getTotalChaptersRead(): Flow<Int>

    @Query("SELECT read_at FROM reading_history WHERE read_at > 0 ORDER BY read_at ASC")
    fun getAllReadTimestamps(): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM reading_history WHERE read_at >= :sinceTimestampMs")
    fun getChaptersReadSince(sinceTimestampMs: Long): Flow<Int>

    @Query("DELETE FROM reading_history WHERE read_at < :timestamp")
    suspend fun deleteHistoryBefore(timestamp: Long)

    @Query("DELETE FROM reading_history WHERE chapter_id = :chapterId")
    suspend fun deleteHistoryForChapter(chapterId: Long)

    @Query("DELETE FROM reading_history")
    suspend fun deleteAll()
}
