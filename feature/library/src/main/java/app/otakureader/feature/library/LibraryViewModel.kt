package app.otakureader.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.core.preferences.ReadingGoalPreferences
import app.otakureader.core.ui.selection.SelectionManager
import app.otakureader.domain.repository.CategoryRepository
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.ReaderSettingsRepository
import app.otakureader.domain.repository.ReadingListRepository
import app.otakureader.domain.repository.StatisticsRepository
import app.otakureader.domain.scheduler.LibraryUpdateScheduler
import app.otakureader.domain.tracking.TrackRepository
import app.otakureader.domain.usecase.GetCategoriesUseCase
import app.otakureader.domain.usecase.GetContinueReadingUseCase
import app.otakureader.domain.usecase.GetLibraryMangaUseCase
import app.otakureader.domain.usecase.GetRecommendationsUseCase
import app.otakureader.domain.usecase.SearchLibraryMangaUseCase
import app.otakureader.domain.usecase.ToggleFavoriteMangaUseCase
import app.otakureader.core.extension.domain.repository.ExtensionRepository
import app.otakureader.domain.repository.EhFavoritesRepository
import app.otakureader.domain.repository.PageBookmarkRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.repository.associateBySourceKey
import app.otakureader.domain.repository.downloadFolderNameFor
import app.otakureader.domain.usecase.SyncEhFavoritesUseCase
import app.otakureader.domain.usecase.SyncLibraryUseCase
import app.otakureader.domain.usecase.downloads.ReindexDownloadsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import app.otakureader.domain.model.SavedLibraryView
import javax.inject.Inject
import app.otakureader.feature.library.R
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Suppress("LargeClass")
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getLibraryManga: GetLibraryMangaUseCase,
    private val searchLibraryManga: SearchLibraryMangaUseCase,
    private val toggleFavoriteManga: ToggleFavoriteMangaUseCase,
    private val libraryPreferences: LibraryPreferences,
    private val generalPreferences: GeneralPreferences,
    private val chapterRepository: ChapterRepository,
    private val mangaRepository: MangaRepository,
    private val downloadRepository: DownloadRepository,
    private val settingsRepository: ReaderSettingsRepository,
    private val trackRepository: TrackRepository,
    private val categoryRepository: CategoryRepository,
    private val getCategories: GetCategoriesUseCase,
    private val getContinueReading: GetContinueReadingUseCase,
    private val readingGoalPreferences: ReadingGoalPreferences,
    private val statisticsRepository: StatisticsRepository,
    private val readingListRepository: ReadingListRepository,
    private val getRecommendations: GetRecommendationsUseCase,
    private val libraryUpdateScheduler: LibraryUpdateScheduler,
    private val reindexDownloads: ReindexDownloadsUseCase,
    private val syncEhFavorites: SyncEhFavoritesUseCase,
    private val ehFavoritesRepository: EhFavoritesRepository,
    private val syncLibrary: SyncLibraryUseCase,
    private val pageBookmarkRepository: PageBookmarkRepository,
    private val sourceRepository: SourceRepository,
    private val extensionRepository: ExtensionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    /** Atomic selection manager — keys are manga IDs. */
    private val selection = SelectionManager<Long>()

    /** Stable seed for RANDOM sort — fixed for the lifetime of this ViewModel so items don't
     *  re-shuffle on unrelated state changes (e.g. download count updates). */
    private val randomSeed: Long = System.currentTimeMillis()

    private val _effect = Channel<LibraryEffect>(Channel.BUFFERED)
    val effect: Flow<LibraryEffect> = _effect.receiveAsFlow()

    /** Holds the full, unfiltered library items for reactive filtering. */
    private val _allItems = MutableStateFlow<List<LibraryMangaItem>>(emptyList())

    /** IDs of manga matching the current search query; null when no search is active. */
    private val _searchMatchingIds = MutableStateFlow<Set<Long>?>(null)
    private var searchJob: Job? = null
    private var syncEhJob: Job? = null
    private var syncJob: Job? = null

    init {
        loadLibrary()
        loadCategories()
        observeLibraryPreferences()
        observeFilteredItems()
        observeNewUpdatesCount()
        observeContinueReading()
        observeGoalProgress()
        observeReadingLists()
        observeDownloadCounts()
        observeIncognitoMode()
        // Sync selection manager into state so UI recomposes
        selection.selected
            .onEach { ids -> _state.update { it.copy(selectedManga = ids) } }
            .launchIn(viewModelScope)
        observeRecommendations()
        observeSavedViews()
        observeDisplayName()
        observeBookmarkedMangaIds()
    }

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.Refresh, is LibraryEvent.OnMangaClick,
            is LibraryEvent.OnMangaLongClick, is LibraryEvent.ContinueReadingClick -> handleNavEvent(event)
            is LibraryEvent.OnSearchQueryChange, is LibraryEvent.ToggleSearchBar,
            is LibraryEvent.OnCategorySelected, is LibraryEvent.ClearSelection,
            is LibraryEvent.SelectAllManga, is LibraryEvent.InvertSelection -> handleUiEvent(event)
            is LibraryEvent.FilterHasNotes, is LibraryEvent.SetSortMode, is LibraryEvent.SetFilterMode,
            is LibraryEvent.SetFilterSource, is LibraryEvent.ToggleNsfw, is LibraryEvent.SetFilterReadingList,
            is LibraryEvent.SetGenreFilter, is LibraryEvent.SetSortAscending,
            is LibraryEvent.ClearAllFilters,
            is LibraryEvent.SetFilterDownloaded, is LibraryEvent.SetFilterUnread,
            is LibraryEvent.SetFilterStarted, is LibraryEvent.SetFilterBookmarked,
            is LibraryEvent.SetFilterTracking,
            is LibraryEvent.SetFilterCompleted -> handleFilterSortEvent(event)
            is LibraryEvent.ToggleFilterSheet -> _state.update { it.copy(showBottomSheet = !it.showBottomSheet) }
            is LibraryEvent.ToggleBottomSheet -> _state.update { it.copy(showBottomSheet = !it.showBottomSheet) }
            is LibraryEvent.SetBottomSheetTab -> _state.update { it.copy(bottomSheetTab = event.tab) }
            is LibraryEvent.SetGroupType -> viewModelScope.launch { libraryPreferences.setGroupType(event.type) }
            is LibraryEvent.SetGridSize, is LibraryEvent.SetShowBadges,
            is LibraryEvent.SetShowDownloadBadge, is LibraryEvent.SetStaggeredGrid,
            is LibraryEvent.SetShowTitle, is LibraryEvent.SetDisplayMode,
            is LibraryEvent.SetShowCategoryTabs,
            is LibraryEvent.SetShowCategoryItemCount,
            is LibraryEvent.SetShowContinueReadingButton,
            is LibraryEvent.SetPortraitColumns,
            is LibraryEvent.SetLandscapeColumns -> handleDisplayEvent(event)
            is LibraryEvent.ToggleIncognito -> toggleIncognitoMode()
            is LibraryEvent.DismissRecommendation -> dismissRecommendation(event.mangaId)
            is LibraryEvent.ToggleAdvancedSearch -> _state.update { it.copy(showAdvancedSearch = !it.showAdvancedSearch) }
            is LibraryEvent.ApplyAdvancedSearch -> applyAdvancedSearch(event.authorQuery, event.tagQuery)
            is LibraryEvent.ToggleFavorite, is LibraryEvent.MarkSelectedAsRead,
            is LibraryEvent.MarkSelectedAsUnread, is LibraryEvent.RemoveSelectedFromLibrary,
            is LibraryEvent.DownloadSelected, is LibraryEvent.MarkSelectedAsCompleted,
            is LibraryEvent.MarkSelectedAsDropped, is LibraryEvent.ShareSelectedManga,
            is LibraryEvent.ViewSelectedManga, is LibraryEvent.UndoLibraryDelete,
            is LibraryEvent.OpenMoveToCategoryDialog, is LibraryEvent.DismissMoveToCategoryDialog,
            is LibraryEvent.MoveToCategory, is LibraryEvent.MigrateSelected,
            is LibraryEvent.UpdateSelected -> handleActionEvent(event)
            is LibraryEvent.UpdateLibrary, is LibraryEvent.UpdateCategory,
            is LibraryEvent.OpenRandomEntry, is LibraryEvent.ReindexDownloads,
            is LibraryEvent.SyncEhFavorites,
            is LibraryEvent.SyncLibrary -> handleNewEvent(event)
            is LibraryEvent.ShowSaveViewDialog, is LibraryEvent.HideSaveViewDialog,
            is LibraryEvent.UpdateSaveViewName, is LibraryEvent.ConfirmSaveView,
            is LibraryEvent.ApplySavedView, is LibraryEvent.DeleteSavedView -> handleSavedViewEvent(event)
            is LibraryEvent.SelectMangaFromMenu -> selection.toggle(event.mangaId)
        }
    }

    /** Persists display-layout preference changes (grid size, badges, staggered/list mode). */
    private fun handleDisplayEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.SetGridSize -> viewModelScope.launch { libraryPreferences.setGridSize(event.size) }
            is LibraryEvent.SetShowBadges -> viewModelScope.launch { libraryPreferences.setShowBadges(event.enabled) }
            is LibraryEvent.SetShowDownloadBadge ->
                viewModelScope.launch { libraryPreferences.setShowDownloadBadge(event.enabled) }
            is LibraryEvent.SetShowTitle ->
                viewModelScope.launch { libraryPreferences.setShowTitle(event.show) }
            is LibraryEvent.SetStaggeredGrid ->
                viewModelScope.launch { libraryPreferences.setStaggeredGrid(event.enabled) }
            is LibraryEvent.SetDisplayMode ->
                viewModelScope.launch { libraryPreferences.setLibraryDisplayMode(event.mode.ordinal) }
            is LibraryEvent.SetShowCategoryTabs ->
                viewModelScope.launch { libraryPreferences.setShowCategoryTabs(event.enabled) }
            is LibraryEvent.SetShowCategoryItemCount ->
                viewModelScope.launch { libraryPreferences.setShowCategoryItemCount(event.enabled) }
            is LibraryEvent.SetShowContinueReadingButton ->
                viewModelScope.launch { libraryPreferences.setShowContinueReadingButton(event.enabled) }
            is LibraryEvent.SetPortraitColumns ->
                viewModelScope.launch { libraryPreferences.setPortraitColumns(event.count) }
            is LibraryEvent.SetLandscapeColumns ->
                viewModelScope.launch { libraryPreferences.setLandscapeColumns(event.count) }
            else -> Unit
        }
    }

    private fun handleNavEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.Refresh -> loadLibrary()
            is LibraryEvent.OnMangaClick -> onMangaClick(event.mangaId)
            is LibraryEvent.OnMangaLongClick -> onMangaLongClick(event.mangaId)
            is LibraryEvent.ContinueReadingClick -> onContinueReadingClick(event.mangaId)
            else -> Unit
        }
    }

    private fun handleUiEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.OnSearchQueryChange -> onSearchQueryChange(event.query)
            is LibraryEvent.ToggleSearchBar -> toggleSearchBar()
            is LibraryEvent.OnCategorySelected -> onCategorySelected(event.categoryId)
            is LibraryEvent.ClearSelection -> clearSelection()
            is LibraryEvent.SelectAllManga -> selection.setAll(_state.value.mangaList.map { it.id }.toSet())
            is LibraryEvent.InvertSelection -> selection.invert(_state.value.mangaList.map { it.id })
            else -> Unit
        }
    }

    private fun handleFilterSortEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.FilterHasNotes -> onFilterHasNotes(event.enabled)
            is LibraryEvent.SetSortMode -> onSetSortMode(event.mode)
            is LibraryEvent.SetFilterMode -> onSetFilterMode(event.mode)
            is LibraryEvent.SetFilterSource -> onSetFilterSource(event.sourceId)
            is LibraryEvent.ToggleNsfw -> onToggleNsfw(event.show)
            is LibraryEvent.SetFilterReadingList -> onSetFilterReadingList(event.listId)
            is LibraryEvent.SetGenreFilter -> _state.update { it.copy(filterGenres = event.genres) }
            is LibraryEvent.SetSortAscending -> _state.update { it.copy(sortAscending = event.ascending) }
            is LibraryEvent.SetFilterDownloaded -> _state.update { it.copy(filterDownloaded = event.state) }
            is LibraryEvent.SetFilterUnread -> _state.update { it.copy(filterUnread = event.state) }
            is LibraryEvent.SetFilterStarted -> _state.update { it.copy(filterStarted = event.state) }
            is LibraryEvent.SetFilterBookmarked -> _state.update { it.copy(filterBookmarked = event.state) }
            is LibraryEvent.SetFilterTracking -> _state.update { it.copy(filterTracking = event.state) }
            is LibraryEvent.SetFilterCompleted -> _state.update { it.copy(filterCompleted = event.state) }
            is LibraryEvent.ClearAllFilters -> {
                viewModelScope.launch { generalPreferences.setDownloadedOnly(false) }
                _state.update {
                    it.copy(
                        selectedCategory = null,
                        filterMode = LibraryFilterMode.ALL,
                        filterGenres = emptySet(),
                        filterHasNotes = false,
                        filterSourceId = null,
                        filterReadingListId = null,
                        sortAscending = true,
                        filterDownloaded = LibraryTriState.DISABLED,
                        filterUnread = LibraryTriState.DISABLED,
                        filterStarted = LibraryTriState.DISABLED,
                        filterBookmarked = LibraryTriState.DISABLED,
                        filterTracking = LibraryTriState.DISABLED,
                        filterCompleted = LibraryTriState.DISABLED,
                    )
                }
            }
            else -> Unit
        }
    }

    private fun handleActionEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.ToggleFavorite -> toggleFavorite(event.mangaId)
            is LibraryEvent.MarkSelectedAsRead -> markSelectedAsRead()
            is LibraryEvent.MarkSelectedAsUnread -> markSelectedAsUnread()
            is LibraryEvent.RemoveSelectedFromLibrary -> removeSelectedFromLibrary()
            is LibraryEvent.DownloadSelected -> downloadSelected()
            is LibraryEvent.MarkSelectedAsCompleted -> markSelectedAsCompleted()
            is LibraryEvent.MarkSelectedAsDropped -> markSelectedAsDropped()
            is LibraryEvent.ShareSelectedManga -> shareSelectedManga()
            is LibraryEvent.ViewSelectedManga -> viewSelectedManga()
            is LibraryEvent.UndoLibraryDelete -> undoLibraryDelete(event.mangaIds)
            is LibraryEvent.OpenMoveToCategoryDialog -> _state.update {
                it.copy(showMoveToCategoryDialog = true, moveToCategoryMangaIds = it.selectedManga)
            }
            is LibraryEvent.DismissMoveToCategoryDialog -> _state.update {
                it.copy(showMoveToCategoryDialog = false, moveToCategoryMangaIds = emptySet())
            }
            is LibraryEvent.MoveToCategory -> moveMangaToCategory(event.mangaIds, event.categoryId)
            is LibraryEvent.MigrateSelected -> migrateSelected()
            is LibraryEvent.UpdateSelected -> updateSelected()
            else -> Unit
        }
    }

    private fun updateSelected() {
        if (selection.snapshotAndClear().isEmpty()) return
        viewModelScope.launch {
            libraryUpdateScheduler.enqueueNow()
            _effect.send(LibraryEffect.ShowSnackbar(R.string.library_update_started))
        }
    }

    private fun migrateSelected() {
        val ids = selection.snapshotAndClear().sorted()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _effect.send(LibraryEffect.NavigateToMigration(ids))
        }
    }

    private fun moveMangaToCategory(mangaIds: Set<Long>, categoryId: Long) {
        viewModelScope.launch {
            val results = coroutineScope {
                mangaIds.map { mangaId ->
                    async { runCatching { categoryRepository.addMangaToCategory(mangaId, categoryId) } }
                }.awaitAll()
            }
            val allSucceeded = results.all { it.isSuccess }
            _state.update { it.copy(showMoveToCategoryDialog = false, moveToCategoryMangaIds = emptySet()) }
            if (allSucceeded) {
                selection.clear()
                _state.update { it.copy(selectedManga = emptySet()) }
                _effect.send(LibraryEffect.ShowSnackbar(R.string.library_moved_to_category))
            } else {
                _effect.send(LibraryEffect.ShowSnackbar(R.string.library_move_to_category_error))
            }
        }
    }

    private fun handleNewEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.UpdateLibrary -> viewModelScope.launch {
                libraryUpdateScheduler.enqueueNow()
                _effect.send(LibraryEffect.ShowSnackbar(R.string.library_update_started))
            }
            is LibraryEvent.UpdateCategory -> viewModelScope.launch {
                libraryUpdateScheduler.enqueueNow()
                _effect.send(LibraryEffect.ShowSnackbar(R.string.library_update_full_started))
            }
            is LibraryEvent.OpenRandomEntry -> {
                val currentList = _state.value.mangaList
                if (currentList.isNotEmpty()) {
                    val random = currentList.random()
                    viewModelScope.launch { _effect.send(LibraryEffect.NavigateToManga(random.id)) }
                }
            }
            is LibraryEvent.ReindexDownloads -> viewModelScope.launch {
                val result = reindexDownloads()
                _effect.send(
                    LibraryEffect.ShowSnackbar(
                        R.string.library_reindex_complete,
                        formatArgs = listOf(result.verifiedDownloads)
                    )
                )
            }
            is LibraryEvent.SyncEhFavorites -> handleSyncEhFavorites()
            is LibraryEvent.SyncLibrary -> handleSyncLibrary()
            else -> Unit
        }
    }

    private fun handleSyncLibrary() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            if (_state.value.incognitoMode) {
                _effect.send(LibraryEffect.ShowSnackbar(R.string.library_sync_incognito_blocked))
                return@launch
            }
            _effect.send(LibraryEffect.ShowSnackbar(R.string.library_sync_started))
            try {
                val summary = syncLibrary()
                val hasPending = summary.attempted > 0
                val hasIssues = hasPending && (summary.conflicts > 0 || summary.failed > 0)
                val msgRes = when {
                    !hasPending -> R.string.library_sync_nothing_pending
                    hasIssues -> R.string.library_sync_complete_with_issues
                    else -> R.string.library_sync_complete
                }
                val args = when {
                    hasIssues -> listOf(summary.successful, summary.attempted, summary.conflicts, summary.failed)
                    hasPending -> listOf(summary.successful, summary.attempted)
                    else -> emptyList()
                }
                _effect.send(LibraryEffect.ShowSnackbar(msgRes, formatArgs = args))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(LibraryEffect.ShowSnackbar(R.string.library_sync_failed))
            }
        }
    }

    // --- Saved views (#1039) ---

    /**
     * Keeps [LibraryState.savedViews] in sync with the DataStore list.
     * Called from [init] so the list is always fresh when the screen opens.
     */
    private fun observeSavedViews() {
        libraryPreferences.savedViewsJson
            .map { json -> runCatching { Json.decodeFromString<List<SavedLibraryView>>(json) }.getOrDefault(emptyList()) }
            .onEach { views -> _state.update { it.copy(savedViews = views) } }
            .launchIn(viewModelScope)
    }

    /**
     * Handles all events related to named saved views.
     *
     * - [LibraryEvent.ShowSaveViewDialog] / [LibraryEvent.HideSaveViewDialog]: toggle the
     *   name-entry dialog and reset the draft name.
     * - [LibraryEvent.UpdateSaveViewName]: typing in the name field updates the draft.
     * - [LibraryEvent.ConfirmSaveView]: snapshots current filter+sort, appends it to the
     *   persisted list, then hides the dialog.
     * - [LibraryEvent.ApplySavedView]: restores filter+sort from a saved view.
     * - [LibraryEvent.DeleteSavedView]: removes a view by ID and persists the updated list.
     */
    private fun handleSavedViewEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.ShowSaveViewDialog ->
                _state.update { it.copy(showSaveViewDialog = true, saveViewName = "") }
            is LibraryEvent.HideSaveViewDialog ->
                _state.update { it.copy(showSaveViewDialog = false, saveViewName = "") }
            is LibraryEvent.UpdateSaveViewName ->
                _state.update { it.copy(saveViewName = event.name) }
            is LibraryEvent.ConfirmSaveView -> confirmSaveView()
            is LibraryEvent.ApplySavedView -> applySavedView(event.view)
            is LibraryEvent.DeleteSavedView -> deleteSavedView(event.id)
            else -> Unit
        }
    }

    private fun confirmSaveView() {
        val state = _state.value
        val name = state.saveViewName.trim()
        if (name.isBlank()) return
        val newView = SavedLibraryView(
            name = name,
            sortField = state.sortMode.ordinal,
            sortAscending = state.sortAscending,
            filterMode = state.filterMode.ordinal,
            filterGenres = state.filterGenres.toList(),
            filterHasNotes = state.filterHasNotes,
        )
        viewModelScope.launch {
            val updated = _state.value.savedViews + newView
            libraryPreferences.setSavedViewsJson(Json.encodeToString(updated))
            _state.update { it.copy(showSaveViewDialog = false, saveViewName = "") }
        }
    }

    private fun applySavedView(view: SavedLibraryView) {
        viewModelScope.launch {
            libraryPreferences.setLibrarySortMode(view.sortField)
            libraryPreferences.setLibraryFilterMode(view.filterMode)
        }
        _state.update {
            it.copy(
                sortAscending = view.sortAscending,
                filterGenres = view.filterGenres.toSet(),
                filterHasNotes = view.filterHasNotes,
            )
        }
    }

    private fun deleteSavedView(id: String) {
        viewModelScope.launch {
            val updated = _state.value.savedViews.filter { it.id != id }
            libraryPreferences.setSavedViewsJson(Json.encodeToString(updated))
        }
    }

    private fun handleSyncEhFavorites() {
        if (syncEhJob?.isActive == true) return
        syncEhJob = viewModelScope.launch {
            if (!ehFavoritesRepository.isConfigured()) {
                _effect.send(LibraryEffect.ShowSnackbar(R.string.library_eh_sync_not_configured))
                return@launch
            }
            _effect.send(LibraryEffect.ShowSnackbar(R.string.library_eh_sync_started))
            try {
                val result = syncEhFavorites()
                val msgRes = if (result.added > 0) {
                    R.string.library_eh_sync_complete
                } else {
                    R.string.library_eh_sync_nothing_new
                }
                val args = if (result.added > 0) listOf(result.added) else emptyList<Any>()
                _effect.send(LibraryEffect.ShowSnackbar(msgRes, formatArgs = args))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(LibraryEffect.ShowSnackbar(R.string.library_eh_sync_failed))
            }
        }
    }

    private fun observeLibraryPreferences() {
        observeDisplayPreferences()
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
            .onEach { id -> _state.update { it.copy(filterSourceId = id) } }
            .launchIn(viewModelScope)
        generalPreferences.showNsfwContent
            .onEach { show -> _state.update { it.copy(showNsfw = show) } }
            .launchIn(viewModelScope)
        generalPreferences.downloadedOnly
            .onEach { v -> _state.update { it.copy(downloadedOnly = v) } }
            .launchIn(viewModelScope)
        generalPreferences.visualEffectsEnabled
            .onEach { enabled -> _state.update { it.copy(visualEffectsEnabled = enabled) } }
            .launchIn(viewModelScope)
        libraryUpdateScheduler.isUpdating()
            .onEach { updating -> _state.update { it.copy(isLibraryUpdating = updating) } }
            .launchIn(viewModelScope)
        libraryPreferences.groupType
            .onEach { v -> _state.update { it.copy(groupType = v) } }
            .launchIn(viewModelScope)
    }

    private fun observeDisplayPreferences() {
        libraryPreferences.gridSize
            .onEach { gridSize -> _state.update { it.copy(gridSize = gridSize) } }
            .launchIn(viewModelScope)
        libraryPreferences.portraitColumns
            .onEach { count -> _state.update { it.copy(portraitColumns = count) } }
            .launchIn(viewModelScope)
        libraryPreferences.landscapeColumns
            .onEach { count -> _state.update { it.copy(landscapeColumns = count) } }
            .launchIn(viewModelScope)
        libraryPreferences.showBadges
            .onEach { show -> _state.update { it.copy(showBadges = show) } }
            .launchIn(viewModelScope)
        libraryPreferences.showDownloadBadge
            .onEach { show -> _state.update { it.copy(showDownloadBadge = show) } }
            .launchIn(viewModelScope)
        libraryPreferences.showTitle
            .onEach { show -> _state.update { it.copy(showTitle = show) } }
            .launchIn(viewModelScope)
        libraryPreferences.isStaggeredGrid
            .onEach { staggered -> _state.update { it.copy(isStaggeredGrid = staggered) } }
            .launchIn(viewModelScope)
        libraryPreferences.libraryDisplayMode
            .onEach { modeInt ->
                _state.update {
                    it.copy(displayMode = LibraryDisplayMode.entries.getOrElse(modeInt) { LibraryDisplayMode.GRID })
                }
            }
            .launchIn(viewModelScope)
        libraryPreferences.showCategoryTabs
            .onEach { show -> _state.update { it.copy(showCategoryTabs = show) } }
            .launchIn(viewModelScope)
        libraryPreferences.showCategoryItemCount
            .onEach { show -> _state.update { it.copy(showCategoryItemCount = show) } }
            .launchIn(viewModelScope)
        libraryPreferences.showContinueReadingButton
            .onEach { show -> _state.update { it.copy(showContinueReadingButton = show) } }
            .launchIn(viewModelScope)
    }

    private fun observeNewUpdatesCount() {
        generalPreferences.lastUpdatesViewedAt
            .flatMapLatest { since -> chapterRepository.countNewUpdatesSince(since) }
            .onEach { count -> _state.update { it.copy(newUpdatesCount = count) } }
            .launchIn(viewModelScope)
    }

    /**
     * Keyed by the same keys a manga row can store, not by [ExtensionSource.id].
     *
     * `ExtensionSource.id` is the raw Tachiyomi source id; the key on a manga row is that id
     * stringified and hashed, which is what `TachiyomiSourceAdapter` exposes as its `id` and what
     * `toSourceId` then derives. Keying by the raw id meant no library entry ever matched, so no
     * source icons were shown.
     */
    private suspend fun buildSourceIconUrlMap(): Map<Long, String?> =
        extensionRepository.getInstalledExtensions().first()
            .flatMap { ext -> ext.sources.map { src -> src to ext.iconUrl } }
            .associateBySourceKey { (src, _) -> src.id.toString() }
            .mapValues { (_, entry) -> entry.second }

    private fun loadLibrary() {
        val isRefreshing = _state.value.mangaList.isNotEmpty()
        _state.update { it.copy(isLoading = !isRefreshing, isRefreshing = isRefreshing) }

        getLibraryManga()
            .map { mangaList ->
                coroutineScope {
                    // Build source metadata (name + lang) lookup in parallel
                    val sourceMetaDeferred = async {
                        // Indexed by every key a manga row can hold, matching getSourceByKey.
                        // This used to parse the id as a number and nothing else, which both
                        // dropped every non-numeric source and, for numeric ones, produced a key
                        // no manga row uses — so `sourceName` came back blank for the whole
                        // library and "group by source" showed numbers.
                        sourceRepository.getSources().first()
                            .associateBySourceKey { it.id }
                            .mapValues { (_, source) -> Pair(source.name, source.lang) }
                    }

                    val sourceIconUrlDeferred = async { buildSourceIconUrlMap() }

                    // One query for the whole library instead of a per-manga tracking check
                    val globalTrackedIdsDeferred = async {
                        trackRepository.observeMangaIdsWithTrackEntries().first()
                    }

                    // Resolve each manga's download-folder key in parallel, then one
                    // filesystem walk for the whole library instead of a per-manga check
                    val mangaKeysDeferred = async {
                        mangaList.associate { manga ->
                            manga.id to (downloadFolderNameFor(manga.sourceId) to manga.title)
                        }
                    }

                    val sourceMeta = sourceMetaDeferred.await()
                    val sourceIconUrls = sourceIconUrlDeferred.await()
                    val globalTrackedIds = globalTrackedIdsDeferred.await()
                    val mangaKeys = mangaKeysDeferred.await()
                    val trackedMangaIds = mangaList.map { it.id }.filter { it in globalTrackedIds }.toSet()
                    val downloadedMangaIds = downloadRepository.getMangaIdsWithDownloads(mangaKeys)

                    mangaList.map { manga ->
                        val meta = sourceMeta[manga.sourceId]
                        manga.toLibraryItem(
                            isDownloaded = manga.id in downloadedMangaIds,
                            hasTracking = manga.id in trackedMangaIds,
                            sourceName = meta?.first ?: "",
                            sourceLanguage = meta?.second ?: "",
                            sourceIconUrl = sourceIconUrls[manga.sourceId],
                        )
                    }
                }
            }
            .onEach { items ->
                _allItems.value = items
                val genres = items.flatMap { it.genres }.distinct().sorted()
                _state.update { it.copy(isLoading = false, isRefreshing = false, error = null, availableGenres = genres) }
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

    private fun loadCategories() {
        viewModelScope.launch {
            getCategories()
                .collect { categories ->
                    val items = categories.map { category ->
                        CategoryItem(
                            id = category.id,
                            name = category.name,
                            count = category.mangaCount
                        )
                    }
                    // If the previously selected category was deleted, clear the selection so
                    // the tab row and filter both reflect the same "All" state.
                    val validIds = items.mapTo(HashSet()) { it.id }
                    _state.update { state ->
                        val validatedCategory = state.selectedCategory?.takeIf { it in validIds }
                        state.copy(categories = items, selectedCategory = validatedCategory)
                    }
                }
        }
    }

    private fun observeFilteredItems() {
        // When category selection changes, fetch the manga IDs in that category (BY_DEFAULT mode only)
        viewModelScope.launch {
            _state.map { it.selectedCategory to it.groupType }
                .distinctUntilChanged()
                .collect { (categoryId, groupType) ->
                    if (categoryId != null && groupType == LibraryGroup.BY_DEFAULT) {
                        val mangaIds = getCategories.getMangaIdsForCategory(categoryId).first()
                        _state.update { it.copy(categoryFilterMangaIds = mangaIds.toSet()) }
                    } else {
                        _state.update { it.copy(categoryFilterMangaIds = emptySet()) }
                    }
                }
        }

        // When reading list filter changes, reactively track the manga IDs in that list
        _state.map { it.filterReadingListId }
            .distinctUntilChanged()
            .flatMapLatest { listId ->
                if (listId != null) {
                    readingListRepository.getListWithManga(listId)
                        .map { (_, items) -> items.map { it.manga.id }.toSet() }
                } else {
                    flowOf(emptySet())
                }
            }
            .onEach { ids -> _state.update { it.copy(readingListMangaIds = ids) } }
            .launchIn(viewModelScope)

        // Combine all items with filter params (now including category, reading list, and search result IDs from state)
        combine(
            _allItems,
            _searchMatchingIds,
            _state.map {
                FilterSortParams(
                    query = it.searchQuery,
                    searchMatchingIds = null, // populated by combine below
                    filterHasNotes = it.filterHasNotes,
                    sortMode = it.sortMode,
                    filterMode = it.filterMode,
                    filterSourceId = it.filterSourceId,
                    showNsfw = it.showNsfw,
                    selectedCategory = it.selectedCategory,
                    categoryMangaIds = it.categoryFilterMangaIds,
                    filterReadingListId = it.filterReadingListId,
                    readingListMangaIds = it.readingListMangaIds,
                    filterGenres = it.filterGenres,
                    sortAscending = it.sortAscending,
                    filterDownloaded = if (it.downloadedOnly) LibraryTriState.ENABLED_IS else it.filterDownloaded,
                    filterUnread = it.filterUnread,
                    filterStarted = it.filterStarted,
                    filterBookmarked = it.filterBookmarked,
                    filterTracking = it.filterTracking,
                    filterCompleted = it.filterCompleted,
                    bookmarkedMangaIds = it.bookmarkedMangaIds,
                    randomSeed = randomSeed,
                    groupType = it.groupType,
                )
            }.distinctUntilChanged()
        ) { items, matchingIds, params ->
            val filtered = applyFiltersAndSort(items, params.copy(searchMatchingIds = matchingIds))
            Triple(filtered, items.size, items)
        }
            .onEach { (filtered, totalCount, unfiltered) ->
                _state.update { it.copy(mangaList = filtered, totalMangaCount = totalCount, allMangaList = unfiltered) }
            }
            .launchIn(viewModelScope)
    }

    private fun onMangaClick(mangaId: Long) {
        // Route through the authoritative SelectionManager so a tap-toggle stays consistent with
        // long-click selection. `selection.selected` is the single source of truth that drives
        // `state.selectedManga` (see init); mutating `_state` directly here diverged from it, so
        // the next `selection` emission (or a bulk action via snapshotAndClear) would silently
        // drop or revert tap-toggled items. If selection is active, toggle; otherwise navigate.
        if (selection.snapshot().isNotEmpty()) {
            selection.toggle(mangaId)
        } else {
            viewModelScope.launch {
                _effect.send(LibraryEffect.NavigateToManga(mangaId))
            }
        }
    }

    private fun onMangaLongClick(mangaId: Long) {
        selection.toggle(mangaId)
    }

    private fun onSearchQueryChange(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(searchQuery = query, isSearching = false) }
            _searchMatchingIds.value = null
            return
        }
        // Mark searching so the UI shows progress instead of a false "no results" while the
        // debounced query is in flight (the matching-id set is empty until results land).
        _state.update { it.copy(searchQuery = query, isSearching = true) }
        _searchMatchingIds.value = emptySet()
        searchJob = viewModelScope.launch {
            delay(300L)
            searchLibraryManga(query).collect { mangas ->
                _searchMatchingIds.value = mangas.map { it.id }.toSet()
                _state.update { it.copy(isSearching = false) }
            }
        }
    }

    private fun toggleSearchBar() {
        _state.update { state ->
            if (state.showSearchBar) {
                // Closing: also clear the query and dismiss any open advanced search sheet
                searchJob?.cancel()
                _searchMatchingIds.value = null
                state.copy(showSearchBar = false, searchQuery = "", showAdvancedSearch = false)
            } else {
                state.copy(showSearchBar = true)
            }
        }
    }

    private fun onCategorySelected(categoryId: Long?) {
        _state.update { it.copy(selectedCategory = categoryId) }
    }

    private fun clearSelection() {
        selection.clear()
    }

    private fun markSelectedAsRead() = markChaptersForSelectedManga(read = true)

    private fun markSelectedAsUnread() = markChaptersForSelectedManga(read = false)

    private fun markChaptersForSelectedManga(read: Boolean) {
        val mangaIds = selection.snapshotAndClear()
        if (mangaIds.isEmpty()) return
        viewModelScope.launch {
            val chapterIds = chapterRepository.getChaptersByMangaIdsSync(mangaIds).map { it.id }
            if (chapterIds.isNotEmpty()) {
                chapterRepository.updateChapterProgress(chapterIds, read = read, lastPageRead = 0)
            }
        }
    }

    private fun removeSelectedFromLibrary() {
        val ids = selection.snapshotAndClear()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val deletedIds = mutableSetOf<Long>()
            ids.forEach { mangaId ->
                try {
                    toggleFavoriteManga(mangaId)
                    deletedIds.add(mangaId)
                }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { }
            }
            if (deletedIds.isNotEmpty()) {
                _effect.send(LibraryEffect.ShowUndoLibraryDelete(count = deletedIds.size, mangaIds = deletedIds))
            }
        }
    }

    private fun undoLibraryDelete(mangaIds: Set<Long>) {
        viewModelScope.launch {
            mangaIds.forEach { mangaId ->
                try { toggleFavoriteManga(mangaId) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { }
            }
        }
    }

    private fun downloadSelected() {
        val mangaIds = selection.snapshotAndClear()
        if (mangaIds.isEmpty()) return
        viewModelScope.launch {
            val mangaById = _allItems.value.associateBy { it.id }
            mangaIds.forEach { mangaId ->
                val manga = mangaById[mangaId] ?: return@forEach
                val chapters = chapterRepository.getChaptersByMangaId(mangaId).first()
                // Must match the folder-name resolution used everywhere else; a mismatch here
                // stores files under a path isChapterDownloaded()/deleteChapterDownload() would
                // never find when checking under the resolved name.
                val sourceName = downloadFolderNameFor(manga.sourceId)
                chapters.filter { !it.read }.forEach { chapter ->
                    downloadRepository.enqueueChapter(
                        mangaId = mangaId,
                        chapterId = chapter.id,
                        sourceName = sourceName,
                        mangaTitle = manga.title,
                        chapterTitle = chapter.name
                    )
                }
            }
        }
    }

    private fun markSelectedAsCompleted() {
        val mangaIds = selection.snapshotAndClear()
        if (mangaIds.isEmpty()) return
        viewModelScope.launch {
            mangaIds.forEach { mangaRepository.markUserCompleted(it, completed = true) }
        }
    }

    private fun markSelectedAsDropped() {
        val mangaIds = selection.snapshotAndClear()
        if (mangaIds.isEmpty()) return
        viewModelScope.launch {
            mangaIds.forEach { mangaRepository.markUserDropped(it, dropped = true) }
        }
    }

    private fun shareSelectedManga() {
        val mangaId = _state.value.selectedManga.singleOrNull() ?: return
        selection.clear()
        viewModelScope.launch {
            val manga = mangaRepository.getMangaById(mangaId) ?: return@launch
            val url = manga.url.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: ""
            _effect.send(LibraryEffect.ShareManga(title = manga.title, url = url))
        }
    }

    private fun viewSelectedManga() {
        val mangaId = _state.value.selectedManga.singleOrNull() ?: return
        selection.clear()
        viewModelScope.launch { _effect.send(LibraryEffect.NavigateToManga(mangaId)) }
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

    private fun observeContinueReading() {
        getContinueReading()
            .onEach { items -> _state.update { it.copy(continueReadingItems = items) } }
            .catch { e -> android.util.Log.w("LibraryViewModel", "observeContinueReading failed", e) }
            .launchIn(viewModelScope)
    }

    private fun observeGoalProgress() {
        combine(
            readingGoalPreferences.dailyChapterGoal,
            readingGoalPreferences.weeklyChapterGoal
        ) { daily, weekly -> Pair(daily, weekly) }
            .flatMapLatest { (dailyGoal, weeklyGoal) ->
                statisticsRepository.getReadingGoalProgress(dailyGoal, weeklyGoal)
            }
            .onEach { goal -> _state.update { it.copy(readingGoal = goal) } }
            .catch { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("LibraryViewModel", "observeGoalProgress failed", e)
            }
            .launchIn(viewModelScope)
    }

    private fun onContinueReadingClick(mangaId: Long) {
        viewModelScope.launch {
            // Prefer the exact chapter the user was reading (ContinueReadingItem has both chapterId
            // and lastPageRead). Fall back to next unread in source order only when no history entry
            // exists in state (e.g. on first read before the carousel has loaded).
            val chapterId = _state.value.continueReadingItems
                .firstOrNull { it.mangaId == mangaId }
                ?.chapterId
                ?: chapterRepository.getNextUnreadChapter(mangaId)?.id
                ?: return@launch
            _effect.send(LibraryEffect.NavigateToReader(mangaId, chapterId))
        }
    }

    private fun observeReadingLists() {
        readingListRepository.getAllLists()
            .map { lists ->
                lists.map { list ->
                    ReadingListFilterItem(
                        id = list.id,
                        name = list.name,
                        count = list.itemCount
                    )
                }
            }
            .onEach { items ->
                _state.update { state ->
                    val currentListId = state.filterReadingListId
                    val stillExists = currentListId == null || items.any { it.id == currentListId }
                    state.copy(
                        readingLists = items,
                        filterReadingListId = if (stillExists) currentListId else null
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun onSetFilterReadingList(listId: Long?) {
        _state.update { it.copy(filterReadingListId = listId) }
    }

    private fun observeDownloadCounts() {
        downloadRepository.observeDownloads()
            .map { downloads ->
                downloads.groupingBy { it.mangaId }.eachCount()
            }
            .distinctUntilChanged()
            .onEach { counts -> _state.update { it.copy(downloadCountByManga = counts) } }
            .catch { e -> android.util.Log.w("LibraryViewModel", "observeDownloadCounts failed", e) }
            .launchIn(viewModelScope)
    }

    private fun observeIncognitoMode() {
        settingsRepository.incognitoMode
            .onEach { enabled -> _state.update { it.copy(incognitoMode = enabled) } }
            .launchIn(viewModelScope)
    }

    private fun toggleIncognitoMode() {
        viewModelScope.launch {
            val current = _state.value.incognitoMode
            settingsRepository.setIncognitoMode(!current)
        }
    }

    private fun observeRecommendations() {
        libraryPreferences.showRecommendations
            .onEach { show -> _state.update { it.copy(showRecommendations = show) } }
            .launchIn(viewModelScope)

        combine(
            getRecommendations(),
            libraryPreferences.dismissedRecommendations,
        ) { recs, dismissed ->
            recs.filter { it.mangaId.toString() !in dismissed }
                .map { rec ->
                    LibraryMangaItem(
                        id = rec.mangaId,
                        title = rec.title,
                        thumbnailUrl = rec.thumbnailUrl,
                        unreadCount = 0,
                        isFavorite = false,
                        sourceId = rec.sourceId,
                    )
                }
        }
            .onEach { items -> _state.update { it.copy(recommendations = items) } }
            .launchIn(viewModelScope)
    }

    private fun observeDisplayName() {
        generalPreferences.displayName
            .onEach { name -> _state.update { it.copy(displayName = name) } }
            .launchIn(viewModelScope)
    }

    private fun observeBookmarkedMangaIds() {
        pageBookmarkRepository.getMangaIdsWithBookmarks()
            .onEach { ids -> _state.update { it.copy(bookmarkedMangaIds = ids) } }
            .launchIn(viewModelScope)
    }

    private fun dismissRecommendation(mangaId: Long) {
        viewModelScope.launch {
            libraryPreferences.dismissRecommendation(mangaId)
        }
    }

    private fun applyAdvancedSearch(authorQuery: String, tagQuery: String) {
        // Strip any existing author:/tag: operators from the current query before appending new ones
        val baseQuery = _state.value.searchQuery
            .replace(Regex("""author:"[^"]*""""), "")
            .replace(Regex("""tag:"[^"]*""""), "")
            .replace(Regex("""author:\S+"""), "")
            .replace(Regex("""tag:\S+"""), "")
            .trim()

        val parts = buildList {
            if (authorQuery.isNotBlank()) {
                val value = authorQuery.trim()
                add(if (' ' in value) """author:"$value"""" else "author:$value")
            }
            if (tagQuery.isNotBlank()) {
                val value = tagQuery.trim()
                add(if (' ' in value) """tag:"$value"""" else "tag:$value")
            }
        }
        val newQuery = listOf(baseQuery).plus(parts).filter { it.isNotBlank() }.joinToString(" ").trim()
        _state.update { it.copy(showAdvancedSearch = false, searchQuery = newQuery) }
        onSearchQueryChange(newQuery)
    }

}
