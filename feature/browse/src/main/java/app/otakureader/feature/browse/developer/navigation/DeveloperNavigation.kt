package app.otakureader.feature.browse.developer.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import app.otakureader.feature.browse.developer.DeveloperScreen

fun NavGraphBuilder.developerScreen(
    onNavigateBack: () -> Unit,
) {
    composable<Route.Developer> {
        DeveloperScreen(onNavigateBack = onNavigateBack)
    }
}
