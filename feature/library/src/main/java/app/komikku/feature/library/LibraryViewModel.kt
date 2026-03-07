package app.komikku.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.komikku.domain.repository.CategoryRepository
import app.komikku.domain.usecase.GetLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Library screen.
 * Follows the MVI pattern: exposes [state] as [StateFlow] and [effect] as a Channel.
 * User actions are dispatched via [onEvent].
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getLibraryUseCase: GetLibraryUseCase,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _effect = Channel<LibraryEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private val searchQuery = MutableStateFlow("")

    private var libraryJob: Job? = null

    init {
        observeLibrary()
        observeCategories()
    }

    private fun observeLibrary() {
        libraryJob = viewModelScope.launch {
            searchQuery
                .flatMapLatest { query -> getLibraryUseCase(query) }
                .catch { e -> _state.update { it.copy(error = e.message, isLoading = false) } }
                .collect { mangaList ->
                    _state.update { it.copy(manga = mangaList, isLoading = false, error = null) }
                }
        }
    }

    private fun observeCategories() {
        viewModelScope.launch {
            categoryRepository.observeCategories().collect { categories ->
                _state.update { it.copy(categories = categories) }
            }
        }
    }

    /** Dispatch a user event to the ViewModel. */
    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.Refresh -> refresh()
            is LibraryEvent.OnMangaClick -> handleMangaClick(event.mangaId)
            is LibraryEvent.OnMangaLongClick -> toggleSelection(event.mangaId)
            is LibraryEvent.OnSearchQueryChange -> updateSearch(event.query)
            is LibraryEvent.OnCategoryChange -> _state.update { it.copy(activeCategory = event.categoryId) }
            is LibraryEvent.ClearSelection -> _state.update { it.copy(selectedManga = emptySet()) }
            is LibraryEvent.RemoveFromLibrary -> removeFromLibrary(event.mangaIds)
        }
    }

    private fun refresh() {
        _state.update { it.copy(isLoading = true) }
        libraryJob?.cancel()
        observeLibrary()
    }

    private fun handleMangaClick(mangaId: Long) {
        if (_state.value.selectedManga.isNotEmpty()) {
            toggleSelection(mangaId)
        } else {
            viewModelScope.launch {
                _effect.send(LibraryEffect.NavigateToManga(mangaId))
            }
        }
    }

    private fun toggleSelection(mangaId: Long) {
        _state.update { current ->
            val selection = current.selectedManga.toMutableSet()
            if (mangaId in selection) selection.remove(mangaId) else selection.add(mangaId)
            current.copy(selectedManga = selection)
        }
    }

    private fun updateSearch(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchQuery.value = query
    }

    private fun removeFromLibrary(mangaIds: Set<Long>) {
        viewModelScope.launch {
            // TODO: call use case to remove from library
            _state.update { it.copy(selectedManga = emptySet()) }
            _effect.send(LibraryEffect.ShowSnackbar("Removed ${mangaIds.size} manga from library"))
        }
    }
}
