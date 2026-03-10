package app.otakureader.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.usecase.GetLibraryMangaUseCase
import app.otakureader.domain.usecase.ToggleFavoriteMangaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getLibraryManga: GetLibraryMangaUseCase,
    private val toggleFavoriteManga: ToggleFavoriteMangaUseCase,
    private val libraryPreferences: LibraryPreferences,
    private val generalPreferences: GeneralPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<LibraryEffect>()
    val effect: SharedFlow<LibraryEffect> = _effect.asSharedFlow()

    /** Holds the full, unfiltered library items for reactive filtering. */
    private val _allItems = MutableStateFlow<List<LibraryMangaItem>>(emptyList())

    init {
        loadLibrary()
        observeLibraryPreferences()
        observeFilteredItems()
    }

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.Refresh -> onRefresh()
            is LibraryEvent.OnMangaClick -> onMangaClick(event.mangaId)
            is LibraryEvent.OnMangaLongClick -> onMangaLongClick(event.mangaId)
            is LibraryEvent.OnSearchQueryChange -> onSearchQueryChange(event.query)
            is LibraryEvent.OnCategorySelected -> onCategorySelected(event.categoryId)
            is LibraryEvent.ClearSelection -> clearSelection()
            is LibraryEvent.ToggleFavorite -> toggleFavorite(event.mangaId)
            is LibraryEvent.FilterHasNotes -> onFilterHasNotes(event.enabled)
            is LibraryEvent.SetSortMode -> onSetSortMode(event.mode)
            is LibraryEvent.SetFilterMode -> onSetFilterMode(event.mode)
            is LibraryEvent.SetFilterSource -> onSetFilterSource(event.sourceId)
            is LibraryEvent.ToggleNsfw -> onToggleNsfw(event.show)
        }
    }

    private fun onRefresh() {
        loadLibrary()
    }

    private fun observeLibraryPreferences() {
        // Observe each preference independently to avoid 6-flow combine type-inference limitation
        libraryPreferences.gridSize
            .onEach { gridSize -> _state.update { it.copy(gridSize = gridSize) } }
            .launchIn(viewModelScope)
        libraryPreferences.showBadges
            .onEach { showBadges -> _state.update { it.copy(showBadges = showBadges) } }
            .launchIn(viewModelScope)
        libraryPreferences.librarySortMode
            .onEach { sortModeInt ->
                _state.update {
                    it.copy(sortMode = LibrarySortMode.entries.getOrElse(sortModeInt) { LibrarySortMode.ALPHABETICAL })
                }
            }
            .launchIn(viewModelScope)
        libraryPreferences.libraryFilterMode
            .onEach { filterModeInt ->
                _state.update {
                    it.copy(filterMode = LibraryFilterMode.entries.getOrElse(filterModeInt) { LibraryFilterMode.ALL })
                }
            }
            .launchIn(viewModelScope)
        libraryPreferences.libraryFilterSourceId
            .onEach { filterSourceId -> _state.update { it.copy(filterSourceId = filterSourceId) } }
            .launchIn(viewModelScope)
        generalPreferences.showNsfwContent
            .onEach { showNsfw -> _state.update { it.copy(showNsfw = showNsfw) } }
            .launchIn(viewModelScope)
    }

    private fun loadLibrary() {
        val isRefreshing = _state.value.mangaList.isNotEmpty()
        _state.update { it.copy(isLoading = !isRefreshing, isRefreshing = isRefreshing) }

        getLibraryManga()
            .map { mangaList ->
                mangaList.map { it.toLibraryItem() }
            }
            .onEach { items ->
                _allItems.value = items
                _state.update { it.copy(isLoading = false, isRefreshing = false, error = null) }
            }
            .catch { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = error.message
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /** Encapsulates all filter/sort parameters derived from state for use in the filtered-items combine. */
    private data class FilterSortParams(
        val query: String,
        val filterHasNotes: Boolean,
        val sortMode: LibrarySortMode,
        val filterMode: LibraryFilterMode,
        val filterSourceId: Long?,
        val showNsfw: Boolean
    )

    private fun observeFilteredItems() {
        // Map state to filter/sort params with distinctUntilChanged to avoid recomputing
        // when only mangaList (a derived field) changes, which would cause an infinite loop.
        val filterParamsFlow = _state.map {
            FilterSortParams(it.searchQuery, it.filterHasNotes, it.sortMode, it.filterMode, it.filterSourceId, it.showNsfw)
        }.distinctUntilChanged()

        combine(_allItems, filterParamsFlow) { items, params ->
            applyFiltersAndSort(items, params)
        }
            .onEach { filtered ->
                _state.update { it.copy(mangaList = filtered) }
            }
            .launchIn(viewModelScope)
    }

    private fun applyFiltersAndSort(items: List<LibraryMangaItem>, params: FilterSortParams): List<LibraryMangaItem> {
        var filtered = items

        // NSFW filter
        if (!params.showNsfw) {
            filtered = filtered.filter { !it.isNsfw }
        }

        // Search filter
        if (params.query.isNotBlank()) {
            filtered = filtered.filter {
                it.title.contains(params.query, ignoreCase = true)
            }
        }

        // Has notes filter
        if (params.filterHasNotes) {
            filtered = filtered.filter { it.hasNote }
        }

        // Filter by source
        if (params.filterSourceId != null) {
            filtered = filtered.filter { it.sourceId == params.filterSourceId }
        }

        // Filter mode
        filtered = when (params.filterMode) {
            // TODO: Re-enable download filtering when isDownloaded is correctly populated
            LibraryFilterMode.DOWNLOADED -> filtered
            LibraryFilterMode.UNREAD -> filtered.filter { it.unreadCount > 0 }
            LibraryFilterMode.COMPLETED -> filtered.filter { it.status == MangaStatus.COMPLETED }
            // TODO: Re-enable tracking filtering when hasTracking is correctly populated
            // (currently left as a no-op because hasTracking is always false until tracking is wired)
            LibraryFilterMode.TRACKING -> filtered
            LibraryFilterMode.ALL -> filtered
        }

        // Sort
        return when (params.sortMode) {
            LibrarySortMode.ALPHABETICAL -> filtered.sortedBy { it.title }
            LibrarySortMode.LAST_READ -> filtered.sortedByDescending { it.lastRead ?: 0L }
            // TODO: Add dateAdded to Manga model to implement DATE_ADDED sort properly
            LibrarySortMode.DATE_ADDED -> filtered.sortedBy { it.title }
            LibrarySortMode.UNREAD_COUNT -> filtered.sortedByDescending { it.unreadCount }
            LibrarySortMode.SOURCE -> filtered.sortedBy { it.sourceId }
        }
    }

    private fun onMangaClick(mangaId: Long) {
        if (_state.value.selectedManga.isNotEmpty()) {
            toggleSelection(mangaId)
        } else {
            viewModelScope.launch {
                _effect.emit(LibraryEffect.NavigateToManga(mangaId))
            }
        }
    }

    private fun onMangaLongClick(mangaId: Long) {
        toggleSelection(mangaId)
    }

    private fun toggleSelection(mangaId: Long) {
        _state.update { state ->
            val currentSelection = state.selectedManga
            val newSelection = if (currentSelection.contains(mangaId)) {
                currentSelection - mangaId
            } else {
                currentSelection + mangaId
            }
            state.copy(selectedManga = newSelection)
        }
    }

    private fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    private fun onCategorySelected(categoryId: Long?) {
        _state.update { it.copy(selectedCategory = categoryId) }
    }

    private fun clearSelection() {
        _state.update { it.copy(selectedManga = emptySet()) }
    }

    private fun toggleFavorite(mangaId: Long) {
        viewModelScope.launch {
            toggleFavoriteManga(mangaId)
        }
    }

    private fun onFilterHasNotes(enabled: Boolean) {
        _state.update { it.copy(filterHasNotes = enabled) }
    }

    private fun onSetSortMode(mode: LibrarySortMode) {
        viewModelScope.launch {
            libraryPreferences.setLibrarySortMode(mode.ordinal)
        }
    }

    private fun onSetFilterMode(mode: LibraryFilterMode) {
        viewModelScope.launch {
            libraryPreferences.setLibraryFilterMode(mode.ordinal)
        }
    }

    private fun onSetFilterSource(sourceId: Long?) {
        viewModelScope.launch {
            libraryPreferences.setLibraryFilterSourceId(sourceId)
        }
    }

    private fun onToggleNsfw(show: Boolean) {
        viewModelScope.launch {
            generalPreferences.setShowNsfwContent(show)
        }
    }

    private fun Manga.toLibraryItem() = LibraryMangaItem(
        id = id,
        title = title,
        thumbnailUrl = thumbnailUrl,
        unreadCount = unreadCount,
        isFavorite = favorite,
        hasNote = !notes.isNullOrBlank(),
        sourceId = sourceId,
        isDownloaded = false, // TODO: Check download status
        hasTracking = false, // TODO: Check tracking status
        isNsfw = false, // TODO: Derive from source/extension NSFW flag
        lastRead = lastRead,
        status = status
    )
}
