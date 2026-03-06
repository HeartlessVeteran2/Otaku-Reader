package app.komikku.feature.history

import app.komikku.core.common.mvi.UiEffect
import app.komikku.core.common.mvi.UiEvent
import app.komikku.core.common.mvi.UiState
import app.komikku.domain.model.ChapterWithHistory

data class HistoryState(
    val isLoading: Boolean = false,
    val history: List<ChapterWithHistory> = emptyList(),
    val searchQuery: String = "",
    val error: String? = null
) : UiState

sealed interface HistoryEvent : UiEvent {
    data class OnChapterClick(val mangaId: Long, val chapterId: Long) : HistoryEvent
    data object ClearHistory : HistoryEvent
    data class OnSearchQueryChange(val query: String) : HistoryEvent
}

sealed interface HistoryEffect : UiEffect {
    data class NavigateToReader(val mangaId: Long, val chapterId: Long) : HistoryEffect
    data class ShowSnackbar(val message: String) : HistoryEffect
}
