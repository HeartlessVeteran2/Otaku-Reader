package app.otakureader.feature.updates.errors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.repository.UpdateErrorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateErrorsViewModel @Inject constructor(
    private val updateErrorRepository: UpdateErrorRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateErrorsState())
    val state: StateFlow<UpdateErrorsState> = _state.asStateFlow()

    private val _effect = Channel<UpdateErrorsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        updateErrorRepository.observeErrors()
            .onEach { errors ->
                val stillErroredIds = errors.map { it.mangaId }.toSet()
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorsByMessage = errors.groupBy { error -> error.errorMessage },
                        // Drop selections for manga whose error was resolved elsewhere (e.g. a
                        // background update succeeding) so selection never references a stale id.
                        selectedMangaIds = it.selectedMangaIds intersect stillErroredIds,
                    )
                }
            }
            .catch { _state.update { it.copy(isLoading = false) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: UpdateErrorsEvent) {
        when (event) {
            is UpdateErrorsEvent.CardClick -> onCardClick(event.mangaId)
            is UpdateErrorsEvent.ToggleSelection -> toggleSelection(event.mangaId)
            UpdateErrorsEvent.SelectAll -> selectAll()
            UpdateErrorsEvent.InvertSelection -> invertSelection()
            UpdateErrorsEvent.ClearSelection -> _state.update { it.copy(selectedMangaIds = emptySet()) }
            is UpdateErrorsEvent.DeleteError -> deleteError(event.mangaId)
            UpdateErrorsEvent.DeleteSelected -> deleteSelected()
            UpdateErrorsEvent.ClearAll -> clearAll()
            UpdateErrorsEvent.MigrateSelected -> migrateSelected()
        }
    }

    private fun allMangaIds(): Set<Long> =
        _state.value.errorsByMessage.values.flatten().map { it.mangaId }.toSet()

    private fun onCardClick(mangaId: Long) {
        if (_state.value.isSelectionMode) {
            toggleSelection(mangaId)
        } else {
            viewModelScope.launch { _effect.send(UpdateErrorsEffect.NavigateToManga(mangaId)) }
        }
    }

    private fun toggleSelection(mangaId: Long) {
        _state.update { st ->
            val sel = st.selectedMangaIds
            st.copy(selectedMangaIds = if (mangaId in sel) sel - mangaId else sel + mangaId)
        }
    }

    private fun selectAll() {
        _state.update { it.copy(selectedMangaIds = allMangaIds()) }
    }

    private fun invertSelection() {
        _state.update { it.copy(selectedMangaIds = allMangaIds() - it.selectedMangaIds) }
    }

    private fun deleteError(mangaId: Long) {
        viewModelScope.launch { updateErrorRepository.clearError(mangaId) }
    }

    private fun deleteSelected() {
        val ids = _state.value.selectedMangaIds
        if (ids.isEmpty()) return
        _state.update { it.copy(selectedMangaIds = emptySet()) }
        viewModelScope.launch {
            ids.forEach { updateErrorRepository.clearError(it) }
        }
    }

    private fun clearAll() {
        _state.update { it.copy(selectedMangaIds = emptySet()) }
        viewModelScope.launch { updateErrorRepository.clearAllErrors() }
    }

    private fun migrateSelected() {
        val ids = _state.value.selectedMangaIds.toList()
        if (ids.isEmpty()) return
        _state.update { it.copy(selectedMangaIds = emptySet()) }
        viewModelScope.launch { _effect.send(UpdateErrorsEffect.NavigateToMigration(ids)) }
    }
}
