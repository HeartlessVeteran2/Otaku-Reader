package app.otakureader.feature.browse.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.otakureader.core.navigation.BrowseRoute
import app.otakureader.core.navigation.ExtensionsRoute
import app.otakureader.core.navigation.SourceDetailRoute
import app.otakureader.feature.browse.BrowseScreen
import app.otakureader.feature.browse.ExtensionsBottomSheet
import app.otakureader.feature.browse.SourceMangaScreen

fun NavGraphBuilder.browseScreen(
    onMangaClick: (sourceId: String, mangaUrl: String, mangaTitle: String) -> Unit,
    onNavigateToSource: (sourceId: String) -> Unit,
    onNavigateToExtensions: () -> Unit,
) {
    composable<BrowseRoute> {
        BrowseScreen(
            onMangaClick = onMangaClick,
            onNavigateToSource = onNavigateToSource,
            onNavigateToExtensions = onNavigateToExtensions
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
    composable<ExtensionsRoute> {
        ExtensionsBottomSheet(
            onDismiss = onDismiss
        )
    }
}
