package app.otakureader.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.usecase.GetReadingStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val getReadingStatsUseCase: GetReadingStatsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(StatisticsState())
    val state: StateFlow<StatisticsState> = _state.asStateFlow()

    private var statsJob: Job? = null

    init {
        loadStats()
    }

    fun onEvent(event: StatisticsEvent) {
        when (event) {
            is StatisticsEvent.Refresh -> loadStats()
        }
    }

    private fun loadStats() {
        statsJob?.cancel()
        _state.update { it.copy(isLoading = true) }
        statsJob = getReadingStatsUseCase()
            .onEach { stats ->
                _state.update { it.copy(isLoading = false, stats = stats, error = null) }
            }
            .catch { error ->
                _state.update { it.copy(isLoading = false, error = error.message) }
            }
            .launchIn(viewModelScope)
    }
}
