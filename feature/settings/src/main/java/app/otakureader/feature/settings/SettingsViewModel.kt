package app.otakureader.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.AppPreferences
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.core.preferences.LocalSourcePreferences
import app.otakureader.core.preferences.ReaderPreferences
import app.otakureader.data.tracking.TrackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val generalPreferences: GeneralPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val readerPreferences: ReaderPreferences,
    private val downloadPreferences: DownloadPreferences,
    private val localSourcePreferences: LocalSourcePreferences,
    private val backupRepository: app.otakureader.data.backup.repository.BackupRepository,
    private val readerSettingsRepository: app.otakureader.feature.reader.repository.ReaderSettingsRepository,
    private val trackManager: TrackManager,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _effect = Channel<SettingsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        observePreferences()
        refreshTrackers()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                generalPreferences.themeMode,
                generalPreferences.useDynamicColor,
                generalPreferences.locale,
                generalPreferences.notificationsEnabled,
                generalPreferences.updateCheckInterval
            ) { themeMode, dynamicColor, locale, notificationsEnabled, updateInterval ->
                SettingsState(
                    themeMode = themeMode,
                    useDynamicColor = dynamicColor,
                    locale = locale,
                    notificationsEnabled = notificationsEnabled,
                    updateCheckInterval = updateInterval
                    // deleteAfterReading feature has been removed
                )
            }.combine(generalPreferences.notificationsEnabled) { state, notificationsEnabled ->
                state.copy(notificationsEnabled = notificationsEnabled)
            }.combine(generalPreferences.updateCheckInterval) { state, updateInterval ->
                state.copy(updateCheckInterval = updateInterval)
            }.combine(downloadPreferences.deleteAfterReading) { state, deleteAfterReading ->
                state.copy(deleteAfterReading = deleteAfterReading)
            }.combine(libraryPreferences.gridSize) { state, gridSize ->
                state.copy(libraryGridSize = gridSize)
            }.combine(libraryPreferences.showBadges) { state, showBadges ->
                state.copy(showBadges = showBadges)
            }.combine(readerPreferences.readerMode) { state, readerMode ->
                state.copy(readerMode = readerMode)
            }.combine(readerPreferences.keepScreenOn) { state, keepScreenOn ->
                state.copy(keepScreenOn = keepScreenOn)
            }.combine(readerSettingsRepository.incognitoMode) { state, incognitoMode ->
                state.copy(incognitoMode = incognitoMode)
            }.combine(downloadPreferences.autoDownloadEnabled) { state, autoDownloadEnabled ->
                state.copy(autoDownloadEnabled = autoDownloadEnabled)
            }.combine(downloadPreferences.downloadOnlyOnWifi) { state, downloadOnlyOnWifi ->
                state.copy(downloadOnlyOnWifi = downloadOnlyOnWifi)
            }.combine(downloadPreferences.autoDownloadLimit) { state, autoDownloadLimit ->
                state.copy(autoDownloadLimit = autoDownloadLimit)
            }.combine(downloadPreferences.saveAsCbz) { state, saveAsCbz ->
                state.copy(saveAsCbz = saveAsCbz)
            }.combine(localSourcePreferences.localSourceDirectory) { state, localDir ->
                state.copy(localSourceDirectory = localDir)
            }.combine(appPreferences.migrationSimilarityThreshold) { state, threshold ->
                state.copy(migrationSimilarityThreshold = threshold)
            }.combine(appPreferences.migrationAlwaysConfirm) { state, alwaysConfirm ->
                state.copy(migrationAlwaysConfirm = alwaysConfirm)
            }.combine(appPreferences.migrationMinChapterCount) { state, minChapters ->
                state.copy(migrationMinChapterCount = minChapters)
            }.collect { newState ->
                _state.update { current ->
                    newState.copy(
                        trackers = current.trackers,
                        trackingLoginInProgress = current.trackingLoginInProgress
                    )
                }
            }
        }
    }

    private fun refreshTrackers() {
        _state.update { it.copy(trackers = trackManager.all.map { t -> TrackerInfo(t.id, t.name, t.isLoggedIn) }) }
    }

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            when (event) {
                is SettingsEvent.SetThemeMode -> generalPreferences.setThemeMode(event.mode)
                is SettingsEvent.SetDynamicColor -> generalPreferences.setUseDynamicColor(event.enabled)
                is SettingsEvent.SetPureBlackDarkMode -> generalPreferences.setUsePureBlackDarkMode(event.enabled)
                is SettingsEvent.SetColorScheme -> generalPreferences.setColorScheme(event.scheme)
                is SettingsEvent.SetLocale -> generalPreferences.setLocale(event.locale)
                is SettingsEvent.SetNotificationsEnabled -> generalPreferences.setNotificationsEnabled(event.enabled)
                is SettingsEvent.SetUpdateInterval -> generalPreferences.setUpdateCheckInterval(event.hours)
                is SettingsEvent.SetLibraryGridSize -> libraryPreferences.setGridSize(event.size)
                is SettingsEvent.SetShowBadges -> libraryPreferences.setShowBadges(event.enabled)
                is SettingsEvent.SetReaderMode -> readerPreferences.setReaderMode(event.mode)
                is SettingsEvent.SetKeepScreenOn -> readerPreferences.setKeepScreenOn(event.enabled)
                is SettingsEvent.SetIncognitoMode -> readerSettingsRepository.setIncognitoMode(event.enabled)
                is SettingsEvent.SetDeleteAfterReading -> downloadPreferences.setDeleteAfterReading(event.enabled)
                is SettingsEvent.SetAutoDownloadEnabled -> downloadPreferences.setAutoDownloadEnabled(event.enabled)
                is SettingsEvent.SetDownloadOnlyOnWifi -> downloadPreferences.setDownloadOnlyOnWifi(event.enabled)
                is SettingsEvent.SetAutoDownloadLimit -> downloadPreferences.setAutoDownloadLimit(event.limit)
                is SettingsEvent.SetSaveAsCbz -> downloadPreferences.setSaveAsCbz(event.enabled)
                is SettingsEvent.SetLocalSourceDirectory -> localSourcePreferences.setLocalSourceDirectory(event.path)
                SettingsEvent.OnCreateBackup -> _effect.send(SettingsEffect.ShowBackupPicker)
                SettingsEvent.OnRestoreBackup -> _effect.send(SettingsEffect.ShowRestorePicker)
                is SettingsEvent.LoginTracker -> loginTracker(event.trackerId, event.username, event.password)
                is SettingsEvent.LogoutTracker -> logoutTracker(event.trackerId)
                is SettingsEvent.SetMigrationSimilarityThreshold ->
                    appPreferences.setMigrationSimilarityThreshold(event.threshold)
                is SettingsEvent.SetMigrationAlwaysConfirm ->
                    appPreferences.setMigrationAlwaysConfirm(event.enabled)
                is SettingsEvent.SetMigrationMinChapterCount ->
                    appPreferences.setMigrationMinChapterCount(event.count)
                SettingsEvent.OnNavigateToMigration ->
                    _effect.send(SettingsEffect.NavigateToMigrationEntry)
            }
        }
    }

    private fun loginTracker(trackerId: Int, username: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(trackingLoginInProgress = true) }
            val tracker = trackManager.get(trackerId)
            val success = tracker?.login(username, password) ?: false
            refreshTrackers()
            _state.update { it.copy(trackingLoginInProgress = false) }
            val trackerName = tracker?.name ?: "tracker"
            val message = if (success) "Logged in to $trackerName" else "Login failed"
            _effect.send(SettingsEffect.ShowSnackbar(message))
        }
    }

    private fun logoutTracker(trackerId: Int) {
        viewModelScope.launch {
            val tracker = trackManager.get(trackerId)
            tracker?.logout()
            refreshTrackers()
            val trackerName = tracker?.name ?: "tracker"
            _effect.send(SettingsEffect.ShowSnackbar("Logged out of $trackerName"))
        }
    }

    /**
     * Handles backup creation after user selects a destination URI.
     */
    fun createBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isBackupInProgress = true) }
            try {
                backupRepository.createBackup(uri)
                _effect.send(SettingsEffect.ShowSnackbar("Backup created successfully"))
            } catch (e: Exception) {
                _effect.send(SettingsEffect.ShowSnackbar("Backup failed: ${e.message}"))
            } finally {
                _state.update { it.copy(isBackupInProgress = false) }
            }
        }
    }

    /**
     * Handles backup restoration after user selects a source URI.
     */
    fun restoreBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isRestoreInProgress = true) }
            try {
                backupRepository.restoreBackup(uri)
                _effect.send(SettingsEffect.ShowSnackbar("Backup restored successfully"))
            } catch (e: Exception) {
                _effect.send(SettingsEffect.ShowSnackbar("Restore failed: ${e.message}"))
            } finally {
                _state.update { it.copy(isRestoreInProgress = false) }
            }
        }
    }
}

