package app.otakureader.feature.library

import app.otakureader.domain.model.ContinueReadingItem
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.model.ReadingGoal
import app.otakureader.domain.model.SavedLibraryView

enum class LibrarySortMode {
    ALPHABETICAL,
    LAST_READ,
    DATE_ADDED,
    UNREAD_COUNT,
    SOURCE,
    LAST_UPDATED,
    TOTAL_CHAPTERS,
    // Appended for stable persisted ordinals — never reorder above entries.
    LATEST_CHAPTER,
    RANDOM,
    CHAPTER_FETCH_DATE,
    TRACKER_MEAN,
}

enum class LibraryFilterMode {
    ALL,
    DOWNLOADED,
    UNREAD,
    COMPLETED,
    DROPPED,
    TRACKING,
    READING_LIST
}

/**
 * Three-state filter for independent per-attribute library filtering.
 *
 * - [DISABLED]: filter is off (include all items).
 * - [ENABLED_IS]: include only items that match.
 * - [ENABLED_NOT]: exclude items that match.
 *
 * Cycles DISABLED → ENABLED_IS → ENABLED_NOT → DISABLED on each tap.
 */
enum class LibraryTriState {
    DISABLED,
    ENABLED_IS,
    ENABLED_NOT;

    fun next(): LibraryTriState = when (this) {
        DISABLED -> ENABLED_IS
        ENABLED_IS -> ENABLED_NOT
        ENABLED_NOT -> DISABLED
    }
}

enum class LibraryBottomSheetTab {
    FILTER,
    SORT,
    DISPLAY,
    GROUP
}

/**
 * Library grouping modes — mirrors Komikku's `LibraryGroup` constants.
 *
 * Ordinals are intentionally NOT used for persistence; use the named constants instead.
 * Persisted as an `Int` via [LibraryPreferences.groupType].
 */
object LibraryGroup {
    const val BY_DEFAULT = 0
    const val BY_SOURCE = 1
    const val BY_STATUS = 2
    const val BY_TRACK_STATUS = 3
    const val UNGROUPED = 4
}

/**
 * Top-level layout for the library grid.
 *
 * - [GRID]: cover-centric grid (the [LibraryState.isStaggeredGrid] flag further selects
 *   uniform vs. waterfall columns; [LibraryState.showTitle] toggles compact vs. cover-only).
 * - [COMFORTABLE_GRID]: like the grid, but the title is a caption *below* each cover instead
 *   of overlaid — matches Mihon/Komikku's "Comfortable grid".
 * - [LIST]: compact single-column rows with a small cover thumbnail, title, and badges.
 *
 * Persisted as an ordinal via `LibraryPreferences.libraryDisplayMode`. New values must be
 * appended (never reordered) so existing persisted ordinals keep resolving correctly.
 */
enum class LibraryDisplayMode {
    GRID,
    LIST,
    COMFORTABLE_GRID,
    // Appended for stable persisted ordinals — never reorder above entries.
    COVER_ONLY,
}

