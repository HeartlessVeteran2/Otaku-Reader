package app.otakureader.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.domain.model.MangaRecommendation
import coil3.compose.AsyncImage
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onMangaClick: (Long) -> Unit,
    onNavigateToUpdates: () -> Unit,
    onNavigateToBrowse: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToMigration: (List<Long>) -> Unit = {},
    onNavigateToCategoryManagement: () -> Unit = {},
    onRecommendationClick: (String) -> Unit = { onNavigateToBrowse() },
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    
    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is LibraryEffect.NavigateToManga -> onMangaClick(effect.mangaId)
                is LibraryEffect.ShowError -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(effect.message)
                    }
                }
                is LibraryEffect.NavigateToMigration -> {
                    onNavigateToMigration(effect.selectedMangaIds)
                }
                is LibraryEffect.NavigateToRecommendationSearch -> {
                    onRecommendationClick(effect.title)
                }
                else -> {}
            }
        }
    }
    
    Scaffold(
        topBar = {
            when {
                // ── Selection mode: action buttons ──────────────────────────
                state.selectedManga.isNotEmpty() -> TopAppBar(
                    title = {
                        Text(stringResource(R.string.library_selected_count, state.selectedManga.size))
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.onEvent(LibraryEvent.ClearSelection) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.library_deselect_all))
                        }
                    },
                    actions = {
                        // Mark all selected as read
                        IconButton(onClick = { viewModel.onEvent(LibraryEvent.MarkSelectedAsRead) }) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.library_mark_selected_read)
                            )
                        }
                        // Mark all selected as unread
                        IconButton(onClick = { viewModel.onEvent(LibraryEvent.MarkSelectedAsUnread) }) {
                            Icon(
                                Icons.Default.RadioButtonUnchecked,
                                contentDescription = stringResource(R.string.library_mark_selected_unread)
                            )
                        }
                        // Download selected
                        IconButton(onClick = { viewModel.onEvent(LibraryEvent.DownloadSelected) }) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = stringResource(R.string.library_download_selected)
                            )
                        }
                        // Remove selected from library
                        IconButton(onClick = { viewModel.onEvent(LibraryEvent.RemoveSelectedFromLibrary) }) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = stringResource(R.string.library_remove_selected)
                            )
                        }
                    }
                )

                // ── Search mode: inline search field ────────────────────────
                state.showSearchBar -> TopAppBar(
                    title = {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.onEvent(LibraryEvent.OnSearchQueryChange(it)) },
                            placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {}),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.onEvent(LibraryEvent.ToggleSearchBar) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.library_search_close))
                        }
                    }
                )

                // ── Normal mode ──────────────────────────────────────────────
                else -> TopAppBar(
                    title = { Text(stringResource(R.string.library_title)) },
                    actions = {
                        IconButton(onClick = { viewModel.onEvent(LibraryEvent.ToggleSearchBar) }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.library_search))
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.library_more))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_downloads)) },
                                onClick = {
                                    showMenu = false
                                    onNavigateToDownloads()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_settings)) },
                                onClick = {
                                    showMenu = false
                                    onNavigateToSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_filter_has_notes)) },
                                onClick = {
                                    showMenu = false
                                    viewModel.onEvent(LibraryEvent.FilterHasNotes(!state.filterHasNotes))
                                },
                                trailingIcon = {
                                    Checkbox(
                                        checked = state.filterHasNotes,
                                        onCheckedChange = { checked ->
                                            showMenu = false
                                            viewModel.onEvent(LibraryEvent.FilterHasNotes(checked))
                                        }
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_manage_categories)) },
                                onClick = {
                                    showMenu = false
                                    onNavigateToCategoryManagement()
                                }
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            LibraryBottomNavigation(
                selectedRoute = "library",
                onNavigateToLibrary = { /* Already here */ },
                onNavigateToUpdates = onNavigateToUpdates,
                onNavigateToBrowse = onNavigateToBrowse,
                onNavigateToHistory = onNavigateToHistory,
                onNavigateToStatistics = onNavigateToStatistics,
                onNavigateToSettings = onNavigateToSettings,
                newUpdatesCount = state.newUpdatesCount
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LibraryContent(
            state = state,
            onEvent = viewModel::onEvent,
            modifier = Modifier.padding(padding)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryContent(
    state: LibraryState,
    onEvent: (LibraryEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onEvent(LibraryEvent.Refresh) },
        modifier = modifier.fillMaxSize()
    ) {
        when {
            state.isLoading && state.mangaList.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            state.mangaList.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    EmptyLibraryMessage(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            else -> {
                MangaGrid(
                    state = state,
                    onEvent = onEvent
                )
            }
        }
    }
}

@Composable
private fun MangaGrid(
    state: LibraryState,
    onEvent: (LibraryEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(state.gridSize),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // "For You" recommendations header (full-width span)
        item(span = { GridItemSpan(maxLineSpan) }) {
            ForYouSection(
                recommendations = state.recommendations,
                isLoading = state.isLoadingRecommendations,
                error = state.recommendationsError,
                hasEnoughManga = state.hasEnoughMangaForRecommendations,
                onRefresh = { onEvent(LibraryEvent.RefreshRecommendations) },
                onRecommendationClick = { onEvent(LibraryEvent.OnRecommendationClick(it)) },
                onDismiss = { onEvent(LibraryEvent.DismissRecommendation(it)) }
            )
        }

        // Category filter chips (full-width span)
        item(span = { GridItemSpan(maxLineSpan) }) {
            CategoryFilterChips(
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                onCategorySelected = { onEvent(LibraryEvent.OnCategorySelected(it)) },
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Manga grid items
        items(
            items = state.mangaList,
            key = { it.id }
        ) { manga ->
            MangaGridItem(
                manga = manga,
                showBadges = state.showBadges,
                onClick = { onEvent(LibraryEvent.OnMangaClick(manga.id)) },
                onLongClick = { onEvent(LibraryEvent.OnMangaLongClick(manga.id)) }
            )
        }
    }
}

@Composable
private fun ForYouSection(
    recommendations: List<MangaRecommendation>,
    isLoading: Boolean,
    error: String?,
    hasEnoughManga: Boolean,
    onRefresh: () -> Unit,
    onRecommendationClick: (MangaRecommendation) -> Unit,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.library_for_you_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (hasEnoughManga && !isLoading) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.library_recommendations_refresh)
                    )
                }
            }
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            !hasEnoughManga -> {
                NotEnoughMangaCard(modifier = Modifier.padding(horizontal = 16.dp))
            }
            error != null -> {
                RecommendationsErrorCard(
                    error = error,
                    onRetry = onRefresh,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            recommendations.isEmpty() -> {
                // Loaded successfully but nothing returned – show nothing extra
            }
            else -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = recommendations,
                        key = { it.title }
                    ) { recommendation ->
                        RecommendationCard(
                            recommendation = recommendation,
                            onClick = { onRecommendationClick(recommendation) },
                            onDismiss = { onDismiss(recommendation.title) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun NotEnoughMangaCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Filter active",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.library_recommendations_not_enough_manga),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecommendationsErrorCard(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.library_recommendations_error_retry))
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: MangaRecommendation,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box {
            Column {
                AsyncImage(
                    model = recommendation.thumbnailUrl,
                    contentDescription = stringResource(
                        R.string.library_recommendations_thumbnail,
                        recommendation.title
                    ),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                )
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = recommendation.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (recommendation.reasonExplanation.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.library_recommendations_reason_prefix,
                                recommendation.reasonExplanation
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            // Dismiss button with semi-transparent background for visibility over thumbnails
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = DISMISS_BUTTON_BACKGROUND_ALPHA),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.library_recommendations_dismiss),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun MangaGridItem(
    manga: LibraryMangaItem,
    showBadges: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column {
            Box {
                AsyncImage(
                    model = manga.thumbnailUrl,
                    contentDescription = manga.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                )
                
                if (showBadges && manga.unreadCount > 0) {
                    UnreadBadge(
                        count = manga.unreadCount,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }
            
            Text(
                text = manga.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun UnreadBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .size(24.dp)
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) stringResource(R.string.library_badge_overflow) else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun EmptyLibraryMessage(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = stringResource(R.string.library_empty_icon_description),
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.library_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private const val DISMISS_BUTTON_BACKGROUND_ALPHA = 0.72f

@Composable
private fun CategoryFilterChips(
    categories: List<CategoryItem>,
    selectedCategory: Long?,
    onCategorySelected: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        // "All" chip
        item {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategorySelected(null) },
                label = { Text(stringResource(R.string.library_category_all)) }
            )
        }
        
        // Category chips
        items(
            items = categories,
            key = { it.id }
        ) { category ->
            FilterChip(
                selected = selectedCategory == category.id,
                onClick = { onCategorySelected(category.id) },
                label = { Text(category.name) }
            )
        }
    }
}

@Composable
private fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier
    )
}

