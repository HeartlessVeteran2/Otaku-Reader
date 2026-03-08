package app.otakureader.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.sourceapi.SourceManga
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val error: String? = null,
    val hasNextPage: Boolean = false,
    val currentPage: Int = 1
) : UiState

sealed interface SourceMangaEvent : UiEvent {
    data object Refresh : SourceMangaEvent
    data class OnMangaClick(val manga: SourceManga) : SourceMangaEvent
    data object LoadNextPage : SourceMangaEvent
}

sealed interface SourceMangaEffect : UiEffect {
    data class NavigateToMangaDetail(val mangaUrl: String, val mangaTitle: String) : SourceMangaEffect
    data class ShowSnackbar(val message: String) : SourceMangaEffect
}

@HiltViewModel
class SourceMangaViewModel @Inject constructor() : ViewModel() {

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
            _state.update {
                it.copy(
                    sourceId = sourceId,
                    sourceName = sourceId, // Could be fetched from repository
                    manga = emptyList(),
                    currentPage = 1,
                    hasNextPage = false
                )
            }
            loadManga()
        }
    }

    fun onEvent(event: SourceMangaEvent) {
        when (event) {
            is SourceMangaEvent.Refresh -> refreshManga()
            is SourceMangaEvent.OnMangaClick -> navigateToDetail(event.manga)
            is SourceMangaEvent.LoadNextPage -> loadNextPage()
        }
    }

    private fun loadManga() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // TODO: Load manga from source repository
            // For now, simulate loading
            kotlinx.coroutines.delay(1000)
            _state.update {
                it.copy(
                    isLoading = false,
                    // Sample data for testing
                    manga = emptyList(),
                    hasNextPage = false
                )
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
        loadManga(page = 1)
    }

    private fun loadNextPage() {
        if (_state.value.isLoadingMore || !_state.value.hasNextPage) return

        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            // TODO: Load next page from source
            kotlinx.coroutines.delay(500)
            _state.update { it.copy(isLoadingMore = false) }
        }
    }

    private fun navigateToDetail(manga: SourceManga) {
        viewModelScope.launch {
            _effect.send(SourceMangaEffect.NavigateToMangaDetail(manga.url, manga.title))
        }
    }
}