data class LibraryState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    /** True while a background library update (the WorkManager job) is running. */
    val isLibraryUpdating: Boolean = false,
    val mangaList: List<LibraryMangaItem> = emptyList(),
    val totalMangaCount: Int = 0,
    val selectedManga: Set<Long> = emptySet(),
    val searchQuery: String = "",
    val showSearchBar: Boolean = false,
    val isSearching: Boolean = false,
    val error: String? = null,
    val categories: List<CategoryItem> = emptyList(),
    val selectedCategory: Long? = null,
    val gridSize: Int = 3,
    val portraitColumns: Int = 0,
    val landscapeColumns: Int = 0,
    val showBadges: Boolean = true,
    val showDownloadBadge: Boolean = true,
    val showTitle: Boolean = true,
    val downloadCountByManga: Map<Long, Int> = emptyMap(),
    val isStaggeredGrid: Boolean = false,
    val displayMode: LibraryDisplayMode = LibraryDisplayMode.GRID,
    val visualEffectsEnabled: Boolean = true,
    val filterHasNotes: Boolean = false,
    val sortMode: LibrarySortMode = LibrarySortMode.ALPHABETICAL,
    val filterMode: LibraryFilterMode = LibraryFilterMode.ALL,
    // Independent tristate filters (Komikku parity) — each can be DISABLED/ENABLED_IS/ENABLED_NOT
    val filterDownloaded: LibraryTriState = LibraryTriState.DISABLED,
    val filterUnread: LibraryTriState = LibraryTriState.DISABLED,
    val filterStarted: LibraryTriState = LibraryTriState.DISABLED,
    val filterBookmarked: LibraryTriState = LibraryTriState.DISABLED,
    val filterTracking: LibraryTriState = LibraryTriState.DISABLED,
    val filterCompleted: LibraryTriState = LibraryTriState.DISABLED,
    val bookmarkedMangaIds: Set<Long> = emptySet(),
    val filterSourceId: Long? = null,
    val showNsfw: Boolean = false,
    val newUpdatesCount: Int = 0,
    val incognitoMode: Boolean = false,
    val downloadedOnly: Boolean = false,
    val categoryFilterMangaIds: Set<Long> = emptySet(), // Manga IDs in selected category
    // Reading list filter
    val readingLists: List<ReadingListFilterItem> = emptyList(),
    val filterReadingListId: Long? = null,
    val readingListMangaIds: Set<Long> = emptySet(),
    // Continue Reading
    val continueReadingItems: List<ContinueReadingItem> = emptyList(),
    // Daily reading goal progress (shown in header when a goal is set)
    val readingGoal: ReadingGoal = ReadingGoal(),
    // Advanced filtering
    val filterGenres: Set<String> = emptySet(),
    val sortAscending: Boolean = true,
    val availableGenres: List<String> = emptyList(),
    val showBottomSheet: Boolean = false,
    val bottomSheetTab: LibraryBottomSheetTab = LibraryBottomSheetTab.FILTER,
    val groupType: Int = LibraryGroup.BY_DEFAULT,
    // Recommendations carousel
    val recommendations: List<LibraryMangaItem> = emptyList(),
    val showRecommendations: Boolean = true,
    // Advanced search sheet
    val showAdvancedSearch: Boolean = false,
    // Category tab display (Komikku parity)
    val showCategoryTabs: Boolean = true,
    val showCategoryItemCount: Boolean = true,
    val showContinueReadingButton: Boolean = true,
    // Saved views (#1039)
    val savedViews: List<SavedLibraryView> = emptyList(),
    val showSaveViewDialog: Boolean = false,
    val saveViewName: String = "",
    val displayName: String = "",
    // Move to category bulk action (GAP 2)
    val showMoveToCategoryDialog: Boolean = false,
    val moveToCategoryMangaIds: Set<Long> = emptySet(),
    // Full unfiltered list — used by composable to compute synthetic group tabs
    val allMangaList: List<LibraryMangaItem> = emptyList(),
)

data class LibraryMangaItem(
    val id: Long,
    val title: String,
    val thumbnailUrl: String?,
    val unreadCount: Int,
    val isFavorite: Boolean,
    val hasNote: Boolean = false,
    val sourceId: Long = 0,
    val isDownloaded: Boolean = false,
    val hasTracking: Boolean = false,
    val isNsfw: Boolean = false,
    val lastRead: Long? = null,
    val dateAdded: Long = 0L,
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val totalChapterCount: Int = 0,
    val lastUpdate: Long = 0L,
    val userCompleted: Boolean = false,
    val userDropped: Boolean = false,
    val genres: List<String> = emptyList(),
    val sourceName: String = "",
    val sourceLanguage: String = "",
    val sourceIconUrl: String? = null,
)

data class CategoryItem(
    val id: Long,
    val name: String,
    val count: Int
)

data class ReadingListFilterItem(
    val id: Long,
    val name: String,
    val count: Int
)

