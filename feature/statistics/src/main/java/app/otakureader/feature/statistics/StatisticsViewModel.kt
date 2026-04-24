package app.otakureader.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.ReadingGoalPreferences
import app.otakureader.domain.ai.AiFeature
import app.otakureader.domain.ai.AiFeatureGate
import app.otakureader.domain.model.ReadingStats
import app.otakureader.domain.repository.StatisticsRepository
import app.otakureader.domain.usecase.GetReadingStatsUseCase
import app.otakureader.domain.usecase.ai.GenerateReadingInsightsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val getReadingStatsUseCase: GetReadingStatsUseCase,
    private val statisticsRepository: StatisticsRepository,
    private val readingGoalPreferences: ReadingGoalPreferences,
    private val generateReadingInsightsUseCase: GenerateReadingInsightsUseCase,
    private val aiFeatureGate: AiFeatureGate
) : ViewModel() {

    private val _state = MutableStateFlow(StatisticsState())
    val state: StateFlow<StatisticsState> = _state.asStateFlow()

    private var statsJob: Job? = null

    init {
        checkAiFeature()
        loadStats()
    }

    fun onEvent(event: StatisticsEvent) {
        when (event) {
            is StatisticsEvent.Refresh -> loadStats()
            is StatisticsEvent.RefreshInsights -> loadInsights(_state.value.stats)
        }
    }

    private fun checkAiFeature() {
        viewModelScope.launch {
            val available = aiFeatureGate.isFeatureAvailable(AiFeature.READING_INSIGHTS)
            _state.update { it.copy(isAiInsightsEnabled = available) }
            if (available && _state.value.stats.totalChaptersRead > 0 && _state.value.insights.isEmpty()) {
                loadInsights(_state.value.stats)
            }
        }
    }

    private fun loadStats() {
        statsJob?.cancel()
        _state.update { it.copy(isLoading = true) }
        statsJob = combine(
            readingGoalPreferences.dailyChapterGoal,
            readingGoalPreferences.weeklyChapterGoal
        ) { daily, weekly -> Pair(daily, weekly) }
            .flatMapLatest { (dailyGoal, weeklyGoal) ->
                combine(
                    getReadingStatsUseCase(),
                    statisticsRepository.getReadingGoalProgress(dailyGoal, weeklyGoal)
                ) { stats, goalProgress -> Pair(stats, goalProgress) }
            }
            .onEach { (stats, goalProgress) ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        stats = stats,
                        readingGoal = goalProgress,
                        error = null
                    )
                }
                if (_state.value.isAiInsightsEnabled && _state.value.insights.isEmpty()) {
                    loadInsights(stats)
                }
            }
            .catch { error ->
                _state.update { it.copy(isLoading = false, error = error.message) }
            }
            .launchIn(viewModelScope)
    }

    private fun loadInsights(stats: ReadingStats) {
        if (stats.totalChaptersRead == 0) return
        viewModelScope.launch {
            _state.update { it.copy(insightsLoading = true) }
            generateReadingInsightsUseCase(stats).onSuccess { result ->
                _state.update { it.copy(insights = result.insights, insightsLoading = false) }
            }.onFailure {
                _state.update { it.copy(insightsLoading = false) }
            }
        }
    }
}
