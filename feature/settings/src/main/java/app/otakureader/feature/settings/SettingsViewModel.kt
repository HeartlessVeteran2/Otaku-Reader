package app.otakureader.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.AppPreferences
import app.otakureader.core.preferences.AiPreferences
import app.otakureader.core.preferences.BackupPreferences
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.core.preferences.LocalSourcePreferences
import app.otakureader.core.preferences.ReaderPreferences
import app.otakureader.core.preferences.ReadingGoalPreferences
import app.otakureader.core.discord.DiscordRpcService
import app.otakureader.data.backup.BackupScheduler
import app.otakureader.data.tracking.TrackManager
import app.otakureader.data.worker.ReadingReminderScheduler
import app.otakureader.feature.reader.model.ImageQuality
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val generalPreferences: GeneralPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val readerPreferences: ReaderPreferences,
    private val downloadPreferences: DownloadPreferences,
    private val localSourcePreferences: LocalSourcePreferences,
    private val backupPreferences: BackupPreferences,
    private val backupRepository: app.otakureader.data.backup.repository.BackupRepository,
    private val backupScheduler: BackupScheduler,
    private val readerSettingsRepository: app.otakureader.feature.reader.repository.ReaderSettingsRepository,
    private val trackManager: TrackManager,
    private val appPreferences: AppPreferences,
    private val aiPreferences: AiPreferences,
    private val readingGoalPreferences: ReadingGoalPreferences,
    private val readingReminderScheduler: ReadingReminderScheduler,
    private val discordRpcService: DiscordRpcService
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _effect = Channel<SettingsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        viewModelScope.launch {
            try {
                aiPreferences.migrateLegacyApiKeyIfNeeded()
            } catch (e: Exception) {
                // Migration failure is non-fatal; the user can re-enter the API key.
                _effect.send(SettingsEffect.ShowSnackbar("Failed to load AI settings. You may need to re-enter your API key."))
            }
        }
        observePreferences()
        observeAiPreferences()
        observeReadingGoalPreferences()
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
            }.combine(generalPreferences.usePureBlackDarkMode) { state, usePureBlack ->
                state.copy(usePureBlackDarkMode = usePureBlack)
            }.combine(generalPreferences.useHighContrast) { state, highContrast ->
                state.copy(useHighContrast = highContrast)
            }.combine(generalPreferences.colorScheme) { state, colorScheme ->
                state.copy(colorScheme = colorScheme)
            }.combine(generalPreferences.customAccentColor) { state, customAccent ->
                state.copy(customAccentColor = customAccent)
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
            }.combine(readerSettingsRepository.preloadPagesBefore) { state, preloadBefore ->
                state.copy(preloadPagesBefore = preloadBefore)
            }.combine(readerSettingsRepository.preloadPagesAfter) { state, preloadAfter ->
                state.copy(preloadPagesAfter = preloadAfter)
            }.combine(readerSettingsRepository.cropBordersEnabled) { state, cropBorders ->
                state.copy(cropBordersEnabled = cropBorders)
            }.combine(readerSettingsRepository.imageQuality) { state, imageQuality ->
                state.copy(imageQuality = imageQuality.name)
            }.combine(readerSettingsRepository.dataSaverEnabled) { state, dataSaver ->
                state.copy(dataSaverEnabled = dataSaver)
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
            }.combine(generalPreferences.showNsfwContent) { state, showNsfw ->
                state.copy(showNsfwContent = showNsfw)
            }.combine(generalPreferences.discordRpcEnabled) { state, discordRpc ->
                state.copy(discordRpcEnabled = discordRpc)
            }.combine(backupPreferences.autoBackupEnabled) { state, enabled ->
                state.copy(autoBackupEnabled = enabled)
            }.combine(backupPreferences.autoBackupIntervalHours) { state, hours ->
                state.copy(autoBackupIntervalHours = hours)
            }.combine(backupPreferences.autoBackupMaxCount) { state, count ->
                state.copy(autoBackupMaxCount = count)
            }.collect { newState ->
                _state.update { current ->
                    newState.copy(
                        // Preserve in-flight backup/restore state so a preference change
                        // doesn't cancel progress indicators mid-operation.
                        isBackupInProgress = current.isBackupInProgress,
                        isRestoreInProgress = current.isRestoreInProgress,
                        restoringBackupFileName = current.restoringBackupFileName,
                        localBackupFiles = current.localBackupFiles,
                        trackers = current.trackers,
                        trackingLoginInProgress = current.trackingLoginInProgress,
                        // Preserve AI fields managed by observeAiPreferences()
                        aiEnabled = current.aiEnabled,
                        aiTier = current.aiTier,
                        aiApiKeySet = current.aiApiKeySet,
                        aiReadingInsights = current.aiReadingInsights,
                        aiSmartSearch = current.aiSmartSearch,
                        aiRecommendations = current.aiRecommendations,
                        aiPanelReader = current.aiPanelReader,
                        aiSfxTranslation = current.aiSfxTranslation,
                        aiSummaryTranslation = current.aiSummaryTranslation,
                        aiSourceIntelligence = current.aiSourceIntelligence,
                        aiSmartNotifications = current.aiSmartNotifications,
                        aiAutoCategorization = current.aiAutoCategorization,
                        aiTokensUsedThisMonth = current.aiTokensUsedThisMonth,
                        aiTokenTrackingPeriod = current.aiTokenTrackingPeriod,
                        // Preserve reading goal fields managed by observeReadingGoalPreferences()
                        dailyChapterGoal = current.dailyChapterGoal,
                        weeklyChapterGoal = current.weeklyChapterGoal,
                        readingRemindersEnabled = current.readingRemindersEnabled,
                        readingReminderHour = current.readingReminderHour
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
                is SettingsEvent.SetHighContrast -> generalPreferences.setUseHighContrast(event.enabled)
                is SettingsEvent.SetColorScheme -> generalPreferences.setColorScheme(event.scheme)
                is SettingsEvent.SetLocale -> generalPreferences.setLocale(event.locale)
                is SettingsEvent.SetNotificationsEnabled -> generalPreferences.setNotificationsEnabled(event.enabled)
                is SettingsEvent.SetUpdateInterval -> generalPreferences.setUpdateCheckInterval(event.hours)
                is SettingsEvent.SetLibraryGridSize -> libraryPreferences.setGridSize(event.size)
                is SettingsEvent.SetShowBadges -> libraryPreferences.setShowBadges(event.enabled)
                is SettingsEvent.SetReaderMode -> readerPreferences.setReaderMode(event.mode)
                is SettingsEvent.SetKeepScreenOn -> readerPreferences.setKeepScreenOn(event.enabled)
                is SettingsEvent.SetIncognitoMode -> readerSettingsRepository.setIncognitoMode(event.enabled)
                is SettingsEvent.SetPreloadPagesBefore -> readerSettingsRepository.setPreloadPagesBefore(event.count)
                is SettingsEvent.SetPreloadPagesAfter -> readerSettingsRepository.setPreloadPagesAfter(event.count)
                is SettingsEvent.SetCropBordersEnabled -> readerSettingsRepository.setCropBordersEnabled(event.enabled)
                is SettingsEvent.SetImageQuality -> {
                    val quality = ImageQuality.entries.firstOrNull { it.name == event.quality }
                        ?: ImageQuality.ORIGINAL
                    readerSettingsRepository.setImageQuality(quality)
                }
                is SettingsEvent.SetDataSaverEnabled -> readerSettingsRepository.setDataSaverEnabled(event.enabled)
                is SettingsEvent.SetDeleteAfterReading -> downloadPreferences.setDeleteAfterReading(event.enabled)
                is SettingsEvent.SetAutoDownloadEnabled -> downloadPreferences.setAutoDownloadEnabled(event.enabled)
                is SettingsEvent.SetDownloadOnlyOnWifi -> downloadPreferences.setDownloadOnlyOnWifi(event.enabled)
                is SettingsEvent.SetAutoDownloadLimit -> downloadPreferences.setAutoDownloadLimit(event.limit)
                is SettingsEvent.SetSaveAsCbz -> downloadPreferences.setSaveAsCbz(event.enabled)
                is SettingsEvent.SetLocalSourceDirectory -> localSourcePreferences.setLocalSourceDirectory(event.path)
                SettingsEvent.OnCreateBackup -> _effect.send(SettingsEffect.ShowBackupPicker)
                SettingsEvent.OnRestoreBackup -> _effect.send(SettingsEffect.ShowRestorePicker)
                is SettingsEvent.SetAutoBackupEnabled -> handleSetAutoBackupEnabled(event.enabled)
                is SettingsEvent.SetAutoBackupInterval -> handleSetAutoBackupInterval(event.hours)
                is SettingsEvent.SetAutoBackupMaxCount -> backupPreferences.setAutoBackupMaxCount(event.count)
                SettingsEvent.RefreshLocalBackups -> refreshLocalBackups()
                is SettingsEvent.RestoreLocalBackup -> restoreLocalBackup(event.fileName)
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
                is SettingsEvent.SetShowNsfwContent ->
                    generalPreferences.setShowNsfwContent(event.enabled)
                is SettingsEvent.SetDiscordRpcEnabled ->
                    handleSetDiscordRpcEnabled(event.enabled)
                is SettingsEvent.SetCustomAccentColor ->
                    generalPreferences.setCustomAccentColor(event.color)
                is SettingsEvent.SetAiEnabled -> aiPreferences.setAiEnabled(event.enabled)
                is SettingsEvent.SetAiTier -> aiPreferences.setAiTier(event.tier)
                is SettingsEvent.SetAiApiKey -> {
                    aiPreferences.setGeminiApiKey(event.key)
                    val persistedKey = aiPreferences.getGeminiApiKey()
                    val isSet = persistedKey.isNotBlank()
                    _state.update { it.copy(aiApiKeySet = isSet) }
                    if (event.key.isNotBlank() && !isSet) {
                        _effect.send(SettingsEffect.ShowSnackbar("Failed to save AI API key"))
                    }
                }
                is SettingsEvent.SetAiReadingInsights -> aiPreferences.setAiReadingInsights(event.enabled)
                is SettingsEvent.SetAiSmartSearch -> aiPreferences.setAiSmartSearch(event.enabled)
                is SettingsEvent.SetAiRecommendations -> aiPreferences.setAiRecommendations(event.enabled)
                is SettingsEvent.SetAiPanelReader -> aiPreferences.setAiPanelReader(event.enabled)
                is SettingsEvent.SetAiSfxTranslation -> aiPreferences.setAiSfxTranslation(event.enabled)
                is SettingsEvent.SetAiSummaryTranslation -> aiPreferences.setAiSummaryTranslation(event.enabled)
                is SettingsEvent.SetAiSourceIntelligence -> aiPreferences.setAiSourceIntelligence(event.enabled)
                is SettingsEvent.SetAiSmartNotifications -> aiPreferences.setAiSmartNotifications(event.enabled)
                is SettingsEvent.SetAiAutoCategorization -> aiPreferences.setAiAutoCategorization(event.enabled)
                SettingsEvent.ClearAiCache -> {
                    aiPreferences.setAiCacheLastCleared(System.currentTimeMillis())
                    _effect.send(SettingsEffect.ShowSnackbar("AI suggestions will refresh for future requests"))
                }
                is SettingsEvent.SetDailyChapterGoal ->
                    readingGoalPreferences.setDailyChapterGoal(event.goal)
                is SettingsEvent.SetWeeklyChapterGoal ->
                    readingGoalPreferences.setWeeklyChapterGoal(event.goal)
                is SettingsEvent.SetReadingRemindersEnabled ->
                    handleSetReadingRemindersEnabled(event.enabled)
                is SettingsEvent.SetReadingReminderHour ->
                    handleSetReadingReminderHour(event.hour)
                is SettingsEvent.SetSyncEnabled -> Unit // TODO: implement cloud sync
                is SettingsEvent.TriggerManualSync -> Unit // TODO: implement cloud sync
                is SettingsEvent.SetAutoSyncEnabled -> Unit // TODO: implement cloud sync
                is SettingsEvent.SetSyncIntervalHours -> Unit // TODO: implement cloud sync
                is SettingsEvent.SetSyncOnlyOnWifi -> Unit // TODO: implement cloud sync
                is SettingsEvent.SetConflictResolutionStrategy -> Unit // TODO: implement cloud sync
            }
        }
    }

    private fun observeAiPreferences() {
        viewModelScope.launch {
            aiPreferences.aiEnabled
                .combine(aiPreferences.aiTier) { enabled, tier ->
                    _state.value.copy(
                        aiEnabled = enabled,
                        aiTier = tier,
                        aiApiKeySet = aiPreferences.getGeminiApiKey().isNotBlank()
                    )
                }.combine(aiPreferences.aiReadingInsights) { state, v -> state.copy(aiReadingInsights = v) }
                .combine(aiPreferences.aiSmartSearch) { state, v -> state.copy(aiSmartSearch = v) }
                .combine(aiPreferences.aiRecommendations) { state, v -> state.copy(aiRecommendations = v) }
                .combine(aiPreferences.aiPanelReader) { state, v -> state.copy(aiPanelReader = v) }
                .combine(aiPreferences.aiSfxTranslation) { state, v -> state.copy(aiSfxTranslation = v) }
                .combine(aiPreferences.aiSummaryTranslation) { state, v -> state.copy(aiSummaryTranslation = v) }
                .combine(aiPreferences.aiSourceIntelligence) { state, v -> state.copy(aiSourceIntelligence = v) }
                .combine(aiPreferences.aiSmartNotifications) { state, v -> state.copy(aiSmartNotifications = v) }
                .combine(aiPreferences.aiAutoCategorization) { state, v -> state.copy(aiAutoCategorization = v) }
                .combine(aiPreferences.aiTokensUsedThisMonth) { state, v -> state.copy(aiTokensUsedThisMonth = v) }
                .combine(aiPreferences.aiTokenTrackingPeriod) { state, v -> state.copy(aiTokenTrackingPeriod = v) }
                .collect { newAiState ->
                    _state.update { current ->
                        current.copy(
                            aiEnabled = newAiState.aiEnabled,
                            aiTier = newAiState.aiTier,
                            aiApiKeySet = newAiState.aiApiKeySet,
                            aiReadingInsights = newAiState.aiReadingInsights,
                            aiSmartSearch = newAiState.aiSmartSearch,
                            aiRecommendations = newAiState.aiRecommendations,
                            aiPanelReader = newAiState.aiPanelReader,
                            aiSfxTranslation = newAiState.aiSfxTranslation,
                            aiSummaryTranslation = newAiState.aiSummaryTranslation,
                            aiSourceIntelligence = newAiState.aiSourceIntelligence,
                            aiSmartNotifications = newAiState.aiSmartNotifications,
                            aiAutoCategorization = newAiState.aiAutoCategorization,
                            aiTokensUsedThisMonth = newAiState.aiTokensUsedThisMonth,
                            aiTokenTrackingPeriod = newAiState.aiTokenTrackingPeriod
                        )
                    }
                }
        }
    }

    private fun observeReadingGoalPreferences() {
        viewModelScope.launch {
            combine(
                readingGoalPreferences.dailyChapterGoal,
                readingGoalPreferences.weeklyChapterGoal,
                readingGoalPreferences.remindersEnabled,
                readingGoalPreferences.reminderHour
            ) { daily, weekly, enabled, hour ->
                ReadingGoalState(daily, weekly, enabled, hour)
            }.collect { goalState ->
                _state.update { current ->
                    current.copy(
                        dailyChapterGoal = goalState.dailyGoal,
                        weeklyChapterGoal = goalState.weeklyGoal,
                        readingRemindersEnabled = goalState.remindersEnabled,
                        readingReminderHour = goalState.reminderHour
                    )
                }
            }
        }
    }

    /** Intermediate holder to avoid destructuring a 4-element array. */
    private data class ReadingGoalState(
        val dailyGoal: Int,
        val weeklyGoal: Int,
        val remindersEnabled: Boolean,
        val reminderHour: Int
    )

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
        val enabled = readingGoalPreferences.remindersEnabled.first()
        if (enabled) {
            readingReminderScheduler.schedule(hour)
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

    private suspend fun handleSetAutoBackupEnabled(enabled: Boolean) {
        backupPreferences.setAutoBackupEnabled(enabled)
        if (enabled) {
            val hours = backupPreferences.autoBackupIntervalHours.first()
            backupScheduler.schedule(hours)
        } else {
            backupScheduler.cancel()
        }
    }

    private suspend fun handleSetAutoBackupInterval(hours: Int) {
        backupPreferences.setAutoBackupIntervalHours(hours)
        val enabled = backupPreferences.autoBackupEnabled.first()
        if (enabled) {
            backupScheduler.schedule(hours)
        }
    }

    private suspend fun handleSetDiscordRpcEnabled(enabled: Boolean) {
        generalPreferences.setDiscordRpcEnabled(enabled)
        if (enabled) {
            discordRpcService.initialize()
        } else {
            discordRpcService.disconnect()
        }
    }

    private suspend fun refreshLocalBackups() {
        try {
            val files = backupRepository.listLocalBackups().map { it.name }
            _state.update { it.copy(localBackupFiles = files) }
        } catch (e: Exception) {
            _effect.send(SettingsEffect.ShowSnackbar("Could not list backups: ${e.message}"))
        }
    }

    private suspend fun restoreLocalBackup(fileName: String) {
        _state.update { it.copy(isRestoreInProgress = true, restoringBackupFileName = fileName) }
        try {
            val files = backupRepository.listLocalBackups()
            val file = files.firstOrNull { it.name == fileName }
                ?: throw IllegalArgumentException("Backup file not found: $fileName")
            backupRepository.restoreLocalBackup(file)
            _effect.send(SettingsEffect.ShowSnackbar("Backup restored successfully"))
        } catch (e: Exception) {
            _effect.send(SettingsEffect.ShowSnackbar("Restore failed: ${e.message}"))
        } finally {
            _state.update { it.copy(isRestoreInProgress = false, restoringBackupFileName = null) }
        }
    }
}

