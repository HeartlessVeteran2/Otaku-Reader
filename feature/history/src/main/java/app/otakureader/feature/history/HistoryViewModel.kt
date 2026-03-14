package app.otakureader.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.usecase.GetHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getHistoryUseCase: GetHistoryUseCase,
    private val chapterRepository: ChapterRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    private val _effect = Channel<HistoryEffect>(Channel.BUFFERED)
    val effect: Flow<HistoryEffect> = _effect.receiveAsFlow()

    private val searchQuery = MutableStateFlow("")

    init {
        _state.update { it.copy(isLoading = true) }
        combine(getHistoryUseCase(), searchQuery) { allEntries, query ->
            if (query.isBlank()) allEntries
            else allEntries.filter { it.chapter.name.contains(query, ignoreCase = true) }
        }
            .onEach { filtered ->
                _state.update { it.copy(isLoading = false, history = filtered, error = null) }
            }
            .catch { error ->
                _state.update { it.copy(isLoading = false, error = error.message) }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: HistoryEvent) {
        when (event) {
            is HistoryEvent.OnChapterClick -> onChapterClick(event.mangaId, event.chapterId)
            is HistoryEvent.OnChapterLongClick -> toggleSelection(event.chapterId)
            is HistoryEvent.ClearHistory -> clearHistory()
            is HistoryEvent.ClearSelection -> clearSelection()
            is HistoryEvent.SelectAll -> selectAll()
            is HistoryEvent.OnSearchQueryChange -> {
                searchQuery.value = event.query
                _state.update { it.copy(searchQuery = event.query) }
            }
            is HistoryEvent.RemoveFromHistory -> removeFromHistory(event.chapterId)
            is HistoryEvent.RemoveSelectedFromHistory -> removeSelectedFromHistory()
        }
    }

    private fun onChapterClick(mangaId: Long, chapterId: Long) {
        if (_state.value.selectedItems.isNotEmpty()) {
            toggleSelection(chapterId)
        } else {
            navigateToReader(mangaId, chapterId)
        }
    }

    private fun toggleSelection(chapterId: Long) {
        _state.update { state ->
            val currentSelection = state.selectedItems
            val newSelection = if (currentSelection.contains(chapterId)) {
                currentSelection - chapterId
            } else {
                currentSelection + chapterId
            }
            state.copy(selectedItems = newSelection)
        }
    }

    private fun clearSelection() {
        _state.update { it.copy(selectedItems = emptySet()) }
    }

    private fun selectAll() {
        _state.update { state ->
            val allIds = state.history.map { it.chapter.id }.toSet()
            state.copy(selectedItems = allIds)
        }
    }

    private fun removeSelectedFromHistory() {
        viewModelScope.launch {
            try {
                val selectedIds = _state.value.selectedItems
                if (selectedIds.isNotEmpty()) {
                    selectedIds.forEach { chapterId ->
                        chapterRepository.removeFromHistory(chapterId)
                    }
                    clearSelection()
                    _effect.send(HistoryEffect.ShowSnackbar("Removed ${selectedIds.size} item(s) from history"))
                }
            } catch (e: Exception) {
                _effect.send(HistoryEffect.ShowSnackbar("Failed to remove from history"))
            }
        }
    }

    private fun navigateToReader(mangaId: Long, chapterId: Long) {
        viewModelScope.launch {
            _effect.send(HistoryEffect.NavigateToReader(mangaId, chapterId))
        }
    }

    private fun clearHistory() {
        viewModelScope.launch {
            try {
                chapterRepository.clearAllHistory()
                _effect.send(HistoryEffect.ShowSnackbar("History cleared"))
            } catch (e: Exception) {
                _effect.send(HistoryEffect.ShowSnackbar("Failed to clear history"))
            }
        }
    }

    private fun removeFromHistory(chapterId: Long) {
        viewModelScope.launch {
            try {
                chapterRepository.removeFromHistory(chapterId)
            } catch (e: Exception) {
                _effect.send(HistoryEffect.ShowSnackbar("Failed to remove from history"))
            }
        }
    }
}
