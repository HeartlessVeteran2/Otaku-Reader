package app.otakureader.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.ui.selection.SelectionManager
import app.otakureader.core.extension.domain.repository.ExtensionRepository
import app.otakureader.domain.repository.ExtensionManagementRepository
import app.otakureader.domain.repository.FeedRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.usecase.library.AddMangaToLibraryUseCase
import app.otakureader.domain.usecase.ToggleFavoriteMangaUseCase
import app.otakureader.domain.usecase.source.GetLatestUpdatesUseCase
import app.otakureader.domain.usecase.source.GetPopularMangaUseCase
import app.otakureader.domain.usecase.source.GetSourceFiltersUseCase
import app.otakureader.domain.usecase.source.GetSourcesUseCase
import app.otakureader.domain.model.SavedSourceSearch
import app.otakureader.domain.usecase.SearchLibraryMangaUseCase
import app.otakureader.domain.usecase.source.SearchMangaUseCase
import app.otakureader.sourceapi.FilterList
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.SourceManga
import app.otakureader.sourceapi.toSourceId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Intermediate result of the NSFW/language/disabled-source filtering pipeline in [BrowseViewModel.init]. */
internal data class SourceFilterResult(
    val sources: List<MangaSource>,
    val availableLangs: List<String>,
    val showNsfw: Boolean,
    val enabledLangs: Set<String>,
    val disabledIds: Set<Long>,
)

/**
 * Filters [sources] by NSFW visibility, enabled languages, and individually-disabled source
 * IDs (in that order), and computes the available-languages list from the NSFW-filtered set.
 * Kept top-level (outside [BrowseViewModel]) so the class body stays under Detekt's LargeClass
 * threshold.
 */
internal fun buildSourceFilterResult(
    sources: List<MangaSource>,
    showNsfw: Boolean,
    enabledLangs: Set<String>,
    disabledIds: Set<Long>,
): SourceFilterResult {
    val nsfwFiltered = if (showNsfw) sources else sources.filter { !it.isNsfw }
    // availableLanguages = all langs from NSFW-filtered set (before language filter)
    val availableLangs = nsfwFiltered.map { it.lang }.distinct().sorted()
    val langFiltered = if (enabledLangs.isEmpty()) nsfwFiltered
        else nsfwFiltered.filter { it.lang in enabledLangs }
    val disabledFiltered = if (disabledIds.isEmpty()) langFiltered
        else langFiltered.filter { it.id.toSourceId() !in disabledIds }
    return SourceFilterResult(disabledFiltered, availableLangs, showNsfw, enabledLangs, disabledIds)
}

