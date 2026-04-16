package app.otakureader.feature.browse

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    onOpdsClick: () -> Unit = {},
    onNavigateToLibrary: (() -> Unit)? = null,
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
                is BrowseEffect.NavigateToLibrary -> {
                    // Navigate to library after bulk add
                    onNavigateToLibrary?.invoke()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (state.isBulkSelectionMode) {
                TopAppBar(
                    title = { Text("${state.selectedManga.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.onEvent(BrowseEvent.ExitBulkSelectionMode) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.browse_exit_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.onEvent(BrowseEvent.ClearSelection) }) {
                            Icon(Icons.Default.ClearAll, contentDescription = stringResource(R.string.browse_clear_selection))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.browse_title)) },
                    actions = {
                        IconButton(onClick = onOpdsClick) {
                            Icon(Icons.Default.Storage, contentDescription = stringResource(R.string.browse_opds_catalogs))
                        }
                        IconButton(onClick = onGlobalSearchClick) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.browse_search))
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.isBulkSelectionMode) {
                FloatingActionButton(
                    onClick = { viewModel.onEvent(BrowseEvent.AddSelectedToLibrary) }
                ) {
                    Icon(Icons.Default.LibraryAdd, contentDescription = stringResource(R.string.browse_add_to_library))
                }
            } else {
                FloatingActionButton(onClick = onInstallExtensionClick) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.browse_install_extension))
                }
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
                placeholder = { Text(stringResource(R.string.browse_search_placeholder)) },
                trailingIcon = {
                    IconButton(onClick = { onEvent(BrowseEvent.Search) }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.browse_search))
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
                    isLoading = state.isLoading,
                    sourceIntelligenceEnabled = state.sourceIntelligenceEnabled,
                    sourceIntelligence = state.sourceIntelligence,
                    isAnalyzingSource = state.isAnalyzingSource
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
        Text(stringResource(R.string.browse_filters))
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
    isLoading: Boolean,
    sourceIntelligenceEnabled: Boolean = false,
    sourceIntelligence: Map<String, String> = emptyMap(),
    isAnalyzingSource: Boolean = false
) {
    Column {
        // Source filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sources, key = { it }) { sourceId ->
                FilterChip(
                    selected = sourceId == currentSourceId,
                    onClick = { onSourceSelect(sourceId) },
                    label = { Text(sourceId.substringAfterLast(".").take(20)) }
                )
            }
        }

        // Source Intelligence card for the selected source
        if (sourceIntelligenceEnabled && currentSourceId != null) {
            val intelligence = sourceIntelligence[currentSourceId]
            when {
                intelligence != null -> {
                    SourceIntelligenceCard(
                        summary = intelligence,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                isAnalyzingSource -> {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.browse_source_intel_analyzing),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                    text = stringResource(R.string.browse_select_source),
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
        items(manga, key = { it.url }) { mangaItem ->
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
                            text = stringResource(R.string.browse_load_more),
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
                text = stringResource(R.string.browse_no_results),
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
                text = stringResource(R.string.browse_no_sources_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.browse_no_sources_message),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Card that shows the AI-generated quality analysis for the currently selected source.
 */
@Composable
private fun SourceIntelligenceCard(
    summary: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "✨",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = stringResource(R.string.browse_source_intel_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}
