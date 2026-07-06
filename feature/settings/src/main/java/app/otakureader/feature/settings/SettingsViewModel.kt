package app.otakureader.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.AppPreferences
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LocalSourcePreferences
import app.otakureader.core.preferences.ReadingGoalPreferences
import app.otakureader.domain.scheduler.CoverRefreshScheduler
import app.otakureader.domain.scheduler.ReminderScheduler
import app.otakureader.domain.repository.ChapterRepository
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
    private val trackerSyncDelegate: TrackerSyncSettingsDelegate,
    private val localSourcePreferences: LocalSourcePreferences,
    private val appPreferences: AppPreferences,
    private val readingGoalPreferences: ReadingGoalPreferences,
    private val generalPreferences: GeneralPreferences,
    private val readingReminderScheduler: ReminderScheduler,
    private val coverRefreshScheduler: CoverRefreshScheduler,
    private val chapterRepository: ChapterRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _effect = Channel<SettingsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        val update: ((SettingsState) -> SettingsState) -> Unit = { _state.update(it) }
        appearanceDelegate.startObserving(viewModelScope, update)
        readerDelegate.startObserving(viewModelScope, update)
        libraryDelegate.startObserving(viewModelScope, update)
        downloadDelegate.startObserving(viewModelScope, update)
        backupDelegate.startObserving(viewModelScope, update)
        trackerSyncDelegate.startObserving(viewModelScope, update)
        observeLocalSourcePreferences()
        observeMigrationPreferences()
        observeReadingGoalPreferences()
        observeImageCachePreferences()
        observeSecurityPreferences()
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
        when {
            handleMigrationEvent(event) -> Unit
            handleReadingGoalEvent(event) -> Unit
            handleDataManagementEvent(event) -> Unit
            handleSecurityEvent(event) -> Unit
            else -> when (event) {
                is SettingsEvent.SetLocalSourceDirectory ->
                    localSourcePreferences.setLocalSourceDirectory(event.path)
                is SettingsEvent.SetAllowLocalSourceHiddenFolders ->
                    localSourcePreferences.setAllowLocalSourceHiddenFolders(event.allowed)
                SettingsEvent.NavigateToAbout ->
                    _effect.send(SettingsEffect.NavigateToAbout)
                else -> Unit
            }
        }
    }

    private suspend fun handleMigrationEvent(event: SettingsEvent): Boolean = when (event) {
        is SettingsEvent.SetMigrationSimilarityThreshold ->
            { appPreferences.setMigrationSimilarityThreshold(event.threshold); true }
        is SettingsEvent.SetMigrationAlwaysConfirm ->
            { appPreferences.setMigrationAlwaysConfirm(event.enabled); true }
        is SettingsEvent.SetMigrationMinChapterCount ->
            { appPreferences.setMigrationMinChapterCount(event.count); true }
        SettingsEvent.OnNavigateToMigration ->
            { _effect.send(SettingsEffect.NavigateToMigrationEntry); true }
        else -> false
    }

    private suspend fun handleReadingGoalEvent(event: SettingsEvent): Boolean = when (event) {
        is SettingsEvent.SetDailyChapterGoal ->
            { readingGoalPreferences.setDailyChapterGoal(event.goal); true }
        is SettingsEvent.SetWeeklyChapterGoal ->
            { readingGoalPreferences.setWeeklyChapterGoal(event.goal); true }
        is SettingsEvent.SetReadingRemindersEnabled ->
            { handleSetReadingRemindersEnabled(event.enabled); true }
        is SettingsEvent.SetReadingReminderHour ->
            { handleSetReadingReminderHour(event.hour); true }
        else -> false
    }

    private suspend fun handleDataManagementEvent(event: SettingsEvent): Boolean = when (event) {
        SettingsEvent.ClearImageCache -> { clearImageCache(); true }
        SettingsEvent.RefreshLibraryCovers -> { refreshLibraryCovers(); true }
        SettingsEvent.ClearHistory -> { clearHistory(); true }
        is SettingsEvent.SetCoilDiskCacheSizeMb ->
            { generalPreferences.setCoilDiskCacheSizeMb(event.sizeMb); true }
        else -> false
    }

    private suspend fun handleSecurityEvent(event: SettingsEvent): Boolean = when (event) {
        is SettingsEvent.SetBiometricLockEnabled ->
            { generalPreferences.setBiometricLockEnabled(event.enabled); true }
        is SettingsEvent.SetBiometricLockTimeout ->
            { generalPreferences.setBiometricLockTimeoutMinutes(event.minutes); true }
        is SettingsEvent.SetBiometricLockScheduleEnabled ->
            { generalPreferences.setBiometricLockScheduleEnabled(event.enabled); true }
        is SettingsEvent.SetBiometricLockStartHour ->
            { generalPreferences.setBiometricLockStartHour(event.hour); true }
        is SettingsEvent.SetBiometricLockEndHour ->
            { generalPreferences.setBiometricLockEndHour(event.hour); true }
        is SettingsEvent.SetBiometricLockActiveDays ->
            { generalPreferences.setBiometricLockActiveDays(event.days); true }
        else -> false
    }

    private fun observeSecurityPreferences() {
        viewModelScope.launch {
            combine(
                generalPreferences.biometricLockEnabled,
                generalPreferences.biometricLockTimeoutMinutes,
            ) { enabled, timeout ->
                _state.update { it.copy(
                    biometricLockEnabled = enabled,
                    biometricLockTimeoutMinutes = timeout,
                ) }
            }.collect { }
        }
        viewModelScope.launch {
            combine(
                generalPreferences.biometricLockScheduleEnabled,
                generalPreferences.biometricLockStartHour,
                generalPreferences.biometricLockEndHour,
                generalPreferences.biometricLockActiveDays,
            ) { schedEnabled, startHour, endHour, activeDays ->
                _state.update { it.copy(
                    biometricLockScheduleEnabled = schedEnabled,
                    biometricLockStartHour = startHour,
                    biometricLockEndHour = endHour,
                    biometricLockActiveDays = activeDays,
                ) }
            }.collect { }
        }
    }

    private fun observeLocalSourcePreferences() {
        viewModelScope.launch {
            localSourcePreferences.localSourceDirectory.collect { dir ->
                _state.update { it.copy(localSourceDirectory = dir) }
            }
        }
        viewModelScope.launch {
            localSourcePreferences.allowLocalSourceHiddenFolders.collect { allowed ->
                _state.update { it.copy(allowLocalSourceHiddenFolders = allowed) }
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

    private fun observeImageCachePreferences() {
        viewModelScope.launch {
            generalPreferences.coilDiskCacheSizeMb.collect { sizeMb ->
                _state.update { it.copy(coilDiskCacheSizeMb = sizeMb) }
            }
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _effect.send(SettingsEffect.ShowSnackbar(context.getString(R.string.settings_clear_cache_failed)))
            }
        }
    }

    private fun refreshLibraryCovers() {
        coverRefreshScheduler.schedule()
        viewModelScope.launch {
            _effect.send(SettingsEffect.ShowSnackbar(context.getString(R.string.settings_refresh_covers_started)))
        }
    }

    private fun clearHistory() {
        viewModelScope.launch {
            try {
                chapterRepository.clearAllHistory()
                _effect.send(SettingsEffect.ShowSnackbar(context.getString(R.string.settings_clear_history_success)))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _effect.send(SettingsEffect.ShowSnackbar(context.getString(R.string.settings_clear_history_failed)))
            }
        }
    }
}
