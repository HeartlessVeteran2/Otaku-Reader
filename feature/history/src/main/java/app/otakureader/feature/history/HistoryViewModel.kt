package app.otakureader.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.usecase.GetHistoryUseCase
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

    private val _effect = MutableSharedFlow<HistoryEffect>()
    val effect: SharedFlow<HistoryEffect> = _effect.asSharedFlow()

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
            is HistoryEvent.OnChapterClick -> navigateToReader(event.mangaId, event.chapterId)
            is HistoryEvent.ClearHistory -> clearHistory()
            is HistoryEvent.OnSearchQueryChange -> {
                searchQuery.value = event.query
                _state.update { it.copy(searchQuery = event.query) }
            }
            is HistoryEvent.RemoveFromHistory -> removeFromHistory(event.chapterId)
        }
    }

    private fun navigateToReader(mangaId: Long, chapterId: Long) {
        viewModelScope.launch {
            _effect.emit(HistoryEffect.NavigateToReader(mangaId, chapterId))
        }
    }

    private fun clearHistory() {
        viewModelScope.launch {
            try {
                chapterRepository.clearAllHistory()
                _effect.emit(HistoryEffect.ShowSnackbar("History cleared"))
            } catch (e: Exception) {
                _effect.emit(HistoryEffect.ShowSnackbar("Failed to clear history"))
            }
        }
    }

    private fun removeFromHistory(chapterId: Long) {
        viewModelScope.launch {
            try {
                chapterRepository.removeFromHistory(chapterId)
            } catch (e: Exception) {
                _effect.emit(HistoryEffect.ShowSnackbar("Failed to remove from history"))
            }
        }
    }
}
