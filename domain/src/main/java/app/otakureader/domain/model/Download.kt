package app.otakureader.domain.model

/**
 * Represents a single download operation in the queue.
 */
data class DownloadItem(
    val id: Long,
    val mangaId: Long,
    val chapterId: Long,
    val mangaTitle: String,
    val chapterTitle: String,
    val progress: Int = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED
) {
    val isActive: Boolean
        get() = status == DownloadStatus.DOWNLOADING ||
            status == DownloadStatus.QUEUED ||
            status == DownloadStatus.PAUSED
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}
