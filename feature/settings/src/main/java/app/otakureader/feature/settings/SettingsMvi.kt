package app.otakureader.feature.settings

import android.net.Uri
import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.core.preferences.LocalSourcePreferences

data class TrackerInfo(
    val id: Int,
    val name: String,
    val isLoggedIn: Boolean
)

/**
 * Root settings state composed from focused per-section sub-states.
 * Screens that only need one section can collect [appearance], [reader], [library], etc.
 * independently to avoid recomposing on unrelated changes.
 */
data class SettingsState(
    val appearance: AppearanceState = AppearanceState(),
    val reader: ReaderSettingsState = ReaderSettingsState(),
    val library: LibrarySettingsState = LibrarySettingsState(),
    val downloads: DownloadSettingsState = DownloadSettingsState(),
    val backup: BackupSettingsState = BackupSettingsState(),
    val tracking: TrackingSettingsState = TrackingSettingsState(),

    // --- Local Source ---
    val localSourceDirectory: String = LocalSourcePreferences.defaultDirectory(),
    val allowLocalSourceHiddenFolders: Boolean = false,

    // --- Notifications ---
    val notificationsEnabled: Boolean = true,
    val updateCheckInterval: Int = 12,

    // --- Migration ---
    val migrationSimilarityThreshold: Float = 0.7f,
    val migrationAlwaysConfirm: Boolean = false,
    val migrationMinChapterCount: Int = 0,

    // --- Browse ---
    val showNsfwContent: Boolean = false,

    // --- Security ---
    val biometricLockEnabled: Boolean = false,
    val biometricLockTimeoutMinutes: Int = 0,
    val biometricLockScheduleEnabled: Boolean = false,
    val biometricLockStartHour: Int = 22,
    val biometricLockEndHour: Int = 8,
    val biometricLockActiveDays: Set<Int> = emptySet(),

    // --- Discord ---
    val discordRpcEnabled: Boolean = false,

    // --- Reading Goals ---
    val dailyChapterGoal: Int = 0,
    val weeklyChapterGoal: Int = 0,
    val readingRemindersEnabled: Boolean = false,
    val readingReminderHour: Int = 20,

    // App Update Checker
    val appUpdateCheckEnabled: Boolean = true,
    val lastAppUpdateCheck: Long = 0L,

    // Image Cache
    val coilDiskCacheSizeMb: Int = app.otakureader.core.preferences.GeneralPreferences.DEFAULT_COIL_DISK_CACHE_MB,
) : UiState {

    // Convenience accessors kept for backward compatibility with existing screen composables.
    // --- Appearance ---
    val themeMode get() = appearance.themeMode
    val useDynamicColor get() = appearance.useDynamicColor
    val usePureBlackDarkMode get() = appearance.usePureBlackDarkMode
    val useHighContrast get() = appearance.useHighContrast
    val colorScheme get() = appearance.colorScheme
    val customAccentColor get() = appearance.customAccentColor
    val locale get() = appearance.locale
    val autoThemeColor get() = appearance.autoThemeColor
    val visualEffectsEnabled get() = appearance.visualEffectsEnabled

    // --- Reader ---
    val readerMode get() = reader.readerMode
    val keepScreenOn get() = reader.keepScreenOn
    val fullscreen get() = reader.fullscreen
    val showContentInCutout get() = reader.showContentInCutout
    val showPageNumber get() = reader.showPageNumber
    val showPageThumbnailStrip get() = reader.showPageThumbnailStrip
    val backgroundColor get() = reader.backgroundColor
    val animatePageTransitions get() = reader.animatePageTransitions
    val showReadingModeOverlay get() = reader.showReadingModeOverlay
    val showTapZonesOverlay get() = reader.showTapZonesOverlay
    val readerScale get() = reader.readerScale
    val autoZoomWideImages get() = reader.autoZoomWideImages
    val tapZoneConfig get() = reader.tapZoneConfig
    val invertTapZones get() = reader.invertTapZones
    val volumeKeysEnabled get() = reader.volumeKeysEnabled
    val volumeKeysInverted get() = reader.volumeKeysInverted
    val volumeKeyBehaviorSinglePage get() = reader.volumeKeyBehaviorSinglePage
    val volumeKeyBehaviorDualPage get() = reader.volumeKeyBehaviorDualPage
    val volumeKeyBehaviorWebtoon get() = reader.volumeKeyBehaviorWebtoon
    val volumeKeyBehaviorSmartPanels get() = reader.volumeKeyBehaviorSmartPanels
    val showActionsOnLongTap get() = reader.showActionsOnLongTap
    val savePagesToSeparateFolders get() = reader.savePagesToSeparateFolders
    val webtoonSidePadding get() = reader.webtoonSidePadding
    val webtoonMenuHideSensitivity get() = reader.webtoonMenuHideSensitivity
    val webtoonDoubleTapZoom get() = reader.webtoonDoubleTapZoom
    val webtoonDisableZoomOut get() = reader.webtoonDisableZoomOut
    val einkFlashOnPageChange get() = reader.einkFlashOnPageChange
    val einkBlackAndWhite get() = reader.einkBlackAndWhite
    val skipReadChapters get() = reader.skipReadChapters
    val skipFilteredChapters get() = reader.skipFilteredChapters
    val skipDuplicateChapters get() = reader.skipDuplicateChapters
    val alwaysShowChapterTransition get() = reader.alwaysShowChapterTransition
    val preloadPagesBefore get() = reader.preloadPagesBefore
    val preloadPagesAfter get() = reader.preloadPagesAfter
    val cropBordersEnabled get() = reader.cropBordersEnabled
    val imageQuality get() = reader.imageQuality
    val dataSaverEnabled get() = reader.dataSaverEnabled
    val incognitoMode get() = reader.incognitoMode
    val secureScreen get() = reader.secureScreen

    // --- Library ---
    val libraryGridSize get() = library.libraryGridSize
    val isStaggeredGrid get() = library.isStaggeredGrid
    val showBadges get() = library.showBadges
    val showDownloadBadge get() = library.showDownloadBadge
    val updateOnlyOnWifi get() = library.updateOnlyOnWifi
    val updateOnlyPinnedCategories get() = library.updateOnlyPinnedCategories
    val autoRefreshOnStart get() = library.autoRefreshOnStart
    val showUpdateProgress get() = library.showUpdateProgress
    val skipUpdatesWithUnread get() = library.skipUpdatesWithUnread
    val skipUpdatesWithCompleted get() = library.skipUpdatesWithCompleted
    val skipUpdatesNeverStarted get() = library.skipUpdatesNeverStarted

    // --- Downloads ---
    val deleteAfterReading get() = downloads.deleteAfterReading
    val saveAsCbz get() = downloads.saveAsCbz
    val autoDownloadEnabled get() = downloads.autoDownloadEnabled
    val downloadOnlyOnWifi get() = downloads.downloadOnlyOnWifi
    val autoDownloadLimit get() = downloads.autoDownloadLimit
    val concurrentDownloads get() = downloads.concurrentDownloads
    val downloadAheadWhileReading get() = downloads.downloadAheadWhileReading
    val downloadAheadOnlyOnWifi get() = downloads.downloadAheadOnlyOnWifi
    val downloadLocation get() = downloads.downloadLocation
    val smartDownloadEnabled get() = downloads.smartDownloadEnabled
    val smartDownloadChaptersAhead get() = downloads.smartDownloadChaptersAhead
    val smartDownloadThreshold get() = downloads.smartDownloadThreshold
    val smartDownloadWifiOnly get() = downloads.smartDownloadWifiOnly
    val smartDownloadFavoritesOnly get() = downloads.smartDownloadFavoritesOnly
    val smartDownloadMinStorageMb get() = downloads.smartDownloadMinStorageMb
    val downloadDataSaverEnabled get() = downloads.downloadDataSaverEnabled
    val autoDownloadCategoryInclude get() = downloads.autoDownloadCategoryInclude
    val autoDownloadCategoryExclude get() = downloads.autoDownloadCategoryExclude
    val availableCategories get() = downloads.availableCategories
    val cbzEncryptionEnabled get() = downloads.cbzEncryptionEnabled

    // --- Backup ---
    val isBackupInProgress get() = backup.isBackupInProgress
    val isRestoreInProgress get() = backup.isRestoreInProgress
    val restoringBackupFileName get() = backup.restoringBackupFileName
    val autoBackupEnabled get() = backup.autoBackupEnabled
    val autoBackupIntervalHours get() = backup.autoBackupIntervalHours
    val autoBackupMaxCount get() = backup.autoBackupMaxCount
    val autoBackupLocationUri get() = backup.autoBackupLocationUri
    val lastAutoBackupTimestamp get() = backup.lastAutoBackupTimestamp
    val localBackupFiles get() = backup.localBackupFiles
    val tachiyomiImportPreview get() = backup.tachiyomiImportPreview
    val isTachiyomiImporting get() = backup.isTachiyomiImporting
    val tachiyomiImportProgress get() = backup.tachiyomiImportProgress
    val tachiyomiImportTotal get() = backup.tachiyomiImportTotal
    val backupEncryptionEnabled get() = backup.backupEncryptionEnabled
    val backupEncryptionPasswordSet get() = backup.backupEncryptionPasswordSet
    val showBackupChecklist get() = backup.showBackupChecklist
    val backupChecklistMangaCount get() = backup.backupChecklistMangaCount
    val backupChecklistCategoryCount get() = backup.backupChecklistCategoryCount
    val backupChecklistTrackingCount get() = backup.backupChecklistTrackingCount
    val showRestoreConfirm get() = backup.showRestoreConfirm
    val pendingRestoreUri get() = backup.pendingRestoreUri
    val pendingRestoreFileName get() = backup.pendingRestoreFileName

    // --- Tracking ---
    val trackers get() = tracking.trackers
    val trackingLoginInProgress get() = tracking.trackingLoginInProgress
    val batchSyncInProgress get() = tracking.batchSyncInProgress
    val batchSyncSummary get() = tracking.batchSyncSummary
}

