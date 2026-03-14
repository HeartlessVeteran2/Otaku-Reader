package app.otakureader.domain.model

/**
 * Represents a single download operation in the queue.
 *
 * @param priority Controls the position of this item in the queue relative to other queued
 *   items. Lower values indicate higher priority (appear first in the queue). The default
 *   value of [DownloadPriority.NORMAL] gives standard FIFO behaviour. Use
 *   [DownloadPriority.HIGH] (or any value below [DownloadPriority.NORMAL]) to move an item
 *   ahead of the normal queue, and [DownloadPriority.LOW] to push it towards the end.
 */
data class DownloadItem(
    val id: Long,
    val mangaId: Long,
    val chapterId: Long,
    val mangaTitle: String,
    val chapterTitle: String,
    val progress: Int = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val priority: Int = DownloadPriority.NORMAL
) {
    val isActive: Boolean
        get() = status == DownloadStatus.DOWNLOADING ||
            status == DownloadStatus.QUEUED ||
            status == DownloadStatus.PAUSED
}

/**
 * Convenience priority constants for [DownloadItem.priority].
 *
 * Lower numeric values produce a higher position in the download queue.  Custom integer
 * values may be used for finer-grained ordering; these constants are provided as sensible
 * defaults.
 */
object DownloadPriority {
    /** Pushed to the front of the queue ahead of [NORMAL] items. */
    const val HIGH = -100

    /** Standard insertion-order (FIFO) priority assigned to new downloads. */
    const val NORMAL = 0

    /** Pushed towards the end of the queue behind [NORMAL] items. */
    const val LOW = 100
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED
}
