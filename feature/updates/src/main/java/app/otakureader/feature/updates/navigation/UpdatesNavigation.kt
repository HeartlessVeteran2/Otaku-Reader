package app.otakureader.feature.updates.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.otakureader.core.navigation.Route
import app.otakureader.feature.updates.DownloadsScreen
import app.otakureader.feature.updates.UpdatesScreen

fun NavGraphBuilder.updatesScreen(
    onMangaClick: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToDownloads: () -> Unit
) {
    composable<Route.Updates> { backStackEntry ->
        val route = backStackEntry.toRoute<Route.Updates>()
        UpdatesScreen(
            onMangaClick = onMangaClick,
            onNavigateBack = onNavigateBack,
            onNavigateToDownloads = onNavigateToDownloads,
            openErrorsOnLaunch = route.openErrors,
        )
    }
}

fun NavGraphBuilder.downloadsScreen(
    onNavigateBack: () -> Unit
) {
    composable<Route.Downloads> {
        DownloadsScreen(
            onNavigateBack = onNavigateBack
        )
    }
}
