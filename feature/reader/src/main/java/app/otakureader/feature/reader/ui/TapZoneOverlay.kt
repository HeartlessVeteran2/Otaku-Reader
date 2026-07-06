package app.otakureader.feature.reader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import app.otakureader.domain.model.TapInvertMode

// ── Region geometry ──────────────────────────────────────────────────────────

private enum class NavAction { PREV, NEXT, LEFT, RIGHT, MENU }

private data class NavRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val action: NavAction,
) {
    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom

    fun invert(mode: TapInvertMode): NavRegion {
        val h = mode.shouldInvertHorizontal
        val v = mode.shouldInvertVertical
        return when {
            h && v -> copy(left = 1f - right, top = 1f - bottom, right = 1f - left, bottom = 1f - top)
            h -> copy(left = 1f - right, right = 1f - left)
            v -> copy(top = 1f - bottom, bottom = 1f - top)
            else -> this
        }
    }
}

private const val MENU_REGION_HEIGHT = 0.05f
private const val ZONE_SIZE_NORMAL = 0.33f
private const val ZONE_SIZE_SMALLER = 0.25f

private val CONSTANT_MENU_REGION = NavRegion(0f, 0f, 1f, MENU_REGION_HEIGHT, NavAction.MENU)

private fun buildRegions(navigationMode: Int, regionSize1: Float): List<NavRegion> {
    val s2 = 1f - regionSize1
    return when (navigationMode) {
        // 0 & 1: L Navigation (L-shaped PREV zone)
        0, 1 -> listOf(
            NavRegion(0f, regionSize1, regionSize1, s2, NavAction.PREV),
            NavRegion(0f, 0f, 1f, regionSize1, NavAction.PREV),
            NavRegion(s2, regionSize1, 1f, s2, NavAction.NEXT),
            NavRegion(0f, s2, 1f, 1f, NavAction.NEXT),
        )
        // 2: Kindlish Navigation (Kindle-style: top = MENU, bottom-left = PREV, bottom-right = NEXT)
        2 -> listOf(
            NavRegion(regionSize1, regionSize1, 1f, 1f, NavAction.NEXT),
            NavRegion(0f, regionSize1, regionSize1, 1f, NavAction.PREV),
        )
        // 3: Edge Navigation (left/right columns = NEXT, bottom-center = PREV)
        3 -> listOf(
            NavRegion(0f, 0f, regionSize1, 1f, NavAction.NEXT),
            NavRegion(regionSize1, s2, s2, 1f, NavAction.PREV),
            NavRegion(s2, 0f, 1f, 1f, NavAction.NEXT),
        )
        // 4: Right-and-Left Navigation (left col = LEFT, right col = RIGHT, center = MENU)
        4 -> listOf(
            NavRegion(0f, 0f, regionSize1, 1f, NavAction.LEFT),
            NavRegion(s2, 0f, 1f, 1f, NavAction.RIGHT),
        )
        // 5: Disabled — no regions; every tap becomes MENU
        else -> emptyList()
    }
}

// ── Debug overlay colors matching Komikku's NavigationRegion colors ──────────

private val ColorPrev = Color(0xCCFF7733.toInt())
private val ColorNext = Color(0xCC84E296.toInt())
private val ColorLeft = Color(0xCC7D1128.toInt())
private val ColorRight = Color(0xCCA6CFD5.toInt())
private val ColorMenu = Color(0xCC95818D.toInt())

private fun NavAction.debugColor() = when (this) {
    NavAction.PREV -> ColorPrev
    NavAction.NEXT -> ColorNext
    NavAction.LEFT -> ColorLeft
    NavAction.RIGHT -> ColorRight
    NavAction.MENU -> ColorMenu
}

// ── NavigationOverlay ─────────────────────────────────────────────────────────

/**
 * Tap-zone navigation overlay matching Komikku's ViewerNavigation system.
 *
 * Six layouts (index 0-5):
 *   0/1 = L Navigation (default), 2 = Kindlish, 3 = Edge,
 *   4 = Right-and-Left, 5 = Disabled.
 *
 * [tapInvertMode] mirrors region coordinates on the chosen axis.
 * [smallerTapZone] shrinks side/corner regions from 33% to 25%.
 *
 * [showDebugOverlay] draws colored region overlays (matches Komikku's
 * NavigationOverlay that appears when the feature is first enabled).
 */
@Composable
fun NavigationOverlay(
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleMenu: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    navigationMode: Int = 0,
    tapInvertMode: TapInvertMode = TapInvertMode.NONE,
    smallerTapZone: Boolean = false,
    showDebugOverlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val regionSize1 = if (smallerTapZone) ZONE_SIZE_SMALLER else ZONE_SIZE_NORMAL

    val regions = remember(navigationMode, tapInvertMode, smallerTapZone) {
        buildRegions(navigationMode, regionSize1).map { it.invert(tapInvertMode) }
    }

    // rememberUpdatedState ensures pointerInput always invokes the latest callbacks
    // even when regions haven't changed (e.g. RTL flip mid-session).
    val onPrevState = rememberUpdatedState(onPrev)
    val onNextState = rememberUpdatedState(onNext)
    val onToggleMenuState = rememberUpdatedState(onToggleMenu)
    val onLongPressState = rememberUpdatedState(onLongPress)

    fun resolveAction(x: Float, y: Float) {
        if (CONSTANT_MENU_REGION.contains(x, y)) { onToggleMenuState.value(); return }
        val region = regions.find { it.contains(x, y) }
        if (region != null) {
            when (region.action) {
                NavAction.PREV -> onPrevState.value()
                NavAction.NEXT -> onNextState.value()
                // LEFT/RIGHT map directly to onPrev/onNext; RTL inversion is already
                // baked into those callbacks at the call site (ReaderScreen).
                NavAction.LEFT -> onPrevState.value()
                NavAction.RIGHT -> onNextState.value()
                NavAction.MENU -> onToggleMenuState.value()
            }
        } else {
            onToggleMenuState.value()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()

        if (showDebugOverlay) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw constant menu region first so navigation regions paint on top,
                // matching the actual hit-test priority (CONSTANT_MENU_REGION checked first).
                listOf(CONSTANT_MENU_REGION).plus(regions).forEach { r ->
                    drawRect(
                        color = r.action.debugColor(),
                        topLeft = Offset(r.left * w, r.top * h),
                        size = androidx.compose.ui.geometry.Size(
                            (r.right - r.left) * w,
                            (r.bottom - r.top) * h,
                        ),
                    )
                }
            }
        }

        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(regions) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (w > 0f && h > 0f) {
                                resolveAction(offset.x / w, offset.y / h)
                            }
                        },
                        onLongPress = { _ -> onLongPressState.value?.invoke() },
                    )
                },
        )
    }
}
