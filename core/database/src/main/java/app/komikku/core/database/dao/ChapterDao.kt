package app.komikku.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.komikku.core.database.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Query("SELECT * FROM chapter WHERE manga_id = :mangaId ORDER BY source_order DESC")
    fun observeChaptersByManga(mangaId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapter WHERE id = :id LIMIT 1")
    suspend fun getChapter(id: Long): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(chapters: List<ChapterEntity>)

    @Query("UPDATE chapter SET read = :read, last_page_read = :lastPageRead WHERE id = :id")
    suspend fun setRead(id: Long, read: Boolean, lastPageRead: Int)

    @Query("UPDATE chapter SET bookmark = :bookmarked WHERE id = :id")
    suspend fun setBookmarked(id: Long, bookmarked: Boolean)

    @Query("SELECT COUNT(*) FROM chapter WHERE manga_id = :mangaId AND read = 0")
    fun observeUnreadCount(mangaId: Long): Flow<Int>
}
