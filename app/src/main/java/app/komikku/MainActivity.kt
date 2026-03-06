package app.komikku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.komikku.core.navigation.BrowseRoute
import app.komikku.core.navigation.HistoryRoute
import app.komikku.core.navigation.LibraryRoute
import app.komikku.core.navigation.MangaDetailRoute
import app.komikku.core.navigation.ReaderRoute
import app.komikku.core.navigation.SettingsRoute
import app.komikku.core.navigation.UpdatesRoute
import app.komikku.core.ui.theme.KomikkuTheme
import app.komikku.feature.browse.BrowseScreen
import app.komikku.feature.history.HistoryScreen
import app.komikku.feature.library.LibraryScreen
import app.komikku.feature.reader.ReaderScreen
import app.komikku.feature.settings.SettingsScreen
import app.komikku.feature.updates.UpdatesScreen
import dagger.hilt.android.AndroidEntryPoint

/** Main entry activity — hosts the Compose navigation graph. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KomikkuTheme {
                KomikkuApp()
            }
        }
    }
}

@Composable
private fun KomikkuApp() {
    val navController = rememberNavController()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { KomikkuBottomBar(navController) }
    ) { paddingValues ->
        KomikkuNavHost(
            navController = navController,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
private fun KomikkuNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = LibraryRoute,
        modifier = modifier
    ) {
        composable<LibraryRoute> {
            LibraryScreen(
                onMangaClick = { mangaId ->
                    navController.navigate(MangaDetailRoute(mangaId))
                }
            )
        }

        composable<UpdatesRoute> {
            UpdatesScreen(
                onChapterClick = { mangaId, chapterId ->
                    navController.navigate(ReaderRoute(mangaId, chapterId))
                }
            )
        }

        composable<BrowseRoute> {
            BrowseScreen(
                onMangaClick = { _, _ -> /* Navigate to manga detail */ }
            )
        }

        composable<HistoryRoute> {
            HistoryScreen(
                onChapterClick = { mangaId, chapterId ->
                    navController.navigate(ReaderRoute(mangaId, chapterId))
                }
            )
        }

        composable<SettingsRoute> {
            SettingsScreen()
        }

        composable<ReaderRoute> { backStackEntry ->
            ReaderScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

private data class TopLevelDestination(
    val route: Any,
    val icon: ImageVector,
    val label: String
)

private val topLevelDestinations = listOf(
    TopLevelDestination(LibraryRoute, Icons.Default.LibraryBooks, "Library"),
    TopLevelDestination(UpdatesRoute, Icons.Default.NewReleases, "Updates"),
    TopLevelDestination(BrowseRoute, Icons.Default.Explore, "Browse"),
    TopLevelDestination(HistoryRoute, Icons.Default.History, "History"),
    TopLevelDestination(SettingsRoute, Icons.Default.Settings, "Settings")
)

@Composable
private fun KomikkuBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Only show bottom bar on top-level destinations
    val showBottomBar = topLevelDestinations.any { dest ->
        currentDestination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true
    }

    if (!showBottomBar) return

    NavigationBar {
        topLevelDestinations.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any {
                it.hasRoute(destination.route::class)
            } == true

            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label
                    )
                },
                label = { Text(destination.label) },
                selected = selected,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
