package app.otakureader.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.core.ui.component.ErrorScreen
import app.otakureader.core.ui.component.LoadingScreen
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    mangaId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToReader: (mangaId: Long, chapterId: Long) -> Unit,
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is DetailsContract.Effect.NavigateToReader -> {
                    onNavigateToReader(effect.mangaId, effect.chapterId)
                }
                is DetailsContract.Effect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is DetailsContract.Effect.ShowError -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                else -> { /* Handle other effects */ }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.manga?.title ?: "Manga Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onEvent(DetailsContract.Event.Refresh) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { /* TODO: Share */ }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.canStartReading) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (state.hasUnreadChapters) {
                            viewModel.onEvent(DetailsContract.Event.ContinueReading)
                        } else {
                            viewModel.onEvent(DetailsContract.Event.StartReading)
                        }
                    },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    text = {
                        Text(
                            if (state.hasUnreadChapters) "Continue Reading" else "Start Reading"
                        )
                    }
                )
            }
        }
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingScreen(modifier = Modifier.padding(paddingValues))
            state.error != null -> ErrorScreen(
                message = state.error ?: "Unknown error",
                onRetry = { viewModel.onEvent(DetailsContract.Event.Refresh) },
                modifier = Modifier.padding(paddingValues)
            )
            state.manga != null -> DetailsContent(
                state = state,
                onEvent = viewModel::onEvent,
                modifier = Modifier.padding(paddingValues)
            )
            else -> EmptyScreen(modifier = Modifier.padding(paddingValues))
        }
    }
}

@Composable
private fun DetailsContent(
    state: DetailsContract.State,
    onEvent: (DetailsContract.Event) -> Unit,
    modifier: Modifier = Modifier
) {
    val manga = state.manga ?: return

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            MangaHeader(
                manga = manga,
                isFavorite = state.isFavorite,
                onToggleFavorite = { onEvent(DetailsContract.Event.ToggleFavorite) }
            )
        }

        item {
            MangaDescription(
                description = manga.description,
                expanded = state.descriptionExpanded,
                onToggle = { onEvent(DetailsContract.Event.ToggleDescription) }
            )
        }

        item {
            HorizontalDivider()
        }

        item {
            ChapterListHeader(
                chapterCount = state.chapters.size,
                sortOrder = state.chapterSortOrder,
                onToggleSort = { onEvent(DetailsContract.Event.ToggleSortOrder) }
            )
        }

        items(state.sortedChapters, key = { it.id }) { chapter ->
            ChapterListItem(
                chapter = chapter,
                onClick = { onEvent(DetailsContract.Event.ChapterClick(chapter.id)) },
                onLongClick = { onEvent(DetailsContract.Event.ChapterLongClick(chapter.id)) }
            )
        }
    }
}

@Composable
private fun MangaHeader(
    manga: app.otakureader.domain.model.Manga,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth()) {
        AsyncImage(
            model = manga.thumbnailUrl,
            contentDescription = manga.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 120.dp, height = 180.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = manga.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            manga.author?.let { author ->
                Text(
                    text = "Author: $author",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            manga.artist?.let { artist ->
                Text(
                    text = "Artist: $artist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "Status: ${manga.status.displayText()}",
                style = MaterialTheme.typography.bodyMedium,
                color = manga.status.colorValue()
            )

            Spacer(modifier = Modifier.height(8.dp))

            FilledIconToggleButton(
                checked = isFavorite,
                onCheckedChange = { onToggleFavorite() }
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from library" else "Add to library"
                )
            }
        }
    }
}

@Composable
private fun MangaDescription(
    description: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (description.isNullOrBlank()) return

    Column(modifier = modifier) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis
        )

        if (description.length > 100) {
            TextButton(onClick = onToggle) {
                Text(if (expanded) "Show less" else "Show more")
            }
        }
    }
}

@Composable
private fun ChapterListHeader(
    chapterCount: Int,
    sortOrder: DetailsContract.ChapterSortOrder,
    onToggleSort: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$chapterCount Chapters",
            style = MaterialTheme.typography.titleMedium
        )

        TextButton(onClick = onToggleSort) {
            Text(
                when (sortOrder) {
                    DetailsContract.ChapterSortOrder.ASCENDING -> "↑ Ascending"
                    DetailsContract.ChapterSortOrder.DESCENDING -> "↓ Descending"
                }
            )
        }
    }
}

@Composable
private fun EmptyScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("No manga details available")
    }
}
