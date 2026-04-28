package app.otakureader.feature.browse

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.domain.model.FeedSavedSearch
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
    val showFilterSheet: Boolean = false,
    /** Currently selected manga for bulk favorite (IDs mapped to manga). */
    val selectedManga: Map<String, SourceManga> = emptyMap(),
    /** True when bulk selection mode is active. */
    val isBulkSelectionMode: Boolean = false,
    /** Saved searches for the current source, loaded from the database. */
    val savedSearches: List<FeedSavedSearch> = emptyList(),
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

    // Bulk favorite events
    data class OnMangaLongClick(val manga: SourceManga) : BrowseEvent
    data class ToggleMangaSelection(val manga: SourceManga) : BrowseEvent
    data object ClearSelection : BrowseEvent
    data object AddSelectedToLibrary : BrowseEvent
    data object ExitBulkSelectionMode : BrowseEvent

    /** Saves the current search query + filters for the active source. */
    data object SaveCurrentSearch : BrowseEvent
    /** Deletes the saved search with the given id. */
    data class DeleteSavedSearch(val searchId: Long) : BrowseEvent
    /** Applies a previously saved search (restores query, filters, runs search). */
    data class ApplySavedSearch(val search: app.otakureader.domain.model.FeedSavedSearch) : BrowseEvent
}

sealed interface BrowseEffect : UiEffect {
    data class NavigateToMangaDetail(val sourceId: String, val mangaUrl: String) : BrowseEffect
    data class ShowSnackbar(val message: String) : BrowseEffect
    /** Navigate to library after bulk add to show newly added manga. */
    data object NavigateToLibrary : BrowseEffect
}
