package app.otakureader.feature.browse

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.sourceapi.FilterList
import app.otakureader.sourceapi.SourceManga

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
    val currentPage: Int = 1,
    val availableFilters: FilterList = FilterList(),
    val activeFilters: FilterList = FilterList(),
    val showFilterSheet: Boolean = false
) : UiState

sealed interface BrowseEvent : UiEvent {
    data class SelectSource(val sourceId: String) : BrowseEvent
    data class OnSearchQueryChange(val query: String) : BrowseEvent
    data object Search : BrowseEvent
    data class OnMangaClick(val manga: SourceManga) : BrowseEvent
    data object LoadNextPage : BrowseEvent
    data object RefreshSources : BrowseEvent
    data object LoadLatest : BrowseEvent
    data object ToggleFilterSheet : BrowseEvent
    data class UpdateFilter(val index: Int, val filter: app.otakureader.sourceapi.Filter<*>) : BrowseEvent
    data object ResetFilters : BrowseEvent
    data object ApplyFilters : BrowseEvent
}

sealed interface BrowseEffect : UiEffect {
    data class NavigateToMangaDetail(val sourceId: String, val mangaUrl: String) : BrowseEffect
    data class ShowSnackbar(val message: String) : BrowseEffect
}
