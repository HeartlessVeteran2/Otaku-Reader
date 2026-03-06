package app.komikku.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.komikku.core.navigation.KomikkuDestinations
import app.komikku.feature.settings.SettingsScreen

fun NavGraphBuilder.settingsScreen() {
    composable<KomikkuDestinations.SettingsRoute> {
        SettingsScreen()
    }
}
