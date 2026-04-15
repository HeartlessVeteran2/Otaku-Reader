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
}
