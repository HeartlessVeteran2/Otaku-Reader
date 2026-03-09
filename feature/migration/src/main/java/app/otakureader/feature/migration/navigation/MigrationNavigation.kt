package app.otakureader.feature.migration.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.otakureader.core.navigation.MigrationRoute
import app.otakureader.feature.migration.MigrationScreen

fun NavGraphBuilder.migrationScreen(
    onNavigateBack: () -> Unit
) {
    composable<MigrationRoute> { backStackEntry ->
        val migrationRoute = backStackEntry.toRoute<MigrationRoute>()
        MigrationScreen(
            selectedMangaIds = migrationRoute.selectedMangaIds,
            onNavigateBack = onNavigateBack
        )
    }
}
