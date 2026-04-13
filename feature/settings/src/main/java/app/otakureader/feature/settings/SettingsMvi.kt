package app.otakureader.feature.settings

import android.net.Uri
import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.core.preferences.AiTier
import app.otakureader.core.preferences.LocalSourcePreferences
import app.otakureader.feature.reader.model.ImageQuality

data class TrackerInfo(
    val id: Int,
    val name: String,
    val isLoggedIn: Boolean
)

enum class SyncStatus {
    DISABLED,
    IDLE,
    SYNCING,
    SUCCESS,
    ERROR
}

data class SettingsState(
    // --- Appearance ---
    val themeMode: Int = 0,            // 0=system, 1=light, 2=dark
    val useDynamicColor: Boolean = true,
    val usePureBlackDarkMode: Boolean = false,
    val useHighContrast: Boolean = false,
    val colorScheme: Int = 0,
    val customAccentColor: Long = 0xFF1976D2L,
    val locale: String = "",

    // --- Reader - Display ---
    val readerMode: Int = 0,
    val keepScreenOn: Boolean = true,
    val fullscreen: Boolean = true,
    val showContentInCutout: Boolean = true,
    val showPageNumber: Boolean = true,
    val backgroundColor: Int = 0,      // 0=Black, 1=White, 2=Gray, 3=Auto
    val animatePageTransitions: Boolean = true,
    val showReadingModeOverlay: Boolean = true,
    val showTapZonesOverlay: Boolean = false,

    // --- Reader - Scale ---
    val readerScale: Int = 0,          // 0=Fit Screen, 1=Fit Width, 2=Fit Height, 3=Original, 4=Smart Fit
    val autoZoomWideImages: Boolean = true,

    // --- Reader - Tap Zones ---
    val tapZoneConfig: Int = 0,        // 0=Default, 1=Left-handed, 2=Kindle, 3=Edge
    val invertTapZones: Boolean = false,

    // --- Reader - Volume Keys ---
    val volumeKeysEnabled: Boolean = false,
    val volumeKeysInverted: Boolean = false,

    // --- Reader - Interaction ---
    val doubleTapAnimationSpeed: Int = 1,  // 0=Slow, 1=Normal, 2=Fast
    val showActionsOnLongTap: Boolean = true,
    val savePagesToSeparateFolders: Boolean = false,

    // --- Reader - Webtoon ---
    val webtoonSidePadding: Int = 0,   // 0=None, 1=Small, 2=Medium, 3=Large
    val webtoonMenuHideSensitivity: Int = 0,  // 0=Low, 1=Medium, 2=High
    val webtoonDoubleTapZoom: Boolean = true,
    val webtoonDisableZoomOut: Boolean = false,

    // --- Reader - E-ink ---
    val einkFlashOnPageChange: Boolean = false,
    val einkBlackAndWhite: Boolean = false,

    // --- Reader - Behavior ---
    val skipReadChapters: Boolean = false,
    val skipFilteredChapters: Boolean = true,
    val skipDuplicateChapters: Boolean = false,
    val alwaysShowChapterTransition: Boolean = true,

    // --- Reader - Preload ---
    val preloadPagesBefore: Int = 2,
    val preloadPagesAfter: Int = 3,
    val cropBordersEnabled: Boolean = false,
    val imageQuality: String = ImageQuality.ORIGINAL.name,
    val dataSaverEnabled: Boolean = false,
    val incognitoMode: Boolean = false,

    // --- Library ---
    val libraryGridSize: Int = 3,
    val showBadges: Boolean = true,
    val updateOnlyOnWifi: Boolean = false,
    val updateOnlyPinnedCategories: Boolean = false,
    val autoRefreshOnStart: Boolean = false,
    val showUpdateProgress: Boolean = true,

    // --- Downloads ---
    val deleteAfterReading: Boolean = false,
    val saveAsCbz: Boolean = false,
    val autoDownloadEnabled: Boolean = false,
    val downloadOnlyOnWifi: Boolean = true,
    val autoDownloadLimit: Int = 3,
    val concurrentDownloads: Int = 2,
    val downloadAheadWhileReading: Int = 0,
    val downloadAheadOnlyOnWifi: Boolean = true,
    val downloadLocation: String? = null,

    // --- Local Source ---
    val localSourceDirectory: String = LocalSourcePreferences.defaultDirectory(),

    // --- Notifications ---
    val notificationsEnabled: Boolean = true,
    val updateCheckInterval: Int = 12,

    // --- Backup ---
    val isBackupInProgress: Boolean = false,
    val isRestoreInProgress: Boolean = false,
    val restoringBackupFileName: String? = null,
    val autoBackupEnabled: Boolean = false,
    val autoBackupIntervalHours: Int = 24,
    val autoBackupMaxCount: Int = 5,
    val localBackupFiles: List<String> = emptyList(),

    // --- Tracking ---
    val trackers: List<TrackerInfo> = emptyList(),
    val trackingLoginInProgress: Boolean = false,

    // --- Migration ---
    val migrationSimilarityThreshold: Float = 0.7f,
    val migrationAlwaysConfirm: Boolean = false,
    val migrationMinChapterCount: Int = 0,

    // --- Browse ---
    val showNsfwContent: Boolean = false,

    // --- Discord ---
    val discordRpcEnabled: Boolean = false,

    // --- AI ---
    val aiEnabled: Boolean = false,
    val aiTier: AiTier = AiTier.FREE,
    val aiApiKeySet: Boolean = false,
    val showRemoveApiKeyDialog: Boolean = false,
    val aiReadingInsights: Boolean = true,
    val aiSmartSearch: Boolean = true,
    val aiRecommendations: Boolean = true,
    val aiPanelReader: Boolean = true,
    val aiSfxTranslation: Boolean = true,
    val aiSummaryTranslation: Boolean = true,
    val aiSourceIntelligence: Boolean = true,
    val aiSmartNotifications: Boolean = true,
    val aiAutoCategorization: Boolean = true,
    val aiTokensUsedThisMonth: Long = 0L,
    val aiTokenTrackingPeriod: String = "",

    // --- Reading Goals ---
    val dailyChapterGoal: Int = 0,
    val weeklyChapterGoal: Int = 0,
    val readingRemindersEnabled: Boolean = false,
    val readingReminderHour: Int = 20,

    // --- Cloud Sync ---
    val syncEnabled: Boolean = false,
    val syncProviderId: String? = null,
    val syncProviderName: String? = null,
    val lastSyncTime: Long? = null,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val autoSyncEnabled: Boolean = false,
    val syncIntervalHours: Int = 24,
    val syncOnlyOnWifi: Boolean = true,
    val conflictResolutionStrategy: String = "PREFER_NEWER"
) : UiState

