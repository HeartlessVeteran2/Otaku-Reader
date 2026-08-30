package app.otakureader.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import app.otakureader.feature.settings.SettingsScreen
import app.otakureader.feature.settings.localSourceBrowserScreen
import app.otakureader.feature.settings.settingsAdvancedScreen
import app.otakureader.feature.settings.settingsAppearanceScreen
import app.otakureader.feature.settings.settingsBrowseScreen
import app.otakureader.feature.settings.cloudbackup.cloudBackupSettingsScreen
import app.otakureader.feature.settings.settingsBackupScreen
import app.otakureader.feature.settings.settingsDiscordScreen
import app.otakureader.feature.settings.datausage.dataUsageScreen
import app.otakureader.feature.settings.settingsDownloadsScreen
import app.otakureader.feature.settings.settingsLibraryScreen
import app.otakureader.feature.settings.settingsNotificationsScreen
import app.otakureader.feature.settings.settingsReaderScreen
import app.otakureader.feature.settings.settingsSecurityScreen
import app.otakureader.feature.settings.settingsTrackingScreen
import app.otakureader.feature.settings.navorder.settingsNavOrderScreen
import app.otakureader.feature.settings.readerpresets.readerPresetsScreen
import app.otakureader.feature.settings.storage.storageAnalyticsScreen
import app.otakureader.feature.settings.sync.syncSettingsScreen
import app.otakureader.feature.settings.widgetConfigurationScreen

/**
 * Registers the settings hub and all settings sub-screen destinations in the NavGraph.
 *
 * Each sub-screen is backed by its own focused ViewModel so that only the preferences
 * relevant to that screen are observed and written.
 */
fun NavGraphBuilder.settingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit = {},
    onNavigateToMigrationEntry: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToReader: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToTracking: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToDiscord: () -> Unit = {},
    onNavigateToSecurity: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToWidgetConfiguration: () -> Unit = {},
    onNavigateToLocalSourceBrowser: () -> Unit = {},
    onNavigateToCloudBackup: () -> Unit = {},
    onNavigateToDataUsage: () -> Unit = {},
    onNavigateToBrowse: () -> Unit = {},
    onNavigateToSync: () -> Unit = {},
    onNavigateToNavOrder: () -> Unit = {},
    onNavigateToReaderPresets: () -> Unit = {},
    onNavigateToStorageAnalytics: () -> Unit = {},
    onNavigateToExtensionRepos: () -> Unit = {},
    onNavigateToAdvanced: () -> Unit = {},
) {
    composable<Route.Settings> {
        SettingsScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToAbout = onNavigateToAbout,
            onNavigateToMigrationEntry = onNavigateToMigrationEntry,
            onNavigateToAppearance = onNavigateToAppearance,
            onNavigateToLibrary = onNavigateToLibrary,
            onNavigateToReader = onNavigateToReader,
            onNavigateToDownloads = onNavigateToDownloads,
            onNavigateToTracking = onNavigateToTracking,
            onNavigateToBrowse = onNavigateToBrowse,
            onNavigateToBackup = onNavigateToBackup,
            onNavigateToDiscord = onNavigateToDiscord,
            onNavigateToSecurity = onNavigateToSecurity,
            onNavigateToNotifications = onNavigateToNotifications,
            onNavigateToWidgetConfiguration = onNavigateToWidgetConfiguration,
            onNavigateToLocalSourceBrowser = onNavigateToLocalSourceBrowser,
            onNavigateToSync = onNavigateToSync,
            onNavigateToNavOrder = onNavigateToNavOrder,
            onNavigateToAdvanced = onNavigateToAdvanced,
        )
    }

    settingsAppearanceScreen(onNavigateBack = onNavigateBack)
    settingsBrowseScreen(
        onNavigateBack = onNavigateBack,
        onNavigateToExtensionRepos = onNavigateToExtensionRepos,
    )
    settingsLibraryScreen(onNavigateBack = onNavigateBack)
    settingsReaderScreen(
        onNavigateBack = onNavigateBack,
        onNavigateToReaderPresets = onNavigateToReaderPresets,
    )
    settingsDownloadsScreen(
        onNavigateBack = onNavigateBack,
        onNavigateToDataUsage = onNavigateToDataUsage,
        onNavigateToStorageAnalytics = onNavigateToStorageAnalytics,
    )
    dataUsageScreen(onNavigateBack = onNavigateBack)
    settingsTrackingScreen(onNavigateBack = onNavigateBack)
    settingsSecurityScreen(onNavigateBack = onNavigateBack)
    settingsNotificationsScreen(onNavigateBack = onNavigateBack)
    settingsBackupScreen(
        onNavigateBack = onNavigateBack,
        onNavigateToMigrationEntry = onNavigateToMigrationEntry,
        onNavigateToCloudBackup = onNavigateToCloudBackup,
    )
    cloudBackupSettingsScreen(onNavigateBack = onNavigateBack)
    settingsDiscordScreen(onNavigateBack = onNavigateBack)
    widgetConfigurationScreen(onNavigateBack = onNavigateBack)
    localSourceBrowserScreen(onNavigateBack = onNavigateBack)
    syncSettingsScreen(onNavigateBack = onNavigateBack)
    settingsNavOrderScreen(onNavigateBack = onNavigateBack)
    settingsAdvancedScreen(onNavigateBack = onNavigateBack)
    readerPresetsScreen(onNavigateBack = onNavigateBack)
    storageAnalyticsScreen(onNavigateBack = onNavigateBack)
}
