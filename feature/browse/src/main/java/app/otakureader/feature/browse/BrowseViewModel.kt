package app.otakureader.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.AiPreferences
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.domain.model.SourceInfo
import app.otakureader.domain.usecase.ai.ScoreSourcesForMangaUseCase
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
    private val aiPreferences: AiPreferences,
    private val scoreSourcesForManga: ScoreSourcesForMangaUseCase,
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
            }
            is BrowseEvent.OnMangaClick -> {
                val sourceId = _state.value.currentSourceId ?: return
                navigateToDetail(sourceId, event.manga.url)
            }
            is BrowseEvent.OnSearchQueryChange -> {
                _state.update { it.copy(searchQuery = event.query) }
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
                performSearch()
            }
            is BrowseEvent.RequestSourceScores -> {
                requestSourceScores(event.mangaId, event.mangaTitle)
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

    private fun performSearch() {
        val query = _state.value.searchQuery
        val sourceId = _state.value.currentSourceId ?: return

        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, error = null) }
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
                            error = error.message ?: "Search failed"
                        )
                    }
                }
        }
    }

    private fun loadLatestUpdates() {
        val sourceId = _state.value.currentSourceId ?: return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            getLatestUpdatesUseCase(sourceId, 1)
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

    /**
     * Observes the combined AI master toggle + source intelligence toggle from preferences.
     * The [BrowseState.sourceIntelligenceEnabled] flag is kept in sync so the UI can
     * conditionally show/hide source scoring chips.
     */
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

    /**
     * Requests AI source intelligence scoring for the given manga.
     *
     * Available sources are mapped to [SourceInfo] objects and passed to
     * [ScoreSourcesForMangaUseCase]. Results are stored in [BrowseState.sourceScores]
     * sorted by overall score descending so the UI can suggest the best source.
     *
     * When AI is disabled or unavailable the use case returns an empty list and no
     * state update is performed – the UI continues to show sources unsorted.
     */
    private fun requestSourceScores(mangaId: Long, mangaTitle: String) {
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzingSource = true) }
            val sourceInfoList = _sources.value.map { source ->
                SourceInfo(
                    sourceId = source.id,
                    sourceName = source.name,
                    chapterCount = 0, // Not available at browse level; AI uses name/language as proxy
                    language = source.lang,
                )
            }
            val result = scoreSourcesForManga(mangaId, mangaTitle, sourceInfoList)
            val scores = result.getOrNull() ?: emptyList()
            _state.update { state ->
                state.copy(
                    isAnalyzingSource = false,
                    sourceScores = scores,
                )
            }
        }
    }
}