sealed interface SettingsEvent : UiEvent {
    // Appearance
    data class SetThemeMode(val mode: Int) : SettingsEvent
    data class SetDynamicColor(val enabled: Boolean) : SettingsEvent
    data class SetPureBlackDarkMode(val enabled: Boolean) : SettingsEvent
    data class SetHighContrast(val enabled: Boolean) : SettingsEvent
    data class SetColorScheme(val scheme: Int) : SettingsEvent
    data class SetCustomAccentColor(val color: Long) : SettingsEvent
    data class SetLocale(val locale: String) : SettingsEvent

    // Reader - Display
    data class SetReaderMode(val mode: Int) : SettingsEvent
    data class SetKeepScreenOn(val enabled: Boolean) : SettingsEvent
    data class SetFullscreen(val enabled: Boolean) : SettingsEvent
    data class SetShowContentInCutout(val enabled: Boolean) : SettingsEvent
    data class SetShowPageNumber(val enabled: Boolean) : SettingsEvent
    data class SetBackgroundColor(val color: Int) : SettingsEvent
    data class SetAnimatePageTransitions(val enabled: Boolean) : SettingsEvent
    data class SetShowReadingModeOverlay(val enabled: Boolean) : SettingsEvent
    data class SetShowTapZonesOverlay(val enabled: Boolean) : SettingsEvent

