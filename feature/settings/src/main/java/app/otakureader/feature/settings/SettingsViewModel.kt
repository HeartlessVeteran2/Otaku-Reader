package app.otakureader.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.AppPreferences
import app.otakureader.core.preferences.AiPreferences
import app.otakureader.core.preferences.AiTier
import app.otakureader.core.preferences.BackupPreferences
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.core.preferences.LocalSourcePreferences
import app.otakureader.core.preferences.ReaderPreferences
import app.otakureader.core.preferences.ReadingGoalPreferences
import app.otakureader.core.preferences.SyncPreferences
import app.otakureader.core.discord.DiscordRpcService
import app.otakureader.data.backup.BackupScheduler
import app.otakureader.data.tracking.TrackManager
import app.otakureader.data.worker.LibraryUpdateScheduler
import app.otakureader.data.worker.ReadingReminderScheduler
import app.otakureader.domain.repository.AiRepository
import app.otakureader.domain.sync.SyncManager
import app.otakureader.domain.sync.SyncStatus as DomainSyncStatus
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
    private val libraryUpdateScheduler: LibraryUpdateScheduler,
    private val aiPreferences: AiPreferences,
    private val aiRepository: AiRepository,
    private val readingGoalPreferences: ReadingGoalPreferences,
    private val readingReminderScheduler: ReadingReminderScheduler,
    private val discordRpcService: DiscordRpcService,
    private val syncPreferences: SyncPreferences,
    private val syncManager: SyncManager
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
                _effect.send(SettingsEffect.ShowSnackbar("Failed to load AI settings. You may need to re-enter your API key."))
            }
        }
        observePreferences()
        observeAiPreferences()
        observeReadingGoalPreferences()
        observeSyncPreferences()
        refreshTrackers()
    }

    private fun observePreferences() {
        observeGeneralPreferences()
        observeLibraryPreferences()
        observeReaderDisplayPreferences()
        observeReaderBehaviorPreferences()
        observeDownloadPreferences()
        observeLocalAndBackupPreferences()
    }

    private fun observeGeneralPreferences() {
        viewModelScope.launch {
            combine(
                generalPreferences.themeMode,
                generalPreferences.useDynamicColor,
                generalPreferences.usePureBlackDarkMode,
                generalPreferences.useHighContrast,
                generalPreferences.colorScheme
            ) { themeMode, useDynamicColor, pureBlack, highContrast, colorScheme ->
                _state.update { it.copy(
                    themeMode = themeMode,
                    useDynamicColor = useDynamicColor,
                    usePureBlackDarkMode = pureBlack,
                    useHighContrast = highContrast,
                    colorScheme = colorScheme,
                ) }
            }.collect { }
        }
        viewModelScope.launch {
            combine(
                generalPreferences.customAccentColor,
                generalPreferences.locale,
                generalPreferences.notificationsEnabled,
                generalPreferences.updateCheckInterval,
                generalPreferences.showNsfwContent
            ) { accent, locale, notifications, updateInterval, showNsfw ->
                _state.update { it.copy(
                    customAccentColor = accent,
                    locale = locale,
                    notificationsEnabled = notifications,
                    updateCheckInterval = updateInterval,
                    showNsfwContent = showNsfw,
                ) }
            }.collect { }
        }
        viewModelScope.launch {
            generalPreferences.discordRpcEnabled.collect { discordRpc ->
                _state.update { it.copy(discordRpcEnabled = discordRpc) }
            }
        }
    }

    private fun observeLibraryPreferences() {
        viewModelScope.launch {
            combine(
                libraryPreferences.gridSize,
                libraryPreferences.showBadges,
                libraryPreferences.updateOnlyOnWifi,
                libraryPreferences.updateOnlyPinnedCategories,
                libraryPreferences.autoRefreshOnStart
            ) { gridSize, showBadges, updateOnWifi, updatePinned, autoRefresh ->
                _state.update { it.copy(
                    libraryGridSize = gridSize,
                    showBadges = showBadges,
                    updateOnlyOnWifi = updateOnWifi,
                    updateOnlyPinnedCategories = updatePinned,
                    autoRefreshOnStart = autoRefresh,
                ) }
            }.collect { }
        }
        viewModelScope.launch {
            libraryPreferences.showUpdateProgress.collect { showProgress ->
                _state.update { it.copy(showUpdateProgress = showProgress) }
            }
        }
    }

    private fun observeReaderDisplayPreferences() {
        viewModelScope.launch {
            combine(
                readerPreferences.readerMode,
                readerPreferences.keepScreenOn,
                readerPreferences.fullscreen,
                readerPreferences.showContentInCutout,
                readerPreferences.showPageNumber
            ) { readerMode, keepScreenOn, fullscreen, showCutout, showPageNum ->
                _state.update { it.copy(
                    readerMode = readerMode,
                    keepScreenOn = keepScreenOn,
                    fullscreen = fullscreen,
                    showContentInCutout = showCutout,
                    showPageNumber = showPageNum,
                ) }
            }.collect { }
        }
        viewModelScope.launch {
            combine(
                readerPreferences.backgroundColor,
                readerPreferences.animatePageTransitions,
                readerPreferences.showReadingModeOverlay,
                readerPreferences.showTapZonesOverlay,
                readerPreferences.readerScale
            ) { bgColor, animate, showMode, showZones, scale ->
                _state.update { it.copy(
                    backgroundColor = bgColor,
                    animatePageTransitions = animate,
                    showReadingModeOverlay = showMode,
                    showTapZonesOverlay = showZones,
                    readerScale = scale,
                ) }
            }.collect { }
        }
        viewModelScope.launch {
            combine(
                readerPreferences.autoZoomWideImages,
                readerPreferences.tapZoneConfig,
                readerPreferences.invertTapZones,
                readerPreferences.volumeKeysEnabled,
                readerPreferences.volumeKeysInverted
            ) { autoZoom, tapConfig, invertZones, volKeys, volInvert ->
                _state.update { it.copy(
                    autoZoomWideImages = autoZoom,
                    tapZoneConfig = tapConfig,
                    invertTapZones = invertZones,
                    volumeKeysEnabled = volKeys,
                    volumeKeysInverted = volInvert,
                ) }
            }.collect { }
        }
        viewModelScope.launch {
            combine(
                readerPreferences.doubleTapAnimationSpeed,
                readerPreferences.showActionsOnLongTap,
                readerPreferences.savePagesToSeparateFolders,
                readerPreferences.webtoonSidePadding,
                readerPreferences.webtoonMenuHideSensitivity
            ) { animSpeed, longTap, separateFolders, sidePadding, menuSensitivity ->
                _state.update { it.copy(
                    doubleTapAnimationSpeed = animSpeed,
                    showActionsOnLongTap = longTap,
                    savePagesToSeparateFolders = separateFolders,
                    webtoonSidePadding = sidePadding,
                    webtoonMenuHideSensitivity = menuSensitivity,
                ) }
            }.collect { }
        }
        viewModelScope.launch {
            combine(
                readerPreferences.webtoonDoubleTapZoom,
                readerPreferences.webtoonDisableZoomOut,
                readerPreferences.einkFlashOnPageChange,
                readerPreferences.einkBlackAndWhite,
                readerPreferences.skipReadChapters
            ) { dtZoom, disableZoomOut, einkFlash, einkBw, skipRead ->
                _state.update { it.copy(
                    webtoonDoubleTapZoom = dtZoom,
                    webtoonDisableZoomOut = disableZoomOut,
                    einkFlashOnPageChange = einkFlash,
                    einkBlackAndWhite = einkBw,
                    skipReadChapters = skipRead,
                ) }
            }.collect { }
        }
    }

    private fun observeReaderBehaviorPreferences() {
        viewModelScope.launch {
            combine(
                readerPreferences.skipFilteredChapters,
                readerPreferences.skipDuplicateChapters,
                readerPreferences.alwaysShowChapterTransition,
                readerSettingsRepository.incognitoMode,
                readerSettingsRepository.preloadPagesBefore
            ) { skipFiltered, skipDupes, showTransition, incognito, preloadBefore ->
                _state.update { it.copy(
                    skipFilteredChapters = skipFiltered,
                    skipDuplicateChapters = skipDupes,
                    alwaysShowChapterTransition = showTransition,
                    incognitoMode = incognito,
                    preloadPagesBefore = preloadBefore,
                ) }
            }.collect { }
        }
        viewModelScope.launch {
            combine(
                readerSettingsRepository.preloadPagesAfter,
                readerSettingsRepository.cropBordersEnabled,
                readerSettingsRepository.imageQuality,
                readerSettingsRepository.dataSaverEnabled
            ) { preloadAfter, cropBorders, imageQuality, dataSaver ->
                _state.update { it.copy(
                    preloadPagesAfter = preloadAfter,
                    cropBordersEnabled = cropBorders,
                    imageQuality = imageQuality.name,
                    dataSaverEnabled = dataSaver,
                ) }
            }.collect { }
        }
    }

    private fun observeDownloadPreferences() {
        viewModelScope.launch {
            combine(
                downloadPreferences.deleteAfterReading,
                downloadPreferences.saveAsCbz,
                downloadPreferences.autoDownloadEnabled,
                downloadPreferences.downloadOnlyOnWifi,
                downloadPreferences.autoDownloadLimit
            ) { deleteAfter, saveCbz, autoDownload, dlWifi, dlLimit ->
                _state.update { it.copy(
                    deleteAfterReading = deleteAfter,
                    saveAsCbz = saveCbz,
                    autoDownloadEnabled = autoDownload,
                    downloadOnlyOnWifi = dlWifi,
                    autoDownloadLimit = dlLimit,
                ) }
            }.collect { }
        }
        viewModelScope.launch {
            combine(
                downloadPreferences.concurrentDownloads,
                downloadPreferences.downloadAheadWhileReading,
                downloadPreferences.downloadAheadOnlyOnWifi,
                downloadPreferences.downloadLocation
            ) { concurrent, dlAhead, dlAheadWifi, dlLocation ->
                _state.update { it.copy(
                    concurrentDownloads = concurrent,
                    downloadAheadWhileReading = dlAhead,
                    downloadAheadOnlyOnWifi = dlAheadWifi,
                    downloadLocation = dlLocation,
                ) }
            }.collect { }
        }
    }

    private fun observeLocalAndBackupPreferences() {
        viewModelScope.launch {
            combine(
                localSourcePreferences.localSourceDirectory,
                appPreferences.migrationSimilarityThreshold,
                appPreferences.migrationAlwaysConfirm,
                appPreferences.migrationMinChapterCount,
                backupPreferences.autoBackupEnabled
            ) { localDir, threshold, alwaysConfirm, minChapters, autoBackup ->
                _state.update { it.copy(
                    localSourceDirectory = localDir,
                    migrationSimilarityThreshold = threshold,
                    migrationAlwaysConfirm = alwaysConfirm,
                    migrationMinChapterCount = minChapters,
                    autoBackupEnabled = autoBackup,
                ) }
            }.collect { }
        }
        viewModelScope.launch {
            combine(
                backupPreferences.autoBackupIntervalHours,
                backupPreferences.autoBackupMaxCount
            ) { backupInterval, backupMax ->
                _state.update { it.copy(
                    autoBackupIntervalHours = backupInterval,
                    autoBackupMaxCount = backupMax,
                ) }
            }.collect { }
        }
    }

    private fun refreshTrackers() {
        _state.update { it.copy(trackers = trackManager.all.map { t -> TrackerInfo(t.id, t.name, t.isLoggedIn) }) }
    }

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            when (event) {
                // Appearance
                is SettingsEvent.SetThemeMode -> generalPreferences.setThemeMode(event.mode)
                is SettingsEvent.SetDynamicColor -> generalPreferences.setUseDynamicColor(event.enabled)
                is SettingsEvent.SetPureBlackDarkMode -> generalPreferences.setUsePureBlackDarkMode(event.enabled)
                is SettingsEvent.SetHighContrast -> generalPreferences.setUseHighContrast(event.enabled)
                is SettingsEvent.SetColorScheme -> generalPreferences.setColorScheme(event.scheme)
                is SettingsEvent.SetCustomAccentColor -> generalPreferences.setCustomAccentColor(event.color)
                is SettingsEvent.SetLocale -> generalPreferences.setLocale(event.locale)

                // Reader - Display
                is SettingsEvent.SetReaderMode -> readerPreferences.setReaderMode(event.mode)
                is SettingsEvent.SetKeepScreenOn -> readerPreferences.setKeepScreenOn(event.enabled)
                is SettingsEvent.SetFullscreen -> readerPreferences.setFullscreen(event.enabled)
                is SettingsEvent.SetShowContentInCutout -> readerPreferences.setShowContentInCutout(event.enabled)
                is SettingsEvent.SetShowPageNumber -> readerPreferences.setShowPageNumber(event.enabled)
                is SettingsEvent.SetBackgroundColor -> readerPreferences.setBackgroundColor(event.color)
                is SettingsEvent.SetAnimatePageTransitions -> readerPreferences.setAnimatePageTransitions(event.enabled)
                is SettingsEvent.SetShowReadingModeOverlay -> readerPreferences.setShowReadingModeOverlay(event.enabled)
                is SettingsEvent.SetShowTapZonesOverlay -> readerPreferences.setShowTapZonesOverlay(event.enabled)

                // Reader - Scale
                is SettingsEvent.SetReaderScale -> readerPreferences.setReaderScale(event.scale)
                is SettingsEvent.SetAutoZoomWideImages -> readerPreferences.setAutoZoomWideImages(event.enabled)

                // Reader - Tap Zones
                is SettingsEvent.SetTapZoneConfig -> readerPreferences.setTapZoneConfig(event.config)
                is SettingsEvent.SetInvertTapZones -> readerPreferences.setInvertTapZones(event.enabled)

                // Reader - Volume Keys
                is SettingsEvent.SetVolumeKeysEnabled -> readerPreferences.setVolumeKeysEnabled(event.enabled)
                is SettingsEvent.SetVolumeKeysInverted -> readerPreferences.setVolumeKeysInverted(event.enabled)

                // Reader - Interaction
                is SettingsEvent.SetDoubleTapAnimationSpeed -> readerPreferences.setDoubleTapAnimationSpeed(event.speed)
                is SettingsEvent.SetShowActionsOnLongTap -> readerPreferences.setShowActionsOnLongTap(event.enabled)
                is SettingsEvent.SetSavePagesToSeparateFolders -> readerPreferences.setSavePagesToSeparateFolders(event.enabled)

                // Reader - Webtoon
                is SettingsEvent.SetWebtoonSidePadding -> readerPreferences.setWebtoonSidePadding(event.padding)
                is SettingsEvent.SetWebtoonMenuHideSensitivity -> readerPreferences.setWebtoonMenuHideSensitivity(event.sensitivity)
                is SettingsEvent.SetWebtoonDoubleTapZoom -> readerPreferences.setWebtoonDoubleTapZoom(event.enabled)
                is SettingsEvent.SetWebtoonDisableZoomOut -> readerPreferences.setWebtoonDisableZoomOut(event.enabled)

                // Reader - E-ink
                is SettingsEvent.SetEinkFlashOnPageChange -> readerPreferences.setEinkFlashOnPageChange(event.enabled)
                is SettingsEvent.SetEinkBlackAndWhite -> readerPreferences.setEinkBlackAndWhite(event.enabled)

                // Reader - Behavior
                is SettingsEvent.SetSkipReadChapters -> readerPreferences.setSkipReadChapters(event.enabled)
                is SettingsEvent.SetSkipFilteredChapters -> readerPreferences.setSkipFilteredChapters(event.enabled)
                is SettingsEvent.SetSkipDuplicateChapters -> readerPreferences.setSkipDuplicateChapters(event.enabled)
                is SettingsEvent.SetAlwaysShowChapterTransition -> readerPreferences.setAlwaysShowChapterTransition(event.enabled)

                // Reader - Other
                is SettingsEvent.SetIncognitoMode -> readerSettingsRepository.setIncognitoMode(event.enabled)
                is SettingsEvent.SetPreloadPagesBefore -> readerSettingsRepository.setPreloadPagesBefore(event.count)
                is SettingsEvent.SetPreloadPagesAfter -> readerSettingsRepository.setPreloadPagesAfter(event.count)
                is SettingsEvent.SetCropBordersEnabled -> readerSettingsRepository.setCropBordersEnabled(event.enabled)
                is SettingsEvent.SetImageQuality -> {
                    val normalizedInput = event.quality.trim().uppercase()
                    val quality = ImageQuality.entries.firstOrNull { 
                        it.name.equals(normalizedInput, ignoreCase = true) 
                    } ?: ImageQuality.ORIGINAL
                    readerSettingsRepository.setImageQuality(quality)
                }
                is SettingsEvent.SetDataSaverEnabled -> readerSettingsRepository.setDataSaverEnabled(event.enabled)

                // Library
                is SettingsEvent.SetLibraryGridSize -> libraryPreferences.setGridSize(event.size)
                is SettingsEvent.SetShowBadges -> libraryPreferences.setShowBadges(event.enabled)
                is SettingsEvent.SetUpdateOnlyOnWifi -> handleSetUpdateOnlyOnWifi(event.enabled)
                is SettingsEvent.SetUpdateOnlyPinnedCategories -> libraryPreferences.setUpdateOnlyPinnedCategories(event.enabled)
                is SettingsEvent.SetAutoRefreshOnStart -> libraryPreferences.setAutoRefreshOnStart(event.enabled)
                is SettingsEvent.SetShowUpdateProgress -> libraryPreferences.setShowUpdateProgress(event.enabled)

                // Downloads
                is SettingsEvent.SetDeleteAfterReading -> downloadPreferences.setDeleteAfterReading(event.enabled)
                is SettingsEvent.SetSaveAsCbz -> downloadPreferences.setSaveAsCbz(event.enabled)
                is SettingsEvent.SetAutoDownloadEnabled -> downloadPreferences.setAutoDownloadEnabled(event.enabled)
                is SettingsEvent.SetDownloadOnlyOnWifi -> downloadPreferences.setDownloadOnlyOnWifi(event.enabled)
                is SettingsEvent.SetAutoDownloadLimit -> downloadPreferences.setAutoDownloadLimit(event.limit)
                is SettingsEvent.SetConcurrentDownloads -> downloadPreferences.setConcurrentDownloads(event.count)
                is SettingsEvent.SetDownloadAheadWhileReading -> downloadPreferences.setDownloadAheadWhileReading(event.count)
                is SettingsEvent.SetDownloadAheadOnlyOnWifi -> downloadPreferences.setDownloadAheadOnlyOnWifi(event.enabled)
                is SettingsEvent.SetDownloadLocation -> downloadPreferences.setDownloadLocation(event.location)

                // Local Source
                is SettingsEvent.SetLocalSourceDirectory -> localSourcePreferences.setLocalSourceDirectory(event.path)

                // Notifications
                is SettingsEvent.SetNotificationsEnabled -> generalPreferences.setNotificationsEnabled(event.enabled)
                is SettingsEvent.SetUpdateInterval -> handleSetUpdateInterval(event.hours)

                // Backup
                SettingsEvent.OnCreateBackup -> _effect.send(SettingsEffect.ShowBackupPicker)
                SettingsEvent.OnRestoreBackup -> _effect.send(SettingsEffect.ShowRestorePicker)
                is SettingsEvent.CreateBackupWithUri -> handleCreateBackupWithUri(event.uri)
                is SettingsEvent.RestoreBackupFromUri -> handleRestoreBackupFromUri(event.uri)
                is SettingsEvent.SetAutoBackupEnabled -> handleSetAutoBackupEnabled(event.enabled)
                is SettingsEvent.SetAutoBackupInterval -> handleSetAutoBackupInterval(event.hours)
                is SettingsEvent.SetAutoBackupMaxCount -> backupPreferences.setAutoBackupMaxCount(event.count)
                SettingsEvent.RefreshLocalBackups -> refreshLocalBackups()
                is SettingsEvent.RestoreLocalBackup -> restoreLocalBackup(event.fileName)

                // Tracking
                is SettingsEvent.LoginTracker -> loginTracker(event.trackerId, event.username, event.password)
                is SettingsEvent.LogoutTracker -> logoutTracker(event.trackerId)

                // Migration
                is SettingsEvent.SetMigrationSimilarityThreshold -> appPreferences.setMigrationSimilarityThreshold(event.threshold)
                is SettingsEvent.SetMigrationAlwaysConfirm -> appPreferences.setMigrationAlwaysConfirm(event.enabled)
                is SettingsEvent.SetMigrationMinChapterCount -> appPreferences.setMigrationMinChapterCount(event.count)
                SettingsEvent.OnNavigateToMigration -> _effect.send(SettingsEffect.NavigateToMigrationEntry)

                // Browse
                is SettingsEvent.SetShowNsfwContent -> generalPreferences.setShowNsfwContent(event.enabled)

                // Discord
                is SettingsEvent.SetDiscordRpcEnabled -> handleSetDiscordRpcEnabled(event.enabled)

                // AI
                is SettingsEvent.SetAiEnabled -> aiPreferences.setAiEnabled(event.enabled)
                is SettingsEvent.SetAiTier -> aiPreferences.setAiTier(event.tier)
                is SettingsEvent.SetAiApiKey -> handleSetAiApiKey(event.key)
                SettingsEvent.RemoveAiApiKey -> _state.update { it.copy(showRemoveApiKeyDialog = true) }
                SettingsEvent.ConfirmRemoveAiApiKey -> handleConfirmRemoveAiApiKey()
                SettingsEvent.DismissRemoveApiKeyDialog -> _state.update { it.copy(showRemoveApiKeyDialog = false) }
                is SettingsEvent.SetAiReadingInsights -> aiPreferences.setAiReadingInsights(event.enabled)
                is SettingsEvent.SetAiSmartSearch -> aiPreferences.setAiSmartSearch(event.enabled)
                is SettingsEvent.SetAiRecommendations -> aiPreferences.setAiRecommendations(event.enabled)
                is SettingsEvent.SetAiPanelReader -> aiPreferences.setAiPanelReader(event.enabled)
                is SettingsEvent.SetAiSfxTranslation -> aiPreferences.setAiSfxTranslation(event.enabled)
                is SettingsEvent.SetAiSummaryTranslation -> aiPreferences.setAiSummaryTranslation(event.enabled)
                is SettingsEvent.SetAiSourceIntelligence -> aiPreferences.setAiSourceIntelligence(event.enabled)
                is SettingsEvent.SetAiSmartNotifications -> aiPreferences.setAiSmartNotifications(event.enabled)
                is SettingsEvent.SetAiAutoCategorization -> aiPreferences.setAiAutoCategorization(event.enabled)
                SettingsEvent.ClearAiCache -> handleClearAiCache()

                // Reading Goals
                is SettingsEvent.SetDailyChapterGoal -> readingGoalPreferences.setDailyChapterGoal(event.goal)
                is SettingsEvent.SetWeeklyChapterGoal -> readingGoalPreferences.setWeeklyChapterGoal(event.goal)
                is SettingsEvent.SetReadingRemindersEnabled -> handleSetReadingRemindersEnabled(event.enabled)
                is SettingsEvent.SetReadingReminderHour -> handleSetReadingReminderHour(event.hour)

                // Cloud Sync
                is SettingsEvent.SetSyncEnabled -> handleSetSyncEnabled(event.enabled, event.providerId)
                SettingsEvent.TriggerManualSync -> handleTriggerManualSync()
                is SettingsEvent.SetAutoSyncEnabled -> syncPreferences.setAutoSyncEnabled(event.enabled)
                is SettingsEvent.SetSyncIntervalHours -> syncPreferences.setSyncIntervalHours(event.hours)
                is SettingsEvent.SetSyncOnlyOnWifi -> syncPreferences.setSyncOnlyOnWifi(event.onlyWifi)
                is SettingsEvent.SetConflictResolutionStrategy -> syncPreferences.setConflictResolutionStrategy(event.strategy)

                SettingsEvent.NavigateToAbout -> _effect.send(SettingsEffect.NavigateToAbout)
            }
        }
    }

    private fun handleSetAiApiKey(key: String) {
        viewModelScope.launch {
            if (key.isNotBlank() && !isGeminiApiKeyFormatValid(key)) {
                _effect.send(SettingsEffect.ShowSnackbar("Invalid API key format"))
                return@launch
            }
            aiPreferences.setGeminiApiKey(key)
            val persistedKey = aiPreferences.getGeminiApiKey()
            val isSet = persistedKey.isNotBlank()
            _state.update { it.copy(aiApiKeySet = isSet) }
            if (key.isNotBlank() && !isSet) {
                _effect.send(SettingsEffect.ShowSnackbar("Failed to save AI API key"))
            } else if (isSet) {
                aiRepository.clearApiKey()
                aiRepository.initialize(persistedKey)
            }
        }
    }

    private fun handleConfirmRemoveAiApiKey() {
        viewModelScope.launch {
            _state.update { it.copy(showRemoveApiKeyDialog = false) }
            aiPreferences.clearGeminiApiKey()
            aiRepository.clearApiKey()
            _state.update { it.copy(aiApiKeySet = false) }
            _effect.send(SettingsEffect.ShowSnackbar("AI API key removed"))
        }
    }

    private fun handleClearAiCache() {
        viewModelScope.launch {
            aiPreferences.setAiCacheLastCleared(System.currentTimeMillis())
            _effect.send(SettingsEffect.ShowSnackbar("AI suggestions will refresh for future requests"))
        }
    }

    private fun isGeminiApiKeyFormatValid(key: String): Boolean {
        return key.matches(Regex("^AIza[0-9A-Za-z_-]{35}$"))
    }

    private fun handleSetDiscordRpcEnabled(enabled: Boolean) {
        viewModelScope.launch {
            generalPreferences.setDiscordRpcEnabled(enabled)
            if (enabled) {
                discordRpcService.initialize()
            } else {
                discordRpcService.disconnect()
            }
        }
    }

    fun createBackup(uri: android.net.Uri) { onEvent(SettingsEvent.CreateBackupWithUri(uri)) }
    fun restoreBackup(uri: android.net.Uri) { onEvent(SettingsEvent.RestoreBackupFromUri(uri)) }

    private fun handleCreateBackupWithUri(uri: android.net.Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isBackupInProgress = true) }
            try {
                backupRepository.createBackup(uri)
                _effect.send(SettingsEffect.ShowSnackbar("Backup created successfully"))
            } catch (e: Exception) {
                _effect.send(SettingsEffect.ShowSnackbar("Failed to create backup: ${e.message}"))
            } finally {
                _state.update { it.copy(isBackupInProgress = false) }
            }
        }
    }

    private fun handleRestoreBackupFromUri(uri: android.net.Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isRestoreInProgress = true) }
            try {
                backupRepository.restoreBackup(uri)
                _effect.send(SettingsEffect.ShowSnackbar("Backup restored successfully"))
            } catch (e: Exception) {
                _effect.send(SettingsEffect.ShowSnackbar("Failed to restore backup: ${e.message}"))
            } finally {
                _state.update { it.copy(isRestoreInProgress = false) }
            }
        }
    }

    private fun handleSetAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            backupPreferences.setAutoBackupEnabled(enabled)
            if (enabled) {
                val intervalHours = backupPreferences.autoBackupIntervalHours.first()
                backupScheduler.schedule(intervalHours)
            } else {
                backupScheduler.cancel()
            }
        }
    }

    private fun handleSetUpdateOnlyOnWifi(enabled: Boolean) {
        viewModelScope.launch {
            libraryPreferences.setUpdateOnlyOnWifi(enabled)
            val intervalHours = state.value.updateCheckInterval
            scheduleLibraryUpdateOrShowError(intervalHours, enabled)
        }
    }

    private fun handleSetUpdateInterval(hours: Int) {
        viewModelScope.launch {
            generalPreferences.setUpdateCheckInterval(hours)
            val wifiOnly = state.value.updateOnlyOnWifi
            scheduleLibraryUpdateOrShowError(hours, wifiOnly)
        }
    }

    private suspend fun scheduleLibraryUpdateOrShowError(intervalHours: Int, wifiOnly: Boolean) {
        try {
            libraryUpdateScheduler.schedule(intervalHours = intervalHours, wifiOnly = wifiOnly)
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Failed to schedule library update (intervalHours=$intervalHours, wifiOnly=$wifiOnly)", e)
            _effect.send(SettingsEffect.ShowSnackbar("Failed to update library scheduler settings"))
        }
    }

    private fun handleSetAutoBackupInterval(hours: Int) {
        viewModelScope.launch {
            backupPreferences.setAutoBackupIntervalHours(hours)
            if (backupPreferences.autoBackupEnabled.first()) {
                backupScheduler.schedule(hours)
            }
        }
    }

    private fun refreshLocalBackups() {
        viewModelScope.launch {
            val files = backupRepository.listLocalBackups().map { it.name }
            _state.update { it.copy(localBackupFiles = files) }
        }
    }

    private fun restoreLocalBackup(fileName: String) {
        viewModelScope.launch {
            _state.update { it.copy(
                isRestoreInProgress = true,
                restoringBackupFileName = fileName
            )}
            try {
                val allFiles = backupRepository.listLocalBackups()
                val file = allFiles.firstOrNull { it.name == fileName }
                    ?: throw IllegalArgumentException("Backup file not found: $fileName")
                backupRepository.restoreLocalBackup(file)
                _effect.send(SettingsEffect.ShowSnackbar("Backup restored successfully"))
            } catch (e: Exception) {
                _effect.send(SettingsEffect.ShowSnackbar("Failed to restore backup: ${e.message}"))
            } finally {
                _state.update { it.copy(
                    isRestoreInProgress = false,
                    restoringBackupFileName = null
                )}
            }
        }
    }

    private fun loginTracker(trackerId: Int, username: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(trackingLoginInProgress = true) }
            try {
                val tracker = trackManager.get(trackerId)
                if (tracker != null) {
                    val success = tracker.login(username, password)
                    if (success) {
                        refreshTrackers()
                        _effect.send(SettingsEffect.ShowSnackbar("Logged in to ${tracker.name}"))
                    } else {
                        _effect.send(SettingsEffect.ShowSnackbar("Failed to login to ${tracker.name}"))
                    }
                }
            } catch (e: Exception) {
                _effect.send(SettingsEffect.ShowSnackbar("Error: ${e.message}"))
            } finally {
                _state.update { it.copy(trackingLoginInProgress = false) }
            }
        }
    }

    private fun logoutTracker(trackerId: Int) {
        viewModelScope.launch {
            val tracker = trackManager.get(trackerId)
            tracker?.logout()
            refreshTrackers()
            _effect.send(SettingsEffect.ShowSnackbar("Logged out from ${tracker?.name}"))
        }
    }

    private fun handleSetReadingRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            readingGoalPreferences.setRemindersEnabled(enabled)
            if (enabled) {
                val hour = readingGoalPreferences.reminderHour.first()
                readingReminderScheduler.schedule(hour)
            } else {
                readingReminderScheduler.cancel()
            }
        }
    }

    private fun handleSetReadingReminderHour(hour: Int) {
        viewModelScope.launch {
            readingGoalPreferences.setReminderHour(hour)
            if (readingGoalPreferences.remindersEnabled.first()) {
                readingReminderScheduler.schedule(hour)
            }
        }
    }

    private fun observeAiPreferences() {
        viewModelScope.launch {
            combine(
                aiPreferences.aiEnabled,
                aiPreferences.aiTier,
                aiPreferences.aiReadingInsights,
                aiPreferences.aiSmartSearch,
                aiPreferences.aiRecommendations
            ) { enabled, tier, insights, smartSearch, recs ->
                _state.update { it.copy(
                    aiEnabled = enabled,
                    aiTier = tier,
                    aiApiKeySet = aiPreferences.getGeminiApiKey().isNotBlank(),
                    aiReadingInsights = insights,
                    aiSmartSearch = smartSearch,
                    aiRecommendations = recs,
                ) }
            }.collect { }
        }
        viewModelScope.launch {
            combine(
                aiPreferences.aiPanelReader,
                aiPreferences.aiSfxTranslation,
                aiPreferences.aiSummaryTranslation,
                aiPreferences.aiSourceIntelligence,
                aiPreferences.aiSmartNotifications
            ) { panelReader, sfx, summary, sourceIntel, smartNotif ->
                _state.update { it.copy(
                    aiPanelReader = panelReader,
                    aiSfxTranslation = sfx,
                    aiSummaryTranslation = summary,
                    aiSourceIntelligence = sourceIntel,
                    aiSmartNotifications = smartNotif,
                ) }
            }.collect { }
        }
        viewModelScope.launch {
            aiPreferences.aiAutoCategorization.collect { autoCat ->
                _state.update { it.copy(aiAutoCategorization = autoCat) }
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
            ) { daily, weekly, reminders, hour ->
                _state.update { current ->
                    current.copy(
                        dailyChapterGoal = daily,
                        weeklyChapterGoal = weekly,
                        readingRemindersEnabled = reminders,
                        readingReminderHour = hour
                    )
                }
            }.collect { }
        }
    }

    private fun observeSyncPreferences() {
        viewModelScope.launch {
            // Combine first 5 flows, then zip in the 6th to stay within the typed overload limit.
            combine(
                syncPreferences.isSyncEnabled,
                syncPreferences.providerId,
                syncPreferences.autoSyncEnabled,
                syncPreferences.syncIntervalHours,
                syncPreferences.syncOnlyOnWifi
            ) { enabled, providerId, autoSync, interval, wifiOnly ->
                SyncPrefBundle(enabled, providerId, autoSync, interval, wifiOnly)
            }.combine(syncPreferences.conflictResolutionStrategy) { bundle, strategy ->
                val lastSync = if (bundle.enabled) syncManager.getLastSyncTime() else null
                _state.update { current ->
                    current.copy(
                        syncEnabled = bundle.enabled,
                        syncProviderId = bundle.providerId,
                        autoSyncEnabled = bundle.autoSync,
                        syncIntervalHours = bundle.interval,
                        syncOnlyOnWifi = bundle.wifiOnly,
                        conflictResolutionStrategy = strategy,
                        lastSyncTime = lastSync
                    )
                }
            }.collect { }
        }
    }

    private fun handleSetSyncEnabled(enabled: Boolean, providerId: String?) {
        viewModelScope.launch {
            if (enabled && providerId != null) {
                syncManager.enableSync(providerId)
            } else {
                syncManager.disableSync()
            }
        }
    }

    private fun handleTriggerManualSync() {
        viewModelScope.launch {
            syncManager.sync()
        }
    }

    /** Intermediate bundle used inside [observeSyncPreferences] to work around the 5-Flow combine limit. */
    private data class SyncPrefBundle(
        val enabled: Boolean,
        val providerId: String?,
        val autoSync: Boolean,
        val interval: Int,
        val wifiOnly: Boolean,
    )
}
