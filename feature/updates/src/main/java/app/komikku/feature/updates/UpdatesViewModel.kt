package app.komikku.feature.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdatesViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(UpdatesState())
    val state = _state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UpdatesState(),
    )

    private val _effect = Channel<UpdatesEffect>()
    val effect = _effect.receiveAsFlow()

    fun onEvent(event: UpdatesEvent) {
        when (event) {
            UpdatesEvent.Refresh -> checkForUpdates()
            is UpdatesEvent.OnChapterClick -> {
                viewModelScope.launch {
                    _effect.send(UpdatesEffect.NavigateToReader(event.mangaId, event.chapterId))
                }
            }
        }
    }

    private fun checkForUpdates() {
        // TODO: implement update checking
    }
}
