package app.otakureader.feature.more.bookmarks

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.domain.model.BookmarkCollection

// ─── Domain display model ─────────────────────────────────────────────────────

/** A single page bookmark enriched with display data for the Bookmarks screen. */
data class BookmarkItem(
    val id: Long,
    val mangaId: Long,
    val chapterId: Long,
    val pageIndex: Int,
    val note: String?,
    val mangaTitle: String,
    val chapterName: String,
    /** Cover URL from the parent manga — used for the thumbnail in the list row. */
    val mangaCoverUrl: String?,
    val collectionId: Long? = null,
)

/** Per-manga group shown in the list: header + expandable chapter sub-groups. */
data class BookmarkGroup(
    val mangaTitle: String,
    val mangaId: Long,
    val mangaCoverUrl: String?,
    val isExpanded: Boolean = true,
    val chapters: List<ChapterGroup>,
)

/** Sub-group of bookmarks belonging to a single chapter within a manga group. */
data class ChapterGroup(
    val chapterId: Long,
    val chapterName: String,
    val bookmarks: List<BookmarkItem>,
)

// ─── State ────────────────────────────────────────────────────────────────────

data class BookmarksState(
    val isLoading: Boolean = true,
    val bookmarks: List<BookmarkItem> = emptyList(),
    val searchQuery: String = "",
    /** Set of mangaIds whose groups are currently collapsed. Default: all expanded. */
    val collapsedManga: Set<Long> = emptySet(),
    val error: String? = null,
    // Collections
    val collections: List<BookmarkCollection> = emptyList(),
    val selectedCollectionId: Long? = null,
    val isManageCollectionsVisible: Boolean = false,
    // Multi-select (Part D)
    val selectedBookmarkIds: Set<Long> = emptySet(),
    /** An export or share is resolving pages; those actions are disabled meanwhile. */
    val isExporting: Boolean = false,
) : UiState {

    val isSelectionMode: Boolean get() = selectedBookmarkIds.isNotEmpty()

    /** Source list: either filtered by collection or all bookmarks. */
    private val collectionFiltered: List<BookmarkItem> = if (selectedCollectionId == null) bookmarks
    else bookmarks.filter { it.collectionId == selectedCollectionId }

    /** Flat list filtered by [searchQuery]. Computed once at construction time. */
    val filteredBookmarks: List<BookmarkItem> = if (searchQuery.isBlank()) collectionFiltered
    else collectionFiltered.filter { bm ->
        bm.mangaTitle.contains(searchQuery, ignoreCase = true) ||
            bm.chapterName.contains(searchQuery, ignoreCase = true) ||
            bm.note?.contains(searchQuery, ignoreCase = true) == true
    }

    /** Grouped structure consumed by the list UI. Computed once at construction time. */
    val grouped: List<BookmarkGroup> = filteredBookmarks
        .groupBy { it.mangaId }
        .map { (mangaId, items) ->
            val first = items.first()
            val chapters = items
                .groupBy { it.chapterId }
                .map { (chapterId, chItems) ->
                    ChapterGroup(
                        chapterId = chapterId,
                        chapterName = chItems.first().chapterName,
                        bookmarks = chItems.sortedBy { it.pageIndex },
                    )
                }
                .sortedBy { it.chapterName }
            BookmarkGroup(
                mangaTitle = first.mangaTitle,
                mangaId = mangaId,
                mangaCoverUrl = first.mangaCoverUrl,
                isExpanded = mangaId !in collapsedManga,
                chapters = chapters,
            )
        }
        .sortedBy { it.mangaTitle }

    val isEmpty: Boolean = !isLoading && filteredBookmarks.isEmpty()
    val hasBookmarks: Boolean = !isLoading && bookmarks.isNotEmpty()
}

// ─── Intent ───────────────────────────────────────────────────────────────────

sealed interface BookmarksIntent : UiEvent {
    data class SearchQueryChanged(val query: String) : BookmarksIntent
    data class ToggleMangaExpanded(val mangaId: Long) : BookmarksIntent
    data class DeleteBookmark(val item: BookmarkItem) : BookmarksIntent
    data class OpenBookmark(val mangaId: Long, val chapterId: Long, val pageIndex: Int) : BookmarksIntent
    // Collections
    data class SelectCollection(val collectionId: Long?) : BookmarksIntent
    data class CreateCollection(val name: String) : BookmarksIntent
    data class RenameCollection(val id: Long, val name: String) : BookmarksIntent
    data class DeleteCollection(val id: Long) : BookmarksIntent
    data object ShowManageCollections : BookmarksIntent
    data object HideManageCollections : BookmarksIntent
    // Multi-select (Part D)
    data class ToggleBookmarkSelection(val id: Long) : BookmarksIntent
    data object SelectAllBookmarks : BookmarksIntent
    data object ClearSelection : BookmarksIntent
    data object ExportSelected : BookmarksIntent
    data object ShareSelected : BookmarksIntent
}

// ─── Effect ───────────────────────────────────────────────────────────────────

sealed interface BookmarksEffect : UiEffect {
    /**
     * [pageIndex] is the whole point of a page bookmark: without it the reader opens the chapter
     * at `lastPageRead`, which is wherever the user stopped and not the panel they saved.
     */
    data class NavigateToReader(val mangaId: Long, val chapterId: Long, val pageIndex: Int) : BookmarksEffect
    data class ShowSnackbar(val message: String) : BookmarksEffect
    /**
     * The export finished. [failed] is carried separately rather than folded into a single count
     * so a partial result reads as one: a page whose source is uninstalled or unreachable cannot
     * be exported, and reporting only [saved] would hide that some panels never arrived.
     */
    data class ExportComplete(val saved: Int, val failed: Int) : BookmarksEffect

    /**
     * The device cannot write to the gallery at all — below API 29 that needs a storage permission
     * this app does not request. Distinct from [ExportComplete] with `saved = 0`, which means the
     * export ran and the pages could not be resolved.
     */
    data object ExportUnsupported : BookmarksEffect

    /**
     * Launch the Android Sharesheet with these page images. Strings, not `Uri`, because the URIs
     * are produced in the data layer and this module parses them at the Intent boundary.
     */
    data class ShareImages(val uris: List<String>, val failed: Int) : BookmarksEffect
}
