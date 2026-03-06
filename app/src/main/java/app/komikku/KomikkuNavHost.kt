package app.komikku

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import app.komikku.core.navigation.LibraryRoute
import app.komikku.core.navigation.MangaDetailRoute
import app.komikku.core.navigation.ReaderRoute
import app.komikku.feature.browse.navigation.browseScreen
import app.komikku.feature.history.navigation.historyScreen
import app.komikku.feature.library.navigation.libraryScreen
import app.komikku.feature.reader.navigation.readerScreen
import app.komikku.feature.settings.navigation.settingsScreen
import app.komikku.feature.updates.navigation.updatesScreen

/**
 * Top-level navigation graph for Komikku.
 * Delegates each destination to its feature-module navigation extension.
 */
@Composable
fun KomikkuNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = LibraryRoute,
        modifier = modifier,
    ) {
        libraryScreen(
            onMangaClick = { mangaId ->
                navController.navigate(MangaDetailRoute(mangaId))
            },
        )
        updatesScreen(
            onChapterClick = { mangaId, chapterId ->
                navController.navigate(ReaderRoute(mangaId, chapterId))
            },
        )
        browseScreen(
            onMangaClick = { _, _ -> /* Navigate to source detail — Phase 1 */ },
        )
        historyScreen(
            onChapterClick = { mangaId, chapterId ->
                navController.navigate(ReaderRoute(mangaId, chapterId))
            },
        )
        settingsScreen()
        readerScreen(
            onBackClick = { navController.popBackStack() },
        )
    }
}
