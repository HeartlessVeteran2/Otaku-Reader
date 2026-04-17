package app.otakureader.feature.history

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.domain.model.ChapterWithHistory
import app.otakureader.feature.history.R
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** History screen showing recently read chapters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onChapterClick: (mangaId: Long, chapterId: Long) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is HistoryEffect.NavigateToReader -> onChapterClick(effect.mangaId, effect.chapterId)
                is HistoryEffect.ShowSnackbar -> scope.launch {
                    snackbarHostState.showSnackbar(effect.message)
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
                        IconButton(onClick = { viewModel.onEvent(HistoryEvent.RemoveSelectedFromHistory) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.history_delete_selected))
                        }
                    } else {
                        if (state.history.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onEvent(HistoryEvent.SelectAll) }) {
                                Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.history_select_all))
                            }
                            TextButton(onClick = { viewModel.onEvent(HistoryEvent.ClearHistory) }) {
                                Text(stringResource(R.string.history_clear_all))
                            }
                        }
                    }
                }
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
                    Text(
                        text = if (state.searchQuery.isBlank()) stringResource(R.string.history_empty)
                        else stringResource(R.string.history_no_results, state.searchQuery)
                    )
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
                        viewModel.onEvent(HistoryEvent.OnChapterLongClick(entry.chapter.id))
                    },
                    onRemoveClick = { entry ->
                        viewModel.onEvent(HistoryEvent.RemoveFromHistory(entry.chapter.id))
                    }
                )
            }
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

@Composable
private fun HistoryList(
    history: List<ChapterWithHistory>,
    selectedItems: Set<Long>,
    onItemClick: (ChapterWithHistory) -> Unit,
    onItemLongClick: (ChapterWithHistory) -> Unit,
    onRemoveClick: (ChapterWithHistory) -> Unit,
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
                    HistoryItem(
                        entry = item.entry,
                        isSelected = selectedItems.contains(item.entry.chapter.id),
                        onItemClick = { onItemClick(item.entry) },
                        onItemLongClick = { onItemLongClick(item.entry) },
                        onRemoveClick = { onRemoveClick(item.entry) }
                    )
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
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(width = 40.dp, height = 56.dp)
            ) {
                AsyncImage(
                    model = entry.mangaThumbnailUrl,
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
            Text(
                text = formatReadAt(entry.readAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Action icons (hidden while selecting)
        if (!isSelected) {
            // Resume reading button (matches Mihon's "play" icon)
            IconButton(onClick = onItemClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.history_resume),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onRemoveClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.history_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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


