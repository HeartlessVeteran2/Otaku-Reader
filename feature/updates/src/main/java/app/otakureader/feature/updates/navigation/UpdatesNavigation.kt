package app.otakureader.feature.updates.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import app.otakureader.feature.updates.DownloadsScreen
import app.otakureader.feature.updates.UpdatesScreen
import app.otakureader.feature.updates.errors.UpdateErrorsScreen

fun NavGraphBuilder.updatesScreen(
    onMangaClick: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToUpdateErrors: () -> Unit = {},
) {
    composable<Route.Updates> {
        UpdatesScreen(
            onMangaClick = onMangaClick,
            onNavigateBack = onNavigateBack,
            onNavigateToDownloads = onNavigateToDownloads,
            onNavigateToUpdateErrors = onNavigateToUpdateErrors,
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

fun NavGraphBuilder.updateErrorsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToManga: (Long) -> Unit,
    onNavigateToMigration: (List<Long>) -> Unit,
) {
    composable<Route.UpdateErrors> {
        UpdateErrorsScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToManga = onNavigateToManga,
            onNavigateToMigration = onNavigateToMigration,
        )
    }
}
