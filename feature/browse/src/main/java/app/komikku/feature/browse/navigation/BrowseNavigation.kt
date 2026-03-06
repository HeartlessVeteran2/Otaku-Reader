package app.komikku.feature.browse.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.komikku.core.navigation.BrowseRoute
import app.komikku.feature.browse.BrowseScreen

fun NavGraphBuilder.browseScreen(
    onMangaClick: (sourceId: String, mangaUrl: String) -> Unit,
) {
    composable<BrowseRoute> {
        BrowseScreen(onMangaClick = onMangaClick)
    }
}
