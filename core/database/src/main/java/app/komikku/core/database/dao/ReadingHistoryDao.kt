package app.komikku.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.komikku.core.database.entity.ChapterEntity
import app.komikku.core.database.entity.MangaEntity
import app.komikku.core.database.entity.ReadingHistoryEntity
import kotlinx.coroutines.flow.Flow

/** Result of joining chapter + manga + history for the history screen. */
data class HistoryWithRelations(
    @Embedded val chapter: ChapterEntity,
    @Embedded(prefix = "manga_") val manga: MangaEntity,
    val readAt: Long,
    val readDurationMs: Long
)

@Dao
interface ReadingHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: ReadingHistoryEntity)

    @Query(
        """
        SELECT
            ch.id, ch.manga_id, ch.source_order, ch.url, ch.name, ch.scanlator,
            ch.read, ch.bookmarked, ch.last_page_read, ch.chapter_number, ch.source_order, ch.date_upload, ch.date_fetch,
            m.id AS manga_id, m.source AS manga_source, m.url AS manga_url,
            m.artist AS manga_artist, m.author AS manga_author, m.description AS manga_description,
            m.genre AS manga_genre, m.title AS manga_title, m.status AS manga_status,
            m.thumbnail_url AS manga_thumbnail_url, m.date_added AS manga_date_added,
            m.last_update AS manga_last_update, m.favorite AS manga_favorite, m.last_init AS manga_last_init,
            rh.read_at, rh.read_duration_ms
        FROM reading_history rh
        INNER JOIN chapter ch ON rh.chapter_id = ch.id
        INNER JOIN manga m ON ch.manga_id = m.id
        ORDER BY rh.read_at DESC
        """
    )
    fun observeHistory(): Flow<List<HistoryWithRelations>>

    @Query("DELETE FROM reading_history WHERE read_at < :timestamp")
    suspend fun deleteHistoryBefore(timestamp: Long)
}
