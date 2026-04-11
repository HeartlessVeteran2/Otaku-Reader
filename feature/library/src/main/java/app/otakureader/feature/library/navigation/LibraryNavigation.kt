package app.otakureader.feature.library.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.LibraryRoute
import app.otakureader.feature.library.LibraryScreen

fun NavGraphBuilder.libraryScreen(
    onMangaClick: (Long) -> Unit,
    onNavigateToUpdates: () -> Unit,
    onNavigateToBrowse: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToMigration: (List<Long>) -> Unit = {},
    onRecommendationClick: (String) -> Unit = { onNavigateToBrowse() }
) {
    composable<LibraryRoute> {
        LibraryScreen(
            onMangaClick = onMangaClick,
            onNavigateToUpdates = onNavigateToUpdates,
            onNavigateToBrowse = onNavigateToBrowse,
            onNavigateToHistory = onNavigateToHistory,
            onNavigateToStatistics = onNavigateToStatistics,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToDownloads = onNavigateToDownloads,
            onNavigateToMigration = onNavigateToMigration,
            onRecommendationClick = onRecommendationClick
        )
    }
}
