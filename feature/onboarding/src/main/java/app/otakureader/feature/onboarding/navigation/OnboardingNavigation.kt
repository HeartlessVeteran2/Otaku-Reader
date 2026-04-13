package app.otakureader.feature.onboarding.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.OnboardingRoute
import app.otakureader.feature.onboarding.OnboardingScreen

fun NavGraphBuilder.onboardingScreen(
    onComplete: () -> Unit,
) {
    composable<OnboardingRoute> {
        OnboardingScreen(
            onComplete = onComplete
        )
    }
}