sealed class LibraryEvent {
    data object Refresh : LibraryEvent()
    data class OnMangaClick(val mangaId: Long) : LibraryEvent()
    data class OnMangaLongClick(val mangaId: Long) : LibraryEvent()
    data class OnSearchQueryChange(val query: String) : LibraryEvent()
    data object ToggleSearchBar : LibraryEvent()
    data class OnCategorySelected(val categoryId: Long?) : LibraryEvent()
    data object ClearSelection : LibraryEvent()
    data object SelectAllManga : LibraryEvent()
    data object InvertSelection : LibraryEvent()
    data class ToggleFavorite(val mangaId: Long) : LibraryEvent()
    data class FilterHasNotes(val enabled: Boolean) : LibraryEvent()
    data class SetSortMode(val mode: LibrarySortMode) : LibraryEvent()
    data class SetFilterMode(val mode: LibraryFilterMode) : LibraryEvent()
    data class SetFilterSource(val sourceId: Long?) : LibraryEvent()
    data class ToggleNsfw(val show: Boolean) : LibraryEvent()
    // New overflow menu events
    data object UpdateLibrary : LibraryEvent()
    data object UpdateCategory : LibraryEvent()
    data object OpenRandomEntry : LibraryEvent()
    data object ReindexDownloads : LibraryEvent()
    /** Push all pending tracker states to remote trackers. No-op in incognito mode. */
    data object SyncLibrary : LibraryEvent()
    // Bulk selection actions
    data object MarkSelectedAsRead : LibraryEvent()
    data object MarkSelectedAsUnread : LibraryEvent()
    data object RemoveSelectedFromLibrary : LibraryEvent()
    data object DownloadSelected : LibraryEvent()
    data object MarkSelectedAsCompleted : LibraryEvent()
    data object MarkSelectedAsDropped : LibraryEvent()
    // Continue Reading
    data class ContinueReadingClick(val mangaId: Long) : LibraryEvent()
    // Reading list filter (null = clear/show all)
    data class SetFilterReadingList(val listId: Long?) : LibraryEvent()
    data object ToggleIncognito : LibraryEvent()
    data class SetGenreFilter(val genres: Set<String>) : LibraryEvent()
    data class SetSortAscending(val ascending: Boolean) : LibraryEvent()
    data object ClearAllFilters : LibraryEvent()
    // Independent tristate filter events (Komikku parity)
    data class SetFilterDownloaded(val state: LibraryTriState) : LibraryEvent()
    data class SetFilterUnread(val state: LibraryTriState) : LibraryEvent()
    data class SetFilterStarted(val state: LibraryTriState) : LibraryEvent()
    data class SetFilterBookmarked(val state: LibraryTriState) : LibraryEvent()
    data class SetFilterTracking(val state: LibraryTriState) : LibraryEvent()
    data class SetFilterCompleted(val state: LibraryTriState) : LibraryEvent()
    data object ToggleBottomSheet : LibraryEvent()
    data class SetBottomSheetTab(val tab: LibraryBottomSheetTab) : LibraryEvent()
    data class SetGroupType(val type: Int) : LibraryEvent()
    data class SetGridSize(val size: Int) : LibraryEvent()
    data class SetPortraitColumns(val count: Int) : LibraryEvent()
    data class SetLandscapeColumns(val count: Int) : LibraryEvent()
    data class SetShowBadges(val enabled: Boolean) : LibraryEvent()
    data class SetShowDownloadBadge(val enabled: Boolean) : LibraryEvent()
    data class SetShowTitle(val show: Boolean) : LibraryEvent()
    data class SetStaggeredGrid(val enabled: Boolean) : LibraryEvent()
    data class SetDisplayMode(val mode: LibraryDisplayMode) : LibraryEvent()
    data class SetShowCategoryTabs(val enabled: Boolean) : LibraryEvent()
    data class SetShowCategoryItemCount(val enabled: Boolean) : LibraryEvent()
    data class SetShowContinueReadingButton(val enabled: Boolean) : LibraryEvent()
    data object ToggleFilterSheet : LibraryEvent()
    data class DismissRecommendation(val mangaId: Long) : LibraryEvent()
    data object ToggleAdvancedSearch : LibraryEvent()
    data class ApplyAdvancedSearch(val authorQuery: String, val tagQuery: String) : LibraryEvent()
    // Single-manga selection actions (#995, #996)
    data object ShareSelectedManga : LibraryEvent()
    data object ViewSelectedManga : LibraryEvent()
    // Saved views (#1039)
    data object ShowSaveViewDialog : LibraryEvent()
    data object HideSaveViewDialog : LibraryEvent()
    data class UpdateSaveViewName(val name: String) : LibraryEvent()
    data object ConfirmSaveView : LibraryEvent()
    data class ApplySavedView(val view: SavedLibraryView) : LibraryEvent()
    data class DeleteSavedView(val id: String) : LibraryEvent()
    // EH favorites sync (#1024)
    data object SyncEhFavorites : LibraryEvent()
    // Selection via context menu (used by tests and selection-mode entry)
    data class SelectMangaFromMenu(val mangaId: Long) : LibraryEvent()
    data class UndoLibraryDelete(val mangaIds: Set<Long>) : LibraryEvent()
    // Move to category bulk action (GAP 2)
    data object OpenMoveToCategoryDialog : LibraryEvent()
    data object DismissMoveToCategoryDialog : LibraryEvent()
    data class MoveToCategory(val mangaIds: Set<Long>, val categoryId: Long) : LibraryEvent()
    data object MigrateSelected : LibraryEvent()
    data object UpdateSelected : LibraryEvent()
}

sealed class LibraryEffect {
    data class NavigateToManga(val mangaId: Long) : LibraryEffect()
    data class NavigateToReader(val mangaId: Long, val chapterId: Long) : LibraryEffect()
    data class ShowError(val message: String) : LibraryEffect()
    data class ShowSnackbar(val messageRes: Int, val formatArgs: List<Any> = emptyList()) : LibraryEffect()
    data class NavigateToMigration(val selectedMangaIds: List<Long>) : LibraryEffect()
    data class ShareManga(val title: String, val url: String) : LibraryEffect()
    data class ShowUndoLibraryDelete(val count: Int, val mangaIds: Set<Long>) : LibraryEffect()
}
