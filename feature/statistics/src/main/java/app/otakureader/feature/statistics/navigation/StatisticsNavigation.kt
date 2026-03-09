package app.otakureader.feature.statistics.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.StatisticsRoute
import app.otakureader.feature.statistics.StatisticsScreen

fun NavGraphBuilder.statisticsScreen(
    onNavigateBack: () -> Unit
) {
    composable<StatisticsRoute> {
        StatisticsScreen(
            onNavigateBack = onNavigateBack
        )
    }
}
