package app.otakureader.feature.recommendations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecommendationsViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(RecommendationsState())
    val state: StateFlow<RecommendationsState> = _state.asStateFlow()

    private val _effects = Channel<RecommendationsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        _state.update { it.copy(isLoading = false, isAiEnabled = false) }
    }

    fun onEvent(event: RecommendationsEvent) {
        when (event) {
            RecommendationsEvent.Refresh -> Unit
            RecommendationsEvent.ForceRefresh -> Unit
            is RecommendationsEvent.DismissRecommendation -> Unit
            is RecommendationsEvent.OnRecommendationClick -> {
                viewModelScope.launch {
                    _effects.send(RecommendationsEffect.NavigateToSearch(event.recommendation.title))
                }
            }
            RecommendationsEvent.DismissError -> _state.update { it.copy(error = null) }
        }
    }
}
