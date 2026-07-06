package app.otakureader.feature.browse.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.otakureader.core.navigation.Route
import app.otakureader.feature.browse.BrowseScreen
import app.otakureader.feature.browse.ExtensionsScreen
import app.otakureader.feature.browse.GlobalSearchScreen
import app.otakureader.feature.browse.SourceMangaDetailScreen
import app.otakureader.feature.browse.SourceMangaScreen
import app.otakureader.feature.browse.extension.ExtensionInstallScreen
import app.otakureader.feature.browse.extension.extensionDetailScreen

@Suppress("UnusedParameter")
fun NavGraphBuilder.browseScreen(
    onMangaClick: (sourceId: String, mangaUrl: String, mangaTitle: String) -> Unit,
    onNavigateToSource: (sourceId: String) -> Unit,
    onNavigateToExtensions: () -> Unit,
    onNavigateToGlobalSearch: () -> Unit,
    onNavigateToOpds: () -> Unit = {},
    onNavigateToMigration: () -> Unit = {},
    // Feed tab
    onNavigateToReader: (mangaId: Long, chapterId: Long) -> Unit = { _, _ -> },
    onNavigateToFeedManagement: () -> Unit = {},
    // Extensions tab
    onNavigateToExtensionSettings: () -> Unit = {},
    onNavigateToExtensionRepositories: () -> Unit = {},
    onNavigateToExtensionDetail: (packageName: String) -> Unit = {},
    // Migrate tab
    onStartMigration: (List<Long>) -> Unit = {},
) {
    composable<Route.Browse> {
        BrowseScreen(
            viewModel = hiltViewModel(),
            onMangaClick = { sourceId, mangaUrl ->
                onMangaClick(sourceId, mangaUrl, "")
            },
            onGlobalSearchClick = onNavigateToGlobalSearch,
            onOpdsClick = onNavigateToOpds,
            onNavigateToReader = onNavigateToReader,
            onNavigateToFeedManagement = onNavigateToFeedManagement,
            onNavigateToExtensionRepositories = onNavigateToExtensionRepositories,
            onNavigateToExtensionDetail = onNavigateToExtensionDetail,
            onStartMigration = onStartMigration,
        )
    }
}

fun NavGraphBuilder.sourceDetailScreen(
    onMangaClick: (sourceId: String, mangaUrl: String, mangaTitle: String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    composable<Route.SourceListing> { backStackEntry ->
        val route = backStackEntry.toRoute<Route.SourceListing>()
        SourceMangaScreen(
            sourceId = route.sourceId.toString(),
            onMangaClick = { mangaUrl, mangaTitle ->
                onMangaClick(route.sourceId.toString(), mangaUrl, mangaTitle)
            },
            onNavigateBack = onNavigateBack,
            initialQuery = route.initialQuery,
        )
    }
}

fun NavGraphBuilder.sourceMangaDetailScreen(
    onNavigateToMangaDetail: (mangaId: Long) -> Unit,
    onNavigateBack: () -> Unit,
) {
    composable<Route.SourceMangaDetail> {
        SourceMangaDetailScreen(
            onNavigateToMangaDetail = onNavigateToMangaDetail,
            onNavigateBack = onNavigateBack,
        )
    }
}

/**
 * Registers [Route.ExtensionCatalog] as a regular full-screen composable destination.
 *
 * Previously this was a ModalBottomSheet overlay. Issue #1117 converts it to a standard
 * screen so users get the Mihon/Komikku experience: a slide-in screen rather than a
 * slide-up sheet. [ExtensionsTabBody] (embedded inline in Browse's Extensions pager tab)
 * is unchanged — this destination is reached from the More screen and Onboarding.
 */
fun NavGraphBuilder.extensionsBottomSheet(
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToRepositories: () -> Unit = {},
    onNavigateToExtensionDetail: (packageName: String) -> Unit = {},
    onNavigateToExtensionInstall: () -> Unit = {},
) {
    composable<Route.ExtensionCatalog> {
        ExtensionsScreen(
            onNavigateBack = onDismiss,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToRepositories = onNavigateToRepositories,
            onNavigateToExtensionDetail = onNavigateToExtensionDetail,
            onNavigateToExtensionInstall = onNavigateToExtensionInstall,
        )
    }
}

fun NavGraphBuilder.extensionInstallScreen(
    onNavigateBack: () -> Unit,
) {
    composable<Route.ExtensionInstall> {
        ExtensionInstallScreen(
            onBackClick = onNavigateBack
        )
    }
}

fun NavGraphBuilder.globalSearchScreen(
    onMangaClick: (sourceId: String, mangaUrl: String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToSource: (sourceId: String, query: String) -> Unit = { _, _ -> },
) {
    composable<Route.Search> { backStackEntry ->
        val route = backStackEntry.toRoute<Route.Search>()
        GlobalSearchScreen(
            initialQuery = route.query,
            onMangaClick = onMangaClick,
            onNavigateBack = onNavigateBack,
            onNavigateToSource = onNavigateToSource,
            viewModel = hiltViewModel()
        )
    }
}

/**
 * Registers the extension detail full-screen destination in the calling nav graph.
 *
 * Wire this inside the app-level NavHost alongside the other browse destinations,
 * passing a lambda that navigates back (typically `navController::popBackStack`).
 */
fun NavGraphBuilder.browseExtensionDetailScreen(
    onNavigateBack: () -> Unit,
) {
    extensionDetailScreen(onNavigateBack = onNavigateBack)
}
