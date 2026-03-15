package app.otakureader.feature.browse.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.otakureader.core.navigation.BrowseRoute
import app.otakureader.core.navigation.ExtensionsRoute
import app.otakureader.core.navigation.GlobalSearchRoute
import app.otakureader.core.navigation.SourceDetailRoute
import app.otakureader.feature.browse.BrowseScreen
import app.otakureader.feature.browse.ExtensionsBottomSheet
import app.otakureader.feature.browse.GlobalSearchScreen
import app.otakureader.feature.browse.SourceMangaScreen
import app.otakureader.core.navigation.ExtensionInstallRoute
import app.otakureader.feature.browse.extension.ExtensionInstallScreen

fun NavGraphBuilder.browseScreen(
    onMangaClick: (sourceId: String, mangaUrl: String, mangaTitle: String) -> Unit,
    onNavigateToSource: (sourceId: String) -> Unit,
    onNavigateToExtensions: () -> Unit,
    onNavigateToGlobalSearch: () -> Unit,
    onNavigateToOpds: () -> Unit = {},
) {
    composable<BrowseRoute> {
        BrowseScreen(
            viewModel = hiltViewModel(),
            onMangaClick = { sourceId, mangaUrl ->
                onMangaClick(sourceId, mangaUrl, "")
            },
            onInstallExtensionClick = onNavigateToExtensions,
            onGlobalSearchClick = onNavigateToGlobalSearch,
            onOpdsClick = onNavigateToOpds
        )
    }
}

fun NavGraphBuilder.sourceDetailScreen(
    onMangaClick: (sourceId: String, mangaUrl: String, mangaTitle: String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    composable<SourceDetailRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<SourceDetailRoute>()
        SourceMangaScreen(
            sourceId = route.sourceId,
            onMangaClick = { mangaUrl, mangaTitle ->
                onMangaClick(route.sourceId, mangaUrl, mangaTitle)
            },
            onNavigateBack = onNavigateBack
        )
    }
}

fun NavGraphBuilder.extensionsBottomSheet(
    onDismiss: () -> Unit,
) {
    composable<ExtensionsRoute>(
        enterTransition = { fadeIn(animationSpec = tween(200)) },
        exitTransition = { fadeOut(animationSpec = tween(200)) },
        popEnterTransition = { fadeIn(animationSpec = tween(200)) },
        popExitTransition = { fadeOut(animationSpec = tween(200)) }
    ) {
        ExtensionsBottomSheet(
            onDismiss = onDismiss
        )
    }
}

fun NavGraphBuilder.globalSearchScreen(
    onMangaClick: (sourceId: String, mangaUrl: String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    composable<GlobalSearchRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<GlobalSearchRoute>()
        GlobalSearchScreen(
            initialQuery = route.query,
            onMangaClick = onMangaClick,
            onNavigateBack = onNavigateBack,
            viewModel = hiltViewModel()
        )
    }
}
