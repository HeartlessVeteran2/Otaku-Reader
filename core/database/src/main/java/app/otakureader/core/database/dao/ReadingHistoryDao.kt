package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.otakureader.core.database.entity.ChapterWithHistoryEntity
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

    @Query("SELECT COALESCE(SUM(read_duration_ms), 0) FROM reading_history")
    fun getTotalReadingTimeMs(): Flow<Long>

    @Query("SELECT COUNT(*) FROM reading_history")
    fun getTotalChaptersRead(): Flow<Int>

    @Query("SELECT read_at FROM reading_history WHERE read_at > 0 ORDER BY read_at ASC")
    fun getAllReadTimestamps(): Flow<List<Long>>

    @Query("DELETE FROM reading_history WHERE read_at < :timestamp")
    suspend fun deleteHistoryBefore(timestamp: Long)

    @Query("DELETE FROM reading_history WHERE chapter_id = :chapterId")
    suspend fun deleteHistoryForChapter(chapterId: Long)

    @Query("DELETE FROM reading_history")
    suspend fun deleteAll()
}
