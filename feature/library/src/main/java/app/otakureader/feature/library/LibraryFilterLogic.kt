package app.otakureader.feature.library

import app.otakureader.domain.model.ContentRating
import app.otakureader.domain.model.Manga
import kotlin.random.Random

internal fun applyFiltersAndSort(items: List<LibraryMangaItem>, params: FilterSortParams): List<LibraryMangaItem> {
    val filtered = items
        .let { applyCategoryFilter(it, params) }
        .let { applyNsfwFilter(it, params) }
        .let { applySearchFilter(it, params) }
        .let { applyHasNotesFilter(it, params) }
        .let { applySourceFilter(it, params) }
        .let { applyReadingListFilter(it, params) }
        .let { applyGenreFilter(it, params.filterGenres) }
        // Legacy single-select filter: drives the quick-access chips in the search bar.
        .let { applyFilterMode(it, params.filterMode) }
        // Independent tristate filters: drives the per-attribute toggles in the filter sheet.
        .let { applyTriStateFilters(it, params) }
    return applySort(filtered, params)
}

internal fun applyCategoryFilter(items: List<LibraryMangaItem>, params: FilterSortParams): List<LibraryMangaItem> {
    val selected = params.selectedCategory ?: return items
    return when (params.groupType) {
        LibraryGroup.BY_SOURCE -> items.filter { it.sourceId == selected }
        LibraryGroup.BY_STATUS -> items.filter { (it.status.ordinal + 1).toLong() == selected }
        else -> items.filter { it.id in params.categoryMangaIds }
    }
}

internal fun applyNsfwFilter(items: List<LibraryMangaItem>, params: FilterSortParams): List<LibraryMangaItem> =
    if (!params.showNsfw) items.filter { !it.isNsfw } else items

internal fun applySearchFilter(items: List<LibraryMangaItem>, params: FilterSortParams): List<LibraryMangaItem> {
    val matchingIds = params.searchMatchingIds ?: return items
    return items.filter { it.id in matchingIds }
}

internal fun applyHasNotesFilter(items: List<LibraryMangaItem>, params: FilterSortParams): List<LibraryMangaItem> =
    if (params.filterHasNotes) items.filter { it.hasNote } else items

internal fun applySourceFilter(items: List<LibraryMangaItem>, params: FilterSortParams): List<LibraryMangaItem> =
    if (params.filterSourceId != null) items.filter { it.sourceId == params.filterSourceId } else items

internal fun applyReadingListFilter(items: List<LibraryMangaItem>, params: FilterSortParams): List<LibraryMangaItem> =
    if (params.filterReadingListId != null) items.filter { it.id in params.readingListMangaIds } else items

internal fun applyGenreFilter(items: List<LibraryMangaItem>, filterGenres: Set<String>): List<LibraryMangaItem> =
    if (filterGenres.isEmpty()) items else items.filter { manga -> manga.genres.any { it in filterGenres } }

internal fun applyFilterMode(items: List<LibraryMangaItem>, filterMode: LibraryFilterMode): List<LibraryMangaItem> =
    when (filterMode) {
        LibraryFilterMode.DOWNLOADED -> items.filter { it.isDownloaded }
        LibraryFilterMode.UNREAD -> items.filter { it.unreadCount > 0 }
        LibraryFilterMode.COMPLETED -> items.filter { it.userCompleted }
        LibraryFilterMode.DROPPED -> items.filter { it.userDropped }
        LibraryFilterMode.TRACKING -> items.filter { it.hasTracking }
        LibraryFilterMode.READING_LIST, LibraryFilterMode.ALL -> items
    }

