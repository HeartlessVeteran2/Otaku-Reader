package app.komikku.feature.history.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.komikku.core.navigation.HistoryRoute
import app.komikku.feature.history.HistoryScreen

fun NavGraphBuilder.historyScreen(
    onChapterClick: (mangaId: Long, chapterId: Long) -> Unit,
) {
    composable<HistoryRoute> {
        HistoryScreen(onChapterClick = onChapterClick)
    }
}
