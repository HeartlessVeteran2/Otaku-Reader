package app.otakureader.feature.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.core.ui.component.ErrorScreen
import app.otakureader.core.ui.component.LoadingScreen
import app.otakureader.sourceapi.SourceManga
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceMangaScreen(
    sourceId: String,
    onMangaClick: (mangaUrl: String, mangaTitle: String) -> Unit,
    onNavigateBack: () -> Unit,
    initialQuery: String = "",
    viewModel: SourceMangaViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(sourceId, initialQuery) {
        viewModel.setSourceId(sourceId, initialQuery)
    }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is SourceMangaEffect.NavigateToMangaDetail -> {
                    onMangaClick(effect.mangaUrl, effect.mangaTitle)
                }
                is SourceMangaEffect.ShowSnackbar -> {
                    // Snackbar shown via Scaffold snackbar host in parent if needed
                }
            }
        }
    }

    Scaffold(
        topBar = {
            if (state.isSearchMode) {
                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = state.searchQuery,
                            onQueryChange = { viewModel.onEvent(SourceMangaEvent.OnSearchQueryChange(it)) },
                            onSearch = { viewModel.onEvent(SourceMangaEvent.Search) },
                            expanded = false,
                            onExpandedChange = {},
                            placeholder = { Text(stringResource(R.string.browse_search_placeholder)) },
                            leadingIcon = {
                                IconButton(onClick = { viewModel.onEvent(SourceMangaEvent.CloseSearch) }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.browse_back),
                                    )
                                }
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        viewModel.onEvent(SourceMangaEvent.OnSearchQueryChange(""))
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.browse_clear_search))
                                    }
                                }
                            },
                        )
                    },
                    expanded = false,
                    onExpandedChange = {},
                    modifier = Modifier.fillMaxWidth(),
                ) {}
            } else {
                TopAppBar(
                    title = { Text(state.sourceName ?: sourceId) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.browse_back))
                        }
                    },
                    actions = {
                        if (state.supportsLatest) {
                            IconButton(onClick = { viewModel.onEvent(SourceMangaEvent.ToggleLatest) }) {
                                Icon(
                                    imageVector = if (state.isShowingLatest) Icons.Default.Whatshot else Icons.Default.NewReleases,
                                    contentDescription = stringResource(
                                        if (state.isShowingLatest) R.string.browse_show_popular
                                        else R.string.browse_source_latest
                                    ),
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.onEvent(SourceMangaEvent.Refresh) }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.browse_refresh))
                        }
                        IconButton(onClick = { viewModel.onEvent(SourceMangaEvent.EnterSearchMode) }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.browse_search))
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingScreen(modifier = Modifier.padding(paddingValues))
            state.error != null -> ErrorScreen(
                message = state.error ?: stringResource(R.string.browse_unknown_error),
                onRetry = { viewModel.onEvent(SourceMangaEvent.Refresh) },
                modifier = Modifier.padding(paddingValues)
            )
            state.manga.isEmpty() -> EmptyMangaView(
                onRefresh = { viewModel.onEvent(SourceMangaEvent.Refresh) },
                modifier = Modifier.padding(paddingValues)
            )
            else -> MangaGrid(
                manga = state.manga,
                onMangaClick = { manga ->
                    viewModel.onEvent(SourceMangaEvent.OnMangaClick(manga))
                },
                isLoadingMore = state.isLoadingMore,
                hasNextPage = state.hasNextPage,
                onLoadMore = { viewModel.onEvent(SourceMangaEvent.LoadNextPage) },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun EmptyMangaView(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.browse_no_manga_found),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.browse_pull_to_refresh),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.browse_refresh))
            }
        }
    }
}

@Composable
private fun MangaGrid(
    manga: List<SourceManga>,
    onMangaClick: (SourceManga) -> Unit,
    isLoadingMore: Boolean,
    hasNextPage: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(manga, key = { it.url }) { mangaItem ->
            SourceMangaCard(
                manga = mangaItem,
                onClick = { onMangaClick(mangaItem) }
            )
        }

        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        if (hasNextPage && !isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clickable { onLoadMore() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.browse_load_more),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun SourceMangaCard(
    manga: SourceManga,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(manga.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = manga.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        // Tonal backdrop so failed/missing covers show a neutral block
                        // instead of an empty hole in the grid.
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            Text(
                text = manga.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
