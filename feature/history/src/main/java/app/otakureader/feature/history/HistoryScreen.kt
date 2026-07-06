package app.otakureader.feature.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.background
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.domain.model.ChapterWithHistory
import app.otakureader.feature.history.R
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Spacing/sizing for the History empty state. */
private object HistoryEmptyStateDefaults {
    val Padding = 32.dp
    val IconSize = 64.dp
    val Spacing = 16.dp
}

/** History screen showing recently read chapters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onChapterClick: (mangaId: Long, chapterId: Long) -> Unit,
    onNavigateBack: () -> Unit,
    onMangaClick: (mangaId: Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is HistoryEffect.NavigateToReader -> onChapterClick(effect.mangaId, effect.chapterId)
                is HistoryEffect.ShowSnackbar -> scope.launch {
                    val msg = if (effect.formatArgs.isEmpty()) {
                        context.getString(effect.messageRes)
                    } else {
                        context.getString(effect.messageRes, *effect.formatArgs.toTypedArray())
                    }
                    snackbarHostState.showSnackbar(msg)
                }
                // Launch in a separate coroutine so collectLatest cancelling on the next swipe
                // does not cut the snackbar short. The ViewModel owns the auto-commit timer;
                // the screen only needs to signal Undo if the user taps the action.
                is HistoryEffect.ShowUndoSnackbar -> scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(effect.messageRes),
                        actionLabel = context.getString(R.string.history_undo_action),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onEvent(HistoryEvent.UndoRemoveFromHistory(effect.chapterId))
                    }
                }
                is HistoryEffect.ShowUndoBatchSnackbar -> scope.launch {
                    val msg = context.getString(effect.messageRes, effect.count)
                    val result = snackbarHostState.showSnackbar(
                        message = msg,
                        actionLabel = context.getString(R.string.history_undo_action),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onEvent(HistoryEvent.UndoBatchRemoveFromHistory(effect.chapterIds))
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (state.selectedItems.isNotEmpty()) {
                        Text(stringResource(R.string.history_selected_count, state.selectedItems.size))
                    } else {
                        Text(stringResource(R.string.history_title))
                    }
                },
                navigationIcon = {
                    if (state.selectedItems.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onEvent(HistoryEvent.ClearSelection) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.history_clear_selection))
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.history_back))
                        }
                    }
                },
                actions = {
                    if (state.selectedItems.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onEvent(HistoryEvent.SelectAll) }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.history_select_all))
                        }
                        IconButton(onClick = { viewModel.onEvent(HistoryEvent.InvertSelection) }) {
                            Icon(Icons.Default.FlipToBack, contentDescription = stringResource(R.string.history_invert_selection))
                        }
                    } else {
                        IconButton(onClick = { showDateRangePicker = true }) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = stringResource(R.string.history_filter_calendar),
                                tint = if (state.hasActiveFilters) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (state.history.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onEvent(HistoryEvent.SelectAll) }) {
                                Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.history_select_all))
                            }
                            TextButton(onClick = { showClearConfirm = true }) {
                                Text(stringResource(R.string.history_clear_all))
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            HistorySelectionBottomBar(
                visible = state.selectedItems.isNotEmpty(),
                onMarkAsReadClicked = { viewModel.onEvent(HistoryEvent.MarkSelectedAsRead) },
                onDeleteClicked = { viewModel.onEvent(HistoryEvent.RemoveSelectedFromHistory) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onEvent(HistoryEvent.OnSearchQueryChange(it)) },
                placeholder = { Text(stringResource(R.string.history_search_placeholder)) },
                singleLine = true,
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onEvent(HistoryEvent.OnSearchQueryChange("")) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.history_clear_search))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Date filter chips (H1 + H4)
            val hasDateFilter = state.dateFilterStart != null || state.dateFilterEnd != null
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp)
            ) {
                val now = System.currentTimeMillis()
                // Quick chip: last 7 days
                FilterChip(
                    selected = state.dateFilterStart == now - 7L * 24 * 60 * 60 * 1000 && state.dateFilterEnd == null,
                    onClick = {
                        if (state.dateFilterStart == now - 7L * 24 * 60 * 60 * 1000 && state.dateFilterEnd == null) {
                            viewModel.onEvent(HistoryEvent.ClearDateFilter)
                        } else {
                            viewModel.onEvent(HistoryEvent.SetDateFilter(now - 7L * 24 * 60 * 60 * 1000, null))
                        }
                    },
                    label = { Text(stringResource(R.string.history_filter_last_7_days)) },
                )
                // Quick chip: last 30 days
                FilterChip(
                    selected = state.dateFilterStart == now - 30L * 24 * 60 * 60 * 1000 && state.dateFilterEnd == null,
                    onClick = {
                        if (state.dateFilterStart == now - 30L * 24 * 60 * 60 * 1000 && state.dateFilterEnd == null) {
                            viewModel.onEvent(HistoryEvent.ClearDateFilter)
                        } else {
                            viewModel.onEvent(HistoryEvent.SetDateFilter(now - 30L * 24 * 60 * 60 * 1000, null))
                        }
                    },
                    label = { Text(stringResource(R.string.history_filter_last_30_days)) },
                )
                // Active custom date range chip (shown when a range picker result is set)
                val filterStart = state.dateFilterStart
                val filterEnd = state.dateFilterEnd
                if (hasDateFilter && filterStart != null && filterEnd != null) {
                    val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
                    val startLabel = Instant.ofEpochMilli(filterStart).atZone(ZoneId.systemDefault()).toLocalDate().format(fmt)
                    val endLabel = Instant.ofEpochMilli(filterEnd).atZone(ZoneId.systemDefault()).toLocalDate().format(fmt)
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.onEvent(HistoryEvent.ClearDateFilter) },
                        label = { Text(stringResource(R.string.history_filter_active, startLabel, endLabel)) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.history_filter_clear),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = state.isPullRefreshing,
                onRefresh = { viewModel.onEvent(HistoryEvent.RefreshHistory) },
                modifier = Modifier.weight(1f),
            ) {
                when {
                    state.isLoading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    state.error != null -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.error ?: stringResource(R.string.history_unknown_error),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    state.history.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.searchQuery.isBlank()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(HistoryEmptyStateDefaults.Padding),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(HistoryEmptyStateDefaults.IconSize),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(HistoryEmptyStateDefaults.Spacing))
                                Text(
                                    text = stringResource(R.string.history_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(text = stringResource(R.string.history_no_results, state.searchQuery))
                        }
                    }
                    else -> HistoryList(
                        history = state.history,
                        selectedItems = state.selectedItems,
                        onItemClick = { entry ->
                            viewModel.onEvent(
                                HistoryEvent.OnChapterClick(entry.chapter.mangaId, entry.chapter.id)
                            )
                        },
                        onItemLongClick = { entry ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onEvent(HistoryEvent.OnChapterLongClick(entry.chapter.id))
                        },
                        onRemoveClick = { entry ->
                            viewModel.onEvent(HistoryEvent.RemoveFromHistory(entry.chapter.id))
                        },
                        onFavoriteClick = { entry ->
                            viewModel.onEvent(HistoryEvent.ToggleMangaFavorite(entry.chapter.mangaId))
                        },
                        onMangaClick = { entry -> onMangaClick(entry.chapter.mangaId) },
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.history_clear_all_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_all_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.onEvent(HistoryEvent.ClearHistory)
                }) {
                    Text(stringResource(R.string.history_clear_all_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.history_clear_all_cancel))
                }
            },
        )
    }

    if (showDateRangePicker) {
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = dateRangePickerState.selectedStartDateMillis
                    val end = dateRangePickerState.selectedEndDateMillis?.let { it + 24 * 60 * 60 * 1000 - 1 }
                    viewModel.onEvent(HistoryEvent.SetDateFilter(start, end))
                    showDateRangePicker = false
                }) {
                    Text(stringResource(R.string.history_filter_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text(stringResource(R.string.history_filter_cancel))
                }
            },
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Grouped list
// ─────────────────────────────────────────────────────────────────────────────

private enum class HistoryDateBucket { TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH, OLDER }

/** Categorises a [ChapterWithHistory] into a relative-date bucket. */
private fun historyDateBucket(readAt: Long): HistoryDateBucket {
    if (readAt <= 0L) return HistoryDateBucket.OLDER
    val today = LocalDate.now()
    val readDay = Instant.ofEpochMilli(readAt).atZone(ZoneId.systemDefault()).toLocalDate()
    return when {
        readDay == today -> HistoryDateBucket.TODAY
        readDay == today.minusDays(1) -> HistoryDateBucket.YESTERDAY
        readDay >= today.minusDays(6) -> HistoryDateBucket.THIS_WEEK
        readDay >= today.minusDays(29) -> HistoryDateBucket.THIS_MONTH
        else -> HistoryDateBucket.OLDER
    }
}

