package app.otakureader.feature.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.domain.usecase.GetRecentUpdatesUseCase
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
class UpdatesViewModel @Inject constructor(
    private val getRecentUpdatesUseCase: GetRecentUpdatesUseCase,
    private val generalPreferences: GeneralPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(UpdatesState())
    val state: StateFlow<UpdatesState> = _state.asStateFlow()

    private val _effect = Channel<UpdatesEffect>()
    val effect = _effect.receiveAsFlow()

    init {
        loadUpdates()
        markUpdatesViewed()
    }

    fun onEvent(event: UpdatesEvent) {
        when (event) {
            UpdatesEvent.Refresh -> loadUpdates()
            is UpdatesEvent.OnChapterClick -> {
                viewModelScope.launch {
                    _effect.send(UpdatesEffect.NavigateToReader(event.mangaId, event.chapterId))
                }
            }
            
            // Update Error Screen events
            UpdatesEvent.ShowUpdateErrors -> {
                _state.update { it.copy(showUpdateErrors = true) }
            }
            UpdatesEvent.HideUpdateErrors -> {
                _state.update { it.copy(showUpdateErrors = false) }
            }
            is UpdatesEvent.ClearUpdateError -> {
                _state.update { state ->
                    state.copy(updateErrors = state.updateErrors.filter { it.mangaId != event.mangaId })
                }
            }
            UpdatesEvent.ClearAllUpdateErrors -> {
                _state.update { it.copy(updateErrors = emptyList()) }
            }
            
            // To-Be-Updated Screen events
            UpdatesEvent.ShowPendingUpdates -> {
                _state.update { it.copy(showPendingUpdates = true) }
                loadPendingUpdates()
            }
            UpdatesEvent.HidePendingUpdates -> {
                _state.update { it.copy(showPendingUpdates = false) }
            }
            UpdatesEvent.StartLibraryUpdate -> {
                startLibraryUpdate()
            }
        }
    }

    private fun loadUpdates() {
        _state.update { it.copy(isLoading = true, error = null) }
        getRecentUpdatesUseCase()
            .onEach { updates ->
                _state.update { it.copy(isLoading = false, updates = updates) }
            }
            .catch { error ->
                _state.update { it.copy(isLoading = false, error = error.message) }
            }
            .launchIn(viewModelScope)
    }

    /** Record the current time so the Library badge counter resets to zero. */
    private fun markUpdatesViewed() {
        viewModelScope.launch {
            generalPreferences.setLastUpdatesViewedAt(System.currentTimeMillis())
        }
    }
    
    /** Load manga that will be checked during the next library update. */
    private fun loadPendingUpdates() {
        viewModelScope.launch {
            // TODO: This is a placeholder. In a real implementation, 
            // this would query the database for manga marked for updates.
            // For now, we'll show an empty list or mock data.
            _state.update { state ->
                state.copy(
                    pendingUpdates = emptyList() // Will be populated from database
                )
            }
        }
    }
    
    /** Start a manual library update. */
    private fun startLibraryUpdate() {
        viewModelScope.launch {
            // TODO: Trigger LibraryUpdateWorker to run immediately
            // For now, just hide the pending updates screen
            _state.update { it.copy(showPendingUpdates = false) }
        }
    }
}
