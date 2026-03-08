package app.otakureader.feature.updates

import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.domain.model.DownloadItem

data class DownloadsState(
    val items: List<DownloadItem> = emptyList()
) : UiState {
    val hasDownloads: Boolean
        get() = items.isNotEmpty()
}

sealed interface DownloadsEvent : UiEvent {
    data class Pause(val id: Long) : DownloadsEvent
    data class Resume(val id: Long) : DownloadsEvent
    data class Cancel(val id: Long) : DownloadsEvent
    data object ClearAll : DownloadsEvent
}
