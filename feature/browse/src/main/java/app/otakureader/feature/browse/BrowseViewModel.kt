package app.otakureader.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.AiPreferences
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.domain.repository.AiRepository
import app.otakureader.domain.usecase.ai.AnalyzeSourceUseCase
import app.otakureader.domain.usecase.source.GetLatestUpdatesUseCase
import app.otakureader.domain.usecase.source.GetPopularMangaUseCase
import app.otakureader.domain.usecase.source.GetSourceFiltersUseCase
import app.otakureader.domain.usecase.source.GetSourcesUseCase
import app.otakureader.domain.usecase.source.SearchMangaUseCase
import app.otakureader.sourceapi.FilterList
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.SourceManga
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val getSourcesUseCase: GetSourcesUseCase,
    private val getPopularMangaUseCase: GetPopularMangaUseCase,
    private val getLatestUpdatesUseCase: GetLatestUpdatesUseCase,
    private val searchMangaUseCase: SearchMangaUseCase,
    private val getSourceFiltersUseCase: GetSourceFiltersUseCase,
    private val generalPreferences: GeneralPreferences,
    private val aiRepository: AiRepository,
    private val aiPreferences: AiPreferences,
    private val analyzeSourceUseCase: AnalyzeSourceUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseState())
    val state = _state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BrowseState(),
    )

    private val _sources = MutableStateFlow<List<MangaSource>>(emptyList())

    private val _effect = Channel<BrowseEffect>()
    val effect = _effect.receiveAsFlow()

    init {
        // Collect sources and filter by NSFW preference
        viewModelScope.launch {
            combine(
                getSourcesUseCase(),
                generalPreferences.showNsfwContent
            ) { sources, showNsfw ->
                if (showNsfw) {
                    sources
                } else {
                    sources.filter { !it.isNsfw }
                }
            }.collect { filteredSources ->
                _sources.value = filteredSources
                _state.update { it.copy(sources = filteredSources.map { s -> s.id }) }
            }
        }
        observeSourceIntelligenceSettings()
    }

    fun onEvent(event: BrowseEvent) {
        when (event) {
            is BrowseEvent.SelectSource -> {
                _state.update {
                    it.copy(
                        currentSourceId = event.sourceId,
                        availableFilters = FilterList(),
                        activeFilters = FilterList()
                    )
                }
                loadPopularManga(event.sourceId)
                loadSourceFilters(event.sourceId)
                // Analyze source intelligence if enabled and not yet cached
                if (_state.value.sourceIntelligenceEnabled &&
                    !_state.value.sourceIntelligence.containsKey(event.sourceId)
                ) {
                    analyzeSource(event.sourceId)
                }
            }
            is BrowseEvent.OnMangaClick -> {
                val sourceId = _state.value.currentSourceId ?: return
                navigateToDetail(sourceId, event.manga.url)
            }
            is BrowseEvent.OnSearchQueryChange -> {
                _state.update { it.copy(searchQuery = event.query) }
            }
            is BrowseEvent.Search -> {
                performSearch()
            }
            is BrowseEvent.LoadNextPage -> {
                loadNextPage()
            }
            is BrowseEvent.RefreshSources -> {
                refreshSources()
            }
            is BrowseEvent.LoadLatest -> {
                val sourceId = _state.value.currentSourceId ?: return
                loadLatestUpdates(sourceId)
            }
            is BrowseEvent.ToggleFilterSheet -> {
                _state.update { it.copy(showFilterSheet = !it.showFilterSheet) }
            }
            is BrowseEvent.UpdateFilter -> {
                updateFilter(event.index, event.filter)
            }
            is BrowseEvent.ResetFilters -> {
                resetFilters()
            }
            is BrowseEvent.ApplyFilters -> {
                _state.update { it.copy(showFilterSheet = false) }
                performSearch()
            }
        }
    }

    private fun loadSourceFilters(sourceId: String) {
        viewModelScope.launch {
            val filters = getSourceFiltersUseCase(sourceId)
            _state.update {
                it.copy(
                    availableFilters = filters,
                    activeFilters = filters
                )
            }
        }
    }

    private fun updateFilter(index: Int, filter: app.otakureader.sourceapi.Filter<*>) {
        val currentFilters = _state.value.activeFilters.filters.toMutableList()
        if (index in currentFilters.indices) {
            currentFilters[index] = filter
            _state.update { it.copy(activeFilters = FilterList(currentFilters)) }
        }
    }

    private fun resetFilters() {
        val sourceId = _state.value.currentSourceId ?: return
        viewModelScope.launch {
            val filters = getSourceFiltersUseCase(sourceId)
            _state.update { it.copy(activeFilters = filters) }
        }
    }

    private fun loadPopularManga(sourceId: String, page: Int = 1) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            getPopularMangaUseCase(sourceId, page)
                .onSuccess { mangaPage ->
                    _state.update { state ->
                        state.copy(
                            isLoading = false,
                            popularManga = if (page == 1) {
                                mangaPage.mangas
                            } else {
                                state.popularManga + mangaPage.mangas
                            },
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

    private fun loadLatestUpdates(sourceId: String, page: Int = 1) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            getLatestUpdatesUseCase(sourceId, page)
                .onSuccess { mangaPage ->
                    _state.update { state ->
                        state.copy(
                            isLoading = false,
                            popularManga = if (page == 1) {
                                mangaPage.mangas
                            } else {
                                state.popularManga + mangaPage.mangas
                            },
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
                            error = error.message ?: "Failed to load latest updates"
                        )
                    }
                }
        }
    }

    private fun performSearch() {
        val query = _state.value.searchQuery
        val sourceId = _state.value.currentSourceId ?: return
        val filters = _state.value.activeFilters

        // Allow filter-only search only when at least one filter is non-default
        if (query.isBlank() && !filters.hasActiveFilters()) return

        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, error = null) }

            searchMangaUseCase(sourceId, query, 1, filters)
                .onSuccess { mangaPage ->
                    _state.update { state ->
                        state.copy(
                            isSearching = false,
                            searchResults = mangaPage.mangas,
                            hasNextPage = mangaPage.hasNextPage,
                            currentPage = 1,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSearching = false,
                            error = error.message ?: "Search failed"
                        )
                    }
                }
        }
    }

    private fun loadNextPage() {
        val currentState = _state.value
        val sourceId = currentState.currentSourceId ?: return

        if (currentState.isSearching) {
            // Load next page of search results
            viewModelScope.launch {
                searchMangaUseCase(
                    sourceId,
                    currentState.searchQuery,
                    currentState.currentPage + 1,
                    currentState.activeFilters
                )
                    .onSuccess { mangaPage ->
                        _state.update { state ->
                            state.copy(
                                searchResults = state.searchResults + mangaPage.mangas,
                                hasNextPage = mangaPage.hasNextPage,
                                currentPage = state.currentPage + 1
                            )
                        }
                    }
            }
        } else {
            // Load next page of popular manga
            loadPopularManga(sourceId, currentState.currentPage + 1)
        }
    }

    private fun refreshSources() {
        viewModelScope.launch {
            // Sources are automatically refreshed through the flow
            _effect.send(BrowseEffect.ShowSnackbar("Sources refreshed"))
        }
    }

    private fun navigateToDetail(sourceId: String, mangaUrl: String) {
        viewModelScope.launch {
            _effect.send(BrowseEffect.NavigateToMangaDetail(sourceId, mangaUrl))
        }
    }

    private fun getCurrentSource(): MangaSource? {
        val sourceId = _state.value.currentSourceId ?: return null
        return _sources.value.find { it.id == sourceId }
    }

    // --- Source Intelligence ---

    private fun observeSourceIntelligenceSettings() {
        combine(
            aiPreferences.aiEnabled,
            aiPreferences.aiSourceIntelligence
        ) { aiEnabled, sourceIntelEnabled ->
            aiEnabled && sourceIntelEnabled
        }.onEach { enabled ->
            _state.update { it.copy(sourceIntelligenceEnabled = enabled) }
        }.launchIn(viewModelScope)
    }

    private fun analyzeSource(sourceId: String) {
        val source = _sources.value.find { it.id == sourceId } ?: return

        viewModelScope.launch {
            if (!aiRepository.isAvailable()) return@launch
            _state.update { it.copy(isAnalyzingSource = true) }
            analyzeSourceUseCase(
                sourceName = source.name,
                sourceLanguage = source.lang,
                isNsfw = source.isNsfw
            ).onSuccess { summary ->
                _state.update { state ->
                    state.copy(
                        isAnalyzingSource = false,
                        sourceIntelligence = state.sourceIntelligence + (sourceId to summary)
                    )
                }
            }.onFailure {
                _state.update { it.copy(isAnalyzingSource = false) }
            }
        }
    }
}

