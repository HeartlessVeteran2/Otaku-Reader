package app.otakureader.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.AppPreferences
import app.otakureader.core.preferences.LocalSourcePreferences
import app.otakureader.core.preferences.ReadingGoalPreferences
import app.otakureader.data.worker.ReadingReminderScheduler
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.feature.settings.delegate.AiSettingsDelegate
import app.otakureader.feature.settings.delegate.AppearanceSettingsDelegate
import app.otakureader.feature.settings.delegate.BackupSettingsDelegate
import app.otakureader.feature.settings.delegate.DownloadSettingsDelegate
import app.otakureader.feature.settings.delegate.LibrarySettingsDelegate
import app.otakureader.feature.settings.delegate.ReaderSettingsDelegate
import app.otakureader.feature.settings.delegate.TrackerSyncSettingsDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appearanceDelegate: AppearanceSettingsDelegate,
    private val readerDelegate: ReaderSettingsDelegate,
    private val libraryDelegate: LibrarySettingsDelegate,
    private val downloadDelegate: DownloadSettingsDelegate,
    private val backupDelegate: BackupSettingsDelegate,
    private val aiDelegate: AiSettingsDelegate,
    private val trackerSyncDelegate: TrackerSyncSettingsDelegate,
    private val localSourcePreferences: LocalSourcePreferences,
    private val appPreferences: AppPreferences,
    private val readingGoalPreferences: ReadingGoalPreferences,
    private val readingReminderScheduler: ReadingReminderScheduler,
    private val chapterRepository: ChapterRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _effect = Channel<SettingsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        viewModelScope.launch {
            try {
                aiDelegate.initAiPrefs()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _effect.send(SettingsEffect.ShowSnackbar("Failed to load AI settings. You may need to re-enter your API key."))
            }
        }
        val update: ((SettingsState) -> SettingsState) -> Unit = { _state.update(it) }
        appearanceDelegate.startObserving(viewModelScope, update)
        readerDelegate.startObserving(viewModelScope, update)
        libraryDelegate.startObserving(viewModelScope, update)
        downloadDelegate.startObserving(viewModelScope, update)
        backupDelegate.startObserving(viewModelScope, update)
        aiDelegate.startObserving(viewModelScope, update)
        trackerSyncDelegate.startObserving(viewModelScope, update)
        observeLocalSourcePreferences()
        observeMigrationPreferences()
        observeReadingGoalPreferences()
    }

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            val send: suspend (SettingsEffect) -> Unit = { _effect.send(it) }
            when {
                appearanceDelegate.handleEvent(event, send) -> Unit
                readerDelegate.handleEvent(event, send) -> Unit
                libraryDelegate.handleEvent(event, send) -> Unit
                downloadDelegate.handleEvent(event, send) -> Unit
                backupDelegate.handleEvent(event, send) -> Unit
                aiDelegate.handleEvent(event, send) -> Unit
                trackerSyncDelegate.handleEvent(event, send) -> Unit
                else -> handleRemainingEvent(event)
            }
        }
    }

    // Delegates provide a public shim used by the backup file-picker result callbacks in
    // SettingsScreen (the screen calls these directly because ActivityResultLauncher callbacks
    // are not routed through onEvent).
    fun createBackup(uri: android.net.Uri) { onEvent(SettingsEvent.CreateBackupWithUri(uri)) }
    fun restoreBackup(uri: android.net.Uri) { onEvent(SettingsEvent.RestoreBackupFromUri(uri)) }

    private suspend fun handleRemainingEvent(event: SettingsEvent) {
        when (event) {
            // Local source
            is SettingsEvent.SetLocalSourceDirectory ->
                localSourcePreferences.setLocalSourceDirectory(event.path)

            // Migration
            is SettingsEvent.SetMigrationSimilarityThreshold ->
                appPreferences.setMigrationSimilarityThreshold(event.threshold)
            is SettingsEvent.SetMigrationAlwaysConfirm ->
                appPreferences.setMigrationAlwaysConfirm(event.enabled)
            is SettingsEvent.SetMigrationMinChapterCount ->
                appPreferences.setMigrationMinChapterCount(event.count)
            SettingsEvent.OnNavigateToMigration ->
                _effect.send(SettingsEffect.NavigateToMigrationEntry)

            // Reading goals
            is SettingsEvent.SetDailyChapterGoal ->
                readingGoalPreferences.setDailyChapterGoal(event.goal)
            is SettingsEvent.SetWeeklyChapterGoal ->
                readingGoalPreferences.setWeeklyChapterGoal(event.goal)
            is SettingsEvent.SetReadingRemindersEnabled ->
                handleSetReadingRemindersEnabled(event.enabled)
            is SettingsEvent.SetReadingReminderHour ->
                handleSetReadingReminderHour(event.hour)

            // Data management
            SettingsEvent.ClearImageCache -> clearImageCache()
            SettingsEvent.ClearHistory -> clearHistory()

            // Navigation
            SettingsEvent.NavigateToAbout -> _effect.send(SettingsEffect.NavigateToAbout)

            else -> Unit
        }
    }

    private fun observeLocalSourcePreferences() {
        viewModelScope.launch {
            localSourcePreferences.localSourceDirectory.collect { dir ->
                _state.update { it.copy(localSourceDirectory = dir) }
            }
        }
    }

    private fun observeMigrationPreferences() {
        viewModelScope.launch {
            combine(
                appPreferences.migrationSimilarityThreshold,
                appPreferences.migrationAlwaysConfirm,
                appPreferences.migrationMinChapterCount
            ) { threshold, alwaysConfirm, minChapters ->
                _state.update { it.copy(
                    migrationSimilarityThreshold = threshold,
                    migrationAlwaysConfirm = alwaysConfirm,
                    migrationMinChapterCount = minChapters,
                ) }
            }.collect { }
        }
    }

    private fun observeReadingGoalPreferences() {
        viewModelScope.launch {
            combine(
                readingGoalPreferences.dailyChapterGoal,
                readingGoalPreferences.weeklyChapterGoal,
                readingGoalPreferences.remindersEnabled,
                readingGoalPreferences.reminderHour
            ) { daily, weekly, reminders, hour ->
                _state.update { it.copy(
                    dailyChapterGoal = daily,
                    weeklyChapterGoal = weekly,
                    readingRemindersEnabled = reminders,
                    readingReminderHour = hour,
                ) }
            }.collect { }
        }
    }

    private suspend fun handleSetReadingRemindersEnabled(enabled: Boolean) {
        readingGoalPreferences.setRemindersEnabled(enabled)
        if (enabled) {
            val hour = readingGoalPreferences.reminderHour.first()
            readingReminderScheduler.schedule(hour)
        } else {
            readingReminderScheduler.cancel()
        }
    }

    private suspend fun handleSetReadingReminderHour(hour: Int) {
        readingGoalPreferences.setReminderHour(hour)
        if (readingGoalPreferences.remindersEnabled.first()) {
            readingReminderScheduler.schedule(hour)
        }
    }

    private fun clearImageCache() {
        viewModelScope.launch {
            try {
                val cacheDir = context.cacheDir.resolve("image_cache")
                val deleted = !cacheDir.exists() || cacheDir.deleteRecursively()
                if (deleted) {
                    _effect.send(SettingsEffect.ShowSnackbar(context.getString(R.string.settings_clear_cache_success)))
                } else {
                    _effect.send(SettingsEffect.ShowSnackbar(context.getString(R.string.settings_clear_cache_failed)))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _effect.send(SettingsEffect.ShowSnackbar(context.getString(R.string.settings_clear_cache_failed)))
            }
        }
    }

    private fun clearHistory() {
        viewModelScope.launch {
            try {
                chapterRepository.clearAllHistory()
                _effect.send(SettingsEffect.ShowSnackbar(context.getString(R.string.settings_clear_history_success)))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _effect.send(SettingsEffect.ShowSnackbar(context.getString(R.string.settings_clear_history_failed)))
            }
        }
    }
}