sealed interface SettingsEvent : UiEvent {
    // Appearance
    data class SetThemeMode(val mode: Int) : SettingsEvent
    data class SetDynamicColor(val enabled: Boolean) : SettingsEvent
    data class SetPureBlackDarkMode(val enabled: Boolean) : SettingsEvent
    data class SetHighContrast(val enabled: Boolean) : SettingsEvent
    data class SetColorScheme(val scheme: Int) : SettingsEvent
    data class SetCustomAccentColor(val color: Long) : SettingsEvent
    data class SetLocale(val locale: String) : SettingsEvent
    data class SetAutoThemeColor(val enabled: Boolean) : SettingsEvent
    data class SetVisualEffectsEnabled(val enabled: Boolean) : SettingsEvent
    data class SetDarkModeScheduleEnabled(val enabled: Boolean) : SettingsEvent
    data class SetDarkModeStartMinute(val minute: Int) : SettingsEvent
    data class SetDarkModeEndMinute(val minute: Int) : SettingsEvent

    // Reader - Display
    data class SetReaderMode(val mode: Int) : SettingsEvent
    data class SetKeepScreenOn(val enabled: Boolean) : SettingsEvent
    data class SetFullscreen(val enabled: Boolean) : SettingsEvent
    data class SetShowContentInCutout(val enabled: Boolean) : SettingsEvent
    data class SetShowPageNumber(val enabled: Boolean) : SettingsEvent
    data class SetShowPageThumbnailStrip(val enabled: Boolean) : SettingsEvent
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
    data class SetVolumeKeyBehaviorForMode(val mode: Int, val behavior: Int) : SettingsEvent

