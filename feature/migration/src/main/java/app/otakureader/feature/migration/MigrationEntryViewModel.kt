package app.otakureader.feature.migration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.usecase.GetLibraryMangaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MigrationEntryViewModel @Inject constructor(
    private val getLibraryManga: GetLibraryMangaUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(MigrationEntryState())
    val state: StateFlow<MigrationEntryState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<MigrationEntryEffect>()
    val effect: SharedFlow<MigrationEntryEffect> = _effect.asSharedFlow()

    init {
        loadLibrary()
    }

    fun onEvent(event: MigrationEntryEvent) {
        when (event) {
            is MigrationEntryEvent.OnSearchQueryChange -> onSearchQueryChange(event.query)
            is MigrationEntryEvent.OnMangaToggle -> toggleManga(event.mangaId)
            MigrationEntryEvent.SelectAll -> selectAll()
            MigrationEntryEvent.ClearSelection -> clearSelection()
            MigrationEntryEvent.OnStartMigration -> startMigration()
            MigrationEntryEvent.NavigateBack -> navigateBack()
            MigrationEntryEvent.Retry -> loadLibrary()
        }
    }

    private fun loadLibrary() {
        _state.update { it.copy(isLoading = true, error = null) }
        getLibraryManga()
            .onEach { manga ->
                _state.update { state ->
                    state.copy(
                        isLoading = false,
                        error = null,
                        mangaList = manga.map { m ->
                            MigrationEntryItem(
                                id = m.id,
                                title = m.title,
                                thumbnailUrl = m.thumbnailUrl
                            )
                        }
                    )
                }
            }
            .catch { e ->
                val message = e.message ?: "Failed to load library"
                _state.update { it.copy(isLoading = false, error = message) }
                _effect.emit(MigrationEntryEffect.ShowError(message))
            }
            .launchIn(viewModelScope)
    }

    private fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    private fun toggleManga(mangaId: Long) {
        _state.update { state ->
            val newSelection = if (mangaId in state.selectedIds) {
                state.selectedIds - mangaId
            } else {
                state.selectedIds + mangaId
            }
            state.copy(selectedIds = newSelection)
        }
    }

    private fun selectAll() {
        _state.update { state ->
            val allIds = filteredList(state).map { it.id }.toSet()
            state.copy(selectedIds = allIds)
        }
    }

    private fun clearSelection() {
        _state.update { it.copy(selectedIds = emptySet()) }
    }

    private fun startMigration() {
        val selected = _state.value.selectedIds.toList()
        if (selected.isEmpty()) return
        viewModelScope.launch {
            _effect.emit(MigrationEntryEffect.NavigateToMigration(selected))
        }
    }

    private fun navigateBack() {
        viewModelScope.launch {
            _effect.emit(MigrationEntryEffect.NavigateBack)
        }
    }

    /** Returns the manga list filtered by the current search query. */
    fun filteredList(state: MigrationEntryState = _state.value): List<MigrationEntryItem> {
        val query = state.searchQuery.trim()
        return if (query.isBlank()) state.mangaList
        else state.mangaList.filter { it.title.contains(query, ignoreCase = true) }
    }
}
