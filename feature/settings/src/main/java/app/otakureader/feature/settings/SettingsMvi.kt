package app.otakureader.feature.settings

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.core.preferences.AiTier
import app.otakureader.core.preferences.LocalSourcePreferences

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
    val themeMode: Int = 0,            // 0=system, 1=light, 2=dark
    val useDynamicColor: Boolean = true,
    val usePureBlackDarkMode: Boolean = false,  // Pure Black AMOLED mode
    val useHighContrast: Boolean = false,       // High-contrast mode for accessibility
    val colorScheme: Int = 0,          // 0=System Default, 1=Dynamic, 2-10=Custom schemes, COLOR_SCHEME_CUSTOM_ACCENT=Custom accent
    val customAccentColor: Long = 0xFF1976D2L, // Custom accent color ARGB (used when colorScheme == 11)
    val locale: String = "",           // BCP-47 tag, or "" for system default
    val readerMode: Int = 0,           // 0=single page, 1=webtoon, 2=dual page, 3=smart panels
    val keepScreenOn: Boolean = true,
    val incognitoMode: Boolean = false, // Incognito mode - reading history not saved
    val preloadPagesBefore: Int = 3,   // Pages to preload before current (0–10)
    val preloadPagesAfter: Int = 3,    // Pages to preload after current (0–10)
    val cropBordersEnabled: Boolean = false, // Automatically crop white/black borders from page images
    /** ImageQuality stored as enum entry name (e.g. "ORIGINAL", "HIGH", "MEDIUM", "LOW") for stability. */
    val imageQuality: String = "ORIGINAL",
    val dataSaverEnabled: Boolean = false,   // Data saver mode - reduce image quality for bandwidth savings
    val deleteAfterReading: Boolean = false,
    val saveAsCbz: Boolean = false,    // Save downloaded chapters as CBZ archives
    val libraryGridSize: Int = 3,      // number of columns (2–5)
    val showBadges: Boolean = true,
    val updateCheckInterval: Int = 12, // hours
    val notificationsEnabled: Boolean = true,
    val autoDownloadEnabled: Boolean = false, // Auto-download new chapters
    val downloadOnlyOnWifi: Boolean = true,   // Download only when connected to Wi-Fi
    val autoDownloadLimit: Int = 3,           // Max chapters to auto-download per manga
    val localSourceDirectory: String = LocalSourcePreferences.defaultDirectory(), // Local source scan directory
    val isBackupInProgress: Boolean = false,
    val isRestoreInProgress: Boolean = false,
    val restoringBackupFileName: String? = null,
    // --- Auto-backup settings ---
    val autoBackupEnabled: Boolean = false,
    val autoBackupIntervalHours: Int = 24,
    val autoBackupMaxCount: Int = 5,
    val localBackupFiles: List<String> = emptyList(),
    val trackers: List<TrackerInfo> = emptyList(),
    val trackingLoginInProgress: Boolean = false,
    // --- Migration settings ---
    val migrationSimilarityThreshold: Float = 0.7f,
    val migrationAlwaysConfirm: Boolean = false,
    val migrationMinChapterCount: Int = 0,
    // --- Browse ---
    val showNsfwContent: Boolean = false,
    // --- Discord Rich Presence ---
    val discordRpcEnabled: Boolean = false,
    // --- AI ---
    val aiEnabled: Boolean = false,
    val aiTier: AiTier = AiTier.FREE,
    val aiApiKeySet: Boolean = false,
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
    /**
     * Conflict resolution strategy stored as enum name (PREFER_NEWER, PREFER_LOCAL, PREFER_REMOTE, MERGE).
     * Using stable string identifier instead of ordinal to avoid issues when enum order changes.
     */
    val conflictResolutionStrategy: String = "PREFER_NEWER"
) : UiState

