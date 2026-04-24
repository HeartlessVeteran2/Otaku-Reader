package app.otakureader.feature.more.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.MoreRoute
import app.otakureader.feature.more.MoreScreen

fun NavGraphBuilder.moreScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToExtensions: () -> Unit = {},
    onNavigateToFeed: () -> Unit = {},
    onNavigateToRecommendations: () -> Unit = {},
) {
    composable<MoreRoute> {
        MoreScreen(
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToDownloads = onNavigateToDownloads,
            onNavigateToStatistics = onNavigateToStatistics,
            onNavigateToAbout = onNavigateToAbout,
            onNavigateToExtensions = onNavigateToExtensions,
            onNavigateToFeed = onNavigateToFeed,
            onNavigateToRecommendations = onNavigateToRecommendations,
        )
    }
}