    // Reader - Interaction
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
    data class SetSecureScreen(val enabled: Boolean) : SettingsEvent

    // Library
    data class SetLibraryGridSize(val size: Int) : SettingsEvent
    data class SetStaggeredGrid(val staggered: Boolean) : SettingsEvent
    data class SetShowBadges(val enabled: Boolean) : SettingsEvent
    data class SetShowDownloadBadge(val enabled: Boolean) : SettingsEvent
    data class SetUpdateOnlyOnWifi(val enabled: Boolean) : SettingsEvent
    data class SetUpdateOnlyPinnedCategories(val enabled: Boolean) : SettingsEvent
    data class SetAutoRefreshOnStart(val enabled: Boolean) : SettingsEvent
    data class SetShowUpdateProgress(val enabled: Boolean) : SettingsEvent
    data class SetSkipUpdatesWithUnread(val enabled: Boolean) : SettingsEvent
    data class SetSkipUpdatesWithCompleted(val enabled: Boolean) : SettingsEvent
    data class SetSkipUpdatesNeverStarted(val enabled: Boolean) : SettingsEvent

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
    data object RequestDownloadLocationPicker : SettingsEvent

    // Smart download rules
    data class SetSmartDownloadEnabled(val enabled: Boolean) : SettingsEvent
    data class SetSmartDownloadChaptersAhead(val count: Int) : SettingsEvent
    data class SetSmartDownloadThreshold(val threshold: Float) : SettingsEvent
    data class SetSmartDownloadWifiOnly(val enabled: Boolean) : SettingsEvent
    data class SetSmartDownloadFavoritesOnly(val enabled: Boolean) : SettingsEvent
    data class SetSmartDownloadMinStorageMb(val mb: Int) : SettingsEvent

