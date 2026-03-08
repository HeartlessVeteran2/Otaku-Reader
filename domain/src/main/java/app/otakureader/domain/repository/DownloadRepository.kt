package app.otakureader.domain.repository

import app.otakureader.domain.model.DownloadItem
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeDownloads(): Flow<List<DownloadItem>>

    /**
     * Enqueue a chapter for download.
     *
     * @param mangaId     database ID of the parent manga
     * @param chapterId   database ID of the chapter
     * @param sourceName  human-readable name of the source extension (used for directory layout)
     * @param mangaTitle  title of the manga
     * @param chapterTitle title of the chapter
     * @param pageUrls    ordered list of remote image URLs to download;
     *                    if empty the download is queued but no pages are fetched yet
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
    suspend fun clearAll()

    /**
     * Returns true when all pages for the given chapter have already been saved to disk.
     */
    fun isChapterDownloaded(sourceName: String, mangaTitle: String, chapterTitle: String): Boolean
}
