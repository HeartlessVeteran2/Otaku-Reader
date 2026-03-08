package app.otakureader.domain.repository

import app.otakureader.domain.model.DownloadItem
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeDownloads(): Flow<List<DownloadItem>>
    suspend fun enqueueChapter(
        mangaId: Long,
        chapterId: Long,
        mangaTitle: String,
        chapterTitle: String
    )

    suspend fun pauseDownload(chapterId: Long)
    suspend fun resumeDownload(chapterId: Long)
    suspend fun cancelDownload(chapterId: Long)
    suspend fun clearAll()
}
