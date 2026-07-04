package app.otakureader.feature.statistics

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.domain.model.Achievement
import app.otakureader.domain.model.ReadingGoal
import app.otakureader.domain.model.ReadingStats

enum class StatsPeriod(val days: Int?) {
    ALL(null),
    DAYS_90(90),
    DAYS_30(30),
    DAYS_7(7),
}

data class StatisticsState(
    val isLoading: Boolean = true,
    val stats: ReadingStats = ReadingStats(),
    val readingGoal: ReadingGoal = ReadingGoal(),
    val achievements: List<Achievement> = emptyList(),
    val selectedPeriod: StatsPeriod = StatsPeriod.ALL,
    val error: String? = null,
    /** Chapters with files on disk; null while the one-shot filesystem scan is still running. */
    val downloadedChapterCount: Int? = null,
) : UiState

sealed interface StatisticsEvent : UiEvent {
    data object Refresh : StatisticsEvent
    data object LoadAchievements : StatisticsEvent
    data class SelectPeriod(val period: StatsPeriod) : StatisticsEvent
}

sealed interface StatisticsEffect : UiEffect
