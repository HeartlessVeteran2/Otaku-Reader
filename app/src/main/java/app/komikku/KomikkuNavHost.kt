package app.komikku

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import app.komikku.core.navigation.KomikkuDestinations
import app.komikku.feature.browse.navigation.browseScreen
import app.komikku.feature.library.navigation.libraryScreen
import app.komikku.feature.reader.navigation.readerScreen
import app.komikku.feature.settings.navigation.settingsScreen
import app.komikku.feature.updates.navigation.updatesScreen

@Composable
fun KomikkuNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = KomikkuDestinations.LibraryRoute,
        modifier = modifier,
    ) {
        libraryScreen(
            onMangaClick = { mangaId ->
                navController.navigate(KomikkuDestinations.MangaDetailRoute(mangaId))
            },
        )
        updatesScreen()
        browseScreen(
            onMangaClick = { mangaId ->
                navController.navigate(KomikkuDestinations.MangaDetailRoute(mangaId))
            },
        )
        settingsScreen()
        readerScreen(
            onBackClick = { navController.popBackStack() },
        )
    }
}
