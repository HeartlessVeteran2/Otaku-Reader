package app.otakureader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import app.otakureader.core.navigation.BrowseRoute
import app.otakureader.core.navigation.ExtensionsRoute
import app.otakureader.core.navigation.GlobalSearchRoute
import app.otakureader.core.navigation.HistoryRoute
import app.otakureader.core.navigation.LibraryRoute
import app.otakureader.core.navigation.MangaDetailRoute
import app.otakureader.core.navigation.MigrationEntryRoute
import app.otakureader.core.navigation.MigrationRoute
import app.otakureader.feature.migration.navigation.migrationEntryScreen
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
import app.otakureader.feature.migration.navigation.migrationScreen
import app.otakureader.feature.reader.navigation.readerScreen
import app.otakureader.feature.settings.navigation.settingsScreen
import app.otakureader.feature.statistics.navigation.statisticsScreen
import app.otakureader.feature.tracking.navigation.trackingScreen
import app.otakureader.feature.updates.navigation.downloadsScreen
import app.otakureader.feature.updates.navigation.updatesScreen
import app.otakureader.core.navigation.DownloadsRoute

@Composable
fun OtakuReaderNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: Any = LibraryRoute
) {
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
                // Navigate to manga details from source
                // For now, navigate to details with a placeholder mangaId
                // In a real implementation, this would fetch/create the manga in the database
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

        // Global search screen - searches across all sources
        globalSearchScreen(
            onMangaClick = { sourceId, _ ->
                navController.navigate(SourceDetailRoute(sourceId))
            },
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Source detail - manga from a specific source
        sourceDetailScreen(
            onMangaClick = { sourceId, mangaUrl, mangaTitle ->
                // Navigate to manga details
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

        // Manga details screen
        detailsScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToReader = { mangaId, chapterId ->
                navController.navigate(ReaderRoute(mangaId, chapterId))
            }
        )

        // Reader screen
        readerScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Settings screen
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

        // Statistics screen
        statisticsScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Migration screen
        migrationScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Migration entry screen (for selection from Settings)
        migrationEntryScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToMigration = { selectedMangaIds ->
                navController.navigate(MigrationRoute(selectedMangaIds))
            }
        )

        // Tracking screen – reached from manga details
        trackingScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )
    }
}
