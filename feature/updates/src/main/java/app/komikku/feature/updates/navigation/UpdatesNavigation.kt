package app.komikku.feature.updates.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.komikku.core.navigation.UpdatesRoute
import app.komikku.feature.updates.UpdatesScreen

fun NavGraphBuilder.updatesScreen(
    onChapterClick: (mangaId: Long, chapterId: Long) -> Unit,
) {
    composable<UpdatesRoute> {
        UpdatesScreen(onChapterClick = onChapterClick)
    }
}
