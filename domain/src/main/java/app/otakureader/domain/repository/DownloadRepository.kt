package app.otakureader.domain.repository

import app.otakureader.domain.model.DownloadItem
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeDownloads(): Flow<List<DownloadItem>>

    /**
     * Enqueue a chapter for download.
     *
     * @param mangaId      database ID of the parent manga
     * @param chapterId    database ID of the chapter
     * @param sourceName   value used as the source directory component in the download path
     *                     (e.g. a source ID string or a short source identifier);
     *                     does not need to be human-readable
     * @param mangaTitle   title of the manga
     * @param chapterTitle title of the chapter
     * @param pageUrls     ordered list of remote image URLs to download;
     *                     if empty the item is queued but no pages are fetched yet
     */
    suspend fun enqueueChapter(
        mangaId: Long,
        chapterId: Long,
        sourceName: String = "",
        mangaTitle: String,
        chapterTitle: String,
        pageUrls: List<String>,
        priority: Int = app.otakureader.domain.model.DownloadPriority.NORMAL
    )

    suspend fun pauseDownload(chapterId: Long)
    suspend fun resumeDownload(chapterId: Long)
    suspend fun cancelDownload(chapterId: Long)

    /**
     * Moves the given chapter to the front of the download queue by assigning it the
     * highest available priority.  If the chapter is not currently in the queue this
     * is a no-op.
     */
    suspend fun prioritizeDownload(chapterId: Long)

    /**
     * Moves all given chapters to the front of the download queue in a single atomic
     * operation. Chapters within the list retain their relative queue order (callers must
     * pass a deterministic ordered collection). Duplicates are treated by first occurrence
     * only; IDs not in the queue are silently ignored.
     *
     * @param chapterIds Ordered list of chapter IDs to prioritize
     */
    suspend fun prioritizeDownloads(chapterIds: List<Long>)

    /**
     * Backwards-compatible overload that accepts a [Set].
     *
     * Note: Sets do not guarantee iteration order. If deterministic ordering is required,
     * use [prioritizeDownloads(List)] with an explicitly ordered list.
     *
     * @param chapterIds Set of chapter IDs to prioritize (iteration order is undefined)
     */
    suspend fun prioritizeDownloads(chapterIds: Set<Long>) = prioritizeDownloads(chapterIds.toList())

    /**
     * Sets an explicit priority value for the given queued chapter.
     *
     * Lower values appear earlier in the queue.  Use the constants in [app.otakureader.domain.model.DownloadPriority]
     * for common presets.  If the chapter is not in the queue this is a no-op.
     */
    suspend fun reorderDownload(chapterId: Long, newPriority: Int)
    suspend fun deleteChapterDownload(
        chapterId: Long,
        sourceName: String,
        mangaTitle: String,
        chapterTitle: String
    )
    suspend fun clearAll()

    /**
     * Returns true when the given chapter has at least one page already saved to disk.
     *
     * Note: this does not guarantee that all pages have been downloaded; callers that require
     * full offline availability should verify completeness (e.g., via page count/metadata).
     *
     * This function performs filesystem I/O and must be called from a coroutine.
     */
    suspend fun isChapterDownloaded(sourceName: String, mangaTitle: String, chapterTitle: String): Boolean

    /**
     * Creates a CBZ archive from the downloaded pages (if any) of the given chapter.
     *
     * The archive is placed inside the chapter's download directory. This operation
     * is independent of the "Save as CBZ" auto-download preference and can be triggered
     * manually from the manga details screen.
     *
     * Note: the underlying implementation may succeed even if the chapter directory
     * contains no page files, and it may overwrite an existing `chapter.cbz` archive.
     *
     * @return [Result.success] when the archive operation completes without error
     *         (including when an existing archive is overwritten or no pages are present),
     *         [Result.failure] only if an error occurs during export.
     */
    suspend fun exportChapterAsCbz(
        sourceName: String,
        mangaTitle: String,
        chapterTitle: String
    ): Result<Unit>

    /**
     * Migrates downloaded chapter files from one manga/source to another.
     * Used during manga migration to preserve downloads when moving between sources.
     *
     * @param fromSourceName Source name of the original manga
     * @param fromMangaTitle Manga title of the original manga
     * @param fromChapterName Chapter name in the original manga
     * @param toSourceName Source name of the target manga
     * @param toMangaTitle Manga title of the target manga
     * @param toChapterName Chapter name in the target manga
     * @param copy If true, copies files (COPY mode). If false, moves files (MOVE mode)
     * @return true if migration was successful, false if no files to migrate or migration failed
     */
    suspend fun migrateChapterDownload(
        fromSourceName: String,
        fromMangaTitle: String,
        fromChapterName: String,
        toSourceName: String,
        toMangaTitle: String,
        toChapterName: String,
        copy: Boolean = false
    ): Boolean
}
