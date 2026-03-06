package app.komikku.feature.updates.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.komikku.core.navigation.KomikkuDestinations
import app.komikku.feature.updates.UpdatesScreen

fun NavGraphBuilder.updatesScreen() {
    composable<KomikkuDestinations.UpdatesRoute> {
        UpdatesScreen()
    }
}
