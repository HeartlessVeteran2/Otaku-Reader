package app.otakureader.feature.statistics

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.domain.model.ReadingStats

data class StatisticsState(
    val isLoading: Boolean = true,
    val stats: ReadingStats = ReadingStats(),
    val error: String? = null
) : UiState

sealed interface StatisticsEvent : UiEvent {
    data object Refresh : StatisticsEvent
}

sealed interface StatisticsEffect : UiEffect
