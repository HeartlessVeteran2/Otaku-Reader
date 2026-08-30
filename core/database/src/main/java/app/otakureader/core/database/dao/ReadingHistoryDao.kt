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
     * accumulating duration — the exact values given are what the row ends up holding.
     *
     * Uses the same UPDATE-then-INSERT transaction pattern as [upsert] to preserve the existing
     * row's `id` (avoiding DELETE-trigger side-effects that `INSERT OR REPLACE` would cause on a
     * table with an auto-generated primary key).
     *
     * **This is for copying a known history value from somewhere else, not for recording reading.**
     * Two callers qualify: restoring a backup, and migrating a manga to another source. Both carry
     * a value that already exists and must land verbatim, and both can legitimately run twice on
     * the same chapter — a restore of the same file, a migration re-run after the source list
     * changed. [upsert] would add the duration in again on each repeat, so the same operation
     * applied twice would report twice the reading time on the statistics screen.
     *
     * For an actual reading session, always use [upsert]: there the accumulation is the point.
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
     * The history rows for a specific set of chapters.
     *
     * A scoped read, because the only alternative was [observeHistory], which returns every row in
     * the app. Migration needs one manga's history and would otherwise have to pull the whole table
     * and filter in memory — fine at ten chapters, not at a library's worth.
     *
     * The caller is responsible for keeping [chapterIds] under SQLite's bound-parameter limit.
     */
    @Query("SELECT * FROM reading_history WHERE chapter_id IN (:chapterIds)")
    suspend fun getHistoryForChapters(chapterIds: Collection<Long>): List<ReadingHistoryEntity>

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
               ch.lastPageRead,
               ch.chapterNumber,
               ch.dateFetch,
               ch.dateUpload,
               rh.read_at,
               rh.read_duration_ms,
               m.title      AS manga_title,
               m.thumbnailUrl AS manga_thumbnail,
               m.favorite   AS manga_favorite
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

    /**
     * Returns one entry per favorited manga, showing the most-recently-read chapter.
     * Deduplication is done in SQL via a subquery so at most 12 rows are ever returned.
     */
    @Query(
        """
        SELECT ch.id                AS id,
               ch.mangaId           AS mangaId,
               ch.url               AS url,
               ch.name              AS name,
               ch.scanlator         AS scanlator,
               ch.read              AS read,
               ch.lastPageRead      AS lastPageRead,
               ch.chapterNumber     AS chapterNumber,
               ch.dateFetch         AS dateFetch,
               ch.dateUpload        AS dateUpload,
               rh.read_at           AS read_at,
               rh.read_duration_ms  AS read_duration_ms,
               m.title              AS manga_title,
               m.thumbnailUrl       AS manga_thumbnail,
               m.favorite           AS manga_favorite
        FROM   reading_history rh
        INNER JOIN chapters ch ON ch.id = rh.chapter_id
        INNER JOIN manga    m  ON m.id  = ch.mangaId
        WHERE  m.favorite = 1
          AND  rh.chapter_id = (
            SELECT rh2.chapter_id
            FROM   reading_history rh2
            INNER JOIN chapters ch2 ON ch2.id = rh2.chapter_id
            WHERE  ch2.mangaId = ch.mangaId
            ORDER BY rh2.read_at DESC, rh2.chapter_id DESC
            LIMIT 1
          )
        ORDER  BY rh.read_at DESC
        LIMIT  12
        """
    )
    fun observeContinueReading(): Flow<List<HistoryWithMangaEntity>>
}
