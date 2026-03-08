package app.otakureader.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.domain.model.Manga
import app.otakureader.domain.usecase.GetLibraryMangaUseCase
import app.otakureader.domain.usecase.ToggleFavoriteMangaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getLibraryManga: GetLibraryMangaUseCase,
    private val toggleFavoriteManga: ToggleFavoriteMangaUseCase,
    private val libraryPreferences: LibraryPreferences
) : ViewModel() {
    
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()
    
    private val _effect = MutableSharedFlow<LibraryEffect>()
    val effect: SharedFlow<LibraryEffect> = _effect.asSharedFlow()
    
    init {
        loadLibrary()
        observeLibraryPreferences()
    }
    
    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.Refresh -> loadLibrary()
            is LibraryEvent.OnMangaClick -> onMangaClick(event.mangaId)
            is LibraryEvent.OnMangaLongClick -> onMangaLongClick(event.mangaId)
            is LibraryEvent.OnSearchQueryChange -> onSearchQueryChange(event.query)
            is LibraryEvent.OnCategorySelected -> onCategorySelected(event.categoryId)
            is LibraryEvent.ClearSelection -> clearSelection()
            is LibraryEvent.ToggleFavorite -> toggleFavorite(event.mangaId)
        }
    }
    
    private fun observeLibraryPreferences() {
        combine(
            libraryPreferences.gridSize,
            libraryPreferences.showBadges
        ) { gridSize, showBadges ->
            _state.update { it.copy(gridSize = gridSize, showBadges = showBadges) }
        }.launchIn(viewModelScope)
    }
    
    private fun loadLibrary() {
        _state.update { it.copy(isLoading = true) }
        
        getLibraryManga()
            .map { mangaList ->
                mangaList.map { it.toLibraryItem() }
            }
            .onEach { items ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        mangaList = items,
                        error = null
                    )
                }
            }
            .catch { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
            }
            .launchIn(viewModelScope)
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
    
    private fun Manga.toLibraryItem() = LibraryMangaItem(
        id = id,
        title = title,
        thumbnailUrl = thumbnailUrl,
        unreadCount = unreadCount,
        isFavorite = favorite
    )
}
