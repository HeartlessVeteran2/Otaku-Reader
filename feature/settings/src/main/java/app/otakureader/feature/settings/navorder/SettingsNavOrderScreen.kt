package app.otakureader.feature.settings.navorder

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import app.otakureader.core.preferences.NavTab
import app.otakureader.feature.settings.R

// ---------------------------------------------------------------------------
// Navigation registration
// ---------------------------------------------------------------------------

fun NavGraphBuilder.settingsNavOrderScreen(onNavigateBack: () -> Unit) {
    composable<Route.SettingsNavOrder> {
        SettingsNavOrderScreen(onNavigateBack = onNavigateBack)
    }
}

// ---------------------------------------------------------------------------
// Drag-and-drop helpers
// ---------------------------------------------------------------------------

/**
 * Holds mutable drag state for a [LazyColumn].
 *
 * [onMove] is called each time the dragging item crosses the midpoint of an adjacent item, so
 * the list re-orders incrementally while the user is still dragging — the same behaviour as
 * most drag-reorder implementations in Compose.
 */
class DragDropState(
    val lazyListState: LazyListState,
    private val onMove: (Int, Int) -> Unit,
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
    var draggingItemOffset by mutableFloatStateOf(0f)

    val draggingItemLayoutInfo: LazyListItemInfo?
        get() = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == draggingItemIndex }

    /** Called when a long-press gesture starts at [offset] inside the list. */
    fun onDragStart(offset: Offset) {
        lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            offset.y.toInt() in item.offset..(item.offset + item.size)
        }?.also {
            draggingItemIndex = it.index
            draggingItemOffset = 0f
        }
    }

    /**
     * Called for every drag delta. Accumulates [offset.y] and checks whether the dragged item
     * has crossed the midpoint of a neighbour — if so, triggers [onMove].
     */
    fun onDrag(offset: Offset) {
        draggingItemOffset += offset.y
        val current = draggingItemIndex ?: return
        val info = draggingItemLayoutInfo ?: return
        val startOffset = draggingItemOffset + info.offset
        val endOffset = startOffset + info.size
        val mid = (startOffset + endOffset) / 2f
        lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.offset <= mid && mid <= it.offset + it.size && it.index != current }
            ?.also { target ->
                onMove(current, target.index)
                draggingItemIndex = target.index
                // After swapping, reset the offset accumulator so the visual position stays
                // anchored to the pointer rather than jumping.
                draggingItemOffset = 0f
            }
    }

    fun onDragInterrupted() {
        draggingItemIndex = null
        draggingItemOffset = 0f
    }
}

@Composable
fun rememberDragDropState(listState: LazyListState, onMove: (Int, Int) -> Unit): DragDropState =
    remember { DragDropState(listState, onMove) }

/**
 * Attaches [detectDragGesturesAfterLongPress] to the composable, forwarding events to [state].
 * Long-press is used instead of immediate drag so that short taps (e.g. the Switch) still work.
 */
fun Modifier.dragContainer(state: DragDropState): Modifier = pointerInput(state) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> state.onDragStart(offset) },
        onDrag = { change, dragAmount ->
            state.onDrag(dragAmount)
            change.consume()
        },
        onDragEnd = { state.onDragInterrupted() },
        onDragCancel = { state.onDragInterrupted() },
    )
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNavOrderScreen(
    onNavigateBack: () -> Unit,
    viewModel: NavOrderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(listState) { from, to ->
        viewModel.onEvent(NavOrderEvent.MoveTab(from, to))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_order_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onEvent(NavOrderEvent.Reset()) }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.nav_order_reset),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .dragContainer(dragDropState),
        ) {
            itemsIndexed(state.tabs, key = { _, entry -> entry.tab.name }) { index, entry ->
                val isDragging = index == dragDropState.draggingItemIndex
                val itemModifier = if (isDragging) {
                    // Lift the dragging item above its siblings and translate it by the
                    // accumulated drag offset so it follows the user's finger.
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationY = dragDropState.draggingItemOffset }
                } else {
                    // Non-dragging items animate smoothly into their new positions as the
                    // list reorders around the dragged item.
                    Modifier.animateItem()
                }
                NavTabRow(
                    entry = entry,
                    index = index,
                    total = state.tabs.size,
                    onMoveUp = { viewModel.onEvent(NavOrderEvent.MoveUp(index)) },
                    onMoveDown = { viewModel.onEvent(NavOrderEvent.MoveDown(index)) },
                    onToggleVisibility = {
                        viewModel.onEvent(NavOrderEvent.ToggleTabVisibility(index))
                    },
                    modifier = itemModifier,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Row composable
// ---------------------------------------------------------------------------

@Composable
private fun NavTabRow(
    entry: NavTabEntry,
    index: Int,
    total: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleVisibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            // Drag handle — the long-press gesture is captured by the LazyColumn modifier,
            // so this icon is purely decorative / visual affordance.
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.nav_order_drag_hint),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )

            Text(
                text = entry.tab.displayName(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )

            // Up / Down buttons kept for accessibility (non-drag reordering).
            IconButton(onClick = onMoveUp, enabled = index > 0) {
                Icon(
                    Icons.Default.ArrowUpward,
                    contentDescription = stringResource(R.string.nav_order_move_up),
                )
            }
            IconButton(onClick = onMoveDown, enabled = index < total - 1) {
                Icon(
                    Icons.Default.ArrowDownward,
                    contentDescription = stringResource(R.string.nav_order_move_down),
                )
            }

            // Visibility toggle — Switch on the right edge.
            Switch(
                checked = entry.isVisible,
                onCheckedChange = { onToggleVisibility() },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Display name helpers
// ---------------------------------------------------------------------------

@Composable
private fun NavTab.displayName(): String = when (this) {
    NavTab.LIBRARY -> stringResource(R.string.nav_tab_library)
    NavTab.UPDATES -> stringResource(R.string.nav_tab_updates)
    NavTab.BROWSE -> stringResource(R.string.nav_tab_browse)
    NavTab.HISTORY -> stringResource(R.string.nav_tab_history)
    NavTab.MORE -> stringResource(R.string.nav_tab_more)
}
