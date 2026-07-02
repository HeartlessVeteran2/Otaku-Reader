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

@Suppress("TooManyFunctions")
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

    @Query("SELECT * FROM manga")
    suspend fun getAllMangaOnce(): List<MangaEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(manga: MangaEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(manga: List<MangaEntity>)

    /** Insert manga that don't already exist (by primary key or unique sourceId+url). Skips conflicts. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExists(manga: List<MangaEntity>): List<Long>

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

    @Query("UPDATE manga SET readerBackgroundColor = :color WHERE id = :id")
    suspend fun updateReaderBackgroundColor(id: Long, color: Long?)

    // Page preloading settings (#264)
    @Query("UPDATE manga SET preloadPagesBefore = :count WHERE id = :id")
    suspend fun updatePreloadPagesBefore(id: Long, count: Int?)

    @Query("UPDATE manga SET preloadPagesAfter = :count WHERE id = :id")
    suspend fun updatePreloadPagesAfter(id: Long, count: Int?)

    @Query("SELECT EXISTS(SELECT 1 FROM manga WHERE id = :id AND favorite = 1)")
    fun isFavorite(id: Long): Flow<Boolean>
    
    @Query("UPDATE manga SET userCompleted = :completed WHERE id = :id")
    suspend fun updateUserCompleted(id: Long, completed: Boolean)

    @Query("UPDATE manga SET userDropped = :dropped WHERE id = :id")
    suspend fun updateUserDropped(id: Long, dropped: Boolean)

    /** Set per-manga cover-theme override; pass null to clear (inherit global). */
    @Query("UPDATE manga SET mangaThemeOverride = :override WHERE id = :id")
    suspend fun updateMangaThemeOverride(id: Long, override: Boolean?)

    /** Persists chapter list sort direction + read/downloaded filter state (Mihon-compatible bit layout). */
    @Query("UPDATE manga SET chapterFlags = :flags WHERE id = :id")
    suspend fun updateChapterFlags(id: Long, flags: Int)

    /** Persist user-info overrides (#998). Pass null for any field to clear that override. */
    @Query("""UPDATE manga SET userTitle = :title, userDescription = :description,
        userAuthor = :author, userArtist = :artist, userThumbnailUrl = :thumbnailUrl,
        userGenre = :genre, userStatus = :status WHERE id = :id""")
    suspend fun updateUserOverrides(
        id: Long,
        title: String?,
        description: String?,
        author: String?,
        artist: String?,
        thumbnailUrl: String?,
        genre: String?,
        status: Int?,
    )

    /** Update only the user cover override, leaving other user-info overrides intact. */
    @Query("UPDATE manga SET userThumbnailUrl = :thumbnailUrl WHERE id = :id")
    suspend fun updateUserThumbnail(id: Long, thumbnailUrl: String?)

    @Query("SELECT * FROM manga WHERE favorite = 1 AND userCompleted = 1 ORDER BY title ASC")
    fun getCompletedManga(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga WHERE favorite = 1 AND userDropped = 1 ORDER BY title ASC")
    fun getDroppedManga(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga WHERE favorite = 1 AND userCompleted = 0 AND userDropped = 0 ORDER BY title ASC")
    fun getActiveManga(): Flow<List<MangaEntity>>

    @Query("SELECT COUNT(*) FROM manga WHERE favorite = 1 AND userCompleted = 1")
    fun getCompletedMangaCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM manga WHERE favorite = 1 AND userDropped = 1")
    fun getDroppedMangaCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM manga WHERE favorite = 1")
    fun countFavorites(): Flow<Int>

    @Query("SELECT genre FROM manga WHERE favorite = 1 AND genre IS NOT NULL")
    fun getFavoriteMangaGenres(): Flow<List<String>>

    /**
     * Full-text search across title, author, and artist for favorited manga.
     * Append '*' to the query for prefix matching (e.g. "one*" matches "One Piece").
     */
    @Query("""
        SELECT manga.* FROM manga
        INNER JOIN manga_fts ON manga.rowid = manga_fts.rowid
        WHERE manga_fts MATCH :query AND manga.favorite = 1
        ORDER BY manga.title ASC
    """)
    fun searchFts(query: String): Flow<List<MangaEntity>>

    /**
     * Browsed-but-not-in-library manga: candidates for the recommendation engine (#943).
     * Includes only entries with a non-empty genre, since the scorer needs genres to compare.
     */
    @Query("SELECT * FROM manga WHERE favorite = 0 AND genre IS NOT NULL AND genre != '' LIMIT :limit")
    suspend fun getRecommendationCandidates(limit: Int = 500): List<MangaEntity>

    // Correlated subqueries (rather than a LEFT JOIN + GROUP BY on chapters) keep this O(N log M)
    // for N favorites over M chapters, so library loading stays fast for large libraries.
    @Query("""
        SELECT m.*,
            (
                SELECT COUNT(*)
                FROM chapters c
                WHERE c.mangaId = m.id AND c.read = 0
            ) as unreadCount,
            (
                SELECT MAX(rh.read_at)
                FROM reading_history rh
                INNER JOIN chapters rc ON rh.chapter_id = rc.id
                WHERE rc.mangaId = m.id
            ) as lastRead
        FROM manga m
        WHERE m.favorite = 1
        ORDER BY m.title ASC
    """)
    fun getFavoriteMangaWithUnreadCount(): Flow<List<MangaWithUnreadCount>>
}
