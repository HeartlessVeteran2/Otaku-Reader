package app.otakureader.feature.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.sourceapi.Filter
import app.otakureader.sourceapi.isActive
import app.otakureader.sourceapi.SourceManga
import coil3.compose.AsyncImage

/**
 * Browse screen for discovering manga from various sources.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    onMangaClick: (sourceId: String, mangaUrl: String) -> Unit,
    onInstallExtensionClick: () -> Unit,
    onGlobalSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle effects
    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is BrowseEffect.NavigateToMangaDetail -> {
                    onMangaClick(effect.sourceId, effect.mangaUrl)
                }
                is BrowseEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Browse") },
                actions = {
                    IconButton(onClick = onGlobalSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onInstallExtensionClick) {
                Icon(Icons.Default.Add, contentDescription = "Install Extension")
            }
        }
    ) { paddingValues ->
        BrowseContent(
            state = state,
            onEvent = viewModel::onEvent,
            modifier = Modifier.padding(paddingValues)
        )
    }

    // Show filter sheet
    if (state.showFilterSheet && state.activeFilters.filters.isNotEmpty()) {
        SourceFilterSheet(
            filters = state.activeFilters,
            onFilterUpdate = { index, filter ->
                viewModel.onEvent(BrowseEvent.UpdateFilter(index, filter))
            },
            onReset = { viewModel.onEvent(BrowseEvent.ResetFilters) },
            onApply = { viewModel.onEvent(BrowseEvent.ApplyFilters) },
            onDismiss = { viewModel.onEvent(BrowseEvent.ToggleFilterSheet) }
        )
    }
}

@Composable
private fun BrowseContent(
    state: BrowseState,
    onEvent: (BrowseEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Search bar with filter button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { onEvent(BrowseEvent.OnSearchQueryChange(it)) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search manga...") },
                trailingIcon = {
                    IconButton(onClick = { onEvent(BrowseEvent.Search) }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
                singleLine = true
            )

            // Filter button - shown when a source has filters
            if (state.availableFilters.filters.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                FilterButton(
                    activeCount = countActiveFilters(state.activeFilters),
                    onClick = { onEvent(BrowseEvent.ToggleFilterSheet) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.isSearching) {
            // Show search results
            SearchResultsContent(
                results = state.searchResults,
                onMangaClick = { onEvent(BrowseEvent.OnMangaClick(it)) },
                onLoadMore = { onEvent(BrowseEvent.LoadNextPage) },
                hasNextPage = state.hasNextPage,
                isLoading = state.isLoading
            )
        } else {
            // Show sources and popular manga
            if (state.sources.isEmpty()) {
                EmptySourcesContent()
            } else {
                SourcesContent(
                    sources = state.sources,
                    currentSourceId = state.currentSourceId,
                    popularManga = state.popularManga,
                    onSourceSelect = { onEvent(BrowseEvent.SelectSource(it)) },
                    onMangaClick = { onEvent(BrowseEvent.OnMangaClick(it)) },
                    onLoadMore = { onEvent(BrowseEvent.LoadNextPage) },
                    hasNextPage = state.hasNextPage,
                    isLoading = state.isLoading
                )
            }
        }

        // Show error if any
        state.error?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun FilterButton(
    activeCount: Int,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Text("Filters")
        if (activeCount > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Badge { Text("$activeCount") }
        }
    }
}

/**
 * Count the number of filters that have been changed from their default state.
 */
private fun countActiveFilters(filters: app.otakureader.sourceapi.FilterList): Int {
    return filters.filters.count { filter -> filter.isActive() }
}
@Composable
private fun SourcesContent(
    sources: List<String>,
    currentSourceId: String?,
    popularManga: List<SourceManga>,
    onSourceSelect: (String) -> Unit,
    onMangaClick: (SourceManga) -> Unit,
    onLoadMore: () -> Unit,
    hasNextPage: Boolean,
    isLoading: Boolean
) {
    Column {
        // Source filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sources) { sourceId ->
                FilterChip(
                    selected = sourceId == currentSourceId,
                    onClick = { onSourceSelect(sourceId) },
                    label = { Text(sourceId.substringAfterLast(".").take(20)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (currentSourceId == null) {
            // Show prompt to select a source
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select a source to browse manga",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else if (isLoading && popularManga.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Show manga grid
            MangaGrid(
                manga = popularManga,
                onMangaClick = onMangaClick,
                onLoadMore = onLoadMore,
                hasNextPage = hasNextPage,
                isLoading = isLoading
            )
        }
    }
}

@Composable
private fun MangaGrid(
    manga: List<SourceManga>,
    onMangaClick: (SourceManga) -> Unit,
    onLoadMore: () -> Unit,
    hasNextPage: Boolean,
    isLoading: Boolean
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(manga) { mangaItem ->
            MangaCard(
                manga = mangaItem,
                onClick = { onMangaClick(mangaItem) }
            )
        }

        if (hasNextPage || isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            text = "Load more",
                            modifier = Modifier.clickable { onLoadMore() },
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaCard(
    manga: SourceManga,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            // Manga cover
            AsyncImage(
                model = manga.thumbnailUrl,
                contentDescription = manga.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f),
                contentScale = ContentScale.Crop
            )

            // Manga title
            Text(
                text = manga.title,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchResultsContent(
    results: List<SourceManga>,
    onMangaClick: (SourceManga) -> Unit,
    onLoadMore: () -> Unit,
    hasNextPage: Boolean,
    isLoading: Boolean
) {
    if (results.isEmpty() && !isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No results found",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        MangaGrid(
            manga = results,
            onMangaClick = onMangaClick,
            onLoadMore = onLoadMore,
            hasNextPage = hasNextPage,
            isLoading = isLoading
        )
    }
}

@Composable
private fun EmptySourcesContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No sources installed",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap + to install a Tachiyomi extension",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
