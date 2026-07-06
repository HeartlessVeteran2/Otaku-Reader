package app.otakureader.feature.more.bookmarks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    private val _effect = Channel<BookmarksEffect>(Channel.BUFFERED)
    val effect: Flow<BookmarksEffect> = _effect.receiveAsFlow()

    private val _collapsedManga = MutableStateFlow<Set<Long>>(emptySet())
    private val _searchQuery = MutableStateFlow("")
    private val _selectedCollectionId = MutableStateFlow<Long?>(null)
    private val _isManageCollectionsVisible = MutableStateFlow(false)
    private val _selectedBookmarkIds = MutableStateFlow<Set<Long>>(emptySet())

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
        combine(_selectedCollectionId, _isManageCollectionsVisible, _selectedBookmarkIds) { colId, mgmt, sel ->
            Triple(colId, mgmt, sel)
        },
    ) { items, query, collapsed, collections, (selectedColId, managingCollections, selectedIds) ->
        BookmarksState(
            isLoading = false,
            bookmarks = items,
            searchQuery = query,
            collapsedManga = collapsed,
            collections = collections,
            selectedCollectionId = selectedColId,
            isManageCollectionsVisible = managingCollections,
            selectedBookmarkIds = selectedIds,
        )
    }
        .catch { emit(BookmarksState(isLoading = false)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = BookmarksState(),
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
                _effect.send(BookmarksEffect.NavigateToReader(intent.mangaId, intent.chapterId))
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
            is BookmarksIntent.ExportSelected -> exportSelected(state.value.selectedBookmarkIds)
            is BookmarksIntent.ShareSelected -> shareSelected(state.value.selectedBookmarkIds)
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

    private fun exportSelected(bookmarkIds: Set<Long>) {
        if (bookmarkIds.isEmpty()) return
        viewModelScope.launch {
            try {
                // Delegate to Screen which has Context for MediaStore access.
                _effect.send(BookmarksEffect.RequestExport(bookmarkIds))
                _selectedBookmarkIds.value = emptySet()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _effect.send(BookmarksEffect.ShowSnackbar(context.getString(R.string.bookmarks_error_export)))
            }
        }
    }

    private fun shareSelected(bookmarkIds: Set<Long>) {
        if (bookmarkIds.isEmpty()) return
        val items = state.value.bookmarks.filter { it.id in bookmarkIds }
        if (items.isEmpty()) {
            _selectedBookmarkIds.value = emptySet()
            return
        }
        viewModelScope.launch {
            _effect.send(BookmarksEffect.ShareSelected(items))
            _selectedBookmarkIds.value = emptySet()
        }
    }
}
