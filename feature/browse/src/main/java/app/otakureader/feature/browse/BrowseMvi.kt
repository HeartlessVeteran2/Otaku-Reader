package app.otakureader.feature.browse

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.domain.model.SavedSourceSearch
import app.otakureader.domain.model.SourceHealthEntry
import app.otakureader.sourceapi.FilterList
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.SourceManga

enum class BrowseSearchScope { SOURCES, LIBRARY }

enum class BrowseTab { FEED, SOURCES, EXTENSIONS, MIGRATE }

data class BrowseState(
    val isLoading: Boolean = false,
    val sources: List<MangaSource> = emptyList(),
    val currentSourceId: String? = null,
    val selectedTab: BrowseTab = BrowseTab.SOURCES,
    /** IDs of the last 5 sources the user browsed (most recent first). */
    val lastUsedSourceIds: List<String> = emptyList(),
    val popularManga: List<SourceManga> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<SourceManga> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearchResults: Boolean = false,
    val error: String? = null,
    val hasNextPage: Boolean = false,
    val currentPage: Int = 1,
    val availableFilters: FilterList = FilterList(),
    val activeFilters: FilterList = FilterList(),
    val showFilterSheet: Boolean = false,
    /** Currently selected manga URLs for bulk favorite. */
    val selectedManga: Set<String> = emptySet(),
    /** True when bulk selection mode is active. */
    val isBulkSelectionMode: Boolean = false,
    /** URLs of manga that are currently in the library (favorited). Used for long-click toggle. */
    val favoritedMangaUrls: Set<String> = emptySet(),
    /** Scope for the search bar: SOURCES searches the selected source, LIBRARY searches the local library. */
    val searchScope: BrowseSearchScope = BrowseSearchScope.SOURCES,
    /** Recent search history (last 10 queries). */
    val searchHistory: List<String> = emptyList(),
    /** Source IDs pinned to the top of the source list in Browse. */
    val pinnedSourceIds: Set<Long> = emptySet(),
    /**
     * Source IDs individually hidden from Browse without uninstalling their owning
     * extension — matches Komikku's per-source disable, distinct from the coarser
     * per-extension enable/disable and per-language filtering.
     */
    val disabledSourceIds: Set<Long> = emptySet(),
    /**
     * All installed sources, unfiltered by NSFW/language/disabled state — used only by
     * the "Manage sources" dialog so a disabled source stays reachable to re-enable.
     */
    val allSources: List<MangaSource> = emptyList(),
    /** Whether the "Manage sources" dialog (per-source enable/disable) is open. */
    val showSourcesFilterDialog: Boolean = false,
    /** User-defined category label per source ID. */
    val sourceCategoryMap: Map<Long, String> = emptyMap(),
    /** Controls visibility of the "Set category" dialog. */
    val showSetCategoryDialog: Boolean = false,
    /** Source ID being configured via the category dialog (stored as Long for preferences). */
    val categoryDialogSourceId: Long? = null,
    /** Current text in the "Set category" dialog input field. */
    val categoryDialogText: String = "",
    /** Per-source health tracking: consecutive failures, last error, disabled flag. */
    val sourceHealth: Map<Long, SourceHealthEntry> = emptyMap(),
    /** Source ID whose diagnostic sheet is currently open; null means sheet is hidden. */
    val selectedDiagnosticSourceId: Long? = null,
    /** Named source search queries bookmarked by the user (#1051). */
    val namedSavedSearches: List<SavedSourceSearch> = emptyList(),
    /** Whether the "Save search" dialog is visible. */
    val showSaveSearchDialog: Boolean = false,
    /** Current text in the "Save search" name field. */
    val saveSearchName: String = "",
    /** Mirrors [app.otakureader.core.preferences.GeneralPreferences.showNsfwContent] for display in the overflow menu. */
    val showNsfw: Boolean = false,
    /** Language codes the user has enabled for Browse source filtering (e.g. "en", "ja"). */
    val enabledLanguages: Set<String> = setOf("en"),
    /** Whether the language filter dialog is open. */
    val showLanguageDialog: Boolean = false,
    /** All distinct language codes present across installed sources (computed in ViewModel). */
    val availableLanguages: List<String> = emptyList(),
    /** Maps source numeric ID → extension iconUrl, for displaying actual source icons in the list. */
    val sourceIconUrls: Map<Long, String?> = emptyMap(),
) : UiState

