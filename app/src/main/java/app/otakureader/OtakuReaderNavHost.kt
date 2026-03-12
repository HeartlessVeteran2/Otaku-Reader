package app.otakureader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import app.otakureader.core.navigation.BrowseRoute
import app.otakureader.core.navigation.DownloadsRoute
import app.otakureader.core.navigation.ExtensionsRoute
import app.otakureader.core.navigation.GlobalSearchRoute
import app.otakureader.core.navigation.HistoryRoute
import app.otakureader.core.navigation.LibraryRoute
import app.otakureader.core.navigation.MangaDetailRoute
import app.otakureader.core.navigation.MigrationEntryRoute
import app.otakureader.core.navigation.MigrationRoute
import app.otakureader.core.navigation.ReaderRoute
import app.otakureader.core.navigation.SettingsRoute
import app.otakureader.core.navigation.SourceDetailRoute
import app.otakureader.core.navigation.StatisticsRoute
import app.otakureader.core.navigation.TrackingRoute
import app.otakureader.core.navigation.UpdatesRoute
import app.otakureader.feature.browse.navigation.browseScreen
import app.otakureader.feature.browse.navigation.extensionsBottomSheet
import app.otakureader.feature.browse.navigation.globalSearchScreen
import app.otakureader.feature.browse.navigation.sourceDetailScreen
import app.otakureader.feature.details.navigation.detailsScreen
import app.otakureader.feature.history.navigation.historyScreen
import app.otakureader.feature.library.navigation.libraryScreen
import app.otakureader.feature.migration.navigation.migrationEntryScreen
import app.otakureader.feature.migration.navigation.migrationScreen
import app.otakureader.feature.reader.navigation.readerScreen
import app.otakureader.feature.settings.navigation.settingsScreen
import app.otakureader.feature.statistics.navigation.statisticsScreen
import app.otakureader.feature.tracking.navigation.trackingScreen
import app.otakureader.feature.updates.navigation.downloadsScreen
import app.otakureader.feature.updates.navigation.updatesScreen
import app.otakureader.util.DeepLinkResult

@Composable
fun OtakuReaderNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: Any = LibraryRoute,
    deepLinkResult: DeepLinkResult? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    // Handle deep link navigation - only trigger once when deepLinkResult changes
    LaunchedEffect(deepLinkResult) {
        when (deepLinkResult) {
            is DeepLinkResult.MangaUrl -> {
                // Use the manga URL from the deep link as a search query to locate the specific manga
                navController.navigate(GlobalSearchRoute(query = deepLinkResult.mangaUrl))
                onDeepLinkConsumed()
            }
            is DeepLinkResult.SearchQuery -> {
                navController.navigate(GlobalSearchRoute(query = deepLinkResult.query))
                onDeepLinkConsumed()
            }
            is DeepLinkResult.NavigateToLibrary -> {
                // Library is the start destination – clear the back stack so the user
                // lands on a fresh library screen regardless of current navigation state.
                navController.navigate(LibraryRoute) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
                onDeepLinkConsumed()
            }
            is DeepLinkResult.NavigateToUpdates -> {
                navController.navigate(UpdatesRoute) {
                    launchSingleTop = true
                }
                onDeepLinkConsumed()
            }
            is DeepLinkResult.ContinueReading -> {
                navController.navigate(
                    ReaderRoute(deepLinkResult.mangaId, deepLinkResult.chapterId)
                ) {
                    launchSingleTop = true
                }
                onDeepLinkConsumed()
            }
            is DeepLinkResult.Invalid, null -> {
                // No deep link to handle
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // Library screen - main entry point
        libraryScreen(
            onMangaClick = { mangaId ->
                navController.navigate(MangaDetailRoute(mangaId))
            },
            onNavigateToUpdates = {
                navController.navigate(UpdatesRoute)
            },
            onNavigateToBrowse = {
                navController.navigate(BrowseRoute)
            },
            onNavigateToHistory = {
                navController.navigate(HistoryRoute)
            },
            onNavigateToStatistics = {
                navController.navigate(StatisticsRoute)
            },
            onNavigateToSettings = {
                navController.navigate(SettingsRoute)
            },
            onNavigateToDownloads = {
                navController.navigate(DownloadsRoute)
            },
            onNavigateToMigration = { selectedMangaIds ->
                navController.navigate(MigrationRoute(selectedMangaIds))
            }
        )

        // Updates screen
        updatesScreen(
            onMangaClick = { mangaId ->
                navController.navigate(MangaDetailRoute(mangaId))
            },
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToDownloads = {
                navController.navigate(DownloadsRoute)
            }
        )

        // Browse screen - list of sources
        browseScreen(
            onMangaClick = { sourceId, mangaUrl, mangaTitle ->
                navController.navigate(MangaDetailRoute(mangaId = 1L))
            },
            onNavigateToSource = { sourceId ->
                navController.navigate(SourceDetailRoute(sourceId))
            },
            onNavigateToExtensions = {
                navController.navigate(ExtensionsRoute)
            },
            onNavigateToGlobalSearch = {
                navController.navigate(GlobalSearchRoute())
            }
        )

        // Global search screen
        globalSearchScreen(
            onMangaClick = { sourceId, _ ->
                navController.navigate(SourceDetailRoute(sourceId))
            },
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Source detail
        sourceDetailScreen(
            onMangaClick = { sourceId, mangaUrl, mangaTitle ->
                navController.navigate(MangaDetailRoute(mangaId = 1L))
            },
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Extensions bottom sheet
        extensionsBottomSheet(
            onDismiss = {
                navController.popBackStack()
            }
        )

        // History screen
        historyScreen(
            onChapterClick = { mangaId, chapterId ->
                navController.navigate(ReaderRoute(mangaId, chapterId))
            },
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Manga details
        detailsScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToReader = { mangaId, chapterId ->
                navController.navigate(ReaderRoute(mangaId, chapterId))
            }
        )

        // Reader
        readerScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Settings
        settingsScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToMigrationEntry = {
                navController.navigate(MigrationEntryRoute)
            }
        )

        downloadsScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Statistics
        statisticsScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Migration
        migrationScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Migration entry
        migrationEntryScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToMigration = { selectedMangaIds ->
                navController.navigate(MigrationRoute(selectedMangaIds))
            }
        )

        // Tracking
        trackingScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )
    }
}