package app.otakureader.feature.history.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.HistoryRoute
import app.otakureader.feature.history.HistoryScreen

fun NavGraphBuilder.historyScreen(
    onChapterClick: (mangaId: Long, chapterId: Long) -> Unit,
    onNavigateBack: () -> Unit,
) {
    composable<HistoryRoute> {
        HistoryScreen(
            onChapterClick = onChapterClick,
            onNavigateBack = onNavigateBack
        )
    }
}