    // Reader - Scale
    data class SetReaderScale(val scale: Int) : SettingsEvent
    data class SetAutoZoomWideImages(val enabled: Boolean) : SettingsEvent

    // Reader - Tap Zones
    data class SetTapZoneConfig(val config: Int) : SettingsEvent
    data class SetInvertTapZones(val enabled: Boolean) : SettingsEvent

    // Reader - Volume Keys
    data class SetVolumeKeysEnabled(val enabled: Boolean) : SettingsEvent
    data class SetVolumeKeysInverted(val enabled: Boolean) : SettingsEvent

    // Reader - Interaction
    data class SetDoubleTapAnimationSpeed(val speed: Int) : SettingsEvent
    data class SetShowActionsOnLongTap(val enabled: Boolean) : SettingsEvent
    data class SetSavePagesToSeparateFolders(val enabled: Boolean) : SettingsEvent

    // Reader - Webtoon
    data class SetWebtoonSidePadding(val padding: Int) : SettingsEvent
    data class SetWebtoonMenuHideSensitivity(val sensitivity: Int) : SettingsEvent
    data class SetWebtoonDoubleTapZoom(val enabled: Boolean) : SettingsEvent
    data class SetWebtoonDisableZoomOut(val enabled: Boolean) : SettingsEvent

    // Reader - E-ink
    data class SetEinkFlashOnPageChange(val enabled: Boolean) : SettingsEvent
    data class SetEinkBlackAndWhite(val enabled: Boolean) : SettingsEvent

    // Reader - Behavior
    data class SetSkipReadChapters(val enabled: Boolean) : SettingsEvent
    data class SetSkipFilteredChapters(val enabled: Boolean) : SettingsEvent
    data class SetSkipDuplicateChapters(val enabled: Boolean) : SettingsEvent
    data class SetAlwaysShowChapterTransition(val enabled: Boolean) : SettingsEvent

    // Reader - Other
    data class SetIncognitoMode(val enabled: Boolean) : SettingsEvent
    data class SetPreloadPagesBefore(val count: Int) : SettingsEvent
    data class SetPreloadPagesAfter(val count: Int) : SettingsEvent
    data class SetCropBordersEnabled(val enabled: Boolean) : SettingsEvent
    data class SetImageQuality(val quality: String) : SettingsEvent
    data class SetDataSaverEnabled(val enabled: Boolean) : SettingsEvent

    // Library
    data class SetLibraryGridSize(val size: Int) : SettingsEvent
    data class SetShowBadges(val enabled: Boolean) : SettingsEvent
    data class SetUpdateOnlyOnWifi(val enabled: Boolean) : SettingsEvent
    data class SetUpdateOnlyPinnedCategories(val enabled: Boolean) : SettingsEvent
    data class SetAutoRefreshOnStart(val enabled: Boolean) : SettingsEvent
    data class SetShowUpdateProgress(val enabled: Boolean) : SettingsEvent

    // Downloads
    data class SetDeleteAfterReading(val enabled: Boolean) : SettingsEvent
    data class SetSaveAsCbz(val enabled: Boolean) : SettingsEvent
    data class SetAutoDownloadEnabled(val enabled: Boolean) : SettingsEvent
    data class SetDownloadOnlyOnWifi(val enabled: Boolean) : SettingsEvent
    data class SetAutoDownloadLimit(val limit: Int) : SettingsEvent
    data class SetConcurrentDownloads(val count: Int) : SettingsEvent
    data class SetDownloadAheadWhileReading(val count: Int) : SettingsEvent
    data class SetDownloadAheadOnlyOnWifi(val enabled: Boolean) : SettingsEvent
    data class SetDownloadLocation(val location: String?) : SettingsEvent

    // Local Source
    data class SetLocalSourceDirectory(val path: String) : SettingsEvent

    // Notifications
    data class SetNotificationsEnabled(val enabled: Boolean) : SettingsEvent
    data class SetUpdateInterval(val hours: Int) : SettingsEvent

