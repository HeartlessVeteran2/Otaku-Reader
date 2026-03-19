package app.otakureader.feature.library

import app.otakureader.domain.model.MangaStatus

enum class LibrarySortMode {
    ALPHABETICAL,
    LAST_READ,
    DATE_ADDED,
    UNREAD_COUNT,
    SOURCE
}

enum class LibraryFilterMode {
    ALL,
    DOWNLOADED,
    UNREAD,
    COMPLETED,
    TRACKING
}

data class LibraryState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val mangaList: List<LibraryMangaItem> = emptyList(),
    val selectedManga: Set<Long> = emptySet(),
    val searchQuery: String = "",
    val error: String? = null,
    val categories: List<CategoryItem> = emptyList(),
    val selectedCategory: Long? = null,
    val gridSize: Int = 3,
    val showBadges: Boolean = true,
    val filterHasNotes: Boolean = false,
    val sortMode: LibrarySortMode = LibrarySortMode.ALPHABETICAL,
    val filterMode: LibraryFilterMode = LibraryFilterMode.ALL,
    val filterSourceId: Long? = null,
    val showNsfw: Boolean = false,
    val newUpdatesCount: Int = 0
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
    val status: MangaStatus = MangaStatus.UNKNOWN
)

data class CategoryItem(
    val id: Long,
    val name: String,
    val count: Int
)

sealed class LibraryEvent {
    data object Refresh : LibraryEvent()
    data class OnMangaClick(val mangaId: Long) : LibraryEvent()
    data class OnMangaLongClick(val mangaId: Long) : LibraryEvent()
    data class OnSearchQueryChange(val query: String) : LibraryEvent()
    data class OnCategorySelected(val categoryId: Long?) : LibraryEvent()
    data object ClearSelection : LibraryEvent()
    data class ToggleFavorite(val mangaId: Long) : LibraryEvent()
    data class FilterHasNotes(val enabled: Boolean) : LibraryEvent()
    data class SetSortMode(val mode: LibrarySortMode) : LibraryEvent()
    data class SetFilterMode(val mode: LibraryFilterMode) : LibraryEvent()
    data class SetFilterSource(val sourceId: Long?) : LibraryEvent()
    data class ToggleNsfw(val show: Boolean) : LibraryEvent()
}

sealed class LibraryEffect {
    data class NavigateToManga(val mangaId: Long) : LibraryEffect()
    data class NavigateToReader(val mangaId: Long, val chapterId: Long?) : LibraryEffect()
    data class ShowError(val message: String) : LibraryEffect()
    data class NavigateToMigration(val selectedMangaIds: List<Long>) : LibraryEffect()
}
