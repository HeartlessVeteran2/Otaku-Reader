package app.otakureader.feature.recommendations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.ai.AiFeature
import app.otakureader.domain.ai.AiFeatureGate
import app.otakureader.domain.repository.RecommendationRepository
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
class RecommendationsViewModel @Inject constructor(
    private val recommendationRepository: RecommendationRepository,
    private val aiFeatureGate: AiFeatureGate,
) : ViewModel() {

    private val _state = MutableStateFlow(RecommendationsState())
    val state: StateFlow<RecommendationsState> = _state.asStateFlow()

    private val _effects = Channel<RecommendationsEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        checkAiAvailability()
        observeRecommendations()
        refresh(forceRefresh = false)
    }

    fun onEvent(event: RecommendationsEvent) {
        when (event) {
            RecommendationsEvent.Refresh -> refresh(forceRefresh = false)
            RecommendationsEvent.ForceRefresh -> refresh(forceRefresh = true)
            is RecommendationsEvent.DismissRecommendation -> dismissRecommendation(event.id)
            is RecommendationsEvent.OnRecommendationClick -> {
                viewModelScope.launch {
                    _effects.send(RecommendationsEffect.NavigateToSearch(event.recommendation.title))
                }
            }
            RecommendationsEvent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun checkAiAvailability() {
        viewModelScope.launch {
            val available = aiFeatureGate.isFeatureAvailable(AiFeature.RECOMMENDATIONS)
            _state.update { it.copy(isAiEnabled = available) }
        }
    }

    private fun observeRecommendations() {
        recommendationRepository.observeRecommendations()
            .onEach { result ->
                if (result != null) {
                    val now = System.currentTimeMillis()
                    _state.update { state ->
                        state.copy(
                            recommendations = result.recommendations,
                            lastRefreshedAt = result.refreshedAt,
                            isCacheExpired = result.expiresAt < now,
                            isLoading = false,
                        )
                    }
                }
            }
            .catch { error ->
                _state.update { it.copy(isLoading = false, error = error.message) }
            }
            .launchIn(viewModelScope)
    }

    private fun refresh(forceRefresh: Boolean) {
        if (_state.value.isLoading && !forceRefresh && _state.value.recommendations.isNotEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = recommendationRepository.getRecommendations(forceRefresh = forceRefresh)
            result.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message) }
            }
        }
    }

    private fun dismissRecommendation(id: String) {
        viewModelScope.launch {
            recommendationRepository.dismissRecommendation(id)
            _state.update { state ->
                state.copy(recommendations = state.recommendations.filterNot { "${it.title}|${it.sourceId}" == id })
            }
        }
    }
}
