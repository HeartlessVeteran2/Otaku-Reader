package app.otakureader.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.repository.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val FEED_ITEMS_LIMIT = 100

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val feedRepository: FeedRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val state = combine(
        feedRepository.getFeedItems(limit = FEED_ITEMS_LIMIT),
        feedRepository.getFeedSources(),
        _isLoading,
        _error
    ) { feedItems, sources, isLoading, error ->
        FeedState(
            isLoading = isLoading,
            feedItems = feedItems,
            feedSources = sources,
            error = error
        )
    }.catch { e ->
        // Emit an error state so the UI can display the error message.
        emit(FeedState(error = e.message))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FeedState(isLoading = true)
    )

    private val _effect = Channel<FeedEffect>()
    val effect = _effect.receiveAsFlow()

    fun onEvent(event: FeedEvent) {
        when (event) {
            is FeedEvent.Refresh -> refresh()
            is FeedEvent.OnFeedItemClick -> navigateToReader(event.mangaId, event.chapterId)
            is FeedEvent.OnMarkAsRead -> markAsRead(event.feedItemId)
            is FeedEvent.OnToggleSource -> toggleSource(event.sourceId, event.enabled)
            is FeedEvent.ClearHistory -> clearHistory()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                feedRepository.refreshFeed()
            } catch (e: Exception) {
                _error.update { e.message }
            } finally {
                _isLoading.update { false }
            }
        }
    }

    private fun navigateToReader(mangaId: Long, chapterId: Long) {
        viewModelScope.launch {
            _effect.send(FeedEffect.NavigateToReader(mangaId, chapterId))
        }
    }

    private fun markAsRead(feedItemId: Long) {
        viewModelScope.launch {
            try {
                feedRepository.markFeedItemAsRead(feedItemId)
            } catch (e: Exception) {
                _effect.send(FeedEffect.ShowSnackbar(e.message ?: "Failed to mark as read"))
            }
        }
    }

    private fun toggleSource(sourceId: Long, enabled: Boolean) {
        viewModelScope.launch {
            try {
                feedRepository.toggleFeedSource(sourceId, enabled)
            } catch (e: Exception) {
                _effect.send(FeedEffect.ShowSnackbar(e.message ?: "Failed to update source"))
            }
        }
    }

    private fun clearHistory() {
        viewModelScope.launch {
            try {
                feedRepository.clearFeedHistory()
            } catch (e: Exception) {
                _effect.send(FeedEffect.ShowSnackbar(e.message ?: "Failed to clear history"))
            }
        }
    }
}
