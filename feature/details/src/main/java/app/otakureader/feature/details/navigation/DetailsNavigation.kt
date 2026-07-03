package app.otakureader.feature.details.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.otakureader.core.navigation.Route
import app.otakureader.feature.details.DetailsScreen

fun NavGraphBuilder.detailsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReader: (mangaId: Long, chapterId: Long) -> Unit,
    onNavigateToTracking: (mangaId: Long, mangaTitle: String) -> Unit = { _, _ -> },
    onNavigateToGlobalSearch: (query: String) -> Unit = {},
    onNavigateToSourceSearch: (sourceId: String, query: String) -> Unit = { _, _ -> },
    onNavigateToMigration: (mangaId: Long) -> Unit = {},
    onNavigateToWebViewFallback: (url: String, title: String) -> Unit = { _, _ -> },
) {
    composable<Route.MangaDetails> { backStackEntry ->
        val route = backStackEntry.toRoute<Route.MangaDetails>()
        DetailsScreen(
            mangaId = route.mangaId,
            onNavigateBack = onNavigateBack,
            onNavigateToReader = onNavigateToReader,
            onNavigateToTracking = onNavigateToTracking,
            onNavigateToGlobalSearch = onNavigateToGlobalSearch,
            onNavigateToSourceSearch = onNavigateToSourceSearch,
            onNavigateToMigration = onNavigateToMigration,
            onNavigateToWebViewFallback = onNavigateToWebViewFallback,
        )
    }
}
