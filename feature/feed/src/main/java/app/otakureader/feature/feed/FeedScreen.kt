package app.otakureader.feature.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.domain.model.FeedItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Scaffold-free content for embedding inside Browse's Feed tab.
 * Callers are responsible for handling [FeedEffect] via the ViewModel.
 */
@Composable
fun FeedContent(
    state: FeedState,
    onEvent: (FeedEvent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val formatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
    }
    when {
        state.isLoading -> Box(
            modifier = modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        state.error != null -> Box(
            modifier = modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = state.error ?: stringResource(R.string.feed_error_unknown),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        state.feedItems.isEmpty() -> Box(
            modifier = modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.feed_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "discovery_chips") {
                DiscoveryModeChips(
                    selected = state.discoveryMode,
                    onSelect = { onEvent(FeedEvent.SetDiscoveryMode(it)) },
                )
            }
            items(items = state.feedItems, key = { it.id }) { item ->
                FeedItemRow(
                    item = item,
                    formatter = formatter,
                    isFavorited = item.mangaId in state.favoritedMangaIds,
                    onClick = { onEvent(FeedEvent.OnFeedItemClick(item.mangaId, item.chapterId)) },
                    onMarkAsRead = { onEvent(FeedEvent.OnMarkAsRead(item.id)) },
                    onLongClick = { onEvent(FeedEvent.LongClickManga(item.mangaId)) },
                )
                HorizontalDivider()
            }
        }
    }
}

/** Feed screen showing the latest chapters across all configured feed sources. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReader: (mangaId: Long, chapterId: Long) -> Unit,
    onNavigateToFeedManagement: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is FeedEffect.NavigateToReader ->
                    onNavigateToReader(effect.mangaId, effect.chapterId)
                is FeedEffect.ShowSnackbar -> scope.launch {
                    snackbarHostState.showSnackbar(effect.message)
                }
                FeedEffect.NavigateToFeedManagement -> onNavigateToFeedManagement()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feed_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.feed_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onEvent(FeedEvent.Refresh) }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.feed_refresh)
                        )
                    }
                    IconButton(onClick = { viewModel.onEvent(FeedEvent.ClearHistory) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.feed_clear)
                        )
                    }
                    IconButton(onClick = { viewModel.onEvent(FeedEvent.ManageSources) }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.feed_manage_sources)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        FeedContent(
            state = state,
            onEvent = viewModel::onEvent,
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedItemRow(
    item: FeedItem,
    formatter: DateTimeFormatter,
    onClick: () -> Unit,
    onMarkAsRead: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorited: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.mangaTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (item.isRead) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = item.chapterName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${item.sourceName} · ${formatter.format(item.timestamp)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isFavorited) {
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = stringResource(R.string.feed_in_library),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        if (!item.isRead) {
            IconButton(onClick = onMarkAsRead, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.feed_mark_as_read),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DiscoveryModeChips(
    selected: FeedDiscoveryMode,
    onSelect: (FeedDiscoveryMode) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(FeedDiscoveryMode.entries.toList()) { mode ->
            androidx.compose.material3.FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = {
                    Text(
                        when (mode) {
                            FeedDiscoveryMode.LATEST -> stringResource(R.string.feed_mode_latest)
                            FeedDiscoveryMode.TRENDING -> stringResource(R.string.feed_mode_trending)
                            FeedDiscoveryMode.RANDOM -> stringResource(R.string.feed_mode_random)
                        }
                    )
                },
            )
        }
    }
}
