package app.otakureader.feature.settings.delegate

import app.otakureader.core.discord.DiscordRpcService
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.feature.settings.SettingsEffect
import app.otakureader.feature.settings.SettingsEvent
import app.otakureader.feature.settings.SettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppearanceSettingsDelegate @Inject constructor(
    private val generalPreferences: GeneralPreferences,
    private val discordRpcService: DiscordRpcService,
) {

    fun startObserving(
        scope: CoroutineScope,
        updateState: ((SettingsState) -> SettingsState) -> Unit,
    ) {
        scope.launch {
            combine(
                generalPreferences.themeMode,
                generalPreferences.useDynamicColor,
                generalPreferences.usePureBlackDarkMode,
                generalPreferences.useHighContrast,
                generalPreferences.colorScheme,
            ) { themeMode, useDynamicColor, pureBlack, highContrast, colorScheme ->
                updateState { it.copy(
                    themeMode = themeMode,
                    useDynamicColor = useDynamicColor,
                    usePureBlackDarkMode = pureBlack,
                    useHighContrast = highContrast,
                    colorScheme = colorScheme,
                ) }
            }.collect { }
        }
        scope.launch {
            combine(
                generalPreferences.customAccentColor,
                generalPreferences.locale,
                generalPreferences.notificationsEnabled,
                generalPreferences.updateCheckInterval,
                generalPreferences.showNsfwContent,
            ) { accent, locale, notifications, updateInterval, showNsfw ->
                updateState { it.copy(
                    customAccentColor = accent,
                    locale = locale,
                    notificationsEnabled = notifications,
                    updateCheckInterval = updateInterval,
                    showNsfwContent = showNsfw,
                ) }
            }.collect { }
        }
        scope.launch {
            generalPreferences.discordRpcEnabled.collect { discordRpc ->
                updateState { it.copy(discordRpcEnabled = discordRpc) }
            }
        }
        scope.launch {
            generalPreferences.autoThemeColor.collect { autoTheme ->
                updateState { it.copy(autoThemeColor = autoTheme) }
            }
        }
        scope.launch {
            combine(
                generalPreferences.appUpdateCheckEnabled,
                generalPreferences.lastAppUpdateCheck,
            ) { enabled, lastCheck ->
                updateState { it.copy(
                    appUpdateCheckEnabled = enabled,
                    lastAppUpdateCheck = lastCheck,
                ) }
            }.collect { }
        }
    }

    suspend fun handleEvent(
        event: SettingsEvent,
        sendEffect: suspend (SettingsEffect) -> Unit,
    ): Boolean = when (event) {
        is SettingsEvent.SetThemeMode -> { generalPreferences.setThemeMode(event.mode); true }
        is SettingsEvent.SetDynamicColor -> { generalPreferences.setUseDynamicColor(event.enabled); true }
        is SettingsEvent.SetPureBlackDarkMode -> { generalPreferences.setUsePureBlackDarkMode(event.enabled); true }
        is SettingsEvent.SetHighContrast -> { generalPreferences.setUseHighContrast(event.enabled); true }
        is SettingsEvent.SetColorScheme -> { generalPreferences.setColorScheme(event.scheme); true }
        is SettingsEvent.SetCustomAccentColor -> { generalPreferences.setCustomAccentColor(event.color); true }
        is SettingsEvent.SetLocale -> { generalPreferences.setLocale(event.locale); true }
        is SettingsEvent.SetAutoThemeColor -> { generalPreferences.setAutoThemeColor(event.enabled); true }
        is SettingsEvent.SetDiscordRpcEnabled -> { handleSetDiscordRpcEnabled(event.enabled); true }
        is SettingsEvent.SetNotificationsEnabled -> { generalPreferences.setNotificationsEnabled(event.enabled); true }
        is SettingsEvent.SetShowNsfwContent -> { generalPreferences.setShowNsfwContent(event.enabled); true }
        else -> false
    }

    private suspend fun handleSetDiscordRpcEnabled(enabled: Boolean) {
        generalPreferences.setDiscordRpcEnabled(enabled)
        if (enabled) {
            discordRpcService.initialize()
        } else {
            discordRpcService.disconnect()
        }
    }
}
