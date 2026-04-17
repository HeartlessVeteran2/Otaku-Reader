package app.otakureader.feature.updates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.domain.model.MangaUpdate
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Updates screen showing newly discovered chapters for library manga. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    onMangaClick: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UpdatesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = androidx.compose.material3.SnackbarHostState()

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is UpdatesEffect.NavigateToReader -> onMangaClick(effect.mangaId)
                is UpdatesEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        modifier = modifier,
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
                        IconButton(onClick = { viewModel.onEvent(UpdatesEvent.DownloadSelected) }) {
                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.updates_download_selected))
                        }
                        IconButton(onClick = { viewModel.onEvent(UpdatesEvent.MarkSelectedAsRead) }) {
                            Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.updates_mark_selected_read))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.updates_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.updates_back))
                        }
                    },
                    actions = {
                        // To-Be-Updated preview icon
                        IconButton(onClick = { viewModel.onEvent(UpdatesEvent.ShowPendingUpdates) }) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = stringResource(R.string.updates_view_pending)
                            )
                        }
                        // Update errors icon with badge
                        if (state.updateErrors.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onEvent(UpdatesEvent.ShowUpdateErrors) }) {
                                BadgedBox(
                                    badge = {
                                        Badge { Text("${state.updateErrors.size}") }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = stringResource(R.string.updates_view_errors)
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onNavigateToDownloads) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = stringResource(R.string.updates_downloads)
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            state.error != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.error ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error
                )
            }

            state.updates.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.updates_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            else -> UpdatesList(
                updates = state.updates,
                selectedItems = state.selectedItems,
                onChapterClick = { update ->
                    viewModel.onEvent(
                        UpdatesEvent.OnChapterClick(
                            mangaId = update.manga.id,
                            chapterId = update.chapter.id
                        )
                    )
                },
                onChapterLongClick = { update ->
                    viewModel.onEvent(UpdatesEvent.OnChapterLongClick(update.chapter.id))
                },
                onDownloadClick = { update ->
                    viewModel.onEvent(
                        UpdatesEvent.OnDownloadChapter(
                            mangaId = update.manga.id,
                            chapterId = update.chapter.id
                        )
                    )
                },
                modifier = Modifier.padding(paddingValues)
            )
        }

        // Update Error Dialog
        if (state.showUpdateErrors) {
            UpdateErrorDialog(
                errors = state.updateErrors,
                onDismiss = { viewModel.onEvent(UpdatesEvent.HideUpdateErrors) },
                onClearError = { viewModel.onEvent(UpdatesEvent.ClearUpdateError(it)) },
                onClearAll = { viewModel.onEvent(UpdatesEvent.ClearAllUpdateErrors) }
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
// Grouped list
// ─────────────────────────────────────────────────────────────────────────────

private enum class UpdateDateBucket { TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH, OLDER }

private fun updateDateBucket(epochMs: Long): UpdateDateBucket {
    if (epochMs <= 0L) return UpdateDateBucket.OLDER
    val today = LocalDate.now()
    val day = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
    return when {
        day == today -> UpdateDateBucket.TODAY
        day == today.minusDays(1) -> UpdateDateBucket.YESTERDAY
        day >= today.minusDays(6) -> UpdateDateBucket.THIS_WEEK
        day >= today.minusDays(29) -> UpdateDateBucket.THIS_MONTH
        else -> UpdateDateBucket.OLDER
    }
}

private sealed interface UpdateListItem {
    data class Header(val bucket: UpdateDateBucket) : UpdateListItem
    data class Entry(val update: MangaUpdate) : UpdateListItem
}

@Composable
private fun bucketLabel(bucket: UpdateDateBucket): String = when (bucket) {
    UpdateDateBucket.TODAY -> stringResource(R.string.updates_date_today)
    UpdateDateBucket.YESTERDAY -> stringResource(R.string.updates_date_yesterday)
    UpdateDateBucket.THIS_WEEK -> stringResource(R.string.updates_date_this_week)
    UpdateDateBucket.THIS_MONTH -> stringResource(R.string.updates_date_this_month)
    UpdateDateBucket.OLDER -> stringResource(R.string.updates_date_older)
}

@Composable
private fun UpdatesList(
    updates: List<MangaUpdate>,
    selectedItems: Set<Long>,
    onChapterClick: (MangaUpdate) -> Unit,
    onChapterLongClick: (MangaUpdate) -> Unit,
    onDownloadClick: (MangaUpdate) -> Unit,
    modifier: Modifier = Modifier
) {
    val listItems = remember(updates) {
        buildList {
            var lastBucket: UpdateDateBucket? = null
            updates.forEach { update ->
                val bucket = updateDateBucket(update.chapter.dateFetch)
                if (bucket != lastBucket) {
                    add(UpdateListItem.Header(bucket))
                    lastBucket = bucket
                }
                add(UpdateListItem.Entry(update))
            }
        }
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(listItems, key = { item ->
            when (item) {
                is UpdateListItem.Header -> "header_${item.bucket}"
                is UpdateListItem.Entry -> item.update.chapter.id
            }
        }) { item ->
            when (item) {
                is UpdateListItem.Header -> UpdatesDateHeader(label = bucketLabel(item.bucket))
                is UpdateListItem.Entry -> {
                    UpdateItem(
                        update = item.update,
                        isSelected = selectedItems.contains(item.update.chapter.id),
                        onClick = { onChapterClick(item.update) },
                        onLongClick = { onChapterLongClick(item.update) },
                        onDownloadClick = { onDownloadClick(item.update) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun UpdatesDateHeader(label: String, modifier: Modifier = Modifier) {
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

@Composable
private fun UpdateItem(
    update: MangaUpdate,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox or cover thumbnail
        if (isSelected) {
            androidx.compose.material3.Checkbox(
                checked = true,
                onCheckedChange = { onClick() },
                modifier = Modifier.size(40.dp)
            )
        } else {
            // Manga cover thumbnail
            Surface(
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(width = 40.dp, height = 56.dp)
            ) {
                AsyncImage(
                    model = update.manga.thumbnailUrl,
                    contentDescription = update.manga.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(5f / 7f)
                        .clip(MaterialTheme.shapes.small)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = update.manga.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = update.chapter.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!isSelected) {
            if (update.chapter.dateFetch > 0L) {
                Text(
                    text = formatFetchDate(update.chapter.dateFetch),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 72.dp)
                )
            }
            // Per-item download button
            IconButton(onClick = onDownloadClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = stringResource(R.string.updates_download_chapter),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
private fun UpdateErrorDialog(
    errors: List<UpdateErrorEntry>,
    onDismiss: () -> Unit,
    onClearError: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.updates_error_title)) },
        text = {
            if (errors.isEmpty()) {
                Text(stringResource(R.string.updates_error_empty))
            } else {
                LazyColumn(modifier = modifier.fillMaxWidth()) {
                    items(errors, key = { it.mangaId }) { error ->
                        UpdateErrorItem(
                            error = error,
                            onClear = { onClearError(error.mangaId) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.updates_error_close))
            }
        },
        dismissButton = {
            if (errors.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text(stringResource(R.string.updates_error_clear_all))
                }
            }
        }
    )
}

@Composable
private fun UpdateErrorItem(
    error: UpdateErrorEntry,
    onClear: () -> Unit,
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
                text = error.mangaTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = error.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (error.timestamp > 0L) {
                Text(
                    text = formatFetchDate(error.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onClear) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = stringResource(R.string.updates_error_clear),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

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
