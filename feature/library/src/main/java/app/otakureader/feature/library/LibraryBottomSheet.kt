package app.otakureader.feature.library

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LibraryBottomSheet(
    state: LibraryState,
    onEvent: (LibraryEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            // Tabs
            TabRow(
                selectedTabIndex = state.bottomSheetTab.ordinal,
                modifier = Modifier.fillMaxWidth(),
            ) {
                LibraryBottomSheetTab.entries.forEach { tab ->
                    Tab(
                        selected = state.bottomSheetTab == tab,
                        onClick = { onEvent(LibraryEvent.SetBottomSheetTab(tab)) },
                        text = { Text(tab.label()) },
                    )
                }
            }

            // Tab content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state.bottomSheetTab) {
                    LibraryBottomSheetTab.DISPLAY -> DisplayTab(state, onEvent)
                    LibraryBottomSheetTab.SORT -> SortTab(state, onEvent)
                    LibraryBottomSheetTab.FILTER -> FilterTab(state, onEvent)
                    LibraryBottomSheetTab.GROUP -> GroupTab(state, onEvent)
                }
            }
        }
    }
}

/** Spacing between the display-mode chips in the library display settings. */
private val DISPLAY_MODE_CHIP_SPACING = 8.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplayTab(
    state: LibraryState,
    onEvent: (LibraryEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Display mode picker — Komikku puts this first.
        Text(
            text = stringResource(R.string.display_mode_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DISPLAY_MODE_CHIP_SPACING),
            verticalArrangement = Arrangement.spacedBy(DISPLAY_MODE_CHIP_SPACING),
        ) {
            // Explicit display order (Grid → Comfortable → Cover-only → List), independent of the
            // enum's declaration/ordinal order, which keeps appended entries stable.
            listOf(
                LibraryDisplayMode.GRID,
                LibraryDisplayMode.COMFORTABLE_GRID,
                LibraryDisplayMode.COVER_ONLY,
                LibraryDisplayMode.LIST,
            ).forEach { mode ->
                FilterChip(
                    selected = state.displayMode == mode,
                    onClick = { onEvent(LibraryEvent.SetDisplayMode(mode)) },
                    label = { Text(mode.label()) },
                )
            }
        }

        // Columns slider — hidden in List mode (Komikku parity). Orientation-aware: portrait
        // and landscape each have their own column count; 0 = Auto (use window-size fallback).
        if (state.displayMode != LibraryDisplayMode.LIST) {
            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            val currentColumns = if (isLandscape) state.landscapeColumns else state.portraitColumns
            val columnLabel = stringResource(
                if (isLandscape) R.string.display_columns_landscape else R.string.display_columns_portrait
            )
            Column {
                Text(
                    text = columnLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                var sliderValue by remember(currentColumns) { mutableFloatStateOf(currentColumns.toFloat()) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.display_columns_auto_short), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = {
                            val count = sliderValue.toInt()
                            if (isLandscape) onEvent(LibraryEvent.SetLandscapeColumns(count))
                            else onEvent(LibraryEvent.SetPortraitColumns(count))
                        },
                        valueRange = 0f..10f,
                        steps = 9,
                        modifier = Modifier.weight(1f),
                    )
                    Text("10", style = MaterialTheme.typography.bodySmall)
                }
                val valueText = if (currentColumns == 0) {
                    stringResource(R.string.display_columns_auto)
                } else {
                    stringResource(R.string.display_grid_size_value, currentColumns)
                }
                Text(text = valueText, style = MaterialTheme.typography.bodyMedium)
            }

            // Staggered grid (only meaningful in grid modes)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.display_staggered_grid))
                Switch(
                    checked = state.isStaggeredGrid,
                    onCheckedChange = { enabled -> onEvent(LibraryEvent.SetStaggeredGrid(enabled)) },
                )
            }
        }

        HorizontalDivider()

        // Overlay section (Komikku parity: groups all cover-overlay toggles under one heading)
        Text(
            text = stringResource(R.string.display_overlay_header),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        // Show download badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.display_show_download_badge))
            Switch(
                checked = state.showDownloadBadge,
                onCheckedChange = { enabled -> onEvent(LibraryEvent.SetShowDownloadBadge(enabled)) },
            )
        }

        // Show unread badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.display_show_badges))
            Switch(
                checked = state.showBadges,
                onCheckedChange = { enabled -> onEvent(LibraryEvent.SetShowBadges(enabled)) },
            )
        }

        // Show title on cover (Otaku-exclusive: overlays the title text on the cover art)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.display_show_title))
            Switch(
                checked = state.showTitle,
                onCheckedChange = { enabled -> onEvent(LibraryEvent.SetShowTitle(enabled)) },
            )
        }

        // Show continue reading button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.display_show_continue_reading_button))
            Switch(
                checked = state.showContinueReadingButton,
                onCheckedChange = { enabled -> onEvent(LibraryEvent.SetShowContinueReadingButton(enabled)) },
            )
        }

        HorizontalDivider()

        // Tabs section (Komikku parity)
        Text(
            text = stringResource(R.string.display_tabs_header),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.display_show_category_tabs))
            Switch(
                checked = state.showCategoryTabs,
                onCheckedChange = { enabled -> onEvent(LibraryEvent.SetShowCategoryTabs(enabled)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.display_show_category_item_count))
            Switch(
                checked = state.showCategoryItemCount,
                onCheckedChange = { enabled -> onEvent(LibraryEvent.SetShowCategoryItemCount(enabled)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortTab(
    state: LibraryState,
    onEvent: (LibraryEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.filter_sheet_sort_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                LibrarySortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.sortMode == mode,
                        onClick = { onEvent(LibraryEvent.SetSortMode(mode)) },
                        label = { Text(mode.label()) },
                    )
                }
            }
            IconToggleButton(
                checked = state.sortAscending,
                onCheckedChange = { onEvent(LibraryEvent.SetSortAscending(it)) },
            ) {
                Icon(
                    imageVector = if (state.sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = if (state.sortAscending)
                        stringResource(R.string.filter_sort_ascending)
                    else
                        stringResource(R.string.filter_sort_descending),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterTab(
    state: LibraryState,
    onEvent: (LibraryEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Header row with clear all
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.filter_sheet_status_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = { onEvent(LibraryEvent.ClearAllFilters) }) {
                Text(stringResource(R.string.filter_sheet_clear_all))
            }
        }

        // Independent tristate filters — each cycles DISABLED → IS → NOT on click.
        TriStateFilterRow(
            label = stringResource(R.string.filter_downloaded),
            state = state.filterDownloaded,
            onClick = { onEvent(LibraryEvent.SetFilterDownloaded(state.filterDownloaded.next())) },
        )
        TriStateFilterRow(
            label = stringResource(R.string.filter_unread),
            state = state.filterUnread,
            onClick = { onEvent(LibraryEvent.SetFilterUnread(state.filterUnread.next())) },
        )
        TriStateFilterRow(
            label = stringResource(R.string.filter_started),
            state = state.filterStarted,
            onClick = { onEvent(LibraryEvent.SetFilterStarted(state.filterStarted.next())) },
        )
        TriStateFilterRow(
            label = stringResource(R.string.filter_bookmarked),
            state = state.filterBookmarked,
            onClick = { onEvent(LibraryEvent.SetFilterBookmarked(state.filterBookmarked.next())) },
        )
        TriStateFilterRow(
            label = stringResource(R.string.filter_tracking),
            state = state.filterTracking,
            onClick = { onEvent(LibraryEvent.SetFilterTracking(state.filterTracking.next())) },
        )
        TriStateFilterRow(
            label = stringResource(R.string.filter_completed),
            state = state.filterCompleted,
            onClick = { onEvent(LibraryEvent.SetFilterCompleted(state.filterCompleted.next())) },
        )

        if (state.availableGenres.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.filter_sheet_genre_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.availableGenres.forEach { genre ->
                    FilterChip(
                        selected = genre in state.filterGenres,
                        onClick = {
                            val updated = if (genre in state.filterGenres) {
                                state.filterGenres - genre
                            } else {
                                state.filterGenres + genre
                            }
                            onEvent(LibraryEvent.SetGenreFilter(updated))
                        },
                        label = { Text(genre) },
                    )
                }
            }
        }
    }
}

/**
 * A single row that shows a filter label and a cycling tristate icon button.
 * Tapping it advances DISABLED → ENABLED_IS → ENABLED_NOT → DISABLED.
 */
@Composable
private fun TriStateFilterRow(
    label: String,
    state: LibraryTriState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                LibraryTriState.DISABLED -> Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = stringResource(R.string.tristate_disabled),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                LibraryTriState.ENABLED_IS -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.tristate_include),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                LibraryTriState.ENABLED_NOT -> Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.tristate_exclude),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupTab(
    state: LibraryState,
    onEvent: (LibraryEvent) -> Unit,
) {
    val context = LocalContext.current
    val groupOptions = remember(context) {
        listOf(
            LibraryGroup.BY_DEFAULT to context.getString(R.string.group_type_by_default),
            LibraryGroup.BY_SOURCE to context.getString(R.string.group_type_by_source),
            LibraryGroup.BY_STATUS to context.getString(R.string.group_type_by_status),
            LibraryGroup.BY_TRACK_STATUS to context.getString(R.string.group_type_by_track_status),
            LibraryGroup.UNGROUPED to context.getString(R.string.group_type_ungrouped),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.group_category_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        groupOptions.forEach { (type, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEvent(LibraryEvent.SetGroupType(type)) }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                if (state.groupType == type) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        if (state.groupType == LibraryGroup.BY_DEFAULT && state.categories.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(R.string.group_category_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                FilterChip(
                    selected = state.selectedCategory == null,
                    onClick = { onEvent(LibraryEvent.OnCategorySelected(null)) },
                    label = { Text(stringResource(R.string.library_category_all)) },
                )
                state.categories.forEach { category ->
                    FilterChip(
                        selected = state.selectedCategory == category.id,
                        onClick = { onEvent(LibraryEvent.OnCategorySelected(category.id)) },
                        label = { Text("${category.name} (${category.count})") },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryDisplayMode.label(): String = when (this) {
    LibraryDisplayMode.GRID -> stringResource(R.string.display_mode_grid)
    LibraryDisplayMode.COMFORTABLE_GRID -> stringResource(R.string.display_mode_comfortable)
    LibraryDisplayMode.LIST -> stringResource(R.string.display_mode_list)
    LibraryDisplayMode.COVER_ONLY -> stringResource(R.string.display_mode_cover_only)
}

@Composable
private fun LibrarySortMode.label(): String = when (this) {
    LibrarySortMode.ALPHABETICAL -> stringResource(R.string.sort_title)
    LibrarySortMode.LAST_READ -> stringResource(R.string.sort_last_read)
    LibrarySortMode.DATE_ADDED -> stringResource(R.string.sort_date_added)
    LibrarySortMode.UNREAD_COUNT -> stringResource(R.string.sort_unread_count)
    LibrarySortMode.SOURCE -> stringResource(R.string.sort_source)
    LibrarySortMode.LAST_UPDATED -> stringResource(R.string.sort_last_updated)
    LibrarySortMode.TOTAL_CHAPTERS -> stringResource(R.string.sort_total_chapters)
    LibrarySortMode.LATEST_CHAPTER -> stringResource(R.string.sort_latest_chapter)
    LibrarySortMode.RANDOM -> stringResource(R.string.sort_random)
    LibrarySortMode.CHAPTER_FETCH_DATE -> stringResource(R.string.sort_chapter_fetch_date)
    LibrarySortMode.TRACKER_MEAN -> stringResource(R.string.sort_tracker_mean)
}

@Composable
private fun LibraryFilterMode.label(): String = when (this) {
    LibraryFilterMode.ALL -> stringResource(R.string.filter_all)
    LibraryFilterMode.DOWNLOADED -> stringResource(R.string.filter_downloaded)
    LibraryFilterMode.UNREAD -> stringResource(R.string.filter_unread)
    LibraryFilterMode.COMPLETED -> stringResource(R.string.filter_completed)
    LibraryFilterMode.DROPPED -> stringResource(R.string.filter_dropped)
    LibraryFilterMode.TRACKING -> stringResource(R.string.filter_tracking)
    LibraryFilterMode.READING_LIST -> stringResource(R.string.filter_reading_list)
}

@Composable
private fun LibraryBottomSheetTab.label(): String = when (this) {
    LibraryBottomSheetTab.DISPLAY -> stringResource(R.string.display_tab)
    LibraryBottomSheetTab.SORT -> stringResource(R.string.sort_tab)
    LibraryBottomSheetTab.FILTER -> stringResource(R.string.filter_tab)
    LibraryBottomSheetTab.GROUP -> stringResource(R.string.group_tab)
}
