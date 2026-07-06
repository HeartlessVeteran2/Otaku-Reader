package app.otakureader.feature.updates

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.domain.model.DownloadItem
import app.otakureader.domain.model.DownloadStatus
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.MangaUpdate
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val UNSET_FETCH_DATE = 0L

/** Updates screen showing newly discovered chapters for library manga. */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UnusedParameter") // onNavigateBack/onNavigateToDownloads kept for nav contract; back arrow removed for Komikku parity
@Composable
fun UpdatesScreen(
    onMangaClick: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToUpdateErrors: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: UpdatesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    BackHandler(enabled = state.selectedItems.isNotEmpty()) {
        viewModel.onEvent(UpdatesEvent.ClearSelection)
    }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is UpdatesEffect.NavigateToReader -> onMangaClick(effect.mangaId)
                is UpdatesEffect.ShowSnackbar -> scope.launch {
                    snackbarHostState.showSnackbar(effect.message)
                }
                // Launch independently so collectLatest cancelling on the next swipe
                // does not cut this snackbar short; each mark-as-read is independently undoable.
                is UpdatesEffect.ShowUndoSnackbar -> scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = effect.message,
                        actionLabel = context.getString(R.string.updates_undo_action),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onEvent(UpdatesEvent.UnmarkChapterAsRead(effect.chapterId))
                    }
                }
                is UpdatesEffect.ShowUndoBulkReadSnackbar -> scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = effect.message,
                        actionLabel = context.getString(R.string.updates_undo_action),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onEvent(UpdatesEvent.UnmarkSelectedAsRead(effect.chapterIds))
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            UpdatesSelectionBottomBar(
                visible = state.selectedItems.isNotEmpty(),
                onDownloadClicked = { viewModel.onEvent(UpdatesEvent.DownloadSelected) },
                onMarkAsReadClicked = { viewModel.onEvent(UpdatesEvent.MarkSelectedAsRead) },
                onMarkAsUnreadClicked = { viewModel.onEvent(UpdatesEvent.MarkSelectedAsUnread) },
            )
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.selectedItems.isNotEmpty()) {
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.updates_selected_count, state.selectedItems.size))
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.onEvent(UpdatesEvent.ClearSelection) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.updates_clear_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.onEvent(UpdatesEvent.SelectAll) }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.updates_select_all))
                        }
                        IconButton(onClick = { viewModel.onEvent(UpdatesEvent.InvertSelection) }) {
                            Icon(Icons.Default.FlipToBack, contentDescription = stringResource(R.string.updates_invert_selection))
                        }
                    }
                )
            } else {
                var overflowMenuExpanded by remember { mutableStateOf(false) }
                TopAppBar(
                    title = { Text(stringResource(R.string.updates_title)) },
                    actions = {
                        // Filter icon — tinted when any date filter is active
                        IconButton(onClick = { viewModel.onEvent(UpdatesEvent.ShowFilterDialog) }) {
                            Icon(
                                imageVector = Icons.Outlined.FilterList,
                                contentDescription = stringResource(R.string.updates_filter),
                                tint = if (state.hasActiveFilters) MaterialTheme.colorScheme.primary
                                       else LocalContentColor.current,
                            )
                        }
                        // Calendar / upcoming icon
                        IconButton(onClick = { viewModel.onEvent(UpdatesEvent.ShowPendingUpdates) }) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = stringResource(R.string.updates_upcoming)
                            )
                        }
                        // Trigger a manual library update
                        IconButton(onClick = { viewModel.onEvent(UpdatesEvent.StartLibraryUpdate) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.updates_refresh_now)
                            )
                        }
                        // Update errors badge (Otaku-exclusive) — opens the dedicated Update Errors screen
                        if (state.updateErrorCount > 0) {
                            IconButton(onClick = onNavigateToUpdateErrors) {
                                BadgedBox(
                                    badge = {
                                        Badge { Text("${state.updateErrorCount}") }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = stringResource(R.string.updates_view_errors)
                                    )
                                }
                            }
                        }
                        // Overflow menu — Otaku-exclusive display mode toggle
                        IconButton(onClick = { overflowMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.updates_more_options)
                            )
                        }
                        DropdownMenu(
                            expanded = overflowMenuExpanded,
                            onDismissRequest = { overflowMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (state.displayMode == UpdatesDisplayMode.GROUPED_BY_MANGA) {
                                            stringResource(R.string.updates_group_by_date)
                                        } else {
                                            stringResource(R.string.updates_group_by_manga)
                                        }
                                    )
                                },
                                onClick = {
                                    viewModel.onEvent(UpdatesEvent.ToggleDisplayMode)
                                    overflowMenuExpanded = false
                                }
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.onEvent(UpdatesEvent.StartLibraryUpdate) },
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
                            text = state.error ?: stringResource(R.string.updates_unknown_error),
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    state.updates.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NewReleases,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.updates_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    else -> {
                        val onChapterClick: (MangaUpdate) -> Unit = { update ->
                            viewModel.onEvent(
                                UpdatesEvent.OnChapterClick(
                                    mangaId = update.manga.id,
                                    chapterId = update.chapter.id
                                )
                            )
                        }
                        val onChapterLongClick: (MangaUpdate) -> Unit = { update ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onEvent(UpdatesEvent.OnChapterLongClick(update.chapter.id))
                        }
                        val onDownloadClick: (MangaUpdate) -> Unit = { update ->
                            viewModel.onEvent(
                                UpdatesEvent.OnDownloadChapter(
                                    mangaId = update.manga.id,
                                    chapterId = update.chapter.id
                                )
                            )
                        }
                        val onMarkAsRead: (Long) -> Unit = { chapterId ->
                            viewModel.onEvent(UpdatesEvent.MarkChapterAsRead(chapterId))
                        }
                        if (state.displayMode == UpdatesDisplayMode.GROUPED_BY_MANGA) {
                            val filteredUpdates = remember(state.updates, state.dateFilterStart, state.dateFilterEnd) {
                                var result = state.updates
                                state.dateFilterStart?.let { start -> result = result.filter { it.chapter.dateFetch >= start } }
                                state.dateFilterEnd?.let { end -> result = result.filter { it.chapter.dateFetch <= end } }
                                result
                            }
                            val filteredGroups = remember(state.groupedByManga, filteredUpdates) {
                                val filteredIds = filteredUpdates.map { it.chapter.id }.toSet()
                                state.groupedByManga
                                    .map { group ->
                                        group.copy(chapters = group.chapters.filter { it.chapter.id in filteredIds })
                                    }
                                    .filter { it.chapters.isNotEmpty() }
                            }
                            if (filteredGroups.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = stringResource(R.string.updates_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                MangaGroupedUpdatesList(
                                    groups = filteredGroups,
                                    selectedItems = state.selectedItems,
                                    activeDownloads = state.activeDownloads,
                                    lastRunSummary = state.lastRunSummary,
                                    expandedMangaGroups = state.expandedMangaGroups,
                                    onToggleGroupExpansion = { viewModel.onEvent(UpdatesEvent.ToggleMangaGroupExpansion(it)) },
                                    onChapterClick = onChapterClick,
                                    onChapterLongClick = onChapterLongClick,
                                    onDownloadClick = onDownloadClick,
                                    onMarkAsRead = onMarkAsRead,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            // Komikku-parity: J2K date-grouped list with per-date manga collapsing
                            val uiModels = remember(state.updates, state.dateFilterStart, state.dateFilterEnd) {
                                buildJk2UiModel(state.updates, state.dateFilterStart, state.dateFilterEnd)
                            }
                            if (uiModels.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = stringResource(R.string.updates_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                UpdatesJk2List(
                                    uiModels = uiModels,
                                    expandedGroups = state.expandedDateMangaGroups,
                                    selectedItems = state.selectedItems,
                                    activeDownloads = state.activeDownloads,
                                    lastRunSummary = state.lastRunSummary,
                                    onToggleGroup = { viewModel.onEvent(UpdatesEvent.ToggleDateMangaGroup(it)) },
                                    onChapterClick = onChapterClick,
                                    onChapterLongClick = onChapterLongClick,
                                    onDownloadClick = onDownloadClick,
                                    onMarkAsRead = onMarkAsRead,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }

        // Filter dialog
        if (state.showFilterDialog) {
            UpdatesFilterDialog(
                dateFilterStart = state.dateFilterStart,
                dateFilterEnd = state.dateFilterEnd,
                onSetFilter = { start, end -> viewModel.onEvent(UpdatesEvent.SetDateFilter(start, end)) },
                onClearFilter = { viewModel.onEvent(UpdatesEvent.ClearDateFilter) },
                onDismiss = { viewModel.onEvent(UpdatesEvent.HideFilterDialog) },
            )
        }

        // To-Be-Updated Dialog
        if (state.showPendingUpdates) {
            PendingUpdatesDialog(
                pendingUpdates = state.pendingUpdates,
                onDismiss = { viewModel.onEvent(UpdatesEvent.HidePendingUpdates) },
                onStartUpdate = { viewModel.onEvent(UpdatesEvent.StartLibraryUpdate) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Manga-grouped list (Mihon-style)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MangaGroupedUpdatesList(
    groups: List<MangaUpdateGroup>,
    selectedItems: Set<Long>,
    activeDownloads: Map<Long, DownloadItem>,
    lastRunSummary: app.otakureader.domain.model.UpdateRunSummary?,
    expandedMangaGroups: Set<Long>,
    onToggleGroupExpansion: (Long) -> Unit,
    onChapterClick: (MangaUpdate) -> Unit,
    onChapterLongClick: (MangaUpdate) -> Unit,
    onDownloadClick: (MangaUpdate) -> Unit,
    onMarkAsRead: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (lastRunSummary != null) {
            item(key = "last_run_summary") {
                LastRunSummaryCard(
                    summary = lastRunSummary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        groups.forEach { group ->
            val isExpanded = group.manga.id in expandedMangaGroups
            val visibleChapters = if (group.chapters.size > 1 && !isExpanded) {
                group.chapters.take(1)
            } else {
                group.chapters
            }
            val hiddenCount = group.chapters.size - visibleChapters.size

            // Manga header row: cover + title + chapter count chip
            item(key = "manga_header_${group.manga.id}") {
                MangaGroupHeader(manga = group.manga, chapterCount = group.chapters.size)
            }
            // Chapter rows (no cover thumbnail — the group header provides context)
            items(visibleChapters, key = { it.chapter.id }) { update ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            onMarkAsRead(update.chapter.id)
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
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = androidx.compose.ui.Modifier.padding(end = 16.dp)
                            )
                        }
                    }
                ) {
                    // Indented chapter row without the manga cover (already shown in header)
                    GroupedChapterItem(
                        update = update,
                        isSelected = selectedItems.contains(update.chapter.id),
                        activeDownload = activeDownloads[update.chapter.id],
                        onClick = { onChapterClick(update) },
                        onLongClick = { onChapterLongClick(update) },
                        onDownloadClick = { onDownloadClick(update) }
                    )
                }
                HorizontalDivider()
            }
            // Expand / collapse affordance for multi-chapter groups
            if (group.chapters.size > 1) {
                item(key = "expand_${group.manga.id}") {
                    MangaGroupExpandRow(
                        label = if (isExpanded) {
                            stringResource(R.string.updates_collapse_chapters)
                        } else {
                            pluralStringResource(R.plurals.updates_more_chapters, hiddenCount, hiddenCount)
                        },
                        isExpanded = isExpanded,
                        onClick = { onToggleGroupExpansion(group.manga.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MangaGroupHeader(
    manga: Manga,
    chapterCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Manga cover thumbnail
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(width = 40.dp, height = 56.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(manga.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = manga.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(5f / 7f)
                    .clip(MaterialTheme.shapes.small)
            )
        }
        Text(
            text = manga.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // Chapter count chip
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = "$chapterCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun MangaGroupExpandRow(
    label: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 62.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun GroupedChapterItem(
    update: MangaUpdate,
    isSelected: Boolean,
    activeDownload: DownloadItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            // Indent to align with title text in the group header (cover width + spacing)
            .padding(start = 62.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            androidx.compose.material3.Checkbox(
                checked = true,
                onCheckedChange = { onClick() },
                modifier = Modifier.size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = update.chapter.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (update.chapter.dateFetch > UNSET_FETCH_DATE) {
                Text(
                    text = formatFetchDate(update.chapter.dateFetch),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (!isSelected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp)
            ) {
                when (activeDownload?.status) {
                    DownloadStatus.DOWNLOADING -> CircularProgressIndicator(
                        progress = { (activeDownload.progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                    DownloadStatus.QUEUED, DownloadStatus.PAUSED -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                    else -> IconButton(onClick = onDownloadClick) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = stringResource(R.string.updates_download_chapter),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// J2K date-grouped list (Komikku parity)
// ─────────────────────────────────────────────────────────────────────────────

/** Builds the flat J2K UI model list from the updates. */
private fun buildJk2UiModel(
    updates: List<MangaUpdate>,
    dateFilterStart: Long?,
    dateFilterEnd: Long?,
): List<UpdatesUiModel> {
    val filtered = if (dateFilterStart != null || dateFilterEnd != null) {
        updates.filter { update ->
            val ts = update.chapter.dateFetch
            if (ts <= UNSET_FETCH_DATE) return@filter true // unset date: keep, falls into today's bucket
            (dateFilterStart == null || ts >= dateFilterStart) && (dateFilterEnd == null || ts <= dateFilterEnd)
        }
    } else {
        updates
    }
    val result = mutableListOf<UpdatesUiModel>()
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now(zoneId)
    val byDate = filtered
        .groupBy { update ->
            val ts = update.chapter.dateFetch
            if (ts <= UNSET_FETCH_DATE) today else Instant.ofEpochMilli(ts).atZone(zoneId).toLocalDate()
        }
        .entries.sortedByDescending { it.key }

    for ((date, dateUpdates) in byDate) {
        val mangaGroups = dateUpdates
            .groupBy { it.manga.id }
            .entries
            .sortedByDescending { (_, chapters) -> chapters.maxOf { it.chapter.dateFetch } }
        result.add(UpdatesUiModel.Header(date = date, mangaCount = mangaGroups.size))
        for ((mangaId, chapters) in mangaGroups) {
            val isExpandable = chapters.size > 1
            val key = "${date}_${mangaId}"
            chapters.forEachIndexed { index, chapter ->
                result.add(UpdatesUiModel.Item(
                    update = chapter,
                    isLeader = index == 0,
                    isExpandable = isExpandable,
                    groupKey = key,
                ))
            }
        }
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdatesJk2List(
    uiModels: List<UpdatesUiModel>,
    expandedGroups: Set<String>,
    selectedItems: Set<Long>,
    activeDownloads: Map<Long, DownloadItem>,
    lastRunSummary: app.otakureader.domain.model.UpdateRunSummary?,
    onToggleGroup: (String) -> Unit,
    onChapterClick: (MangaUpdate) -> Unit,
    onChapterLongClick: (MangaUpdate) -> Unit,
    onDownloadClick: (MangaUpdate) -> Unit,
    onMarkAsRead: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        // "Last updated X ago" — italic, matches Komikku's updatesLastUpdatedItem
        if (lastRunSummary != null && lastRunSummary.timestamp > 0L) {
            item(key = "last_updated") {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(
                            R.string.updates_last_updated,
                            DateUtils.getRelativeTimeSpanString(
                                lastRunSummary.timestamp,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                            ).toString(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(
            items = uiModels,
            contentType = { if (it is UpdatesUiModel.Header) "header" else "item" },
            key = {
                when (it) {
                    is UpdatesUiModel.Header -> "header_${it.date}"
                    is UpdatesUiModel.Item -> "item_${it.update.chapter.id}"
                }
            },
        ) { uiModel ->
            when (uiModel) {
                is UpdatesUiModel.Header -> Jk2DateHeader(date = uiModel.date, mangaCount = uiModel.mangaCount)
                is UpdatesUiModel.Item -> {
                    val isExpanded = uiModel.groupKey in expandedGroups
                    val visible = uiModel.isLeader || isExpanded
                    AnimatedVisibility(
                        visible = visible,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    onMarkAsRead(uiModel.update.chapter.id)
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
                                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(end = 16.dp),
                                    )
                                }
                            },
                        ) {
                            Jk2UpdateItem(
                                update = uiModel.update,
                                isLeader = uiModel.isLeader,
                                isExpandable = uiModel.isExpandable,
                                isExpanded = isExpanded,
                                isSelected = uiModel.update.chapter.id in selectedItems,
                                activeDownload = activeDownloads[uiModel.update.chapter.id],
                                onToggleGroup = { onToggleGroup(uiModel.groupKey) },
                                onClick = { onChapterClick(uiModel.update) },
                                onLongClick = { onChapterLongClick(uiModel.update) },
                                onDownloadClick = { onDownloadClick(uiModel.update) },
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private val dateHeaderFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
private fun Jk2DateHeader(date: LocalDate, mangaCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = date.format(dateHeaderFormatter),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (mangaCount > 1) {
            Text(
                text = "$mangaCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Jk2UpdateItem(
    update: MangaUpdate,
    isLeader: Boolean,
    isExpandable: Boolean,
    isExpanded: Boolean,
    isSelected: Boolean,
    activeDownload: DownloadItem?,
    onToggleGroup: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textAlpha = if (update.chapter.read) 0.38f else 1f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick, // haptic handled by screen-level onChapterLongClick
            )
            .padding(
                top = if (isLeader) 8.dp else 0.dp,
                bottom = if (isLeader) 8.dp else 6.dp,
                start = 12.dp,
                end = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cover (leader), checkbox (when selected), or spacer (follower)
        if (isSelected) {
            androidx.compose.material3.Checkbox(
                checked = true,
                onCheckedChange = { onClick() },
                modifier = Modifier.size(48.dp),
            )
        } else if (isLeader) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(width = 48.dp, height = 64.dp),
                onClick = onClick,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(update.manga.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = update.manga.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.small),
                )
            }
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }

        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .weight(1f),
        ) {
            if (isLeader) {
                Text(
                    text = update.manga.title,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = textAlpha),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!update.chapter.read) {
                    Icon(
                        imageVector = Icons.Filled.Circle,
                        contentDescription = null,
                        modifier = Modifier
                            .size(8.dp)
                            .padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = update.chapter.name,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = textAlpha),
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(weight = 1f, fill = false),
                )
                if (update.chapter.lastPageRead > 0 && !update.chapter.read) {
                    Text(
                        text = stringResource(R.string.updates_chapter_progress, update.chapter.lastPageRead + 1),
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.38f),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Expand/collapse button for multi-chapter groups (leader only)
        if (isLeader && isExpandable) {
            IconButton(
                onClick = onToggleGroup,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) {
                        stringResource(R.string.updates_collapse_group)
                    } else {
                        stringResource(R.string.updates_expand_group)
                    },
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Download indicator / button
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
            when (activeDownload?.status) {
                DownloadStatus.DOWNLOADING -> CircularProgressIndicator(
                    progress = { (activeDownload.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
                DownloadStatus.QUEUED, DownloadStatus.PAUSED -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
                else -> IconButton(onClick = onDownloadClick) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = stringResource(R.string.updates_download_chapter),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// Filter dialog for date range selection
@Composable
private fun UpdatesFilterDialog(
    dateFilterStart: Long?,
    dateFilterEnd: Long?,
    onSetFilter: (Long?, Long?) -> Unit,
    onClearFilter: () -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val zoneId = remember { ZoneId.systemDefault() }
    val last7DaysStart = remember(today, zoneId) {
        today.minusDays(7).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }
    val last30DaysStart = remember(today, zoneId) {
        today.minusDays(30).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.updates_filter)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    stringResource(R.string.updates_filter_last_7_days) to last7DaysStart,
                    stringResource(R.string.updates_filter_last_30_days) to last30DaysStart,
                ).forEach { (label, start) ->
                    val isSelected = dateFilterStart == start && dateFilterEnd == null
                    Surface(
                        onClick = { if (isSelected) onClearFilter() else onSetFilter(start, null) },
                        shape = MaterialTheme.shapes.small,
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = if (dateFilterStart != null || dateFilterEnd != null) {
            { TextButton(onClick = { onClearFilter(); onDismiss() }) { Text(stringResource(R.string.updates_clear_filter)) } }
        } else null,
    )
}

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)

// L-9: Return a descriptive fallback instead of an empty string so that the UI
// never shows a blank date field when formatting fails.
private fun formatFetchDate(epochMs: Long): String = runCatching {
    Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(dateFormatter)
}.getOrDefault("Unknown date")

@Composable
private fun PendingUpdatesDialog(
    pendingUpdates: List<PendingUpdateManga>,
    onDismiss: () -> Unit,
    onStartUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.updates_pending_title)) },
        text = {
            if (pendingUpdates.isEmpty()) {
                Column(
                    modifier = modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.updates_pending_empty),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.padding(8.dp))
                    Text(
                        text = stringResource(R.string.updates_pending_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column {
                    Text(
                        text = stringResource(R.string.updates_pending_count, pendingUpdates.size),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(modifier = modifier.fillMaxWidth()) {
                        items(pendingUpdates, key = { it.mangaId }) { manga ->
                            PendingUpdateItem(manga = manga)
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.updates_pending_close))
            }
        },
        dismissButton = {
            if (pendingUpdates.isNotEmpty()) {
                TextButton(onClick = onStartUpdate) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(stringResource(R.string.updates_pending_start))
                }
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Last run summary diagnostics card (#1041)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LastRunSummaryCard(
    summary: app.otakureader.domain.model.UpdateRunSummary,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.updates_last_run_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LastRunStat(
                    label = stringResource(R.string.updates_last_run_checked),
                    value = summary.checkedCount.toString(),
                )
                LastRunStat(
                    label = stringResource(R.string.updates_last_run_new),
                    value = summary.newChaptersCount.toString(),
                    highlight = summary.newChaptersCount > 0,
                )
                LastRunStat(
                    label = stringResource(R.string.updates_last_run_skipped),
                    value = summary.skippedCount.toString(),
                )
                LastRunStat(
                    label = stringResource(R.string.updates_last_run_failed),
                    value = summary.failedCount.toString(),
                    highlight = summary.failedCount > 0,
                    highlightError = true,
                )
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.updates_last_run_meta,
                    formatFetchDate(summary.timestamp),
                    formatDurationMs(summary.durationMs),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LastRunStat(
    label: String,
    value: String,
    highlight: Boolean = false,
    highlightError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                highlight && highlightError -> MaterialTheme.colorScheme.error
                highlight -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDurationMs(durationMs: Long): String {
    val seconds = durationMs / 1_000
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}

@Composable
private fun PendingUpdateItem(
    manga: PendingUpdateManga,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = manga.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = manga.sourceName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (manga.lastChecked > 0L) {
                Text(
                    text = stringResource(R.string.updates_pending_last_checked, formatFetchDate(manga.lastChecked)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UpdatesSelectionBottomBar(
    visible: Boolean,
    onDownloadClicked: () -> Unit,
    onMarkAsReadClicked: () -> Unit,
    onMarkAsUnreadClicked: () -> Unit,
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
                    IconButton(onClick = onDownloadClicked) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = stringResource(R.string.updates_download_selected),
                        )
                    }
                    Text(
                        text = stringResource(R.string.updates_download_selected),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onMarkAsReadClicked) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.updates_mark_selected_read),
                        )
                    }
                    Text(
                        text = stringResource(R.string.updates_mark_selected_read),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onMarkAsUnreadClicked) {
                        Icon(
                            Icons.Default.RemoveDone,
                            contentDescription = stringResource(R.string.updates_mark_selected_unread),
                        )
                    }
                    Text(
                        text = stringResource(R.string.updates_mark_selected_unread),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
