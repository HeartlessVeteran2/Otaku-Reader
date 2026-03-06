package app.komikku.feature.settings

data class SettingsState(
    val theme: String = "system",
    val dynamicColors: Boolean = true,
    val gridSize: Int = 3,
    val autoUpdate: Boolean = true,
)

sealed class SettingsEvent {
    data class OnThemeChange(val theme: String) : SettingsEvent()
    data class OnDynamicColorsChange(val enabled: Boolean) : SettingsEvent()
    data class OnGridSizeChange(val size: Int) : SettingsEvent()
    data class OnAutoUpdateChange(val enabled: Boolean) : SettingsEvent()
}

sealed class SettingsEffect
