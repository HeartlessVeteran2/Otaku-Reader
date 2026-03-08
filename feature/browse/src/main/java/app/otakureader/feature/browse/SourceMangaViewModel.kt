package app.otakureader.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.domain.usecase.source.GetPopularMangaUseCase
import app.otakureader.domain.usecase.source.GetSourcesUseCase
import app.otakureader.domain.usecase.source.SearchMangaUseCase
import app.otakureader.sourceapi.SourceManga
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
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
    val error: String? = null,
    val hasNextPage: Boolean = false,
    val currentPage: Int = 1,
    val searchQuery: String = "",
    val isSearchMode: Boolean = false
) : UiState

sealed interface SourceMangaEvent : UiEvent {
    data object Refresh : SourceMangaEvent
    data class OnMangaClick(val manga: SourceManga) : SourceMangaEvent
    data object LoadNextPage : SourceMangaEvent
    data class OnSearchQueryChange(val query: String) : SourceMangaEvent
    data object Search : SourceMangaEvent
    data object ToggleSearchMode : SourceMangaEvent
}

sealed interface SourceMangaEffect : UiEffect {
    data class NavigateToMangaDetail(val mangaUrl: String, val mangaTitle: String) : SourceMangaEffect
    data class ShowSnackbar(val message: String) : SourceMangaEffect
}

@HiltViewModel
class SourceMangaViewModel @Inject constructor(
    private val getPopularMangaUseCase: GetPopularMangaUseCase,
    private val searchMangaUseCase: SearchMangaUseCase,
    private val getSourcesUseCase: GetSourcesUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SourceMangaState())
    val state = _state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SourceMangaState(),
    )

    private val _effect = Channel<SourceMangaEffect>()
    val effect = _effect.receiveAsFlow()

    private var loadMangaJob: Job? = null

    fun setSourceId(sourceId: String) {
        if (_state.value.sourceId != sourceId) {
            _state.update {
                it.copy(
                    sourceId = sourceId,
                    manga = emptyList(),
                    currentPage = 1,
                    hasNextPage = false
                )
            }
            fetchSourceName(sourceId)
            loadManga()
        }
    }

    fun onEvent(event: SourceMangaEvent) {
        when (event) {
            is SourceMangaEvent.Refresh -> refreshManga()
            is SourceMangaEvent.OnMangaClick -> navigateToDetail(event.manga)
            is SourceMangaEvent.LoadNextPage -> loadNextPage()
            is SourceMangaEvent.OnSearchQueryChange -> {
                _state.update { it.copy(searchQuery = event.query) }
            }
            is SourceMangaEvent.Search -> {
                _state.update {
                    it.copy(
                        currentPage = 1,
                        manga = emptyList(),
                        hasNextPage = false
                    )
                }
                loadManga()
            }
            is SourceMangaEvent.ToggleSearchMode -> {
                _state.update {
                    val newMode = !it.isSearchMode
                    it.copy(
                        isSearchMode = newMode,
                        searchQuery = if (!newMode) "" else it.searchQuery,
                        manga = emptyList(),
                        currentPage = 1,
                        hasNextPage = false
                    )
                }
                loadManga()
            }
        }
    }

    private fun fetchSourceName(sourceId: String) {
        viewModelScope.launch {
            val sources = getSourcesUseCase().first { it.isNotEmpty() }
            val source = sources.find { it.id == sourceId }
            _state.update { it.copy(sourceName = source?.name ?: sourceId) }
        }
    }

    private fun loadManga(page: Int = _state.value.currentPage) {
        val currentState = _state.value
        val sourceId = currentState.sourceId

        loadMangaJob?.cancel()
        _state.update { it.copy(isLoading = page == 1, isLoadingMore = page > 1, error = null) }

        loadMangaJob = viewModelScope.launch {
            val result = if (currentState.isSearchMode && currentState.searchQuery.isNotBlank()) {
                searchMangaUseCase(sourceId, currentState.searchQuery, page)
            } else {
                getPopularMangaUseCase(sourceId, page)
            }

            result.onSuccess { mangaPage ->
                _state.update { state ->
                    state.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        manga = if (page == 1) mangaPage.mangas else state.manga + mangaPage.mangas,
                        hasNextPage = mangaPage.hasNextPage,
                        currentPage = page,
                        error = null
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = error.message ?: "Failed to load manga"
                    )
                }
            }
        }
    }

    private fun refreshManga() {
        _state.update {
            it.copy(
                currentPage = 1,
                manga = emptyList(),
                hasNextPage = false,
                error = null
            )
        }
        loadManga()
    }

    private fun loadNextPage() {
        val currentState = _state.value
        if (currentState.isLoading || currentState.isLoadingMore || !currentState.hasNextPage) return

        loadManga(page = currentState.currentPage + 1)
    }

    private fun navigateToDetail(manga: SourceManga) {
        viewModelScope.launch {
            _effect.send(SourceMangaEffect.NavigateToMangaDetail(manga.url, manga.title))
        }
    }
}
