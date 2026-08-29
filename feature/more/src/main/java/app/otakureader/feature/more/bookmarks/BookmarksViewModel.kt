package app.otakureader.feature.more.bookmarks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.bookmark.BookmarkPageExporter
import app.otakureader.domain.model.BookmarkPageRef
import app.otakureader.domain.model.ExportResult
import app.otakureader.domain.repository.BookmarkCollectionRepository
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.PageBookmarkRepository
import app.otakureader.feature.more.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookmarksViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pageBookmarkRepository: PageBookmarkRepository,
    private val bookmarkCollectionRepository: BookmarkCollectionRepository,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val bookmarkPageExporter: BookmarkPageExporter,
) : ViewModel() {

    private val _effect = Channel<BookmarksEffect>(Channel.BUFFERED)
    val effect: Flow<BookmarksEffect> = _effect.receiveAsFlow()

    private val _collapsedManga = MutableStateFlow<Set<Long>>(emptySet())
    private val _searchQuery = MutableStateFlow("")
    private val _selectedCollectionId = MutableStateFlow<Long?>(null)
    private val _isManageCollectionsVisible = MutableStateFlow(false)
    private val _selectedBookmarkIds = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * True while an export or share is resolving pages.
     *
     * Not cosmetic: a page that is not downloaded has to be fetched from the source, so a large
     * selection can take seconds. Without this the buttons look exactly as dead as the placeholder
     * they replaced.
     */
    private val _isExporting = MutableStateFlow(false)

    private val enrichedBookmarks = pageBookmarkRepository.getAllBookmarks()
        .mapLatest { bookmarks ->
            if (bookmarks.isEmpty()) return@mapLatest emptyList()

            val mangaMap = mangaRepository
                .getMangaByIds(bookmarks.map { it.mangaId }.distinct())
                .associate { manga -> manga.id to manga }

            val chapterMap = coroutineScope {
                bookmarks.map { it.chapterId }.distinct()
                    .map { id -> async { id to chapterRepository.getChapterById(id) } }
                    .awaitAll()
                    .toMap()
            }

            bookmarks.map { bm ->
                val manga = mangaMap[bm.mangaId]
                BookmarkItem(
                    id = bm.id,
                    mangaId = bm.mangaId,
                    chapterId = bm.chapterId,
                    pageIndex = bm.pageIndex,
                    note = bm.note,
                    mangaTitle = manga?.title.orEmpty(),
                    chapterName = chapterMap[bm.chapterId]?.name.orEmpty(),
                    mangaCoverUrl = manga?.thumbnailUrl,
                    collectionId = bm.collectionId,
                )
            }
        }
        .catch { emit(emptyList()) }

    val state: StateFlow<BookmarksState> = combine(
        enrichedBookmarks,
        _searchQuery,
        _collapsedManga,
        bookmarkCollectionRepository.getAllCollections(),
        combine(
            _selectedCollectionId,
            _isManageCollectionsVisible,
            _selectedBookmarkIds,
            _isExporting,
        ) { colId, mgmt, sel, exporting -> Selection(colId, mgmt, sel, exporting) },
    ) { items, query, collapsed, collections, selection ->
        BookmarksState(
            isLoading = false,
            bookmarks = items,
            searchQuery = query,
            collapsedManga = collapsed,
            collections = collections,
            selectedCollectionId = selection.collectionId,
            isManageCollectionsVisible = selection.managingCollections,
            selectedBookmarkIds = selection.selectedIds,
            isExporting = selection.isExporting,
        )
    }
        .catch { emit(BookmarksState(isLoading = false)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = BookmarksState(),
        )

    /** The four selection-ish flows, bundled because [combine] takes at most five sources. */
    private data class Selection(
        val collectionId: Long?,
        val managingCollections: Boolean,
        val selectedIds: Set<Long>,
        val isExporting: Boolean,
    )

    fun onIntent(intent: BookmarksIntent) {
        when (intent) {
            is BookmarksIntent.SearchQueryChanged -> _searchQuery.value = intent.query

            is BookmarksIntent.ToggleMangaExpanded -> _collapsedManga.update { current ->
                if (intent.mangaId in current) current - intent.mangaId
                else current + intent.mangaId
            }

            is BookmarksIntent.DeleteBookmark -> deleteBookmark(intent.item)

            is BookmarksIntent.OpenBookmark -> viewModelScope.launch {
                _effect.send(
                    BookmarksEffect.NavigateToReader(intent.mangaId, intent.chapterId, intent.pageIndex)
                )
            }

            // Collections
            is BookmarksIntent.SelectCollection -> _selectedCollectionId.value = intent.collectionId
            is BookmarksIntent.CreateCollection -> createCollection(intent.name)
            is BookmarksIntent.RenameCollection -> renameCollection(intent.id, intent.name)
            is BookmarksIntent.DeleteCollection -> deleteCollection(intent.id)
            is BookmarksIntent.ShowManageCollections -> _isManageCollectionsVisible.value = true
            is BookmarksIntent.HideManageCollections -> _isManageCollectionsVisible.value = false

            // Multi-select
            is BookmarksIntent.ToggleBookmarkSelection -> _selectedBookmarkIds.update { current ->
                if (intent.id in current) current - intent.id else current + intent.id
            }
            is BookmarksIntent.SelectAllBookmarks -> _selectedBookmarkIds.update {
                state.value.filteredBookmarks.map { it.id }.toSet()
            }
            is BookmarksIntent.ClearSelection -> _selectedBookmarkIds.value = emptySet()
            // Read from the backing flow, not from [state]. `state` is a `combine` republished
            // through `stateIn`, so it trails its inputs by a dispatch — an export triggered in
            // that window would act on the previous selection, or on none at all.
            is BookmarksIntent.ExportSelected -> exportSelected(_selectedBookmarkIds.value)
            is BookmarksIntent.ShareSelected -> shareSelected(_selectedBookmarkIds.value)
        }
    }

    private fun deleteBookmark(item: BookmarkItem) {
        viewModelScope.launch {
            try {
                pageBookmarkRepository.removeBookmark(item.chapterId, item.pageIndex)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _effect.send(BookmarksEffect.ShowSnackbar(context.getString(R.string.bookmarks_error_delete_bookmark)))
            }
        }
    }

    private fun createCollection(name: String) {
        viewModelScope.launch {
            try {
                bookmarkCollectionRepository.addCollection(name)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _effect.send(BookmarksEffect.ShowSnackbar(context.getString(R.string.bookmarks_error_create_collection)))
            }
        }
    }

    private fun renameCollection(id: Long, name: String) {
        viewModelScope.launch {
            try {
                bookmarkCollectionRepository.renameCollection(id, name)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _effect.send(BookmarksEffect.ShowSnackbar(context.getString(R.string.bookmarks_error_rename_collection)))
            }
        }
    }

    private fun deleteCollection(id: Long) {
        viewModelScope.launch {
            try {
                bookmarkCollectionRepository.deleteCollection(id)
                // Clear selection if we deleted the selected collection
                if (_selectedCollectionId.value == id) _selectedCollectionId.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _effect.send(BookmarksEffect.ShowSnackbar(context.getString(R.string.bookmarks_error_delete_collection)))
            }
        }
    }

    /**
     * Saves the selected pages to the device gallery (#1132).
     *
     * This used to send a `RequestExport` effect that the Screen answered with a snackbar reading
     * "image export coming in v1.1" — a live button that did nothing, which CLAUDE.md names as the
     * mistake not to make. The work now happens in [BookmarkPageExporter], which resolves each
     * bookmark to its page image from the download, the CBZ or the source.
     */
    private fun exportSelected(bookmarkIds: Set<Long>) {
        val refs = pageRefsFor(bookmarkIds)
        if (refs.isEmpty()) {
            _selectedBookmarkIds.value = emptySet()
            return
        }
        viewModelScope.launch {
            _isExporting.value = true
            try {
                when (val result = bookmarkPageExporter.exportToGallery(refs)) {
                    is ExportResult.Completed ->
                        _effect.send(BookmarksEffect.ExportComplete(result.saved, result.failed))
                    ExportResult.GalleryUnsupported ->
                        _effect.send(BookmarksEffect.ExportUnsupported)
                }
                _selectedBookmarkIds.value = emptySet()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _effect.send(BookmarksEffect.ShowSnackbar(context.getString(R.string.bookmarks_error_export)))
            } finally {
                _isExporting.value = false
            }
        }
    }

    /**
     * Resolves the selection to the triples the exporter needs.
     *
     * Read from the already-loaded list rather than re-queried: the screen is displaying these
     * rows, so a second database round-trip would only add a way for the two to disagree.
     */
    private fun pageRefsFor(bookmarkIds: Set<Long>): List<BookmarkPageRef> =
        state.value.bookmarks
            .filter { it.id in bookmarkIds }
            .map {
                BookmarkPageRef(
                    mangaId = it.mangaId,
                    chapterId = it.chapterId,
                    pageIndex = it.pageIndex,
                    mangaTitle = it.mangaTitle,
                    chapterName = it.chapterName,
                )
            }

    /**
     * Shares the selected pages as images (#1133).
     *
     * Previously this shared a *text* list of "manga · chapter · page N" lines, which is not what
     * a user selecting panels is asking for. The images are copied into app cache and handed over
     * as `content://` URIs; they deliberately do not go to the gallery, since sharing a panel
     * should not silently add it to the camera roll.
     */
    private fun shareSelected(bookmarkIds: Set<Long>) {
        val refs = pageRefsFor(bookmarkIds)
        if (refs.isEmpty()) {
            _selectedBookmarkIds.value = emptySet()
            return
        }
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val result = bookmarkPageExporter.prepareForShare(refs)
                if (result.uris.isEmpty()) {
                    _effect.send(BookmarksEffect.ShowSnackbar(context.getString(R.string.bookmarks_error_share)))
                } else {
                    _effect.send(BookmarksEffect.ShareImages(result.uris, result.failed))
                }
                _selectedBookmarkIds.value = emptySet()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _effect.send(BookmarksEffect.ShowSnackbar(context.getString(R.string.bookmarks_error_share)))
            } finally {
                _isExporting.value = false
            }
        }
    }
}
