package app.otakureader.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.repository.FeedRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.sourceapi.toSourceId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedFeedViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    sourceRepository: SourceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SavedFeedState())
    val state: StateFlow<SavedFeedState> = _state.asStateFlow()

    private val _effect = Channel<SavedFeedEffect>()
    val effect = _effect.receiveAsFlow()

    init {
        // Combined rather than collected separately: what the user can add is defined by what is
        // installed *minus* what they already added, so a change to either side has to recompute
        // both halves of the state together.
        combine(
            feedRepository.getFeedSources(),
            sourceRepository.getSources(),
        ) { feedSources, installed ->
            val alreadyAdded = feedSources.mapTo(mutableSetOf()) { it.sourceId }
            feedSources to installed
                .map { FeedSourceOption(it.id.toSourceId(), it.name, it.lang) }
                .filterNot { it.sourceId in alreadyAdded }
                .sortedBy { it.name.lowercase() }
        }
            .onEach { (feedSources, available) ->
                _state.value = SavedFeedState(sources = feedSources, availableSources = available)
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: SavedFeedEvent) {
        when (event) {
            is SavedFeedEvent.AddSource -> addSource(event.sourceId, event.sourceName)
            is SavedFeedEvent.RemoveSource -> removeSource(event.sourceId)
            is SavedFeedEvent.ToggleSource -> toggleSource(event.sourceId, event.enabled)
        }
    }

    private fun addSource(sourceId: Long, sourceName: String) {
        viewModelScope.launch {
            try {
                feedRepository.addFeedSource(sourceId, sourceName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(SavedFeedEffect.ShowSnackbar("Failed to add source: ${e.message}"))
            }
        }
    }

    private fun removeSource(sourceId: Long) {
        viewModelScope.launch {
            try {
                feedRepository.removeFeedSource(sourceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(SavedFeedEffect.ShowSnackbar("Failed to remove source: ${e.message}"))
            }
        }
    }

    private fun toggleSource(sourceId: Long, enabled: Boolean) {
        viewModelScope.launch {
            try {
                feedRepository.toggleFeedSource(sourceId, enabled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effect.send(SavedFeedEffect.ShowSnackbar("Failed to update source: ${e.message}"))
            }
        }
    }
}
