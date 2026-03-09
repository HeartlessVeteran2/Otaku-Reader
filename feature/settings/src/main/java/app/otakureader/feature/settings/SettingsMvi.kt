package app.otakureader.feature.settings

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.core.preferences.LocalSourcePreferences

data class TrackerInfo(
    val id: Int,
    val name: String,
    val isLoggedIn: Boolean
)

data class SettingsState(
    val themeMode: Int = 0,            // 0=system, 1=light, 2=dark
    val useDynamicColor: Boolean = true,
    val usePureBlackDarkMode: Boolean = false,  // Pure Black AMOLED mode
    val colorScheme: Int = 0,          // 0=System Default, 1=Dynamic, 2-10=Custom schemes
    val locale: String = "",           // BCP-47 tag, or "" for system default
    val readerMode: Int = 0,           // 0=single page, 1=webtoon, 2=dual page, 3=smart panels
    val keepScreenOn: Boolean = true,
    val incognitoMode: Boolean = false, // Incognito mode - reading history not saved
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
    val trackers: List<TrackerInfo> = emptyList(),
    val trackingLoginInProgress: Boolean = false,
    // --- Migration settings ---
    val migrationSimilarityThreshold: Float = 0.7f,
    val migrationAlwaysConfirm: Boolean = false,
    val migrationMinChapterCount: Int = 0
) : UiState

sealed interface SettingsEvent : UiEvent {
    data class SetThemeMode(val mode: Int) : SettingsEvent
    data class SetDynamicColor(val enabled: Boolean) : SettingsEvent
    data class SetPureBlackDarkMode(val enabled: Boolean) : SettingsEvent
    data class SetColorScheme(val scheme: Int) : SettingsEvent
    data class SetLocale(val locale: String) : SettingsEvent
    data class SetReaderMode(val mode: Int) : SettingsEvent
    data class SetKeepScreenOn(val enabled: Boolean) : SettingsEvent
    data class SetIncognitoMode(val enabled: Boolean) : SettingsEvent
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
    data class LoginTracker(val trackerId: Int, val username: String, val password: String) : SettingsEvent
    data class LogoutTracker(val trackerId: Int) : SettingsEvent
    data class SetMigrationSimilarityThreshold(val threshold: Float) : SettingsEvent
    data class SetMigrationAlwaysConfirm(val enabled: Boolean) : SettingsEvent
    data class SetMigrationMinChapterCount(val count: Int) : SettingsEvent
}

sealed interface SettingsEffect : UiEffect {
    data class ShowSnackbar(val message: String) : SettingsEffect
    data object ShowBackupPicker : SettingsEffect
    data object ShowRestorePicker : SettingsEffect
}
