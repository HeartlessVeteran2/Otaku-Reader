package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.otakureader.core.database.entity.MangaEntity
import app.otakureader.core.database.entity.MangaWithUnreadCount
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {
    @Query("SELECT * FROM manga WHERE favorite = 1 ORDER BY title ASC")
    fun getFavoriteManga(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga WHERE favorite = 1 AND title LIKE :query || '%' ORDER BY title ASC")
    fun searchFavoriteManga(query: String): Flow<List<MangaEntity>>
    
    @Query("SELECT * FROM manga WHERE id = :id")
    suspend fun getMangaById(id: Long): MangaEntity?
    
    @Query("SELECT * FROM manga WHERE id = :id")
    fun getMangaByIdFlow(id: Long): Flow<MangaEntity?>
    
    @Query("SELECT * FROM manga WHERE sourceId = :sourceId AND url = :url")
    suspend fun getMangaBySourceAndUrl(sourceId: Long, url: String): MangaEntity?

    @Query("SELECT * FROM manga WHERE id IN (:ids)")
    suspend fun getMangaByIds(ids: List<Long>): List<MangaEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(manga: MangaEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(manga: List<MangaEntity>)
    
    @Update
    suspend fun update(manga: MangaEntity)
    
    @Delete
    suspend fun delete(manga: MangaEntity)
    
    @Query("DELETE FROM manga WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("UPDATE manga SET favorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE manga SET autoDownload = :autoDownload WHERE id = :id")
    suspend fun updateAutoDownload(id: Long, autoDownload: Boolean)

    @Query("UPDATE manga SET notes = :notes WHERE id = :id")
    suspend fun updateNote(id: Long, notes: String?)

    @Query("UPDATE manga SET notifyNewChapters = :notify WHERE id = :id")
    suspend fun updateNotifyNewChapters(id: Long, notify: Boolean)

    // Per-manga reader settings (#260)
    @Query("UPDATE manga SET readerDirection = :direction WHERE id = :id")
    suspend fun updateReaderDirection(id: Long, direction: Int?)

    @Query("UPDATE manga SET readerMode = :mode WHERE id = :id")
    suspend fun updateReaderMode(id: Long, mode: Int?)

    @Query("UPDATE manga SET readerColorFilter = :filter WHERE id = :id")
    suspend fun updateReaderColorFilter(id: Long, filter: Int?)

    @Query("UPDATE manga SET readerCustomTintColor = :color WHERE id = :id")
    suspend fun updateReaderCustomTintColor(id: Long, color: Long?)

    // Page preloading settings (#264)
    @Query("UPDATE manga SET preloadPagesBefore = :count WHERE id = :id")
    suspend fun updatePreloadPagesBefore(id: Long, count: Int?)

    @Query("UPDATE manga SET preloadPagesAfter = :count WHERE id = :id")
    suspend fun updatePreloadPagesAfter(id: Long, count: Int?)

    @Query("""UPDATE manga SET
        readerDirection = NULL,
        readerMode = NULL,
        readerColorFilter = NULL,
        readerCustomTintColor = NULL,
        preloadPagesBefore = NULL,
        preloadPagesAfter = NULL
        WHERE id = :id""")
    suspend fun resetReaderOverrides(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM manga WHERE id = :id AND favorite = 1)")
    fun isFavorite(id: Long): Flow<Boolean>
    
    @Query("SELECT COUNT(*) FROM manga WHERE favorite = 1")
    fun getFavoriteMangaCount(): Flow<Int>

    @Query("SELECT genre FROM manga WHERE favorite = 1 AND genre IS NOT NULL")
    fun getFavoriteMangaGenres(): Flow<List<String>>

    @Query("""
        SELECT m.*, COALESCE(SUM(CASE WHEN c.read = 0 THEN 1 ELSE 0 END), 0) as unreadCount
        FROM manga m
        LEFT JOIN chapters c ON m.id = c.mangaId
        WHERE m.favorite = 1
        GROUP BY m.id
        ORDER BY m.title ASC
    """)
    fun getFavoriteMangaWithUnreadCount(): Flow<List<MangaWithUnreadCount>>
}
