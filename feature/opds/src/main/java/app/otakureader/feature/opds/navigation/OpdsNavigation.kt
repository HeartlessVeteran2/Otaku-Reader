package app.otakureader.feature.opds.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.OpdsRoute
import app.otakureader.feature.opds.OpdsScreen

fun NavGraphBuilder.opdsScreen(
    onNavigateBack: () -> Unit
) {
    composable<OpdsRoute> {
        OpdsScreen(
            onNavigateBack = onNavigateBack
        )
    }
}
