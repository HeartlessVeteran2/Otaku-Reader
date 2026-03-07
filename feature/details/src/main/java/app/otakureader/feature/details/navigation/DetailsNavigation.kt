package app.otakureader.feature.details.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.otakureader.core.navigation.MangaDetailRoute
import app.otakureader.feature.details.DetailsScreen

fun NavGraphBuilder.detailsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReader: (mangaId: Long, chapterId: Long) -> Unit,
) {
    composable<MangaDetailRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<MangaDetailRoute>()
        DetailsScreen(
            mangaId = route.mangaId,
            onNavigateBack = onNavigateBack,
            onNavigateToReader = onNavigateToReader
        )
    }
}
