package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.otakureader.core.database.entity.ChapterWithHistoryEntity
import app.otakureader.core.database.entity.LastReadInfo
import app.otakureader.core.database.entity.ReadingHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: ReadingHistoryEntity)

    @Query("SELECT * FROM reading_history ORDER BY read_at DESC")
    fun observeHistory(): Flow<List<ReadingHistoryEntity>>

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