@Composable
private fun historyBucketLabel(bucket: HistoryDateBucket): String = when (bucket) {
    HistoryDateBucket.TODAY -> stringResource(R.string.history_date_today)
    HistoryDateBucket.YESTERDAY -> stringResource(R.string.history_date_yesterday)
    HistoryDateBucket.THIS_WEEK -> stringResource(R.string.history_date_this_week)
    HistoryDateBucket.THIS_MONTH -> stringResource(R.string.history_date_this_month)
    HistoryDateBucket.OLDER -> stringResource(R.string.history_date_older)
}

/** Sealed wrapper so [LazyColumn] items can be either a header or a data row. */
private sealed interface HistoryListItem {
    data class Header(val bucket: HistoryDateBucket) : HistoryListItem
    data class Entry(val entry: ChapterWithHistory) : HistoryListItem
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryList(
    history: List<ChapterWithHistory>,
    selectedItems: Set<Long>,
    onItemClick: (ChapterWithHistory) -> Unit,
    onItemLongClick: (ChapterWithHistory) -> Unit,
    onRemoveClick: (ChapterWithHistory) -> Unit,
    onFavoriteClick: (ChapterWithHistory) -> Unit,
    onMangaClick: (ChapterWithHistory) -> Unit,
    modifier: Modifier = Modifier
) {
    // Build the flat list with injected date-group headers
    val listItems = remember(history) {
        buildList {
            var lastBucket: HistoryDateBucket? = null
            history.forEach { entry ->
                val bucket = historyDateBucket(entry.readAt)
                if (bucket != lastBucket) {
                    add(HistoryListItem.Header(bucket))
                    lastBucket = bucket
                }
                add(HistoryListItem.Entry(entry))
            }
        }
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(listItems, key = { item ->
            when (item) {
                is HistoryListItem.Header -> "header_${item.bucket}"
                is HistoryListItem.Entry -> item.entry.chapter.id
            }
        }) { item ->
            when (item) {
                is HistoryListItem.Header -> HistoryDateHeader(label = historyBucketLabel(item.bucket))
                is HistoryListItem.Entry -> {
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                onRemoveClick(item.entry)
                                true
                            } else false
                        }
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = androidx.compose.ui.Modifier.padding(end = 16.dp)
                                )
                            }
                        }
                    ) {
                        HistoryItem(
                            entry = item.entry,
                            isSelected = selectedItems.contains(item.entry.chapter.id),
                            onItemClick = { onItemClick(item.entry) },
                            onItemLongClick = { onItemLongClick(item.entry) },
                            onRemoveClick = { onRemoveClick(item.entry) },
                            onFavoriteClick = { onFavoriteClick(item.entry) },
                            onMangaCoverClick = { onMangaClick(item.entry) },
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun HistoryDateHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Row item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryItem(
    entry: ChapterWithHistory,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onMangaCoverClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Selection checkbox or cover thumbnail
        if (isSelected) {
            Checkbox(
                checked = true,
                onCheckedChange = { onItemClick() },
                modifier = Modifier.size(40.dp)
            )
        } else {
            Surface(
                onClick = onMangaCoverClick,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(width = 40.dp, height = 56.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(entry.mangaThumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = entry.mangaTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(5f / 7f)
                        .clip(MaterialTheme.shapes.small)
                )
            }
        }

        // Text block
        Column(modifier = Modifier.weight(1f)) {
            val title = entry.mangaTitle
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
            Text(
                text = entry.chapter.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            // Read progress ("Page X") — only when partially read
            val lastPage = entry.chapter.lastPageRead
            if (lastPage > 0 && !entry.chapter.read) {
                Text(
                    text = stringResource(R.string.history_read_progress, lastPage + 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatReadAt(entry.readAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Action icons (hidden while selecting)
        if (!isSelected) {
            // Add to library / in-library indicator — matches Komikku's favorite button per row
            IconToggleButton(checked = entry.mangaFavorite, onCheckedChange = { onFavoriteClick() }) {
                if (entry.mangaFavorite) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = stringResource(R.string.history_in_library),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(R.string.history_add_to_library),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Resume reading button (matches Mihon's "play" icon)
            IconButton(onClick = onItemClick) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.history_resume),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onRemoveClick) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.history_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatReadAt(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return DATE_FORMATTER.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
}

private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.getDefault())

@Composable
private fun HistorySelectionBottomBar(
    visible: Boolean,
    onMarkAsReadClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = tween(delayMillis = 300)),
        exit = shrinkVertically(animationSpec = tween()),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onMarkAsReadClicked) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.history_mark_read),
                        )
                    }
                    Text(
                        text = stringResource(R.string.history_mark_read),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onDeleteClicked) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.history_delete_selected),
                        )
                    }
                    Text(
                        text = stringResource(R.string.history_delete_selected),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}


