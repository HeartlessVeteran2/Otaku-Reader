package app.otakureader.feature.recommendations

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState

data class RecommendationsState(
    val isLoading: Boolean = false,
    val isAiEnabled: Boolean = false,
) : UiState

sealed interface RecommendationsEvent : UiEvent {
    data object Refresh : RecommendationsEvent
    data object ForceRefresh : RecommendationsEvent
    data object DismissError : RecommendationsEvent
}

sealed interface RecommendationsEffect : UiEffect {
    data class ShowSnackbar(val message: String) : RecommendationsEffect
}
