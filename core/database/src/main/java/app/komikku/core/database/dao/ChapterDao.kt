package app.komikku.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.komikku.core.database.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Query("SELECT * FROM chapter WHERE mangaId = :mangaId ORDER BY sourceOrder DESC")
    fun getChaptersByMangaId(mangaId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapter WHERE id = :id")
    suspend fun getChapterById(id: Long): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Query("UPDATE chapter SET read = :read WHERE id = :id")
    suspend fun markRead(id: Long, read: Boolean)

    @Query("UPDATE chapter SET read = 1 WHERE mangaId = :mangaId")
    suspend fun markAllRead(mangaId: Long)

    @Query("SELECT COUNT(*) FROM chapter WHERE mangaId = :mangaId AND read = 0")
    fun getUnreadCount(mangaId: Long): Flow<Int>
}
