package app.komikku.feature.library.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.komikku.core.navigation.LibraryRoute
import app.komikku.feature.library.LibraryScreen

fun NavGraphBuilder.libraryScreen(
    onMangaClick: (Long) -> Unit,
) {
    composable<LibraryRoute> {
        LibraryScreen(onMangaClick = onMangaClick)
    }
}
