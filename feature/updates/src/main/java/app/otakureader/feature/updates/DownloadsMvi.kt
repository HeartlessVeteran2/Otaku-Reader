package app.otakureader.feature.updates

import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.domain.model.DownloadItem

data class DownloadsState(
    val items: List<DownloadItem> = emptyList(),
    val selectedItems: Set<Long> = emptySet()
) : UiState {
    val hasDownloads: Boolean
        get() = items.isNotEmpty()
}

sealed interface DownloadsEvent : UiEvent {
    data class OnItemClick(val id: Long) : DownloadsEvent
    data class OnItemLongClick(val id: Long) : DownloadsEvent
    data class Pause(val id: Long) : DownloadsEvent
    data class Resume(val id: Long) : DownloadsEvent
    data class Cancel(val id: Long) : DownloadsEvent
    data object ClearAll : DownloadsEvent
    data object ClearSelection : DownloadsEvent
    data object SelectAll : DownloadsEvent
    data object PauseSelected : DownloadsEvent
    data object ResumeSelected : DownloadsEvent
    data object CancelSelected : DownloadsEvent
}
