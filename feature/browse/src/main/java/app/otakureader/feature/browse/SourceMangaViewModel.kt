package app.otakureader.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.usecase.source.GetPopularMangaUseCase
import app.otakureader.domain.usecase.source.SearchMangaUseCase
import app.otakureader.sourceapi.SourceManga
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SourceMangaState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val sourceId: String = "",
    val sourceName: String? = null,
    val manga: List<SourceManga> = emptyList(),
    val searchQuery: String = "",
    val isSearchMode: Boolean = false,
    val error: String? = null,
    val hasNextPage: Boolean = false,
    val currentPage: Int = 1
) : UiState

sealed interface SourceMangaEvent : UiEvent {
    data object Refresh : SourceMangaEvent
    data class OnMangaClick(val manga: SourceManga) : SourceMangaEvent
    data object LoadNextPage : SourceMangaEvent
    data class OnSearchQueryChange(val query: String) : SourceMangaEvent
    data object EnterSearchMode : SourceMangaEvent
    data object Search : SourceMangaEvent
    data object CloseSearch : SourceMangaEvent
}

sealed interface SourceMangaEffect : UiEffect {
    data class NavigateToMangaDetail(val mangaUrl: String, val mangaTitle: String) : SourceMangaEffect
    data class ShowSnackbar(val message: String) : SourceMangaEffect
}

@HiltViewModel
class SourceMangaViewModel @Inject constructor(
    private val getPopularMangaUseCase: GetPopularMangaUseCase,
    private val searchMangaUseCase: SearchMangaUseCase,
    private val sourceRepository: SourceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SourceMangaState())
    val state = _state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SourceMangaState(),
    )

    private val _effect = Channel<SourceMangaEffect>()
    val effect = _effect.receiveAsFlow()

    fun setSourceId(sourceId: String) {
        if (_state.value.sourceId != sourceId) {
            viewModelScope.launch {
                val sourceName = sourceRepository.getSource(sourceId)?.name ?: sourceId
                _state.update {
                    it.copy(
                        sourceId = sourceId,
                        sourceName = sourceName,
                        manga = emptyList(),
                        currentPage = 1,
                        hasNextPage = false,
                        isSearchMode = false,
                        searchQuery = "",
                        error = null,
                    )
                }
                loadManga(sourceId, page = 1)
            }
        }
    }

    fun onEvent(event: SourceMangaEvent) {
        when (event) {
            is SourceMangaEvent.Refresh -> refreshManga()
            is SourceMangaEvent.OnMangaClick -> navigateToDetail(event.manga)
            is SourceMangaEvent.LoadNextPage -> loadNextPage()
            is SourceMangaEvent.OnSearchQueryChange -> _state.update { it.copy(searchQuery = event.query) }
            is SourceMangaEvent.EnterSearchMode -> _state.update { it.copy(isSearchMode = true, searchQuery = "") }
            is SourceMangaEvent.Search -> performSearch()
            is SourceMangaEvent.CloseSearch -> closeSearch()
        }
    }

    private fun loadManga(sourceId: String, page: Int = 1) {
        val isFirstPage = page == 1
        if (isFirstPage) {
            _state.update { it.copy(isLoading = true, error = null) }
        } else {
            _state.update { it.copy(isLoadingMore = true) }
        }
        viewModelScope.launch {
            getPopularMangaUseCase(sourceId, page)
                .onSuccess { mangaPage ->
                    _state.update { state ->
                        state.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            manga = if (isFirstPage) mangaPage.mangas else state.manga + mangaPage.mangas,
                            hasNextPage = mangaPage.hasNextPage,
                            currentPage = page,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (isFirstPage) {
                        // First page failure: show error screen
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isLoadingMore = false,
                                error = error.message ?: "Failed to load manga",
                            )
                        }
                    } else {
                        // Pagination failure: keep existing results, show snackbar
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isLoadingMore = false,
                            )
                        }
                        _effect.send(SourceMangaEffect.ShowSnackbar(error.message ?: "Failed to load more manga"))
                    }
                }
        }
    }

    private fun performSearch() {
        val currentState = _state.value
        val query = currentState.searchQuery
        val sourceId = currentState.sourceId
        if (sourceId.isBlank()) return

        _state.update { it.copy(isLoading = true, isSearchMode = true, manga = emptyList(), error = null) }
        viewModelScope.launch {
            searchMangaUseCase(sourceId, query, page = 1)
                .onSuccess { mangaPage ->
                    _state.update { state ->
                        state.copy(
                            isLoading = false,
                            manga = mangaPage.mangas,
                            hasNextPage = mangaPage.hasNextPage,
                            currentPage = 1,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Search failed",
                        )
                    }
                }
        }
    }

    private fun closeSearch() {
        val currentState = _state.value
        if (!currentState.isSearchMode) return
        _state.update { it.copy(isSearchMode = false, searchQuery = "", manga = emptyList(), currentPage = 1) }
        loadManga(currentState.sourceId, page = 1)
    }

    private fun refreshManga() {
        val currentState = _state.value
        _state.update {
            it.copy(
                currentPage = 1,
                manga = emptyList(),
                hasNextPage = false,
                error = null,
            )
        }
        if (currentState.isSearchMode && currentState.searchQuery.isNotBlank()) {
            performSearch()
        } else {
            loadManga(currentState.sourceId, page = 1)
        }
    }

    private fun loadNextPage() {
        val currentState = _state.value
        if (currentState.isLoadingMore || !currentState.hasNextPage) return
        if (currentState.sourceId.isBlank()) return

        val nextPage = currentState.currentPage + 1
        if (currentState.isSearchMode) {
            _state.update { it.copy(isLoadingMore = true) }
            viewModelScope.launch {
                searchMangaUseCase(currentState.sourceId, currentState.searchQuery, nextPage)
                    .onSuccess { mangaPage ->
                        _state.update { state ->
                            state.copy(
                                isLoadingMore = false,
                                manga = state.manga + mangaPage.mangas,
                                hasNextPage = mangaPage.hasNextPage,
                                currentPage = nextPage,
                            )
                        }
                    }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        _state.update { it.copy(isLoadingMore = false) }
                        _effect.send(SourceMangaEffect.ShowSnackbar(error.message ?: "Failed to load more manga"))
                    }
            }
        } else {
            loadManga(currentState.sourceId, nextPage)
        }
    }

    private fun navigateToDetail(manga: SourceManga) {
        viewModelScope.launch {
            _effect.send(SourceMangaEffect.NavigateToMangaDetail(manga.url, manga.title))
        }
    }
}
