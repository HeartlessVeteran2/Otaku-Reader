package app.komikku.feature.browse.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.komikku.core.navigation.KomikkuDestinations
import app.komikku.feature.browse.BrowseScreen

fun NavGraphBuilder.browseScreen(
    onMangaClick: (Long) -> Unit,
) {
    composable<KomikkuDestinations.BrowseRoute> {
        BrowseScreen(onMangaClick = onMangaClick)
    }
}
