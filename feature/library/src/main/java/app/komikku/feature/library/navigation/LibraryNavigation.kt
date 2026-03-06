package app.komikku.feature.library.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.komikku.core.navigation.KomikkuDestinations
import app.komikku.feature.library.LibraryScreen

fun NavGraphBuilder.libraryScreen(
    onMangaClick: (Long) -> Unit,
) {
    composable<KomikkuDestinations.LibraryRoute> {
        LibraryScreen(onMangaClick = onMangaClick)
    }
}
