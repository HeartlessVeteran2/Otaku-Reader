package app.komikku.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
class BrowseViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(BrowseState())
    val state = _state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BrowseState(),
    )

    private val _effect = Channel<BrowseEffect>()
    val effect = _effect.receiveAsFlow()

    fun onEvent(event: BrowseEvent) {
        when (event) {
            is BrowseEvent.OnMangaClick -> navigateToDetail(event.mangaId)
            is BrowseEvent.OnSearchQueryChange -> _state.update { it.copy(searchQuery = event.query) }
            is BrowseEvent.OnSearch -> performSearch()
        }
    }

    private fun navigateToDetail(mangaId: Long) {
        viewModelScope.launch { _effect.send(BrowseEffect.NavigateToMangaDetail(mangaId)) }
    }

    private fun performSearch() {
        // TODO: implement search via source-api
    }
}
