package app.otakureader.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/**
 * State holder for the app's top-level navigation.
 *
 * Tracks current route, whether bottom bar should be shown,
 * and provides navigation actions for each top-level destination.
 */
class OtakuReaderNavigator(
    val navController: NavHostController,
) {
    val currentDestination: NavDestination?
        @Composable get() = navController
            .currentBackStackEntryAsState()
            .value?.destination

    /** True when the current screen is a top-level tab (shows bottom bar). */
    val isTopLevelDestination: Boolean
        @Composable get() {
            val dest = currentDestination
            return dest != null && topLevelRoutes.any { route ->
                dest.hasRoute(route::class)
            }
        }

    val topLevelRoutes = listOf(
        Route.Library,
        Route.Browse,
        Route.History,
        Route.Updates,
        Route.More,
    )

    fun navigateToTopLevelDestination(route: Route) {
        navController.navigate(route) {
            // Pop up to the start destination of the graph to
            // avoid building a large stack of destinations
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            // Avoid multiple copies of the same destination
            launchSingleTop = true
            // Restore state when reselecting a previously selected tab
            restoreState = true
        }
    }

    fun navigateToMangaDetails(mangaId: Long) {
        navController.navigate(Route.MangaDetails(mangaId))
    }

    fun navigateToReader(mangaId: Long, chapterId: Long = 0L) {
        navController.navigate(Route.Reader(mangaId, chapterId))
    }

    fun navigateToSourceListing(sourceId: String) {
        navController.navigate(Route.SourceListing(sourceId))
    }

    fun navigateToExtensionCatalog() {
        navController.navigate(Route.ExtensionCatalog)
    }

    fun navigateToSearch(query: String) {
        navController.navigate(Route.Search(query))
    }

    fun navigateToSettings() {
        navController.navigate(Route.Settings)
    }

    fun navigateUp() {
        navController.navigateUp()
    }
}

/**
 * Creates and remembers an [OtakuReaderNavigator] tied to the local [NavHostController].
 */
@Composable
fun rememberOtakuReaderNavigator(
    navController: NavHostController = rememberNavController(),
): OtakuReaderNavigator {
    return remember(navController) {
        OtakuReaderNavigator(navController)
    }
}
