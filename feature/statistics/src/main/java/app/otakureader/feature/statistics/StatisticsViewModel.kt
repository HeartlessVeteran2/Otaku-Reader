package app.otakureader.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.ReadingGoalPreferences
import app.otakureader.domain.model.ReadingStats
import app.otakureader.domain.repository.AchievementRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.StatisticsRepository
import app.otakureader.domain.usecase.GetReadingStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    private val achievementRepository: AchievementRepository,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StatisticsState())
    val state: StateFlow<StatisticsState> = _state.asStateFlow()

    private var statsJob: Job? = null

    init {
        loadStats()
        loadDownloadedChapterCount()
        achievementRepository.observeAll()
            .onEach { achievements ->
                _state.update { it.copy(achievements = achievements) }
            }
            .catch { /* non-fatal: achievements are supplementary */ }
            .launchIn(viewModelScope)
    }

    /**
     * One-shot filesystem scan — downloads are file-based (completed rows are removed from the
     * download queue table), so this count cannot come from a reactive DB query.
     * [DownloadRepository.reindexDownloads] is a pure scan whose `verifiedDownloads` count is
     * exactly "chapter folders with real content on disk".
     */
    private fun loadDownloadedChapterCount() {
        viewModelScope.launch {
            try {
                val result = downloadRepository.reindexDownloads()
                _state.update { it.copy(downloadedChapterCount = result.verifiedDownloads) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Non-fatal: the tile just stays hidden if the scan fails.
            }
        }
    }

    fun onEvent(event: StatisticsEvent) {
        when (event) {
            is StatisticsEvent.Refresh -> loadStats()
            is StatisticsEvent.LoadAchievements -> { /* observer already active from init */ }
            is StatisticsEvent.SelectPeriod -> {
                _state.update { it.copy(selectedPeriod = event.period) }
                loadStats(event.period)
            }
        }
    }

    private fun loadStats(period: StatsPeriod = _state.value.selectedPeriod) {
        statsJob?.cancel()
        _state.update { it.copy(isLoading = true) }
        val sinceMs = period.days?.let { System.currentTimeMillis() - it * 86_400_000L }
        val statsFlow = if (sinceMs != null) getReadingStatsUseCase(sinceMs = sinceMs) else getReadingStatsUseCase()
        statsJob = combine(
            readingGoalPreferences.dailyChapterGoal,
            readingGoalPreferences.weeklyChapterGoal
        ) { daily, weekly -> Pair(daily, weekly) }
            .flatMapLatest { (dailyGoal, weeklyGoal) ->
                combine(
                    statsFlow,
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
            }
            .catch { error ->
                _state.update { it.copy(isLoading = false, error = error.message) }
            }
            .launchIn(viewModelScope)
    }
}
