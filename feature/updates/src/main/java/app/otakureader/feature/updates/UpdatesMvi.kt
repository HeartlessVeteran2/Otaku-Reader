package app.otakureader.feature.updates

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.domain.model.MangaUpdate

/**
 * Represents a failed update entry for the error screen.
 */
data class UpdateErrorEntry(
    val mangaId: Long,
    val mangaTitle: String,
    val thumbnailUrl: String?,
    val errorMessage: String,
    val timestamp: Long
)

/**
 * Represents a manga that will be checked during the next library update.
 */
data class PendingUpdateManga(
    val mangaId: Long,
    val title: String,
    val thumbnailUrl: String?,
    val sourceName: String,
    val lastChecked: Long
)

data class UpdatesState(
    val isLoading: Boolean = false,
    val updates: List<MangaUpdate> = emptyList(),
    val error: String? = null,
    /** Selected chapter IDs for bulk operations. */
    val selectedItems: Set<Long> = emptySet(),
    /** List of failed updates for the Update Error Screen. */
    val updateErrors: List<UpdateErrorEntry> = emptyList(),
    /** Whether the update error screen is visible. */
    val showUpdateErrors: Boolean = false,
    /** List of manga that will be checked in the next update. */
    val pendingUpdates: List<PendingUpdateManga> = emptyList(),
    /** Whether the To-Be-Updated screen is visible. */
    val showPendingUpdates: Boolean = false
) : UiState

sealed interface UpdatesEvent : UiEvent {
    data object Refresh : UpdatesEvent
    data class OnChapterClick(val mangaId: Long, val chapterId: Long) : UpdatesEvent
    data class OnChapterLongClick(val chapterId: Long) : UpdatesEvent
    data class OnDownloadChapter(val mangaId: Long, val chapterId: Long) : UpdatesEvent
    data object ClearSelection : UpdatesEvent
    data object SelectAll : UpdatesEvent
    data object DownloadSelected : UpdatesEvent
    data object MarkSelectedAsRead : UpdatesEvent

    // Update Error Screen events
    data object ShowUpdateErrors : UpdatesEvent
    data object HideUpdateErrors : UpdatesEvent
    data class ClearUpdateError(val mangaId: Long) : UpdatesEvent
    data object ClearAllUpdateErrors : UpdatesEvent

    // To-Be-Updated Screen events
    data object ShowPendingUpdates : UpdatesEvent
    data object HidePendingUpdates : UpdatesEvent
    data object StartLibraryUpdate : UpdatesEvent
}

sealed interface UpdatesEffect : UiEffect {
    data class NavigateToReader(val mangaId: Long, val chapterId: Long) : UpdatesEffect
    data class ShowSnackbar(val message: String) : UpdatesEffect
}
