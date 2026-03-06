package app.komikku.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.komikku.core.database.entity.ChapterEntity
import app.komikku.core.database.entity.MangaEntity
import app.komikku.core.database.entity.ReadingHistoryEntity
import kotlinx.coroutines.flow.Flow

/** Result of joining chapter + manga + history for the history screen. */
data class HistoryWithRelations(
    val chapter: ChapterEntity,
    val manga: MangaEntity,
    val readAt: Long,
    val readDurationMs: Long
)

@Dao
interface ReadingHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: ReadingHistoryEntity)

    @Query(
        """
        SELECT ch.*, m.*, rh.read_at, rh.read_duration_ms
        FROM reading_history rh
        INNER JOIN chapter ch ON rh.chapter_id = ch.id
        INNER JOIN manga m ON ch.manga_id = m.id
        ORDER BY rh.read_at DESC
        """
    )
    fun observeHistory(): Flow<List<ReadingHistoryEntity>>

    @Query("DELETE FROM reading_history WHERE read_at < :timestamp")
    suspend fun deleteHistoryBefore(timestamp: Long)
}
