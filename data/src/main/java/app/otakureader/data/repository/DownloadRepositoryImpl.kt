package app.otakureader.data.repository

import app.otakureader.core.database.dao.DownloadDao
import app.otakureader.core.database.entity.DownloadEntity
import app.otakureader.domain.model.Download
import app.otakureader.domain.model.DownloadState
import app.otakureader.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val downloadDao: DownloadDao
) : DownloadRepository {

    override fun observeDownloads(): Flow<List<Download>> {
        return downloadDao.observeAllDownloads().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeDownloadsByMangaId(mangaId: Long): Flow<List<Download>> {
        return downloadDao.observeDownloadsByMangaId(mangaId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getDownloadByChapterId(chapterId: Long): Download? {
        return downloadDao.getDownloadByChapterId(chapterId)?.toDomain()
    }

    override suspend fun insertDownload(download: Download): Long {
        return downloadDao.insertDownload(download.toEntity())
    }

    override suspend fun updateDownloadState(chapterId: Long, state: DownloadState) {
        downloadDao.updateDownloadState(chapterId, serializeState(state))
    }

    override suspend fun updateDownloadProgress(
        chapterId: Long,
        downloadedPages: Int,
        totalPages: Int,
        progress: Int
    ) {
        downloadDao.updateDownloadProgress(chapterId, downloadedPages, totalPages, progress)
    }

    override suspend fun updateDownloadError(chapterId: Long, error: String) {
        downloadDao.updateDownloadError(chapterId, error)
    }

    override suspend fun deleteDownload(chapterId: Long) {
        downloadDao.deleteDownloadWithPages(chapterId)
    }

    override suspend fun deleteDownloadsByMangaId(mangaId: Long) {
        downloadDao.deleteDownloadsByMangaId(mangaId)
    }

    override suspend fun isChapterDownloaded(chapterId: Long): Boolean {
        return downloadDao.isChapterDownloaded(chapterId)
    }

    private fun DownloadEntity.toDomain() = Download(
        id = id,
        chapterId = chapterId,
        mangaId = mangaId,
        sourceId = sourceId,
        chapterName = chapterName,
        mangaTitle = mangaTitle,
        state = deserializeState(state),
        progress = progress,
        totalPages = totalPages,
        downloadedPages = downloadedPages,
        error = error,
        timestamp = timestamp
    )

    private fun Download.toEntity() = DownloadEntity(
        id = id,
        chapterId = chapterId,
        mangaId = mangaId,
        sourceId = sourceId,
        chapterName = chapterName,
        mangaTitle = mangaTitle,
        state = serializeState(state),
        progress = progress,
        totalPages = totalPages,
        downloadedPages = downloadedPages,
        error = error,
        timestamp = timestamp
    )

    private fun serializeState(state: DownloadState): String {
        return when (state) {
            is DownloadState.Queued -> "Queued"
            is DownloadState.Downloading -> "Downloading"
            is DownloadState.Completed -> "Completed"
            is DownloadState.Failed -> "Failed"
            is DownloadState.Paused -> "Paused"
            is DownloadState.Cancelled -> "Cancelled"
        }
    }

    private fun deserializeState(state: String): DownloadState {
        return when (state) {
            "Queued" -> DownloadState.Queued
            "Downloading" -> DownloadState.Downloading
            "Completed" -> DownloadState.Completed
            "Failed" -> DownloadState.Failed
            "Paused" -> DownloadState.Paused
            "Cancelled" -> DownloadState.Cancelled
            else -> DownloadState.Queued
        }
    }
}
