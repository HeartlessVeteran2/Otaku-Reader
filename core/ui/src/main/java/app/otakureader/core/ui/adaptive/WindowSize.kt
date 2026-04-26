package app.otakureader.core.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Coarse window-width breakpoints that mirror the Material 3 `WindowWidthSizeClass`
 * semantics without pulling in the `material3-window-size-class` artifact.
 *
 * Breakpoints follow the official Material 3 guidance:
 *  - Compact:  width  < 600 dp  (most phones in portrait)
 *  - Medium:   600 dp ≤ width < 840 dp  (large phones, small tablets, foldables)
 *  - Expanded: width ≥ 840 dp  (tablets in landscape, Samsung DeX, ChromeOS, desktop)
 *
 * Use [rememberWindowWidthSizeClass] from any composable to make adaptive
 * decisions (e.g. switching from a single-pane phone layout to a two-pane
 * list/detail layout on a tablet).
 */
enum class WindowWidthSizeClass {
    Compact,
    Medium,
    Expanded,
}

/** Convenience: `true` when the current width is [WindowWidthSizeClass.Expanded]. */
val WindowWidthSizeClass.isExpanded: Boolean
    get() = this == WindowWidthSizeClass.Expanded

/** Convenience: `true` when the current width is [WindowWidthSizeClass.Medium] or wider. */
val WindowWidthSizeClass.isAtLeastMedium: Boolean
    get() = this != WindowWidthSizeClass.Compact

/**
 * Returns the [WindowWidthSizeClass] for the current [LocalConfiguration].
 *
 * This is composition-aware: when the device is rotated, the window is resized
 * (e.g. multi-window / DeX), or a foldable is unfolded, the value is recomputed
 * and triggers recomposition of consumers.
 *
 * Computing the size class from `LocalConfiguration.screenWidthDp` keeps this
 * helper usable from any composable without requiring an `Activity` reference,
 * which is what the official `calculateWindowSizeClass(Activity)` API needs.
 */
@Composable
fun rememberWindowWidthSizeClass(): WindowWidthSizeClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return remember(widthDp) { fromWidthDp(widthDp) }
}

internal fun fromWidthDp(widthDp: Int): WindowWidthSizeClass = when {
    widthDp < 600 -> WindowWidthSizeClass.Compact
    widthDp < 840 -> WindowWidthSizeClass.Medium
    else -> WindowWidthSizeClass.Expanded
}
