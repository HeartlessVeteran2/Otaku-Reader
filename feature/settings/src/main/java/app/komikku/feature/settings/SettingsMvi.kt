package app.komikku.feature.settings

import app.komikku.core.common.mvi.UiEffect
import app.komikku.core.common.mvi.UiEvent
import app.komikku.core.common.mvi.UiState

data class SettingsState(
    val themeMode: Int = 0,           // 0=system, 1=light, 2=dark
    val useDynamicColor: Boolean = true,
    val readerMode: Int = 0,          // 0=paged, 1=webtoon, 2=LTR
    val updateCheckInterval: Int = 12, // hours
    val notificationsEnabled: Boolean = true
) : UiState

sealed interface SettingsEvent : UiEvent {
    data class SetThemeMode(val mode: Int) : SettingsEvent
    data class SetDynamicColor(val enabled: Boolean) : SettingsEvent
    data class SetReaderMode(val mode: Int) : SettingsEvent
    data class SetUpdateInterval(val hours: Int) : SettingsEvent
    data class SetNotificationsEnabled(val enabled: Boolean) : SettingsEvent
}

sealed interface SettingsEffect : UiEffect {
    data class ShowSnackbar(val message: String) : SettingsEffect
}
