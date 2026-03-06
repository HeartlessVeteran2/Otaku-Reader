package app.komikku.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.komikku.core.navigation.KomikkuDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<KomikkuDestinations.ReaderRoute>()
    val mangaId: Long = route.mangaId
    val chapterId: Long = route.chapterId

    private val _state = MutableStateFlow(ReaderState())
    val state = _state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReaderState(),
    )

    private val _effect = Channel<ReaderEffect>()
    val effect = _effect.receiveAsFlow()

    init {
        loadChapter()
    }

    fun onEvent(event: ReaderEvent) {
        when (event) {
            is ReaderEvent.OnPageChange -> _state.update { it.copy(currentPage = event.page) }
            is ReaderEvent.OnBackClick -> viewModelScope.launch { _effect.send(ReaderEffect.NavigateBack) }
        }
    }

    private fun loadChapter() {
        _state.update { it.copy(isLoading = false) }
    }
}
