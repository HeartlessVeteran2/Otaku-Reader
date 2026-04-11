package app.otakureader.feature.feed.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.FeedRoute
import app.otakureader.feature.feed.FeedScreen

fun NavGraphBuilder.feedScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReader: (mangaId: Long, chapterId: Long) -> Unit
) {
    composable<FeedRoute> {
        FeedScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToReader = onNavigateToReader
        )
    }
}