/** Applies independent tristate filters (Komikku parity) in a single pass. */
internal fun applyTriStateFilters(items: List<LibraryMangaItem>, params: FilterSortParams): List<LibraryMangaItem> {
    val anyActive = params.filterDownloaded != LibraryTriState.DISABLED ||
        params.filterUnread != LibraryTriState.DISABLED ||
        params.filterStarted != LibraryTriState.DISABLED ||
        params.filterBookmarked != LibraryTriState.DISABLED ||
        params.filterTracking != LibraryTriState.DISABLED ||
        params.filterCompleted != LibraryTriState.DISABLED
    if (!anyActive) return items
    return items.filter { item ->
        triStateMatches(params.filterDownloaded, item.isDownloaded) &&
            triStateMatches(params.filterUnread, item.unreadCount > 0) &&
            triStateMatches(params.filterStarted, item.lastRead != null) &&
            triStateMatches(params.filterBookmarked, item.id in params.bookmarkedMangaIds) &&
            triStateMatches(params.filterTracking, item.hasTracking) &&
            triStateMatches(params.filterCompleted, item.userCompleted)
    }
}

private fun triStateMatches(state: LibraryTriState, value: Boolean): Boolean = when (state) {
    LibraryTriState.DISABLED -> true
    LibraryTriState.ENABLED_IS -> value
    LibraryTriState.ENABLED_NOT -> !value
}

internal fun applySort(items: List<LibraryMangaItem>, params: FilterSortParams): List<LibraryMangaItem> {
    val sorted = when (params.sortMode) {
        LibrarySortMode.ALPHABETICAL -> items.sortedBy { it.title }
        LibrarySortMode.LAST_READ -> items.sortedByDescending { it.lastRead ?: 0L }
        LibrarySortMode.DATE_ADDED -> items.sortedByDescending { it.dateAdded }
        LibrarySortMode.UNREAD_COUNT -> items.sortedByDescending { it.unreadCount }
        LibrarySortMode.SOURCE -> items.sortedBy { it.sourceId }
        LibrarySortMode.LAST_UPDATED -> items.sortedByDescending { it.lastUpdate }
        LibrarySortMode.TOTAL_CHAPTERS -> items.sortedByDescending { it.totalChapterCount }
        // Both LATEST_CHAPTER and LAST_UPDATED use the same lastUpdate field; a dedicated
        // latestChapterUpload field would be needed to distinguish them in the data model.
        LibrarySortMode.LATEST_CHAPTER -> items.sortedByDescending { it.lastUpdate }
        // Seeded with the ViewModel's session seed so items don't re-shuffle on every emission.
        LibrarySortMode.RANDOM -> items.shuffled(Random(params.randomSeed))
        // Chapter fetch date maps to dateAdded until a dedicated field is added to the data model.
        LibrarySortMode.CHAPTER_FETCH_DATE -> items.sortedByDescending { it.dateAdded }
        // Tracker mean score requires a tracker-score field; falls back to title until available.
        LibrarySortMode.TRACKER_MEAN -> items.sortedBy { it.title }
    }
    return if (params.sortAscending) sorted else sorted.reversed()
}

internal fun Manga.toLibraryItem(
    isDownloaded: Boolean = false,
    hasTracking: Boolean = false,
    sourceName: String = "",
    sourceLanguage: String = "",
    sourceIconUrl: String? = null,
) = LibraryMangaItem(
    id = id,
    title = title,
    thumbnailUrl = thumbnailUrl,
    unreadCount = unreadCount,
    isFavorite = favorite,
    hasNote = !notes.isNullOrBlank(),
    sourceId = sourceId,
    isDownloaded = isDownloaded,
    hasTracking = hasTracking,
    isNsfw = contentRating == ContentRating.EROTICA || contentRating == ContentRating.PORNOGRAPHIC,
    lastRead = lastRead,
    dateAdded = dateAdded,
    status = status,
    totalChapterCount = totalChapters,
    lastUpdate = lastUpdate,
    userCompleted = userCompleted,
    userDropped = userDropped,
    genres = genre,
    sourceName = sourceName,
    sourceLanguage = sourceLanguage,
    sourceIconUrl = sourceIconUrl,
)
