package app.otakureader.feature.updates.errors

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.domain.model.UpdateError

data class UpdateErrorsState(
    val isLoading: Boolean = true,
    /** Unresolved update failures grouped by their raw error message (sticky-header key). */
    val errorsByMessage: Map<String, List<UpdateError>> = emptyMap(),
    /** Manga IDs currently selected for bulk actions; non-empty means selection mode is active. */
    val selectedMangaIds: Set<Long> = emptySet(),
) : UiState {
    val isSelectionMode: Boolean get() = selectedMangaIds.isNotEmpty()
    val isEmpty: Boolean get() = errorsByMessage.isEmpty()
}

sealed interface UpdateErrorsEvent : UiEvent {
    /** Tap on a card: navigates when not selecting, toggles selection when selection mode is active. */
    data class CardClick(val mangaId: Long) : UpdateErrorsEvent
    /** Long-press on a card: always toggles selection (enters selection mode if not already active). */
    data class ToggleSelection(val mangaId: Long) : UpdateErrorsEvent
    data object SelectAll : UpdateErrorsEvent
    data object InvertSelection : UpdateErrorsEvent
    data object ClearSelection : UpdateErrorsEvent
    /** Swipe-to-dismiss a single card, regardless of selection state. */
    data class DeleteError(val mangaId: Long) : UpdateErrorsEvent
    data object DeleteSelected : UpdateErrorsEvent
    data object ClearAll : UpdateErrorsEvent
    data object MigrateSelected : UpdateErrorsEvent
}

sealed interface UpdateErrorsEffect : UiEffect {
    data class NavigateToManga(val mangaId: Long) : UpdateErrorsEffect
    data class NavigateToMigration(val mangaIds: List<Long>) : UpdateErrorsEffect
}
