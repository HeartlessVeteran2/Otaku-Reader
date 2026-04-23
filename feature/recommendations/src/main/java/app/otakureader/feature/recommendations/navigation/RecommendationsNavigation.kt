package app.otakureader.feature.recommendations.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.RecommendationsRoute
import app.otakureader.feature.recommendations.RecommendationsScreen

fun NavGraphBuilder.recommendationsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSearch: (String) -> Unit,
) {
    composable<RecommendationsRoute> {
        RecommendationsScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToSearch = onNavigateToSearch,
        )
    }
}
