package app.otakureader

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import app.otakureader.core.navigation.BrowseRoute
import app.otakureader.core.navigation.HistoryRoute
import app.otakureader.core.navigation.LibraryRoute
import app.otakureader.core.navigation.MoreRoute
import app.otakureader.core.navigation.UpdatesRoute

/**
 * Bottom navigation bar for the main app navigation.
 * Provides quick access to Library, Updates, Browse, History, and More.
 */
@Composable
fun OtakuReaderBottomBar(
    navController: NavController,
    newUpdatesCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isTopLevelDestination = currentDestination?.hierarchy?.any { destination ->
        destination.hasRoute(LibraryRoute::class) ||
        destination.hasRoute(UpdatesRoute::class) ||
        destination.hasRoute(BrowseRoute::class) ||
        destination.hasRoute(HistoryRoute::class) ||
        destination.hasRoute(MoreRoute::class)
    } == true

    if (!isTopLevelDestination) return

    NavigationBar(modifier = modifier) {
        // Library
        val librarySelected = currentDestination?.hasRoute(LibraryRoute::class) == true
        val libraryScale by animateFloatAsState(
            targetValue = if (librarySelected) 1.18f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "libraryIconScale"
        )
        NavigationBarItem(
            icon = {
                Icon(
                    Icons.Default.CollectionsBookmark,
                    contentDescription = stringResource(R.string.nav_library),
                    modifier = Modifier.scale(libraryScale)
                )
            },
            label = { Text(stringResource(R.string.nav_library)) },
            selected = librarySelected,
            onClick = {
                navController.navigate(LibraryRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )

        // Updates
        val updatesSelected = currentDestination?.hasRoute(UpdatesRoute::class) == true
        val updatesScale by animateFloatAsState(
            targetValue = if (updatesSelected) 1.18f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "updatesIconScale"
        )
        NavigationBarItem(
            icon = {
                BadgedBox(
                    badge = {
                        if (newUpdatesCount > 0) {
                            Badge {
                                Text(if (newUpdatesCount > 99) "99+" else newUpdatesCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.NewReleases,
                        contentDescription = stringResource(R.string.nav_updates),
                        modifier = Modifier.scale(updatesScale)
                    )
                }
            },
            label = { Text(stringResource(R.string.nav_updates)) },
            selected = updatesSelected,
            onClick = {
                navController.navigate(UpdatesRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )

        // Browse
        val browseSelected = currentDestination?.hasRoute(BrowseRoute::class) == true
        val browseScale by animateFloatAsState(
            targetValue = if (browseSelected) 1.18f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "browseIconScale"
        )
        NavigationBarItem(
            icon = {
                Icon(
                    Icons.Default.Explore,
                    contentDescription = stringResource(R.string.nav_browse),
                    modifier = Modifier.scale(browseScale)
                )
            },
            label = { Text(stringResource(R.string.nav_browse)) },
            selected = browseSelected,
            onClick = {
                navController.navigate(BrowseRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )

        // History
        val historySelected = currentDestination?.hasRoute(HistoryRoute::class) == true
        val historyScale by animateFloatAsState(
            targetValue = if (historySelected) 1.18f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "historyIconScale"
        )
        NavigationBarItem(
            icon = {
                Icon(
                    Icons.Default.History,
                    contentDescription = stringResource(R.string.nav_history),
                    modifier = Modifier.scale(historyScale)
                )
            },
            label = { Text(stringResource(R.string.nav_history)) },
            selected = historySelected,
            onClick = {
                navController.navigate(HistoryRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )

        // More
        val moreSelected = currentDestination?.hasRoute(MoreRoute::class) == true
        val moreScale by animateFloatAsState(
            targetValue = if (moreSelected) 1.18f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "moreIconScale"
        )
        NavigationBarItem(
            icon = {
                Icon(
                    Icons.Default.MoreHoriz,
                    contentDescription = stringResource(R.string.nav_more),
                    modifier = Modifier.scale(moreScale)
                )
            },
            label = { Text(stringResource(R.string.nav_more)) },
            selected = moreSelected,
            onClick = {
                navController.navigate(MoreRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )
    }
}
