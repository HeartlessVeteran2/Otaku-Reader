package app.komikku.feature.browse

import app.komikku.core.common.mvi.UiEffect
import app.komikku.core.common.mvi.UiEvent
import app.komikku.core.common.mvi.UiState
import app.komikku.sourceapi.SourceManga

data class BrowseState(
    val isLoading: Boolean = false,
    val sources: List<String> = emptyList(),
    val currentSourceId: String? = null,
    val popularManga: List<SourceManga> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<SourceManga> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
    val hasNextPage: Boolean = false,
    val currentPage: Int = 1
) : UiState

sealed interface BrowseEvent : UiEvent {
    data class SelectSource(val sourceId: String) : BrowseEvent
    data class OnSearchQueryChange(val query: String) : BrowseEvent
    data object Search : BrowseEvent
    data class OnMangaClick(val manga: SourceManga) : BrowseEvent
    data object LoadNextPage : BrowseEvent
}

sealed interface BrowseEffect : UiEffect {
    data class NavigateToMangaDetail(val sourceId: String, val mangaUrl: String) : BrowseEffect
    data class ShowSnackbar(val message: String) : BrowseEffect
}