// Pre-existing size debt: this ViewModel already owned Sources + Extensions + Global Search +
// Filters + Pinning + Categories + Saved Searches + Health + Selection before the per-source
// disable feature (Manage Sources dialog) pushed it a little further past Detekt's LargeClass
// threshold. A real split (e.g. extracting source pinning/categories/health into a delegate)
// is worth doing but is too risky to attempt blind in this change — tracked as follow-up.
@Suppress("LargeClass")
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val getSourcesUseCase: GetSourcesUseCase,
    private val getPopularMangaUseCase: GetPopularMangaUseCase,
    private val getLatestUpdatesUseCase: GetLatestUpdatesUseCase,
    private val searchMangaUseCase: SearchMangaUseCase,
    private val getSourceFiltersUseCase: GetSourceFiltersUseCase,
    private val addMangaToLibraryUseCase: AddMangaToLibraryUseCase,
    private val toggleFavoriteMangaUseCase: ToggleFavoriteMangaUseCase,
    private val mangaRepository: MangaRepository,
    private val feedRepository: FeedRepository,
    private val generalPreferences: GeneralPreferences,
    private val searchLibraryMangaUseCase: SearchLibraryMangaUseCase,
    private val extensionManagementRepository: ExtensionManagementRepository,
    private val extensionRepository: ExtensionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseState())
    val state = _state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BrowseState(),
    )

    private val _sources = MutableStateFlow<List<MangaSource>>(emptyList())

    /** Atomic selection manager — keys are manga URLs. */
    private val selection = SelectionManager<String>()

    private val _effect = Channel<BrowseEffect>()
    val effect = _effect.receiveAsFlow()

    /** Serializes NSFW toggle writes so rapid double-taps cannot race. */
    private val nsfwToggleMutex = Mutex()

    init {
        // Collect sources and filter by NSFW preference and enabled languages.
        // Also mirrors showNsfw + enabledLanguages into state for the Browse UI.
        viewModelScope.launch {
            combine(
                getSourcesUseCase(),
                generalPreferences.showNsfwContent,
                generalPreferences.enabledSourceLanguages,
                generalPreferences.disabledSourceIds,
                ::buildSourceFilterResult,
            ).flowOn(Dispatchers.Default).collect { result ->
                _sources.value = result.sources
                _state.update {
                    it.copy(
                        sources = result.sources,
                        showNsfw = result.showNsfw,
                        enabledLanguages = result.enabledLangs,
                        availableLanguages = result.availableLangs,
                        disabledSourceIds = result.disabledIds,
                    )
                }
            }
        }
        // Unfiltered source list for the "Manage sources" dialog — must not be subject to the
        // NSFW/language/disabled filtering above, or a disabled source could never be re-enabled.
        getSourcesUseCase()
            .onEach { sources -> _state.update { it.copy(allSources = sources) } }
            .launchIn(viewModelScope)
        observeSavedSearches()
        observeLibraryFavorites()
        // Sync selection manager into state so UI recomposes
        combine(selection.selected, selection.isActive) { ids, active ->
            ids to active
        }.onEach { (ids, active) ->
            _state.update { it.copy(selectedManga = ids, isBulkSelectionMode = active) }
        }.launchIn(viewModelScope)
        observeSearchHistory()
        observeSourcePinning()
        observeNamedSavedSearches()
        observeLastUsedSources()
        observeSourceIconUrls()
    }

    private fun observeSourceIconUrls() {
        extensionRepository.getInstalledExtensions()
            .map { extensions ->
                buildMap {
                    extensions.forEach { ext ->
                        ext.sources.forEach { src -> put(src.id, ext.iconUrl) }
                    }
                }
            }
            .onEach { iconMap -> _state.update { it.copy(sourceIconUrls = iconMap) } }
            .launchIn(viewModelScope)
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun onEvent(event: BrowseEvent) {
        when (event) {
            is BrowseEvent.SelectSource -> {
                viewModelScope.launch { generalPreferences.recordSourceUsed(event.sourceId) }
                _state.update {
                    it.copy(
                        currentSourceId = event.sourceId,
                        searchQuery = "",
                        availableFilters = FilterList(),
                        activeFilters = FilterList(),
                        hasSearchResults = false,
                        searchResults = emptyList()
                    )
                }
                if (event.loadLatest) loadLatestUpdates(event.sourceId) else loadPopularManga(event.sourceId)
                loadSourceFilters(event.sourceId)
            }
            is BrowseEvent.ClearSourceSelection -> {
                _state.update {
                    it.copy(
                        currentSourceId = null,
                        popularManga = emptyList(),
                        searchQuery = "",
                        hasSearchResults = false,
                        searchResults = emptyList(),
                        error = null,
                        availableFilters = FilterList(),
                        activeFilters = FilterList(),
                        showFilterSheet = false,
                    )
                }
            }
            is BrowseEvent.SelectTab -> {
                _state.update { it.copy(selectedTab = event.tab) }
            }
            is BrowseEvent.OnMangaClick -> {
                val sourceId = _state.value.currentSourceId ?: return
                navigateToDetail(sourceId, event.manga.url)
            }
            is BrowseEvent.OnSearchQueryChange -> {
                _state.update {
                    it.copy(
                        searchQuery = event.query,
                        hasSearchResults = if (event.query.isBlank()) false else it.hasSearchResults
                    )
                }
            }
            is BrowseEvent.Search -> performSearch()
            is BrowseEvent.LoadNextPage -> loadNextPage()
            is BrowseEvent.RefreshSources -> refreshSources()
            is BrowseEvent.LoadLatest -> loadLatestUpdates()
            is BrowseEvent.ToggleFilterSheet -> {
                _state.update { it.copy(showFilterSheet = !it.showFilterSheet) }
            }
            is BrowseEvent.UpdateFilter -> {
                val currentFilters = _state.value.activeFilters.filters.toMutableList()
                if (event.index < currentFilters.size) {
                    currentFilters[event.index] = event.filter
                    _state.update { it.copy(activeFilters = FilterList(currentFilters)) }
                }
            }
            is BrowseEvent.ResetFilters -> {
                _state.update {
                    it.copy(activeFilters = it.availableFilters)
                }
            }
            is BrowseEvent.ApplyFilters -> {
                _state.update { it.copy(showFilterSheet = false) }
                persistActiveFilters()
                performSearch()
            }

            // Bulk favorite events
            is BrowseEvent.OnMangaLongClick -> {
                toggleMangaSelection(event.manga)
            }
            is BrowseEvent.ToggleMangaSelection -> {
                toggleMangaSelection(event.manga)
            }
            is BrowseEvent.ClearSelection -> {
                selection.clear()
            }
            is BrowseEvent.AddSelectedToLibrary -> {
                addSelectedToLibrary()
            }
            is BrowseEvent.ExitBulkSelectionMode -> {
                selection.clear()
            }
            is BrowseEvent.LongClickManga -> quickToggleFavorite(event.manga)
            is BrowseEvent.SaveCurrentSearch -> saveCurrentSearch()
            is BrowseEvent.DeleteSavedSearch -> deleteSavedSearch(event.searchId)
            is BrowseEvent.ApplySavedSearch -> applySavedSearch(event.search)
            is BrowseEvent.SetSearchScope -> _state.update {
                it.copy(searchScope = event.scope, searchResults = emptyList(), hasSearchResults = false)
            }
            is BrowseEvent.ClearSearchHistory -> viewModelScope.launch { generalPreferences.clearBrowseSearchHistory() }
            is BrowseEvent.DeleteSearchHistoryItem ->
                viewModelScope.launch { generalPreferences.removeBrowseSearchHistory(event.query) }

            // Source pinning & categories
            is BrowseEvent.TogglePinSource ->
                viewModelScope.launch { generalPreferences.togglePinnedSource(event.sourceId) }
            is BrowseEvent.ToggleDisableSource ->
                viewModelScope.launch { generalPreferences.toggleDisabledSource(event.sourceId) }
            is BrowseEvent.ShowSourcesFilterDialog -> _state.update { it.copy(showSourcesFilterDialog = true) }
            is BrowseEvent.DismissSourcesFilterDialog -> _state.update { it.copy(showSourcesFilterDialog = false) }
            is BrowseEvent.OpenSetCategoryDialog -> _state.update {
                it.copy(
                    showSetCategoryDialog = true,
                    categoryDialogSourceId = event.sourceId,
                    categoryDialogText = event.currentCategory,
                )
            }
            is BrowseEvent.UpdateCategoryDialogText -> _state.update {
                it.copy(categoryDialogText = event.text)
            }
            is BrowseEvent.ConfirmSetCategory -> {
                val state = _state.value
                val sid = state.categoryDialogSourceId ?: return
                viewModelScope.launch {
                    generalPreferences.setSourceCategory(sid, state.categoryDialogText)
                }
                _state.update { it.copy(showSetCategoryDialog = false, categoryDialogSourceId = null, categoryDialogText = "") }
            }
            is BrowseEvent.DismissSetCategoryDialog -> _state.update {
                it.copy(showSetCategoryDialog = false, categoryDialogSourceId = null, categoryDialogText = "")
            }

            // Named saved source searches (#1051)
            is BrowseEvent.ShowSaveSearchDialog -> _state.update {
                it.copy(showSaveSearchDialog = true, saveSearchName = "")
            }
            is BrowseEvent.HideSaveSearchDialog -> _state.update {
                it.copy(showSaveSearchDialog = false, saveSearchName = "")
            }
            is BrowseEvent.UpdateSaveSearchName -> _state.update {
                it.copy(saveSearchName = event.name)
            }
            is BrowseEvent.ConfirmSaveSearch -> confirmSaveNamedSearch()
            is BrowseEvent.ApplyNamedSavedSearch -> applyNamedSavedSearch(event.search)
            is BrowseEvent.DeleteNamedSavedSearch -> deleteNamedSavedSearch(event.id)
            is BrowseEvent.ToggleNsfwFilter -> {
                // Mutex serializes rapid taps: the second tap waits until the first write
                // has been queued, so both reads see different values and the toggle is correct.
                viewModelScope.launch {
                    nsfwToggleMutex.withLock {
                        generalPreferences.setShowNsfwContent(!state.value.showNsfw)
                    }
                }
            }
            is BrowseEvent.ShowLanguageDialog -> _state.update { it.copy(showLanguageDialog = true) }
            is BrowseEvent.DismissLanguageDialog -> _state.update { it.copy(showLanguageDialog = false) }
            is BrowseEvent.SetEnabledLanguages -> {
                viewModelScope.launch {
                    generalPreferences.setEnabledSourceLanguages(event.languages)
                    _state.update { it.copy(showLanguageDialog = false) }
                }
            }
        }
    }

    private fun loadPopularManga(sourceId: String, page: Int = 1) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            getPopularMangaUseCase(sourceId, page)
                .onSuccess { mangaPage ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            popularManga = if (page == 1) mangaPage.mangas else it.popularManga + mangaPage.mangas,
                            hasNextPage = mangaPage.hasNextPage,
                            currentPage = page,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load manga"
                        )
                    }
                }
        }
    }

    private var filterLoadJob: kotlinx.coroutines.Job? = null

    private fun loadSourceFilters(sourceId: String) {
        // Cancel any in-flight load so a slower fetch for a previously-selected source can't
        // land after a newer selection and overwrite the wrong source's filters.
        filterLoadJob?.cancel()
        filterLoadJob = viewModelScope.launch {
            // Two independent fetches: one stays at defaults (for "reset"), one receives the
            // user's previously-saved selections so filters survive across sessions.
            val defaults = getSourceFiltersUseCase(sourceId)
            val active = getSourceFiltersUseCase(sourceId)
            generalPreferences.getBrowseFilterState(sourceId)?.let { saved ->
                BrowseFilterStatePersistence.apply(active, saved)
            }
            // Only apply if this is still the selected source.
            if (_state.value.currentSourceId == sourceId) {
                _state.update {
                    it.copy(
                        availableFilters = defaults,
                        activeFilters = active
                    )
                }
            }
        }
    }

    private fun persistActiveFilters() {
        val sourceId = _state.value.currentSourceId ?: return
        val active = _state.value.activeFilters
        viewModelScope.launch {
            val encoded = if (active.hasActiveFilters()) BrowseFilterStatePersistence.encode(active) else null
            generalPreferences.setBrowseFilterState(sourceId, encoded)
        }
    }

    private fun performSearch() {
        val query = _state.value.searchQuery
        if (_state.value.searchScope == BrowseSearchScope.LIBRARY) {
            performLibrarySearch(query)
            return
        }
        val sourceId = _state.value.currentSourceId ?: return

        if (query.isNotBlank()) {
            viewModelScope.launch { generalPreferences.addBrowseSearchHistory(query) }
        }

        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, hasSearchResults = true, error = null) }
            searchMangaUseCase(sourceId, query, 1, _state.value.activeFilters)
                .onSuccess { mangaPage ->
                    _state.update {
                        it.copy(
                            isSearching = false,
                            searchResults = mangaPage.mangas,
                            hasNextPage = mangaPage.hasNextPage,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSearching = false,
                            hasSearchResults = false,
                            error = error.message ?: "Search failed"
                        )
                    }
                }
        }
    }

    private fun loadLatestUpdates(sourceId: String? = null) {
        val resolvedSourceId = sourceId ?: _state.value.currentSourceId ?: return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            getLatestUpdatesUseCase(resolvedSourceId, 1)
                .onSuccess { mangaPage ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            popularManga = mangaPage.mangas,
                            hasNextPage = mangaPage.hasNextPage,
                            currentPage = 1,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load latest updates"
                        )
                    }
                }
        }
    }

    private fun loadNextPage() {
        val currentState = _state.value
        if (!currentState.hasNextPage || currentState.isLoading) return

        val sourceId = currentState.currentSourceId ?: return

        if (currentState.searchQuery.isNotBlank()) {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                searchMangaUseCase(
                    sourceId,
                    currentState.searchQuery,
                    currentState.currentPage + 1,
                    currentState.activeFilters
                )
                    .onSuccess { mangaPage ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                searchResults = it.searchResults + mangaPage.mangas,
                                hasNextPage = mangaPage.hasNextPage,
                                currentPage = it.currentPage + 1
                            )
                        }
                    }
                    .onFailure {
                        _state.update { it.copy(isLoading = false) }
                    }
            }
        } else {
            // Load next page of popular manga
            loadPopularManga(sourceId, currentState.currentPage + 1)
        }
    }

    private fun refreshSources() {
        viewModelScope.launch {
            extensionManagementRepository.refreshSources()
                .onSuccess { _effect.send(BrowseEffect.ShowSnackbar("Sources refreshed")) }
                .onFailure { _effect.send(BrowseEffect.ShowSnackbar("Failed to refresh sources")) }
        }
    }

    private fun navigateToDetail(sourceId: String, mangaUrl: String) {
        viewModelScope.launch {
            _effect.send(BrowseEffect.NavigateToMangaDetail(sourceId, mangaUrl))
        }
    }

    /**
     * Helper to get the currently selected source.
     * Kept for future refactoring opportunities.
     */
    @Suppress("UnusedPrivateMember")
    private fun getCurrentSource(): MangaSource? {
        val sourceId = _state.value.currentSourceId ?: return null
        return _sources.value.find { it.id == sourceId }
    }

    /**
     * Toggles selection of a manga for bulk favorite.
     * Enables bulk selection mode when first manga is selected.
     */
    private fun toggleMangaSelection(manga: SourceManga) {
        selection.toggle(manga.url)
    }
    
    /**
     * Adds all selected manga to the library.
     * Shows success/failure message and optionally navigates to library.
     */
    private fun addSelectedToLibrary() {
        viewModelScope.launch {
            val sourceId = _state.value.currentSourceId ?: return@launch
            val selectedUrls = selection.snapshotAndClear()
            if (selectedUrls.isEmpty()) return@launch

            // Look up SourceManga from available lists by URL
            val allManga = _state.value.popularManga + _state.value.searchResults
            val selected = selectedUrls.mapNotNull { url -> allManga.find { it.url == url } }
            
            if (selected.isEmpty()) return@launch
            
            addMangaToLibraryUseCase(selected, sourceId)
                .onSuccess { addedCount ->
                    _effect.send(BrowseEffect.ShowSnackbar("$addedCount manga added to library"))
                    _effect.send(BrowseEffect.NavigateToLibrary)
                }
                .onFailure { error ->
                    _effect.send(BrowseEffect.ShowSnackbar("Failed to add manga: ${error.message}"))
                }
        }
    }

    // --- Quick Favorite Toggle (long-click) ---

    /**
     * Keeps [BrowseState.favoritedMangaUrls] up-to-date by observing the library.
     * This lets the UI indicate which browse results are already in the library.
     */
    private fun observeLibraryFavorites() {
        mangaRepository.getLibraryManga()
            .map { mangaList -> mangaList.map { it.url }.toSet() }
            .onEach { urls -> _state.update { it.copy(favoritedMangaUrls = urls) } }
            .launchIn(viewModelScope)
    }

    /**
     * Quickly adds or removes a single [SourceManga] from the library without
     * entering bulk selection mode. Shows a snackbar confirming the action.
     */
    private fun quickToggleFavorite(manga: app.otakureader.sourceapi.SourceManga) {
        val sourceId = _state.value.currentSourceId ?: return
        viewModelScope.launch {
            val alreadyFavorited = manga.url in _state.value.favoritedMangaUrls
            if (alreadyFavorited) {
                // Look up the DB record to get the ID needed for toggleFavorite
                val dbManga = mangaRepository.getMangaBySourceAndUrl(
                    sourceId = sourceId.toSourceId(),
                    url = manga.url
                )
                if (dbManga != null) {
                    toggleFavoriteMangaUseCase(dbManga.id)
                    _effect.send(BrowseEffect.ShowSnackbar("Removed from library"))
                }
            } else {
                addMangaToLibraryUseCase(manga, sourceId)
                    .onSuccess { _effect.send(BrowseEffect.ShowSnackbar("Added to library")) }
                    .onFailure { _effect.send(BrowseEffect.ShowSnackbar("Failed to add to library")) }
            }
        }
    }

    // --- Saved Searches ---

    private fun observeSearchHistory() {
        generalPreferences.browseSearchHistory
            .onEach { history -> _state.update { it.copy(searchHistory = history) } }
            .launchIn(viewModelScope)
    }

    /** Keeps [BrowseState.pinnedSourceIds] and [BrowseState.sourceCategoryMap] in sync with preferences. */
    private fun observeSourcePinning() {
        generalPreferences.pinnedSourceIds
            .onEach { ids -> _state.update { it.copy(pinnedSourceIds = ids) } }
            .launchIn(viewModelScope)
        generalPreferences.sourceCategoryMap
            .onEach { map -> _state.update { it.copy(sourceCategoryMap = map) } }
            .launchIn(viewModelScope)
    }

    private fun observeLastUsedSources() {
        generalPreferences.lastUsedSourceIds
            .onEach { ids -> _state.update { it.copy(lastUsedSourceIds = ids) } }
            .launchIn(viewModelScope)
    }

    // --- Named Saved Source Searches (#1051) ---

    /**
     * Decodes the raw JSON from preferences into a list of [SavedSourceSearch] objects, filters by
     * the currently selected source, and keeps [BrowseState.namedSavedSearches] up-to-date.
     *
     * Combining with [currentSourceId] ensures chips only show for the active source — searches
     * saved for other sources are hidden rather than mixed in.
     */
    private fun observeNamedSavedSearches() {
        combine(
            generalPreferences.savedSourceSearchesJson.map { json ->
                runCatching { Json.decodeFromString<List<SavedSourceSearch>>(json) }.getOrDefault(emptyList())
            },
            _state.map { it.currentSourceId }.distinctUntilChanged()
        ) { list, sourceId ->
            if (sourceId != null) list.filter { it.sourceName == sourceId } else emptyList()
        }
            .onEach { filtered -> _state.update { it.copy(namedSavedSearches = filtered) } }
            .launchIn(viewModelScope)
    }

    private fun confirmSaveNamedSearch() {
        val state = _state.value
        val name = state.saveSearchName.trim()
        if (name.isBlank()) {
            viewModelScope.launch { _effect.send(BrowseEffect.ShowSnackbar("Enter a name for the search")) }
            return
        }
        val query = state.searchQuery.trim()
        if (query.isBlank()) {
            viewModelScope.launch { _effect.send(BrowseEffect.ShowSnackbar("Enter a search query first")) }
            return
        }
        val sourceId = state.currentSourceId
        val newSearch = SavedSourceSearch(
            name = name,
            query = query,
            sourceId = sourceId?.toLongOrNull() ?: 0L,
            sourceName = sourceId ?: "",
        )
        val updated = state.namedSavedSearches + newSearch
        viewModelScope.launch {
            runCatching {
                generalPreferences.setSavedSourceSearchesJson(Json.encodeToString(updated))
            }.onSuccess {
                _effect.send(BrowseEffect.ShowSnackbar("Search \"$name\" saved"))
            }.onFailure {
                _effect.send(BrowseEffect.ShowSnackbar("Failed to save search"))
            }
        }
        _state.update { it.copy(showSaveSearchDialog = false, saveSearchName = "") }
    }

    private fun applyNamedSavedSearch(search: SavedSourceSearch) {
        val savedSourceId = search.sourceName
        val currentSourceId = _state.value.currentSourceId
        if (savedSourceId.isNotEmpty() && savedSourceId != currentSourceId) {
            _state.update {
                it.copy(
                    currentSourceId = savedSourceId,
                    searchQuery = search.query,
                    availableFilters = FilterList(),
                    activeFilters = FilterList(),
                    hasSearchResults = false,
                    searchResults = emptyList()
                )
            }
            loadPopularManga(savedSourceId)
            loadSourceFilters(savedSourceId)
        } else {
            _state.update { it.copy(searchQuery = search.query) }
        }
        performSearch()
    }

    private fun deleteNamedSavedSearch(id: String) {
        val updated = _state.value.namedSavedSearches.filterNot { it.id == id }
        viewModelScope.launch {
            try {
                generalPreferences.setSavedSourceSearchesJson(Json.encodeToString(updated))
                _effect.send(BrowseEffect.ShowSnackbar("Saved search removed"))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _effect.send(BrowseEffect.ShowSnackbar("Failed to remove saved search"))
            }
        }
    }


    private var librarySearchJob: kotlinx.coroutines.Job? = null

    private fun performLibrarySearch(query: String) {
        librarySearchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), hasSearchResults = false, isSearching = false) }
            return
        }
        librarySearchJob = viewModelScope.launch {
            if (query.isNotBlank()) generalPreferences.addBrowseSearchHistory(query)
            _state.update { it.copy(isSearching = true, hasSearchResults = true, error = null) }
            searchLibraryMangaUseCase(query).collect { mangas ->
                val results = mangas.map { manga ->
                    SourceManga(url = manga.id.toString(), title = manga.title, thumbnailUrl = manga.thumbnailUrl)
                }
                _state.update { it.copy(searchResults = results, isSearching = false) }
            }
        }
    }

    private fun observeSavedSearches() {
        combine(
            feedRepository.getSavedSearches(),
            _state.map { it.currentSourceId }.distinctUntilChanged()
        ) { searches, sourceId ->
            if (sourceId != null) searches.filter { it.sourceName == sourceId }
            else searches
        }
            .onEach { filtered -> _state.update { it.copy(savedSearches = filtered) } }
            .launchIn(viewModelScope)
    }

    private fun saveCurrentSearch() {
        val state = _state.value
        val sourceId = state.currentSourceId ?: return
        val query = state.searchQuery.trim()
        if (query.isBlank()) {
            viewModelScope.launch { _effect.send(BrowseEffect.ShowSnackbar("Enter a search query to save")) }
            return
        }
        viewModelScope.launch {
            runCatching {
                feedRepository.addSavedSearch(
                    sourceId = sourceId.toLongOrNull() ?: 0L,
                    sourceName = sourceId,
                    query = query,
                    filters = emptyMap(),
                )
            }.onSuccess {
                _effect.send(BrowseEffect.ShowSnackbar("Search saved"))
            }.onFailure {
                _effect.send(BrowseEffect.ShowSnackbar("Failed to save search"))
            }
        }
    }

    private fun deleteSavedSearch(searchId: Long) {
        viewModelScope.launch {
            // try/catch with explicit CancellationException rethrow so coroutine cancellation
            // (e.g. ViewModel cleared mid-delete) doesn't get routed through the failure snackbar.
            try {
                feedRepository.removeSavedSearch(searchId)
                _effect.send(BrowseEffect.ShowSnackbar("Search deleted"))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _effect.send(BrowseEffect.ShowSnackbar("Failed to delete search"))
            }
        }
    }

    private fun applySavedSearch(search: app.otakureader.domain.model.FeedSavedSearch) {
        _state.update { it.copy(searchQuery = search.query) }
        performSearch()
    }
}
