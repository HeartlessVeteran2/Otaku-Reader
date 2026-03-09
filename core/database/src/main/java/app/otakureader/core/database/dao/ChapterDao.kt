package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.otakureader.core.database.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId ORDER BY sourceOrder DESC")
    fun getChaptersByMangaId(mangaId: Long): Flow<List<ChapterEntity>>
    
    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId AND read = 0 ORDER BY sourceOrder ASC LIMIT 1")
    suspend fun getNextUnreadChapter(mangaId: Long): ChapterEntity?
    
    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getChapterById(id: Long): ChapterEntity?
    
    @Query("SELECT * FROM chapters WHERE id = :id")
    fun getChapterByIdFlow(id: Long): Flow<ChapterEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chapter: ChapterEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>)
    
    @Update
    suspend fun update(chapter: ChapterEntity)
    
    @Query("UPDATE chapters SET read = :read, lastPageRead = :lastPageRead WHERE id = :chapterId")
    suspend fun updateChapterProgress(chapterId: Long, read: Boolean, lastPageRead: Int)
    
    @Query("UPDATE chapters SET read = :read, lastPageRead = :lastPageRead WHERE id IN (:chapterIds)")
    suspend fun updateChapterProgress(chapterIds: List<Long>, read: Boolean, lastPageRead: Int)

    @Query("UPDATE chapters SET bookmark = :bookmark WHERE id = :chapterId")
    suspend fun updateBookmark(chapterId: Long, bookmark: Boolean)
    
    @Delete
    suspend fun delete(chapter: ChapterEntity)
    
    @Query("DELETE FROM chapters WHERE mangaId = :mangaId")
    suspend fun deleteByMangaId(mangaId: Long)
    
    @Query("SELECT COUNT(*) FROM chapters WHERE mangaId = :mangaId AND read = 0")
    fun getUnreadCountByMangaId(mangaId: Long): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM chapters WHERE mangaId = :mangaId AND read = 1")
    fun getReadCountByMangaId(mangaId: Long): Flow<Int>
}
