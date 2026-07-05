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
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import app.otakureader.core.navigation.Route
import app.otakureader.core.preferences.NavOrderPreferences
import app.otakureader.core.preferences.NavTab

/**
 * Bottom navigation bar. Tab order is user-configurable via [NavOrderPreferences].
 */
@Composable
fun OtakuReaderBottomBar(
    navController: NavController,
    navOrderPreferences: NavOrderPreferences,
    newUpdatesCount: Int = 0,
    activeDownloadCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isTopLevelDestination = currentDestination?.hierarchy?.any { destination ->
        destination.hasRoute(Route.Library::class) ||
        destination.hasRoute(Route.Updates::class) ||
        destination.hasRoute(Route.Browse::class) ||
        destination.hasRoute(Route.History::class) ||
        destination.hasRoute(Route.More::class)
    } == true

    if (!isTopLevelDestination) return

    val tabOrderEntries by navOrderPreferences.tabOrder.collectAsStateWithLifecycle(emptyList())
    val tabOrder = tabOrderEntries.filter { it.isVisible }.map { it.tab }
    if (tabOrder.isEmpty()) return

    NavigationBar(
        modifier = modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
    ) {
        tabOrder.forEach { tab ->
            val selected = when (tab) {
                NavTab.LIBRARY -> currentDestination?.hasRoute(Route.Library::class) == true
                NavTab.UPDATES -> currentDestination?.hasRoute(Route.Updates::class) == true
                NavTab.BROWSE -> currentDestination?.hasRoute(Route.Browse::class) == true
                NavTab.HISTORY -> currentDestination?.hasRoute(Route.History::class) == true
                NavTab.MORE -> currentDestination?.hasRoute(Route.More::class) == true
            }
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.18f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "${tab.name}IconScale",
            )
            val route: Route = when (tab) {
                NavTab.LIBRARY -> Route.Library
                NavTab.UPDATES -> Route.Updates
                NavTab.BROWSE -> Route.Browse
                NavTab.HISTORY -> Route.History
                NavTab.MORE -> Route.More
            }
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                label = { Text(tab.label()) },
                icon = {
                    when (tab) {
                        NavTab.UPDATES -> BadgedBox(badge = {
                            if (newUpdatesCount > 0) {
                                val overflow = stringResource(R.string.badge_count_overflow)
                                Badge { Text(if (newUpdatesCount > 99) overflow else newUpdatesCount.toString()) }
                            }
                        }) {
                            Icon(Icons.Default.NewReleases, contentDescription = null, modifier = Modifier.scale(scale))
                        }
                        NavTab.LIBRARY -> BadgedBox(badge = {
                            if (activeDownloadCount > 0) {
                                val overflow = stringResource(R.string.badge_count_overflow)
                                Badge { Text(if (activeDownloadCount > 99) overflow else activeDownloadCount.toString()) }
                            }
                        }) {
                            Icon(Icons.Default.CollectionsBookmark, contentDescription = null, modifier = Modifier.scale(scale))
                        }
                        else -> Icon(tab.icon(), contentDescription = null, modifier = Modifier.scale(scale))
                    }
                },
            )
        }
    }
}

@Composable
private fun NavTab.label(): String = when (this) {
    NavTab.LIBRARY -> stringResource(R.string.nav_library)
    NavTab.UPDATES -> stringResource(R.string.nav_updates)
    NavTab.BROWSE -> stringResource(R.string.nav_browse)
    NavTab.HISTORY -> stringResource(R.string.nav_history)
    NavTab.MORE -> stringResource(R.string.nav_more)
}

@Suppress("UnusedReceiverParameter")
private fun NavTab.icon() = when (this) {
    NavTab.LIBRARY -> Icons.Default.CollectionsBookmark
    NavTab.UPDATES -> Icons.Default.NewReleases
    NavTab.BROWSE -> Icons.Default.Explore
    NavTab.HISTORY -> Icons.Default.History
    NavTab.MORE -> Icons.Default.MoreHoriz
}
