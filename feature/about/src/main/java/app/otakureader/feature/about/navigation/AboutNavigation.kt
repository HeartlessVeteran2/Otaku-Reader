package app.otakureader.feature.about.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import app.otakureader.feature.about.AboutScreen
import app.otakureader.feature.about.PrivacyPolicyScreen

fun NavGraphBuilder.aboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit = {},
    onNavigateToDeveloper: () -> Unit = {},
) {
    composable<Route.About> {
        AboutScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToPrivacyPolicy = onNavigateToPrivacyPolicy,
            onNavigateToDeveloper = onNavigateToDeveloper,
        )
    }
}

fun NavGraphBuilder.privacyPolicyScreen(
    onNavigateBack: () -> Unit,
) {
    composable<Route.PrivacyPolicy> {
        PrivacyPolicyScreen(
            onNavigateBack = onNavigateBack,
        )
    }
}
