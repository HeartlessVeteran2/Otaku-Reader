package app.otakureader.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.ReadingGoalPreferences
import app.otakureader.domain.model.DownloadStatus
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.ReaderSettingsRepository
import app.otakureader.domain.repository.StatisticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Mirrors Komikku's `DownloadQueueState`: whether the queue is empty, paused, or actively working. */
sealed interface DownloadQueueDisplayState {
    data object Stopped : DownloadQueueDisplayState
    data class Paused(val pending: Int) : DownloadQueueDisplayState
    data class Downloading(val pending: Int) : DownloadQueueDisplayState
}

data class MoreState(
    val currentStreak: Int = 0,
    val todayChaptersRead: Int = 0,
    val dailyGoal: Int = 0,
    val incognitoMode: Boolean = false,
    val downloadedOnly: Boolean = false,
    val downloadQueueState: DownloadQueueDisplayState = DownloadQueueDisplayState.Stopped,
)

sealed interface MoreEvent {
    data class SetIncognitoMode(val enabled: Boolean) : MoreEvent
    data class SetDownloadedOnly(val enabled: Boolean) : MoreEvent
}

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val statisticsRepository: StatisticsRepository,
    private val readingGoalPreferences: ReadingGoalPreferences,
    private val readerSettingsRepository: ReaderSettingsRepository,
    private val generalPreferences: GeneralPreferences,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    companion object {
        private const val SUBSCRIBE_STOP_TIMEOUT_MS = 5_000L
    }

    private val goalProgress = combine(
        readingGoalPreferences.dailyChapterGoal,
        readingGoalPreferences.weeklyChapterGoal,
    ) { daily, weekly -> daily to weekly }
        .distinctUntilChanged()
        .flatMapLatest { (daily, weekly) ->
            statisticsRepository.getReadingGoalProgress(daily, weekly)
        }

    private val downloadQueueState = downloadRepository.observeDownloads()
        .map { downloads ->
            val active = downloads.filter { it.isActive }
            when {
                active.isEmpty() -> DownloadQueueDisplayState.Stopped
                active.any { it.status == DownloadStatus.DOWNLOADING } ->
                    DownloadQueueDisplayState.Downloading(pending = active.size)
                else -> DownloadQueueDisplayState.Paused(pending = active.size)
            }
        }
        .distinctUntilChanged()

    val state: StateFlow<MoreState> =
        combine(
            goalProgress,
            readerSettingsRepository.incognitoMode,
            generalPreferences.downloadedOnly,
            downloadQueueState,
        ) { goalProgress, incognito, downloadedOnly, downloadQueueState ->
            MoreState(
                currentStreak = goalProgress.currentStreak,
                todayChaptersRead = goalProgress.dailyProgress,
                dailyGoal = goalProgress.dailyGoal,
                incognitoMode = incognito,
                downloadedOnly = downloadedOnly,
                downloadQueueState = downloadQueueState,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIBE_STOP_TIMEOUT_MS),
                initialValue = MoreState(),
            )

    fun onEvent(event: MoreEvent) {
        when (event) {
            is MoreEvent.SetIncognitoMode -> viewModelScope.launch {
                readerSettingsRepository.setIncognitoMode(event.enabled)
            }
            is MoreEvent.SetDownloadedOnly -> viewModelScope.launch {
                generalPreferences.setDownloadedOnly(event.enabled)
            }
        }
    }
}
