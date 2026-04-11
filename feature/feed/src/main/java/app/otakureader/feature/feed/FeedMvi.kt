package app.otakureader.feature.feed

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.domain.model.FeedItem
import app.otakureader.domain.model.FeedSource

data class FeedState(
    val isLoading: Boolean = false,
    val feedItems: List<FeedItem> = emptyList(),
    val feedSources: List<FeedSource> = emptyList(),
    val error: String? = null
) : UiState

sealed interface FeedEvent : UiEvent {
    data object Refresh : FeedEvent
    data class OnFeedItemClick(val mangaId: Long, val chapterId: Long) : FeedEvent
    data class OnMarkAsRead(val feedItemId: Long) : FeedEvent
    data class OnToggleSource(val sourceId: Long, val enabled: Boolean) : FeedEvent
    data object ClearHistory : FeedEvent
}

sealed interface FeedEffect : UiEffect {
    data class NavigateToReader(val mangaId: Long, val chapterId: Long) : FeedEffect
    data class ShowSnackbar(val message: String) : FeedEffect
}