    // Local Source
    data class SetLocalSourceDirectory(val path: String) : SettingsEvent
    data class SetAllowLocalSourceHiddenFolders(val allowed: Boolean) : SettingsEvent

    // Notifications
    data class SetNotificationsEnabled(val enabled: Boolean) : SettingsEvent
    data class SetUpdateInterval(val hours: Int) : SettingsEvent

    // Backup
    data object OnCreateBackup : SettingsEvent
    data object OnRestoreBackup : SettingsEvent
    data object OnImportTachiyomiBackup : SettingsEvent
    /** User tapped the Backup button — shows the checklist dialog before the file picker. */
    data object ShowBackupChecklist : SettingsEvent
    /** User confirmed the checklist dialog — opens the file-save picker. */
    data object ConfirmCreateBackup : SettingsEvent
    /** User cancelled the checklist dialog. */
    data object DismissBackupChecklist : SettingsEvent
    /** File picker returned a restore URI — show the preflight confirmation before restoring. */
    data class ShowRestoreConfirm(val uri: Uri) : SettingsEvent
    /** User confirmed the restore preflight dialog — proceed with the restore. */
    data class ConfirmRestore(val uri: Uri) : SettingsEvent
    /** User cancelled the restore preflight dialog. */
    data object DismissRestoreConfirm : SettingsEvent
    data class CreateBackupWithUri(val uri: Uri) : SettingsEvent
    data class RestoreBackupFromUri(val uri: Uri) : SettingsEvent
    data class ImportTachiyomiBackupFromUri(val uri: Uri) : SettingsEvent
    data class ConfirmTachiyomiImport(val overwriteExisting: Boolean) : SettingsEvent
    data object CancelTachiyomiImport : SettingsEvent
    data class SetAutoBackupEnabled(val enabled: Boolean) : SettingsEvent
    data class SetAutoBackupInterval(val hours: Int) : SettingsEvent
    data class SetAutoBackupMaxCount(val count: Int) : SettingsEvent
    data object RequestAutoBackupLocationPicker : SettingsEvent
    data class SetAutoBackupLocation(val uri: String) : SettingsEvent
    data object RefreshLocalBackups : SettingsEvent
    data class RestoreLocalBackup(val fileName: String) : SettingsEvent

    // Backup Encryption
    data class SetBackupEncryptionEnabled(val enabled: Boolean) : SettingsEvent
    data object RequestSetBackupPassword : SettingsEvent
    data class SetBackupEncryptionPassword(val password: String) : SettingsEvent
    data class CreateEncryptedBackupWithUri(val uri: Uri, val password: String) : SettingsEvent
    data class RestoreEncryptedBackupFromUri(val uri: Uri, val password: String) : SettingsEvent

    // Tracking
    data class LoginTracker(val trackerId: Int, val username: String, val password: String) : SettingsEvent
    data class LogoutTracker(val trackerId: Int) : SettingsEvent
    data object SyncAllTrackers : SettingsEvent
    data object DismissTrackerSyncSummary : SettingsEvent

