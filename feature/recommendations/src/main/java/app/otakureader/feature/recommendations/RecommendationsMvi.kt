package app.otakureader.feature.recommendations

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.domain.model.MangaRecommendation

data class RecommendationsState(
    val isLoading: Boolean = true,
    val recommendations: List<MangaRecommendation> = emptyList(),
    val error: String? = null,
    val isAiEnabled: Boolean = true,
    val lastRefreshedAt: Long? = null,
    val isCacheExpired: Boolean = false,
) : UiState

sealed interface RecommendationsEvent : UiEvent {
    data object Refresh : RecommendationsEvent
    data object ForceRefresh : RecommendationsEvent
    data class DismissRecommendation(val id: String) : RecommendationsEvent
    data class OnRecommendationClick(val recommendation: MangaRecommendation) : RecommendationsEvent
    data object DismissError : RecommendationsEvent
}

sealed interface RecommendationsEffect : UiEffect {
    data class NavigateToSearch(val query: String) : RecommendationsEffect
    data class ShowSnackbar(val message: String) : RecommendationsEffect
}
