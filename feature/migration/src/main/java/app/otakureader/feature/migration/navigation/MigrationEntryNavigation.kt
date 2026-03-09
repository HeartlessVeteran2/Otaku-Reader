package app.otakureader.feature.migration.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.MigrationEntryRoute
import app.otakureader.feature.migration.MigrationEntryScreen

fun NavGraphBuilder.migrationEntryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMigration: (List<Long>) -> Unit
) {
    composable<MigrationEntryRoute> {
        MigrationEntryScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToMigration = onNavigateToMigration
        )
    }
}
