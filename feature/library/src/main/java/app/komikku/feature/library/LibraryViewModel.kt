package app.komikku.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.komikku.domain.manga.usecase.GetLibraryMangaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getLibraryMangaUseCase: GetLibraryMangaUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state = _state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryState(),
    )

    private val _effect = Channel<LibraryEffect>()
    val effect = _effect.receiveAsFlow()

    init {
        loadLibrary()
    }

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.OnMangaClick -> navigateToDetail(event.mangaId)
            is LibraryEvent.OnSearchQueryChange -> updateSearchQuery(event.query)
            is LibraryEvent.OnRefresh -> loadLibrary()
        }
    }

    private fun loadLibrary() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            getLibraryMangaUseCase()
                .catch { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
                .collect { manga -> _state.update { it.copy(isLoading = false, manga = manga) } }
        }
    }

    private fun navigateToDetail(mangaId: Long) {
        viewModelScope.launch { _effect.send(LibraryEffect.NavigateToMangaDetail(mangaId)) }
    }

    private fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }
}
