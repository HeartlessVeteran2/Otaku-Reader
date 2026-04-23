package app.otakureader.feature.library.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.LibraryRoute
import app.otakureader.feature.library.LibraryScreen

fun NavGraphBuilder.libraryScreen(
    onMangaClick: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToMigration: (List<Long>) -> Unit = {},
    onNavigateToCategoryManagement: () -> Unit = {},
    onRecommendationClick: (String) -> Unit = {},
    onNavigateToReader: (mangaId: Long, chapterId: Long) -> Unit = { _, _ -> }
) {
    composable<LibraryRoute> {
        LibraryScreen(
            onMangaClick = onMangaClick,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToDownloads = onNavigateToDownloads,
            onNavigateToMigration = onNavigateToMigration,
            onNavigateToCategoryManagement = onNavigateToCategoryManagement,
            onRecommendationClick = onRecommendationClick,
            onNavigateToReader = onNavigateToReader
        )
    }
}
