package app.otakureader.feature.updates

import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.domain.model.DownloadItem
import app.otakureader.domain.model.DownloadStatus

data class DownloadsState(
    val items: List<DownloadItem> = emptyList(),
    val selectedItems: Set<Long> = emptySet()
) : UiState {
    val hasDownloads: Boolean
        get() = items.isNotEmpty()

    val isDownloaderRunning: Boolean
        get() = items.any {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
        }
}

sealed interface DownloadsEvent : UiEvent {
    data class OnItemClick(val id: Long) : DownloadsEvent
    data class OnItemLongClick(val id: Long) : DownloadsEvent
    data class Pause(val id: Long) : DownloadsEvent
    data class Resume(val id: Long) : DownloadsEvent
    data class Cancel(val id: Long) : DownloadsEvent
    /** Move the given item to the front of the download queue. */
    data class Prioritize(val id: Long) : DownloadsEvent
    data object ClearAll : DownloadsEvent
    /** Pause every active (queued/downloading) download. */
    data object PauseAll : DownloadsEvent
    /** Resume every paused download. */
    data object ResumeAll : DownloadsEvent
    /** Re-queue every failed download. */
    data object RetryAllFailed : DownloadsEvent
    data object ClearSelection : DownloadsEvent
    data object SelectAll : DownloadsEvent
    data object PauseSelected : DownloadsEvent
    data object ResumeSelected : DownloadsEvent
    data object CancelSelected : DownloadsEvent
    /** Move all selected items to the front of the download queue. */
    data object PrioritizeSelected : DownloadsEvent

    /** Reorder the entire queue by each chapter's upload date (Komikku parity). */
    data class SortByUploadDate(val newestFirst: Boolean) : DownloadsEvent

    /** Reorder the entire queue by each chapter's chapter number (Komikku parity). */
    data class SortByChapterNumber(val ascending: Boolean) : DownloadsEvent
}
