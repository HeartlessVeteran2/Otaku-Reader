package app.otakureader.feature.tracking.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.otakureader.core.navigation.TrackingRoute
import app.otakureader.feature.tracking.TrackingScreen

fun NavController.navigateToTracking(
    mangaId: Long,
    mangaTitle: String,
    navOptions: NavOptions? = null
) {
    navigate(TrackingRoute(mangaId, mangaTitle), navOptions)
}

fun NavGraphBuilder.trackingScreen(
    onNavigateBack: () -> Unit
) {
    composable<TrackingRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<TrackingRoute>()

        if (route.mangaId == 0L) {
            LaunchedEffect(Unit) { onNavigateBack() }
            return@composable
        }

        TrackingScreen(
            mangaId = route.mangaId,
            mangaTitle = route.mangaTitle,
            onNavigateBack = onNavigateBack
        )
    }
}
