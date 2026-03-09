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
        pageUrls: List<String> = emptyList()
    )

    suspend fun pauseDownload(chapterId: Long)
    suspend fun resumeDownload(chapterId: Long)
    suspend fun cancelDownload(chapterId: Long)
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
}