    // Backup
    data object OnCreateBackup : SettingsEvent
    data object OnRestoreBackup : SettingsEvent
    data class CreateBackupWithUri(val uri: Uri) : SettingsEvent
    data class RestoreBackupFromUri(val uri: Uri) : SettingsEvent
    data class SetAutoBackupEnabled(val enabled: Boolean) : SettingsEvent
    data class SetAutoBackupInterval(val hours: Int) : SettingsEvent
    data class SetAutoBackupMaxCount(val count: Int) : SettingsEvent
    data object RefreshLocalBackups : SettingsEvent
    data class RestoreLocalBackup(val fileName: String) : SettingsEvent

    // Tracking
    data class LoginTracker(val trackerId: Int, val username: String, val password: String) : SettingsEvent
    data class LogoutTracker(val trackerId: Int) : SettingsEvent

    // Migration
    data class SetMigrationSimilarityThreshold(val threshold: Float) : SettingsEvent
    data class SetMigrationAlwaysConfirm(val enabled: Boolean) : SettingsEvent
    data class SetMigrationMinChapterCount(val count: Int) : SettingsEvent
    data object OnNavigateToMigration : SettingsEvent

    // Browse
    data class SetShowNsfwContent(val enabled: Boolean) : SettingsEvent

    // Discord
    data class SetDiscordRpcEnabled(val enabled: Boolean) : SettingsEvent

    // AI
    data class SetAiEnabled(val enabled: Boolean) : SettingsEvent
    data class SetAiTier(val tier: AiTier) : SettingsEvent
    data class SetAiApiKey(val key: String) : SettingsEvent
    data object RemoveAiApiKey : SettingsEvent
    data object ConfirmRemoveAiApiKey : SettingsEvent
    data object DismissRemoveApiKeyDialog : SettingsEvent
    data class SetAiReadingInsights(val enabled: Boolean) : SettingsEvent
    data class SetAiSmartSearch(val enabled: Boolean) : SettingsEvent
    data class SetAiRecommendations(val enabled: Boolean) : SettingsEvent
    data class SetAiPanelReader(val enabled: Boolean) : SettingsEvent
    data class SetAiSfxTranslation(val enabled: Boolean) : SettingsEvent
    data class SetAiSummaryTranslation(val enabled: Boolean) : SettingsEvent
    data class SetAiSourceIntelligence(val enabled: Boolean) : SettingsEvent
    data class SetAiSmartNotifications(val enabled: Boolean) : SettingsEvent
    data class SetAiAutoCategorization(val enabled: Boolean) : SettingsEvent
    data object ClearAiCache : SettingsEvent

    // Reading Goals
    data class SetDailyChapterGoal(val goal: Int) : SettingsEvent
    data class SetWeeklyChapterGoal(val goal: Int) : SettingsEvent
    data class SetReadingRemindersEnabled(val enabled: Boolean) : SettingsEvent
    data class SetReadingReminderHour(val hour: Int) : SettingsEvent

    // Cloud Sync
    data class SetSyncEnabled(val enabled: Boolean, val providerId: String?) : SettingsEvent
    data object TriggerManualSync : SettingsEvent
    data class SetAutoSyncEnabled(val enabled: Boolean) : SettingsEvent
    data class SetSyncIntervalHours(val hours: Int) : SettingsEvent
    data class SetSyncOnlyOnWifi(val onlyWifi: Boolean) : SettingsEvent
    data class SetConflictResolutionStrategy(val strategy: String) : SettingsEvent

    // Navigation
    data object NavigateToAbout : SettingsEvent
}

sealed interface SettingsEffect : UiEffect {
    data class ShowSnackbar(val message: String) : SettingsEffect
    data object ShowBackupPicker : SettingsEffect
    data object ShowRestorePicker : SettingsEffect
    data object NavigateToMigrationEntry : SettingsEffect
    data object NavigateToAbout : SettingsEffect
    data class ShowDownloadLocationPicker(val currentLocation: String?) : SettingsEffect
}
