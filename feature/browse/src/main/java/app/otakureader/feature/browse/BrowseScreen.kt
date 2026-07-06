package app.otakureader.feature.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.core.ui.theme.LocalOtakuColors
import app.otakureader.feature.feed.FeedContent
import app.otakureader.feature.feed.FeedEffect
import app.otakureader.feature.feed.FeedEvent
import app.otakureader.feature.feed.FeedViewModel
import app.otakureader.feature.migration.MigrationEntryContent
import app.otakureader.feature.migration.MigrationEntryEffect
import app.otakureader.feature.migration.MigrationEntryEvent
import app.otakureader.feature.migration.MigrationEntryItem
import app.otakureader.feature.migration.MigrationEntryViewModel
import app.otakureader.sourceapi.Filter
import app.otakureader.sourceapi.MangaSource
import app.otakureader.sourceapi.SourceManga
import app.otakureader.sourceapi.isActive
import app.otakureader.sourceapi.toSourceId
import coil3.compose.AsyncImage
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Browse screen — Feed | Sources | Extensions | Migrate tab layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    onMangaClick: (sourceId: String, mangaUrl: String) -> Unit,
    onGlobalSearchClick: () -> Unit = {},
    onOpdsClick: () -> Unit = {},
    onNavigateToLibrary: (() -> Unit)? = null,
    // Feed tab callbacks
    onNavigateToReader: (mangaId: Long, chapterId: Long) -> Unit = { _, _ -> },
    onNavigateToFeedManagement: () -> Unit = {},
    // Extensions tab callbacks
    onNavigateToExtensionRepositories: () -> Unit = {},
    onNavigateToExtensionDetail: (packageName: String) -> Unit = {},
    // Migrate tab callback — navigate to the migration wizard with selected manga
    onStartMigration: (List<Long>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is BrowseEffect.NavigateToMangaDetail -> onMangaClick(effect.sourceId, effect.mangaUrl)
                is BrowseEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is BrowseEffect.NavigateToLibrary -> onNavigateToLibrary?.invoke()
            }
        }
    }

    val tabs = BrowseTab.entries
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    // Keep pager and state in sync
    LaunchedEffect(state.selectedTab) {
        val target = tabs.indexOf(state.selectedTab)
        if (target >= 0 && pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val tab = tabs.getOrNull(page)
            if (tab != null && tab != state.selectedTab) {
                viewModel.onEvent(BrowseEvent.SelectTab(tab))
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
                    },
                )
            } else {
                var overflowExpanded by remember { mutableStateOf(false) }
                TopAppBar(
                    title = { Text(stringResource(R.string.browse_title)) },
                    actions = {
                        IconButton(onClick = onOpdsClick) {
                            Icon(Icons.Default.Language, contentDescription = stringResource(R.string.browse_opds_catalogs))
                        }
                        IconButton(onClick = onGlobalSearchClick) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.browse_search))
                        }
                        if (state.selectedTab == BrowseTab.SOURCES) {
                            IconButton(onClick = { viewModel.onEvent(BrowseEvent.ShowLanguageDialog) }) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = stringResource(R.string.browse_language_filter)
                                )
                            }
                        }
                        if (state.selectedTab == BrowseTab.SOURCES || state.selectedTab == BrowseTab.EXTENSIONS) {
                            Box {
                                IconButton(onClick = { overflowExpanded = true }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.browse_more_options)
                                    )
                                }
                                DropdownMenu(
                                    expanded = overflowExpanded,
                                    onDismissRequest = { overflowExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (state.showNsfw) {
                                                    stringResource(R.string.browse_hide_nsfw)
                                                } else {
                                                    stringResource(R.string.browse_show_nsfw)
                                                }
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (state.showNsfw) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            viewModel.onEvent(BrowseEvent.ToggleNsfwFilter)
                                            overflowExpanded = false
                                        }
                                    )
                                    if (state.selectedTab == BrowseTab.SOURCES) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.browse_manage_sources)) },
                                            leadingIcon = {
                                                Icon(Icons.Default.FilterList, contentDescription = null)
                                            },
                                            onClick = {
                                                viewModel.onEvent(BrowseEvent.ShowSourcesFilterDialog)
                                                overflowExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.isBulkSelectionMode) {
                FloatingActionButton(onClick = { viewModel.onEvent(BrowseEvent.AddSelectedToLibrary) }) {
                    Icon(Icons.Default.LibraryAdd, contentDescription = stringResource(R.string.browse_add_to_library))
                }
            }
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // ── Tab Row ──────────────────────────────────────────────────────────────────
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 0.dp,
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                when (tab) {
                                    BrowseTab.FEED -> stringResource(R.string.browse_tab_feed)
                                    BrowseTab.SOURCES -> stringResource(R.string.browse_tab_sources)
                                    BrowseTab.EXTENSIONS -> stringResource(R.string.browse_tab_extensions)
                                    BrowseTab.MIGRATE -> stringResource(R.string.browse_tab_migrate)
                                }
                            )
                        },
                    )
                }
            }

            // ── Pager content ──────────────────────────────────────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = state.currentSourceId == null,
            ) { page ->
                when (tabs.getOrNull(page)) {
                    BrowseTab.FEED -> {
                        val feedVm: FeedViewModel = hiltViewModel()
                        val feedState by feedVm.state.collectAsStateWithLifecycle()
                        LaunchedEffect(feedVm) {
                            feedVm.effect.collectLatest { effect ->
                                when (effect) {
                                    is FeedEffect.NavigateToReader ->
                                        onNavigateToReader(effect.mangaId, effect.chapterId)
                                    is FeedEffect.ShowSnackbar ->
                                        snackbarHostState.showSnackbar(effect.message)
                                    FeedEffect.NavigateToFeedManagement ->
                                        onNavigateToFeedManagement()
                                }
                            }
                        }
                        FeedContent(
                            state = feedState,
                            onEvent = feedVm::onEvent,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    BrowseTab.SOURCES -> SourcesTabContent(
                        state = state,
                        onEvent = viewModel::onEvent,
                    )
                    BrowseTab.EXTENSIONS -> {
                        ExtensionsTabBody(
                            onNavigateToRepositories = onNavigateToExtensionRepositories,
                            onNavigateToExtensionDetail = onNavigateToExtensionDetail,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    BrowseTab.MIGRATE -> {
                        val migVm: MigrationEntryViewModel = hiltViewModel()
                        val migState by migVm.state.collectAsStateWithLifecycle()
                        val migFiltered = remember(migState.mangaList, migState.searchQuery) {
                            migVm.filteredList(migState)
                        }
                        LaunchedEffect(migVm) {
                            migVm.effect.collectLatest { effect ->
                                when (effect) {
                                    is MigrationEntryEffect.NavigateToMigration ->
                                        onStartMigration(effect.selectedMangaIds)
                                    MigrationEntryEffect.NavigateBack -> { /* embedded — no back */ }
                                    is MigrationEntryEffect.ShowError ->
                                        snackbarHostState.showSnackbar(effect.message)
                                }
                            }
                        }
                        MigrationEntryContent(
                            state = migState,
                            filtered = migFiltered,
                            onEvent = migVm::onEvent,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    null -> {}
                }
            }
        }
    }

    // Filter sheet
    if (state.showFilterSheet && state.activeFilters.filters.isNotEmpty()) {
        SourceFilterSheet(
            filters = state.activeFilters,
            onFilterUpdate = { index, filter -> viewModel.onEvent(BrowseEvent.UpdateFilter(index, filter)) },
            onReset = { viewModel.onEvent(BrowseEvent.ResetFilters) },
            onApply = { viewModel.onEvent(BrowseEvent.ApplyFilters) },
            onDismiss = { viewModel.onEvent(BrowseEvent.ToggleFilterSheet) },
        )
    }

    // Set category dialog
    if (state.showSetCategoryDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(BrowseEvent.DismissSetCategoryDialog) },
            title = { Text(stringResource(R.string.source_set_category)) },
            text = {
                OutlinedTextField(
                    value = state.categoryDialogText,
                    onValueChange = { viewModel.onEvent(BrowseEvent.UpdateCategoryDialogText(it)) },
                    placeholder = { Text(stringResource(R.string.source_category_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(BrowseEvent.ConfirmSetCategory) }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(BrowseEvent.DismissSetCategoryDialog) }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    // Language filter dialog
    if (state.showLanguageDialog) {
        LanguageFilterDialog(
            availableLanguages = state.availableLanguages,
            enabledLanguages = state.enabledLanguages,
            onConfirm = { selected -> viewModel.onEvent(BrowseEvent.SetEnabledLanguages(selected)) },
            onDismiss = { viewModel.onEvent(BrowseEvent.DismissLanguageDialog) },
        )
    }

    // Manage sources dialog — per-source enable/disable, matches Komikku's SourcesFilterScreen
    if (state.showSourcesFilterDialog) {
        SourcesFilterDialog(
            sources = state.allSources,
            disabledSourceIds = state.disabledSourceIds,
            onToggleDisable = { sourceId -> viewModel.onEvent(BrowseEvent.ToggleDisableSource(sourceId)) },
            onDismiss = { viewModel.onEvent(BrowseEvent.DismissSourcesFilterDialog) },
        )
    }

    // Save search dialog
    if (state.showSaveSearchDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(BrowseEvent.HideSaveSearchDialog) },
            title = { Text(stringResource(R.string.browse_save_search)) },
            text = {
                OutlinedTextField(
                    value = state.saveSearchName,
                    onValueChange = { viewModel.onEvent(BrowseEvent.UpdateSaveSearchName(it)) },
                    placeholder = { Text(stringResource(R.string.browse_save_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(BrowseEvent.ConfirmSaveSearch) }) {
                    Text(stringResource(R.string.browse_save_search_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(BrowseEvent.HideSaveSearchDialog) }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// Language filter dialog — multi-select chips for source language filtering
// ────────────────────────────────────────────────────────────────────────────────

private val LANGUAGE_DIALOG_ROW_VERTICAL_PADDING = 4.dp
private val LANGUAGE_DIALOG_ICON_SIZE = 20.dp
private val LANGUAGE_DIALOG_ICON_TEXT_SPACING = 12.dp

@Composable
private fun LanguageFilterDialog(
    availableLanguages: List<String>,
    enabledLanguages: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(enabledLanguages) { mutableStateOf(enabledLanguages) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.browse_language_filter)) },
        text = {
            Column {
                if (availableLanguages.isEmpty()) {
                    Text(stringResource(R.string.browse_no_languages))
                } else {
                    availableLanguages.forEach { lang ->
                        val checked = lang in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = checked,
                                    role = Role.Checkbox,
                                    onValueChange = {
                                        selected = if (checked) selected - lang else selected + lang
                                    },
                                )
                                .padding(vertical = LANGUAGE_DIALOG_ROW_VERTICAL_PADDING),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (checked) {
                                Icon(
                                    Icons.Default.Done,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(LANGUAGE_DIALOG_ICON_SIZE),
                                )
                            } else {
                                Spacer(Modifier.size(LANGUAGE_DIALOG_ICON_SIZE))
                            }
                            Spacer(Modifier.width(LANGUAGE_DIALOG_ICON_TEXT_SPACING))
                            Text(lang.uppercase(Locale.ROOT), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/**
 * Lists every installed source (unfiltered by NSFW/language/disabled state) with a live
 * enable/disable toggle per row. Matches Komikku's SourcesFilterScreen — the one place a
 * source hidden from Browse stays reachable so disabling it is always reversible.
 */
@Composable
private fun SourcesFilterDialog(
    sources: List<MangaSource>,
    disabledSourceIds: Set<Long>,
    onToggleDisable: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.browse_manage_sources)) },
        text = {
            if (sources.isEmpty()) {
                Text(stringResource(R.string.browse_no_sources_message))
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = SOURCES_FILTER_DIALOG_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState()),
                ) {
                    sources.forEach { source ->
                        val sourceIdLong = source.id.toSourceId()
                        val enabled = sourceIdLong !in disabledSourceIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = enabled,
                                    role = Role.Checkbox,
                                    onValueChange = { onToggleDisable(sourceIdLong) },
                                )
                                .padding(vertical = LANGUAGE_DIALOG_ROW_VERTICAL_PADDING),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (enabled) {
                                Icon(
                                    Icons.Default.Done,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(LANGUAGE_DIALOG_ICON_SIZE),
                                )
                            } else {
                                Spacer(Modifier.size(LANGUAGE_DIALOG_ICON_SIZE))
                            }
                            Spacer(Modifier.width(LANGUAGE_DIALOG_ICON_TEXT_SPACING))
                            Text(source.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

private val SOURCES_FILTER_DIALOG_MAX_HEIGHT = 400.dp

// ────────────────────────────────────────────────────────────────────────────────
// Sources tab — source list + inline manga grid
// ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SourcesTabContent(
    state: BrowseState,
    onEvent: (BrowseEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        when {
            state.hasSearchResults || state.isSearching -> {
                // Search bar + results
                BrowseSearchBar(state = state, onEvent = onEvent)
                SearchResultsContent(
                    results = state.searchResults,
                    onMangaClick = { onEvent(BrowseEvent.OnMangaClick(it)) },
                    onLoadMore = { onEvent(BrowseEvent.LoadNextPage) },
                    hasNextPage = state.hasNextPage,
                    isLoading = state.isSearching || state.isLoading,
                    onMangaLongClick = { onEvent(BrowseEvent.LongClickManga(it)) },
                    favoritedMangaUrls = state.favoritedMangaUrls,
                )
            }
            state.currentSourceId != null -> {
                // Selected source: show manga grid with back button header
                SelectedSourceHeader(
                    source = state.sources.find { it.id == state.currentSourceId },
                    sourceId = state.currentSourceId,
                    onBack = { onEvent(BrowseEvent.ClearSourceSelection) },
                )
                BrowseSearchBar(state = state, onEvent = onEvent)
                if (state.isLoading && state.popularManga.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    MangaGrid(
                        manga = state.popularManga,
                        onMangaClick = { onEvent(BrowseEvent.OnMangaClick(it)) },
                        onLoadMore = { onEvent(BrowseEvent.LoadNextPage) },
                        hasNextPage = state.hasNextPage,
                        isLoading = state.isLoading,
                        currentSourceId = state.currentSourceId,
                        onMangaLongClick = { onEvent(BrowseEvent.LongClickManga(it)) },
                        favoritedMangaUrls = state.favoritedMangaUrls,
                    )
                }
                state.error?.let { error ->
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            state.sources.isEmpty() -> EmptySourcesContent()
            else -> {
                // Source browser: floating search + source list (search floats over the list)
                SourceListContent(state = state, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun BrowseSearchBar(
    state: BrowseState,
    onEvent: (BrowseEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { onEvent(BrowseEvent.OnSearchQueryChange(it)) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.browse_search_placeholder)) },
                trailingIcon = if (state.currentSourceId != null) {
                    {
                        IconButton(onClick = { onEvent(BrowseEvent.Search) }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.browse_search))
                        }
                    }
                } else null,
                singleLine = true,
            )
            if (state.searchQuery.isNotBlank() && state.currentSourceId != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = { onEvent(BrowseEvent.ShowSaveSearchDialog) }) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = stringResource(R.string.browse_save_search))
                }
            }
            if (state.currentSourceId != null && state.availableFilters.filters.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                FilterButton(activeCount = countActiveFilters(state.activeFilters), onClick = { onEvent(BrowseEvent.ToggleFilterSheet) })
            }
        }

        // Search scope selector — only show when a source is selected
        if (state.currentSourceId != null) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                BrowseSearchScope.entries.forEachIndexed { index, scope ->
                    SegmentedButton(
                        selected = state.searchScope == scope,
                        onClick = { onEvent(BrowseEvent.SetSearchScope(scope)) },
                        shape = SegmentedButtonDefaults.itemShape(index, BrowseSearchScope.entries.size),
                        label = {
                            Text(
                                when (scope) {
                                    BrowseSearchScope.SOURCES -> stringResource(R.string.browse_scope_sources)
                                    BrowseSearchScope.LIBRARY -> stringResource(R.string.browse_scope_library)
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedSourceHeader(
    source: MangaSource?,
    sourceId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.browse_back_to_sources))
        }
        val displayName = source?.name ?: sourceId.substringAfterLast(".")
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        source?.let {
            LanguageBadge(lang = it.lang)
            if (it.isNsfw) {
                Spacer(Modifier.width(4.dp))
                NsfwBadge()
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// Source list (browse mode — no source selected)
// ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SourceListContent(
    state: BrowseState,
    onEvent: (BrowseEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolve last-used MangaSources from the full source list
    val allSources = if (state.searchQuery.isBlank()) {
        state.sources
    } else {
        state.sources.filter {
            it.name.contains(state.searchQuery, ignoreCase = true) ||
                it.lang.contains(state.searchQuery, ignoreCase = true)
        }
    }
    val lastUsedSources = state.lastUsedSourceIds.mapNotNull { id -> allSources.find { it.id == id } }

    val pinnedSources = allSources.filter { it.id.toSourceId() in state.pinnedSourceIds }
    val unpinnedSources = allSources.filter { it.id.toSourceId() !in state.pinnedSourceIds }
    // Group unpinned sources by their assigned source category when one is set, otherwise by
    // language — matching Komikku's SourcesScreen, which shows language headers (English, Multi, …)
    // for uncategorized sources rather than a single flat list.
    val grouped: Map<String, List<MangaSource>> = unpinnedSources.groupBy { src ->
        state.sourceCategoryMap[src.id.toSourceId()]?.takeIf { it.isNotBlank() }
            ?: src.lang.uppercase(Locale.ROOT)
    }
    val categoryOrder = grouped.keys.sortedWith(compareBy { if (it.isBlank()) "" else it })

    val listState = rememberLazyListState()
    val isScrollingUp = listState.isScrollingUp()

    Column(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isScrollingUp,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            FloatingSourceSearchBox(
                searchQuery = state.searchQuery,
                onQueryChange = { onEvent(BrowseEvent.OnSearchQueryChange(it)) },
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            // Recent searches
            if (state.searchHistory.isNotEmpty()) {
                item(key = "search_history_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.browse_search_history),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { onEvent(BrowseEvent.ClearSearchHistory) }) {
                            Text(stringResource(R.string.filter_sheet_clear_all))
                        }
                    }
                }
                item(key = "search_history_row") {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.searchHistory, key = { it }) { query ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    onEvent(BrowseEvent.OnSearchQueryChange(query))
                                    onEvent(BrowseEvent.Search)
                                },
                                label = { Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { onEvent(BrowseEvent.DeleteSearchHistoryItem(query)) },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.browse_search_history_remove),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
    
            // Named saved searches — only shown when there are saved searches for any source
            if (state.namedSavedSearches.isNotEmpty()) {
                item(key = "named_searches_header") {
                    SourceSectionHeader(stringResource(R.string.browse_saved_searches))
                }
                item(key = "named_searches_row") {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(state.namedSavedSearches, key = { _, search -> "ns_${search.id}" }) { index, search ->
                            FilterChip(
                                selected = false,
                                onClick = { onEvent(BrowseEvent.ApplyNamedSavedSearch(search)) },
                                label = { Text(search.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (index > 0) {
                                            IconButton(
                                                onClick = { onEvent(BrowseEvent.MoveNamedSavedSearchUp(search.id)) },
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                                    contentDescription = stringResource(R.string.browse_move_saved_search_earlier),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                        if (index < state.namedSavedSearches.lastIndex) {
                                            IconButton(
                                                onClick = { onEvent(BrowseEvent.MoveNamedSavedSearchDown(search.id)) },
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = stringResource(R.string.browse_move_saved_search_later),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { onEvent(BrowseEvent.DeleteNamedSavedSearch(search.id)) },
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(R.string.browse_delete_saved_search),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
    
            // Last used section
            if (lastUsedSources.isNotEmpty()) {
                item(key = "last_used_header") {
                    SourceSectionHeader(stringResource(R.string.browse_last_used))
                }
                items(lastUsedSources, key = { "last_${it.id}" }) { source ->
                    SourceRow(
                        source = source,
                        isPinned = source.id.toSourceId() in state.pinnedSourceIds,
                        categoryMap = state.sourceCategoryMap,
                        iconUrl = source.id.toLongOrNull()?.let { state.sourceIconUrls[it] },
                        onSelect = { onEvent(BrowseEvent.SelectSource(source.id)) },
                        onLatest = { onEvent(BrowseEvent.SelectSource(source.id, loadLatest = true)) },
                        onTogglePin = { onEvent(BrowseEvent.TogglePinSource(source.id.toSourceId())) },
                        onOpenSetCategory = { sid, cat -> onEvent(BrowseEvent.OpenSetCategoryDialog(sid, cat)) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
    
            // Pinned section
            if (pinnedSources.isNotEmpty()) {
                item(key = "pinned_header") {
                    SourceSectionHeader(stringResource(R.string.source_pinned_section))
                }
                items(pinnedSources, key = { "pinned_${it.id}" }) { source ->
                    SourceRow(
                        source = source,
                        isPinned = true,
                        categoryMap = state.sourceCategoryMap,
                        iconUrl = source.id.toLongOrNull()?.let { state.sourceIconUrls[it] },
                        onSelect = { onEvent(BrowseEvent.SelectSource(source.id)) },
                        onLatest = { onEvent(BrowseEvent.SelectSource(source.id, loadLatest = true)) },
                        onTogglePin = { onEvent(BrowseEvent.TogglePinSource(source.id.toSourceId())) },
                        onOpenSetCategory = { sid, cat -> onEvent(BrowseEvent.OpenSetCategoryDialog(sid, cat)) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
    
            // Unpinned sources, grouped by category
            for (category in categoryOrder) {
                val catSources = grouped[category] ?: continue
                if (category.isNotBlank()) {
                    item(key = "cat_header_$category") {
                        SourceSectionHeader(category)
                    }
                } else if (pinnedSources.isNotEmpty() || lastUsedSources.isNotEmpty()) {
                    // "All sources" header when there are other sections above
                    item(key = "all_sources_header") {
                        SourceSectionHeader(stringResource(R.string.browse_all_sources))
                    }
                }
                items(catSources, key = { "src_${it.id}" }) { source ->
                    SourceRow(
                        source = source,
                        isPinned = false,
                        categoryMap = state.sourceCategoryMap,
                        iconUrl = source.id.toLongOrNull()?.let { state.sourceIconUrls[it] },
                        onSelect = { onEvent(BrowseEvent.SelectSource(source.id)) },
                        onLatest = { onEvent(BrowseEvent.SelectSource(source.id, loadLatest = true)) },
                        onTogglePin = { onEvent(BrowseEvent.TogglePinSource(source.id.toSourceId())) },
                        onOpenSetCategory = { sid, cat -> onEvent(BrowseEvent.OpenSetCategoryDialog(sid, cat)) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        } // end LazyColumn
    } // end Column
}

@Composable
private fun SourceSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, bottom = 4.dp, end = 16.dp),
    )
}

// ────────────────────────────────────────────────────────────────────────────────
// SourceRow composable
// ────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceRow(
    source: MangaSource,
    isPinned: Boolean,
    categoryMap: Map<Long, String>,
    onSelect: () -> Unit,
    onLatest: () -> Unit,
    onTogglePin: () -> Unit,
    onOpenSetCategory: (Long, String) -> Unit,
    iconUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val sourceIdLong = source.id.toSourceId()
    val currentCategory = categoryMap[sourceIdLong] ?: ""

    Box {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onSelect,
                    onLongClick = { menuExpanded = true },
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (iconUrl != null) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = source.name,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                )
            } else {
                SourceAvatar(name = source.name)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LanguageBadge(lang = source.lang)
                    if (source.isNsfw) NsfwBadge()
                }
            }

            if (source.supportsLatest) {
                TextButton(
                    onClick = onLatest,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(stringResource(R.string.browse_source_latest), style = MaterialTheme.typography.labelSmall)
                }
            }

            IconButton(onClick = onTogglePin, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (isPinned) stringResource(R.string.source_unpin) else stringResource(R.string.source_pin),
                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(if (isPinned) stringResource(R.string.source_unpin) else stringResource(R.string.source_pin)) },
                leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                onClick = { menuExpanded = false; onTogglePin() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.source_set_category)) },
                onClick = { menuExpanded = false; onOpenSetCategory(sourceIdLong, currentCategory) },
            )
        }
    }
}

private const val AVATAR_HUE_MASK = 0xFFFFFF
private const val AVATAR_HUE_DEGREES = 360

@Composable
private fun SourceAvatar(name: String, modifier: Modifier = Modifier) {
    val initial = name.firstOrNull()?.uppercaseChar() ?: '?'
    // Derive a stable color from the source name hash
    val hue = (name.hashCode().and(AVATAR_HUE_MASK).toLong() % AVATAR_HUE_DEGREES).toFloat()
        .let { if (it < 0) it + AVATAR_HUE_DEGREES else it }
    val bgColor = Color.hsl(hue, 0.5f, 0.4f)
    Box(
        modifier = modifier
            .size(40.dp)
            .background(color = bgColor, shape = RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial.toString(),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LanguageBadge(lang: String, modifier: Modifier = Modifier) {
    Badge(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(lang.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun NsfwBadge(modifier: Modifier = Modifier) {
    Badge(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(stringResource(R.string.browse_source_nsfw_badge), style = MaterialTheme.typography.labelSmall)
    }
}

// ────────────────────────────────────────────────────────────────────────────────
// Manga grid + cards
// ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun FilterButton(activeCount: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(stringResource(R.string.browse_filters))
        if (activeCount > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Badge { Text("$activeCount") }
        }
    }
}

private fun countActiveFilters(filters: app.otakureader.sourceapi.FilterList): Int =
    filters.filters.count { filter -> filter.isActive() }

/** Returns true when a source ID string suggests manhwa/webtoon content. */
private fun isManhwaSource(sourceId: String): Boolean {
    val normalized = sourceId.lowercase()
    return normalized.contains("manhwa") || normalized.contains("webtoon") ||
        normalized.contains("korean") || normalized.contains("toon") ||
        normalized.contains("naver") || normalized.contains("kakao") ||
        normalized.contains("lezhin")
}

@Composable
private fun MangaGrid(
    manga: List<SourceManga>,
    onMangaClick: (SourceManga) -> Unit,
    onLoadMore: () -> Unit,
    hasNextPage: Boolean,
    isLoading: Boolean,
    currentSourceId: String? = null,
    onMangaLongClick: ((SourceManga) -> Unit)? = null,
    favoritedMangaUrls: Set<String> = emptySet(),
) {
    val otaku = LocalOtakuColors.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(manga, key = { it.url }) { mangaItem ->
            MangaCard(
                manga = mangaItem,
                onClick = { onMangaClick(mangaItem) },
                currentSourceId = currentSourceId,
                onLongClick = onMangaLongClick?.let { { it(mangaItem) } },
                isInLibrary = mangaItem.url in favoritedMangaUrls,
            )
        }
        if (hasNextPage || isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            text = stringResource(R.string.browse_load_more),
                            modifier = Modifier.clickable { onLoadMore() },
                            color = otaku.accent,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MangaCard(
    manga: SourceManga,
    onClick: () -> Unit,
    currentSourceId: String? = null,
    onLongClick: (() -> Unit)? = null,
    isInLibrary: Boolean = false,
) {
    val isManga = currentSourceId == null || !isManhwaSource(currentSourceId)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = manga.thumbnailUrl,
                    contentDescription = manga.title,
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.7f),
                    contentScale = ContentScale.Crop,
                )
                if (currentSourceId != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(
                                color = if (isManga) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = if (isManga) stringResource(R.string.browse_manga_type_badge)
                            else stringResource(R.string.browse_manhwa_type_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isManga) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                if (isInLibrary) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(6.dp),
                            )
                            .padding(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = stringResource(R.string.browse_in_library_badge),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Text(
                text = manga.title,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
    isLoading: Boolean,
    onMangaLongClick: ((SourceManga) -> Unit)? = null,
    favoritedMangaUrls: Set<String> = emptySet(),
) {
    if (results.isEmpty() && !isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.browse_no_results),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    } else {
        MangaGrid(
            manga = results,
            onMangaClick = onMangaClick,
            onLoadMore = onLoadMore,
            hasNextPage = hasNextPage,
            isLoading = isLoading,
            onMangaLongClick = onMangaLongClick,
            favoritedMangaUrls = favoritedMangaUrls,
        )
    }
}

@Composable
private fun EmptySourcesContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.browse_no_sources_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.browse_no_sources_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun FloatingSourceSearchBox(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.browse_search_placeholder)) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = if (searchQuery.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.browse_clear_search))
                }
            }
        } else null,
        shape = RoundedCornerShape(24.dp),
        singleLine = true,
    )
}

@Composable
private fun LazyListState.isScrollingUp(): Boolean {
    var isScrollingUp by remember { mutableStateOf(true) }
    var previousIndex by remember { mutableIntStateOf(firstVisibleItemIndex) }
    LaunchedEffect(this) {
        snapshotFlow { firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { current ->
                if (previousIndex != current) {
                    isScrollingUp = previousIndex > current
                    previousIndex = current
                }
            }
    }
    return isScrollingUp
}