sealed interface SettingsEvent : UiEvent {
    data class SetThemeMode(val mode: Int) : SettingsEvent
    data class SetDynamicColor(val enabled: Boolean) : SettingsEvent
    data class SetPureBlackDarkMode(val enabled: Boolean) : SettingsEvent
    data class SetHighContrast(val enabled: Boolean) : SettingsEvent
    data class SetColorScheme(val scheme: Int) : SettingsEvent
    data class SetCustomAccentColor(val color: Long) : SettingsEvent
    data class SetLocale(val locale: String) : SettingsEvent
    data class SetReaderMode(val mode: Int) : SettingsEvent
    data class SetKeepScreenOn(val enabled: Boolean) : SettingsEvent
    data class SetIncognitoMode(val enabled: Boolean) : SettingsEvent
    data class SetPreloadPagesBefore(val count: Int) : SettingsEvent
    data class SetPreloadPagesAfter(val count: Int) : SettingsEvent
    data class SetCropBordersEnabled(val enabled: Boolean) : SettingsEvent
    data class SetImageQuality(val quality: String) : SettingsEvent
    data class SetDataSaverEnabled(val enabled: Boolean) : SettingsEvent
    data class SetDeleteAfterReading(val enabled: Boolean) : SettingsEvent
    data class SetLibraryGridSize(val size: Int) : SettingsEvent
    data class SetShowBadges(val enabled: Boolean) : SettingsEvent
    data class SetUpdateInterval(val hours: Int) : SettingsEvent
    data class SetNotificationsEnabled(val enabled: Boolean) : SettingsEvent
    data class SetAutoDownloadEnabled(val enabled: Boolean) : SettingsEvent
    data class SetDownloadOnlyOnWifi(val enabled: Boolean) : SettingsEvent
    data class SetAutoDownloadLimit(val limit: Int) : SettingsEvent
    data class SetSaveAsCbz(val enabled: Boolean) : SettingsEvent
    data class SetLocalSourceDirectory(val path: String) : SettingsEvent
    data object OnCreateBackup : SettingsEvent
    data object OnRestoreBackup : SettingsEvent
    // Auto-backup events
    data class SetAutoBackupEnabled(val enabled: Boolean) : SettingsEvent
    data class SetAutoBackupInterval(val hours: Int) : SettingsEvent
    data class SetAutoBackupMaxCount(val count: Int) : SettingsEvent
    data object RefreshLocalBackups : SettingsEvent
    data class RestoreLocalBackup(val fileName: String) : SettingsEvent
    data class LoginTracker(val trackerId: Int, val username: String, val password: String) : SettingsEvent
    data class LogoutTracker(val trackerId: Int) : SettingsEvent
    data class SetMigrationSimilarityThreshold(val threshold: Float) : SettingsEvent
    data class SetMigrationAlwaysConfirm(val enabled: Boolean) : SettingsEvent
    data class SetMigrationMinChapterCount(val count: Int) : SettingsEvent
    data object OnNavigateToMigration : SettingsEvent
    data class SetShowNsfwContent(val enabled: Boolean) : SettingsEvent
    // --- Discord Rich Presence ---
    data class SetDiscordRpcEnabled(val enabled: Boolean) : SettingsEvent
    // --- AI ---
    data class SetAiEnabled(val enabled: Boolean) : SettingsEvent
    data class SetAiTier(val tier: AiTier) : SettingsEvent
    data class SetAiApiKey(val key: String) : SettingsEvent
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
    // --- Reading Goals ---
    data class SetDailyChapterGoal(val goal: Int) : SettingsEvent
    data class SetWeeklyChapterGoal(val goal: Int) : SettingsEvent
    data class SetReadingRemindersEnabled(val enabled: Boolean) : SettingsEvent
    data class SetReadingReminderHour(val hour: Int) : SettingsEvent
    // --- Cloud Sync ---
    data class SetSyncEnabled(val enabled: Boolean, val providerId: String?) : SettingsEvent
    data object TriggerManualSync : SettingsEvent
    data class SetAutoSyncEnabled(val enabled: Boolean) : SettingsEvent
    data class SetSyncIntervalHours(val hours: Int) : SettingsEvent
    data class SetSyncOnlyOnWifi(val onlyWifi: Boolean) : SettingsEvent
    data class SetConflictResolutionStrategy(val strategy: String) : SettingsEvent
}

sealed interface SettingsEffect : UiEffect {
    data class ShowSnackbar(val message: String) : SettingsEffect
    data object ShowBackupPicker : SettingsEffect
    data object ShowRestorePicker : SettingsEffect
    data object NavigateToMigrationEntry : SettingsEffect
}
