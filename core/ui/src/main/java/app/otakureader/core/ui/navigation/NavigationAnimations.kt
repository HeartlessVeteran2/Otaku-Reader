package app.otakureader.core.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Standard animation durations for navigation transitions.
 */
object NavigationAnimations {
    const val DEFAULT_DURATION = 300
    const val SLOW_DURATION = 400
    const val FAST_DURATION = 200

    /**
     * Standard slide in from right (for forward navigation).
     */
    val slideInFromRight: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(
                durationMillis = DEFAULT_DURATION,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(animationSpec = tween(DEFAULT_DURATION))
    }

    /**
     * Standard slide out to left (for forward navigation).
     */
    val slideOutToLeft: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { -it / 3 },
            animationSpec = tween(
                durationMillis = DEFAULT_DURATION,
                easing = FastOutSlowInEasing
            )
        ) + fadeOut(animationSpec = tween(DEFAULT_DURATION))
    }

    /**
     * Standard slide in from left (for back navigation).
     */
    val slideInFromLeft: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(
            initialOffsetX = { -it / 3 },
            animationSpec = tween(
                durationMillis = DEFAULT_DURATION,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(animationSpec = tween(DEFAULT_DURATION))
    }

    /**
     * Standard slide out to right (for back navigation).
     */
    val slideOutToRight: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(
                durationMillis = DEFAULT_DURATION,
                easing = FastOutSlowInEasing
            )
        ) + fadeOut(animationSpec = tween(DEFAULT_DURATION))
    }

    /**
     * Fade in with scale (for dialogs/overlays).
     */
    val fadeInWithScale: EnterTransition =
        fadeIn(animationSpec = tween(DEFAULT_DURATION)) +
        scaleIn(
            initialScale = 0.92f,
            animationSpec = tween(DEFAULT_DURATION, easing = FastOutSlowInEasing)
        )

    /**
     * Fade out with scale (for dialogs/overlays).
     */
    val fadeOutWithScale: ExitTransition =
        fadeOut(animationSpec = tween(DEFAULT_DURATION)) +
        scaleOut(
            targetScale = 0.92f,
            animationSpec = tween(DEFAULT_DURATION, easing = FastOutSlowInEasing)
        )

    /**
     * Slide in from bottom (for bottom sheets).
     */
    val slideInFromBottom: EnterTransition =
        slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(DEFAULT_DURATION, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(DEFAULT_DURATION))

    /**
     * Slide out to bottom (for bottom sheets).
     */
    val slideOutToBottom: ExitTransition =
        slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(DEFAULT_DURATION, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(DEFAULT_DURATION))

    /**
     * Default forward navigation transitions (slide from right).
     */
    val defaultForwardEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = slideInFromRight
    val defaultForwardExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = slideOutToLeft

    /**
     * Default back navigation transitions (slide from left).
     */
    val defaultBackEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = slideInFromLeft
    val defaultBackExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = slideOutToRight
}

/**
 * Composable with default animations for navigation.
 */
fun NavGraphBuilder.animatedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = NavigationAnimations.defaultForwardEnter,
    exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = NavigationAnimations.defaultForwardExit,
    popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = NavigationAnimations.defaultBackEnter,
    popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = NavigationAnimations.defaultBackExit,
    content: @Composable (NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        deepLinks = deepLinks,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition,
        content = { backStackEntry -> content(backStackEntry) }
    )
}

/**
 * Composable for modal/bottom sheet style transitions.
 */
fun NavGraphBuilder.modalComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        deepLinks = deepLinks,
        enterTransition = { NavigationAnimations.fadeInWithScale },
        exitTransition = { NavigationAnimations.fadeOutWithScale },
        popEnterTransition = { NavigationAnimations.fadeInWithScale },
        popExitTransition = { NavigationAnimations.fadeOutWithScale },
        content = { backStackEntry -> content(backStackEntry) }
    )
}

/**
 * Composable for bottom sheet style transitions.
 */
fun NavGraphBuilder.bottomSheetComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        deepLinks = deepLinks,
        enterTransition = { NavigationAnimations.slideInFromBottom },
        exitTransition = { NavigationAnimations.fadeOutWithScale },
        popEnterTransition = { NavigationAnimations.fadeInWithScale },
        popExitTransition = { NavigationAnimations.slideOutToBottom },
        content = { backStackEntry -> content(backStackEntry) }
    )
}
