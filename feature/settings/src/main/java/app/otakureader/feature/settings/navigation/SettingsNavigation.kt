package app.otakureader.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.SettingsRoute
import app.otakureader.feature.settings.SettingsScreen

fun NavGraphBuilder.settingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMigrationEntry: () -> Unit = {}
) {
    composable<SettingsRoute> {
        SettingsScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToMigrationEntry = onNavigateToMigrationEntry
        )
    }
}
