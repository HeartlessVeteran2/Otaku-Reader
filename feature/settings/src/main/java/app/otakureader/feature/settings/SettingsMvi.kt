package app.otakureader.feature.settings

import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState

data class SettingsState(
    val themeMode: Int = 0,            // 0=system, 1=light, 2=dark
    val useDynamicColor: Boolean = true,
    val locale: String = "",           // BCP-47 tag, or "" for system default
    val readerMode: Int = 0,           // 0=single page, 1=webtoon, 2=dual page, 3=smart panels
    val keepScreenOn: Boolean = true,
    val incognitoMode: Boolean = false, // Incognito mode - reading history not saved
    val libraryGridSize: Int = 3,      // number of columns (2–5)
    val showBadges: Boolean = true,
    val updateCheckInterval: Int = 12, // hours
    val notificationsEnabled: Boolean = true,
    val isBackupInProgress: Boolean = false,
    val isRestoreInProgress: Boolean = false
) : UiState

sealed interface SettingsEvent : UiEvent {
    data class SetThemeMode(val mode: Int) : SettingsEvent
    data class SetDynamicColor(val enabled: Boolean) : SettingsEvent
    data class SetLocale(val locale: String) : SettingsEvent
    data class SetReaderMode(val mode: Int) : SettingsEvent
    data class SetKeepScreenOn(val enabled: Boolean) : SettingsEvent
    data class SetIncognitoMode(val enabled: Boolean) : SettingsEvent
    data class SetLibraryGridSize(val size: Int) : SettingsEvent
    data class SetShowBadges(val enabled: Boolean) : SettingsEvent
    data class SetUpdateInterval(val hours: Int) : SettingsEvent
    data class SetNotificationsEnabled(val enabled: Boolean) : SettingsEvent
    data object OnCreateBackup : SettingsEvent
    data object OnRestoreBackup : SettingsEvent
}

sealed interface SettingsEffect : UiEffect {
    data class ShowSnackbar(val message: String) : SettingsEffect
    data object ShowBackupPicker : SettingsEffect
    data object ShowRestorePicker : SettingsEffect
}