    // Migration
    data class SetMigrationSimilarityThreshold(val threshold: Float) : SettingsEvent
    data class SetMigrationAlwaysConfirm(val enabled: Boolean) : SettingsEvent
    data class SetMigrationMinChapterCount(val count: Int) : SettingsEvent
    data object OnNavigateToMigration : SettingsEvent

    // Browse
    data class SetShowNsfwContent(val enabled: Boolean) : SettingsEvent

    // Security
    data class SetBiometricLockEnabled(val enabled: Boolean) : SettingsEvent
    data class SetBiometricLockTimeout(val minutes: Int) : SettingsEvent
    data class SetBiometricLockScheduleEnabled(val enabled: Boolean) : SettingsEvent
    data class SetBiometricLockStartHour(val hour: Int) : SettingsEvent
    data class SetBiometricLockEndHour(val hour: Int) : SettingsEvent
    data class SetBiometricLockActiveDays(val days: Set<Int>) : SettingsEvent

    // Discord
    data class SetDiscordRpcEnabled(val enabled: Boolean) : SettingsEvent

    // Reading Goals
    data class SetDailyChapterGoal(val goal: Int) : SettingsEvent
    data class SetWeeklyChapterGoal(val goal: Int) : SettingsEvent
    data class SetReadingRemindersEnabled(val enabled: Boolean) : SettingsEvent
    data class SetReadingReminderHour(val hour: Int) : SettingsEvent

    // Cloud Sync
    // Removed: cloud sync events (provider selection, manual sync, auto-sync interval,
    // conflict resolution, self-hosted URL/token, Google Drive sign-in/disconnect) were
    // extracted along with the cloud sync modules.

    // App Update Checker
    data class SetAppUpdateCheckEnabled(val enabled: Boolean) : SettingsEvent
    data object CheckForAppUpdate : SettingsEvent

    // Data management
    data object ClearImageCache : SettingsEvent
    data object RefreshLibraryCovers : SettingsEvent
    data object ClearHistory : SettingsEvent
    data class SetCoilDiskCacheSizeMb(val sizeMb: Int) : SettingsEvent

    // Navigation
    data object NavigateToAbout : SettingsEvent
    data object NavigateToCloudBackup : SettingsEvent
    data object NavigateToDataUsage : SettingsEvent
    data object NavigateToStorageAnalytics : SettingsEvent

    // Downloads — Data Saver
    data class SetDownloadDataSaverEnabled(val enabled: Boolean) : SettingsEvent

    // Downloads — Per-category auto-download filter
    data class SetAutoDownloadCategoryInclude(val categoryIds: Set<Long>) : SettingsEvent
    data class SetAutoDownloadCategoryExclude(val categoryIds: Set<Long>) : SettingsEvent

    // Downloads — CBZ Encryption
    data class SetCbzEncryptionEnabled(val enabled: Boolean) : SettingsEvent
    data class SetCbzEncryptionPassword(val password: String) : SettingsEvent
}

sealed interface SettingsEffect : UiEffect {
    data class ShowSnackbar(val message: String) : SettingsEffect
    data object ShowBackupPicker : SettingsEffect
    data object ShowRestorePicker : SettingsEffect
    data object ShowTachiyomiImportPicker : SettingsEffect
    data object NavigateToMigrationEntry : SettingsEffect
    data object NavigateToAbout : SettingsEffect
    data class ShowDownloadLocationPicker(val currentLocation: String?) : SettingsEffect
    data object ShowAutoBackupLocationPicker : SettingsEffect
    data object NavigateToCloudBackup : SettingsEffect
    data object NavigateToDataUsage : SettingsEffect
    data object NavigateToStorageAnalytics : SettingsEffect
    data object ShowEncryptionPasswordSetupDialog : SettingsEffect
    data class ShowEncryptionPasswordForBackupDialog(val uri: Uri) : SettingsEffect
    data class ShowEncryptionPasswordForRestoreDialog(val uri: Uri) : SettingsEffect
    data object ShowCbzEncryptionPasswordDialog : SettingsEffect
}