sealed interface BrowseEvent : UiEvent {
    data class SelectSource(val sourceId: String, val loadLatest: Boolean = false) : BrowseEvent
    data object ClearSourceSelection : BrowseEvent
    data class SelectTab(val tab: BrowseTab) : BrowseEvent
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

    /**
     * Long-click on a manga card in Browse: quickly add to or remove from the library
     * without entering bulk selection mode.
     */
    data class LongClickManga(val manga: SourceManga) : BrowseEvent

    data class SetSearchScope(val scope: BrowseSearchScope) : BrowseEvent
    data object ClearSearchHistory : BrowseEvent
    /** Removes a single recent-search entry. */
    data class DeleteSearchHistoryItem(val query: String) : BrowseEvent

    // --- Source pinning & categories ---
    /** Toggles the pinned state for the source identified by its numeric ID string. */
    data class TogglePinSource(val sourceId: Long) : BrowseEvent
    /** Toggles whether the source is hidden from Browse without uninstalling its extension. */
    data class ToggleDisableSource(val sourceId: Long) : BrowseEvent
    /** Opens the "Manage sources" dialog (per-source enable/disable), listing all sources. */
    data object ShowSourcesFilterDialog : BrowseEvent
    /** Dismisses the "Manage sources" dialog. */
    data object DismissSourcesFilterDialog : BrowseEvent
    /** Opens the "Set category" dialog pre-populated with the current label. */
    data class OpenSetCategoryDialog(val sourceId: Long, val currentCategory: String) : BrowseEvent
    /** User typed in the category dialog text field. */
    data class UpdateCategoryDialogText(val text: String) : BrowseEvent
    /** User confirmed the category dialog — persists the category. */
    data object ConfirmSetCategory : BrowseEvent
    /** User dismissed the category dialog without saving. */
    data object DismissSetCategoryDialog : BrowseEvent

    // --- Named saved source searches (#1051) ---
    /** Opens the "Save search" dialog for the current query + source. */
    data object ShowSaveSearchDialog : BrowseEvent
    /** Dismisses the "Save search" dialog without saving. */
    data object HideSaveSearchDialog : BrowseEvent
    /** Updates the name field in the "Save search" dialog. */
    data class UpdateSaveSearchName(val name: String) : BrowseEvent
    /** Saves the current search query under the given name. */
    data object ConfirmSaveSearch : BrowseEvent
    /** Re-applies a previously saved named search (sets query + runs search). */
    data class ApplyNamedSavedSearch(val search: SavedSourceSearch) : BrowseEvent
    /** Removes a saved named search by its UUID id. */
    data class DeleteNamedSavedSearch(val id: String) : BrowseEvent
    /** Moves a saved named search one position earlier in display order (Komikku parity). */
    data class MoveNamedSavedSearchUp(val id: String) : BrowseEvent
    /** Moves a saved named search one position later in display order (Komikku parity). */
    data class MoveNamedSavedSearchDown(val id: String) : BrowseEvent

    /** Toggles the global NSFW content preference from the Browse overflow menu. */
    data object ToggleNsfwFilter : BrowseEvent

    /** Opens the language filter dialog. */
    data object ShowLanguageDialog : BrowseEvent
    /** Dismisses the language filter dialog without saving. */
    data object DismissLanguageDialog : BrowseEvent
    /** Persists the selected language set and re-filters the source list. */
    data class SetEnabledLanguages(val languages: Set<String>) : BrowseEvent
}

sealed interface BrowseEffect : UiEffect {
    data class NavigateToMangaDetail(val sourceId: String, val mangaUrl: String) : BrowseEffect
    data class ShowSnackbar(val message: String) : BrowseEffect
    /** Navigate to library after bulk add to show newly added manga. */
    data object NavigateToLibrary : BrowseEffect
}
