package app.otakureader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.otakureader.core.database.entity.ChapterEntity
import app.otakureader.core.database.entity.ChapterWithMangaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId ORDER BY sourceOrder DESC")
    fun getChaptersByMangaId(mangaId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId ORDER BY sourceOrder DESC")
    suspend fun getChaptersByMangaIdOnce(mangaId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE mangaId IN (:mangaIds) ORDER BY sourceOrder DESC")
    suspend fun getChaptersByMangaIdsOnce(mangaIds: Collection<Long>): List<ChapterEntity>

    @Query("SELECT * FROM chapters")
    suspend fun getAllChaptersOnce(): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId AND url = :url LIMIT 1")
    suspend fun getChapterByMangaIdAndUrl(mangaId: Long, url: String): ChapterEntity?
    
    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId AND read = 0 ORDER BY sourceOrder ASC LIMIT 1")
    suspend fun getNextUnreadChapter(mangaId: Long): ChapterEntity?
    
    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getChapterById(id: Long): ChapterEntity?
    
    @Query("SELECT * FROM chapters WHERE id = :id")
    fun getChapterByIdFlow(id: Long): Flow<ChapterEntity?>
    
    /**
     * Inserts a chapter, or refreshes the source-provided metadata of the row that already has this
     * `(mangaId, url)`. Returns the row's id, which is stable across repeated calls.
     *
     * Uses the UPDATE-then-INSERT transaction pattern — the same one [ReadingHistoryDao.upsert]
     * uses, and for the same reason — rather than `INSERT OR REPLACE`.
     *
     * `REPLACE` on the unique `(mangaId, url)` index **deletes** the conflicting row and inserts a
     * new one, and because `id` is `autoGenerate` the replacement gets a *different* id. Six tables
     * store a chapter id: `reading_history`, `page_bookmarks` and `reader_comments` declare a
     * foreign key with `ON DELETE CASCADE`, so they would be destroyed outright, while
     * `download_queue`, `sync_queue` and `feed_items` would be left pointing at a row that no longer
     * exists. The symptoms are scattered and hard to attribute — a history entry that will not
     * resume, a bookmark that opens nothing, a queued download that never starts. See #1254.
     *
     * The UPDATE deliberately touches only what the source owns. `read`, `lastPageRead`,
     * `userNotes` and `dateFetch` are the user's or ours, and a metadata refresh must not reset
     * them; `id` must not move at all.
     */
    @Transaction
    suspend fun upsert(chapter: ChapterEntity): Long {
        val updated = updateSourceMetadata(
            mangaId = chapter.mangaId,
            url = chapter.url,
            name = chapter.name,
            scanlator = chapter.scanlator,
            chapterNumber = chapter.chapterNumber,
            dateUpload = chapter.dateUpload,
        )
        return if (updated == 0) {
            insertNew(chapter)
        } else {
            // The row was already there, so report the id it kept rather than a fresh one. Callers
            // such as the backup restorer link other rows to this id.
            getChapterByMangaIdAndUrl(chapter.mangaId, chapter.url)?.id ?: 0L
        }
    }

    /**
     * [upsert] for a batch, in one transaction so a partial refresh cannot be observed.
     */
    @Transaction
    suspend fun upsertAll(chapters: List<ChapterEntity>) {
        for (chapter in chapters) {
            upsert(chapter)
        }
    }

    /**
     * Refreshes only the fields the source is authoritative for. Returns the number of rows
     * matched, which [upsert] uses to decide whether an insert is needed.
     *
     * Three of the four are guarded, because an incoming entity carrying a default is far more often
     * a gap in the fetch than a genuine change:
     * - `scanlator` is `COALESCE`d — null means unknown, not "no longer scanlated".
     * - `chapterNumber` is only taken when `>= 0`; `-1f` is this codebase's unknown sentinel (the
     *   entity default, the domain default, and what `FillMissingChaptersUseCase` filters on).
     * - `dateUpload` is only taken when `> 0`; `LibraryUpdateWorker.recordFeedItems` already does
     *   `takeIf { it > 0 }` for the same reason — a source reporting 0 would pin the row to 1970.
     *
     * Four columns are deliberately absent, and each would be a bug to add:
     * - `id` — the entire point of #1254.
     * - `read`, `lastPageRead`, `userNotes` — the user's, not the source's.
     * - `dateFetch` — "when we first saw it". `getRecentUpdates` orders by it and
     *   `countNewUpdatesSince` drives the Updates badge, so refreshing it would resurrect every
     *   existing chapter into the Updates screen on every library refresh.
     * - `sourceOrder` — `domain.model.Chapter` has no such field, so `Chapter.toEntity()` always
     *   writes `0`. Updating it from an incoming entity would zero the ordering that
     *   `TachiyomiBackupImporter` imported, and four queries here `ORDER BY sourceOrder`.
     */
    @Query(
        """
        UPDATE chapters
        SET name = :name,
            scanlator = COALESCE(:scanlator, scanlator),
            chapterNumber = CASE WHEN :chapterNumber >= 0 THEN :chapterNumber ELSE chapterNumber END,
            dateUpload = CASE WHEN :dateUpload > 0 THEN :dateUpload ELSE dateUpload END
        WHERE mangaId = :mangaId AND url = :url
        """
    )
    suspend fun updateSourceMetadata(
        mangaId: Long,
        url: String,
        name: String,
        scanlator: String?,
        chapterNumber: Float,
        dateUpload: Long,
    ): Int

    /**
     * Raw insert for a chapter known not to exist. `ABORT` rather than `REPLACE` on purpose: inside
     * [upsert]'s transaction a conflict here means the row appeared concurrently, and failing loudly
     * is better than silently reassigning its id — which is the whole bug this replaced.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNew(chapter: ChapterEntity): Long


    @Update
    suspend fun update(chapter: ChapterEntity)
    
    @Query("UPDATE chapters SET read = :read, lastPageRead = :lastPageRead WHERE id = :chapterId")
    suspend fun updateChapterProgress(chapterId: Long, read: Boolean, lastPageRead: Int)
    
    @Query("UPDATE chapters SET read = :read, lastPageRead = :lastPageRead WHERE id IN (:chapterIds)")
    suspend fun updateChapterProgress(chapterIds: Collection<Long>, read: Boolean, lastPageRead: Int)

    @Query("UPDATE chapters SET userNotes = :notes WHERE id = :chapterId")
    suspend fun updateChapterNotes(chapterId: Long, notes: String?)
    
    @Delete
    suspend fun delete(chapter: ChapterEntity)
    
    @Query("DELETE FROM chapters WHERE mangaId = :mangaId")
    suspend fun deleteByMangaId(mangaId: Long)
    
    @Query("SELECT COUNT(*) FROM chapters WHERE mangaId = :mangaId AND read = 0")
    fun getUnreadCountByMangaId(mangaId: Long): Flow<Int>

    /** Total chapter count across every favorited (library) manga, for the Statistics screen. */
    @Query("SELECT COUNT(*) FROM chapters INNER JOIN manga ON chapters.mangaId = manga.id WHERE manga.favorite = 1")
    fun getTotalChapterCountForLibrary(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM chapters WHERE mangaId = :mangaId AND read = 1")
    fun getReadCountByMangaId(mangaId: Long): Flow<Int>

    /**
     * Returns the most recently fetched chapters (dateFetch > 0) for library manga,
     * paired with their parent manga, ordered newest-first. Limited to 200 rows.
     */
    @Transaction
    @Query(
        "SELECT chapters.* FROM chapters " +
            "INNER JOIN manga ON chapters.mangaId = manga.id " +
            "WHERE chapters.dateFetch > 0 AND manga.favorite = 1 " +
            "ORDER BY chapters.dateFetch DESC " +
            "LIMIT 200"
    )
    fun getRecentUpdates(): Flow<List<ChapterWithMangaEntity>>

    /**
     * Counts library chapters fetched after [since] (epoch millis). Used for the Updates badge.
     */
    @Query(
        "SELECT COUNT(*) FROM chapters " +
            "INNER JOIN manga ON chapters.mangaId = manga.id " +
            "WHERE chapters.dateFetch > :since AND manga.favorite = 1"
    )
    fun countNewUpdatesSince(since: Long): Flow<Int>
}
