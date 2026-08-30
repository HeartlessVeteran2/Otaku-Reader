package app.otakureader.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.core.ui.components.IncognitoBanner
import app.otakureader.core.ui.adaptive.WindowWidthSizeClass
import app.otakureader.core.ui.adaptive.isExpanded
import app.otakureader.core.ui.adaptive.rememberWindowWidthSizeClass
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.model.SavedLibraryView
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Refresh

private const val CATEGORY_TABS_Z_INDEX = 2f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onMangaClick: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToMigration: (List<Long>) -> Unit = {},
    onNavigateToCategoryManagement: () -> Unit = {},
    onNavigateToReader: (mangaId: Long, chapterId: Long) -> Unit = { _, _ -> },
    onNavigateToMergeDuplicates: () -> Unit = {},
    onNavigateToShareLibrary: () -> Unit = {},
    onNavigateToScanLibrary: () -> Unit = {},
    onNavigateToMaintenance: () -> Unit = {},
    onBrowseClick: (() -> Unit)? = null,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }
    var pendingBulkAction: LibraryEvent? by remember { mutableStateOf(null) }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is LibraryEffect.NavigateToManga -> onMangaClick(effect.mangaId)
                is LibraryEffect.NavigateToReader -> onNavigateToReader(effect.mangaId, effect.chapterId)
                is LibraryEffect.ShowError -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(effect.message)
                    }
                }
                is LibraryEffect.NavigateToMigration -> {
                    onNavigateToMigration(effect.selectedMangaIds)
                }
                is LibraryEffect.ShowSnackbar -> scope.launch {
                    val msg = if (effect.formatArgs.isEmpty()) {
                        context.getString(effect.messageRes)
                    } else {
                        context.getString(effect.messageRes, *effect.formatArgs.toTypedArray())
                    }
                    snackbarHostState.showSnackbar(msg)
                }
                is LibraryEffect.ShareManga -> {
                    val shareText = if (effect.url.isNotEmpty()) "${effect.title}\n${effect.url}" else effect.title
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                    }
                    runCatching {
                        context.startActivity(
                            android.content.Intent.createChooser(shareIntent, effect.title)
                        )
                    }.onFailure {
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.library_share_error)) }
                    }
                }
                is LibraryEffect.ShowUndoLibraryDelete -> scope.launch {
                    val msg = context.resources.getQuantityString(
                        R.plurals.library_removed_count_undo, effect.count, effect.count
                    )
                    val result = snackbarHostState.showSnackbar(
                        message = msg,
                        actionLabel = context.getString(R.string.library_undo_action),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onEvent(LibraryEvent.UndoLibraryDelete(effect.mangaIds))
                    }
                }
            }
        }
    }

    pendingBulkAction?.let { action ->
        val actionLabel = when (action) {
            is LibraryEvent.RemoveSelectedFromLibrary -> stringResource(R.string.library_remove_selected)
            is LibraryEvent.MarkSelectedAsCompleted -> stringResource(R.string.library_mark_selected_completed)
            is LibraryEvent.MarkSelectedAsDropped -> stringResource(R.string.library_mark_selected_dropped)
            else -> ""
        }
        AlertDialog(
            onDismissRequest = { pendingBulkAction = null },
            title = { Text(stringResource(R.string.library_bulk_action_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.library_bulk_action_confirm_message,
                        state.selectedManga.size,
                        actionLabel,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onEvent(action)
                    pendingBulkAction = null
                }) {
                    Text(stringResource(R.string.library_bulk_action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulkAction = null }) {
                    Text(stringResource(R.string.library_bulk_action_cancel))
                }
            },
        )
    }

    BackHandler(enabled = state.selectedManga.isNotEmpty() || state.showSearchBar) {
        when {
            state.selectedManga.isNotEmpty() -> viewModel.onEvent(LibraryEvent.ClearSelection)
            state.showSearchBar -> viewModel.onEvent(LibraryEvent.ToggleSearchBar)
        }
    }

    Scaffold(
        topBar = {
            when {
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
                        IconButton(onClick = { viewModel.onEvent(LibraryEvent.SelectAllManga) }) {
                            Icon(
                                Icons.Outlined.SelectAll,
                                contentDescription = stringResource(R.string.library_select_all),
                            )
                        }
                        IconButton(onClick = { viewModel.onEvent(LibraryEvent.InvertSelection) }) {
                            Icon(
                                Icons.Outlined.FlipToBack,
                                contentDescription = stringResource(R.string.library_invert_selection),
                            )
                        }
                    }
                )
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
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onEvent(LibraryEvent.OnSearchQueryChange("")) }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.library_search_clear)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.onEvent(LibraryEvent.ToggleSearchBar) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.library_search_close))
                        }
                    }
                )
                else -> TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val selectedCategory = state.selectedCategory
                            val titleText = if (!state.showCategoryTabs && selectedCategory != null) {
                                when (state.groupType) {
                                    LibraryGroup.BY_SOURCE -> state.allMangaList
                                        .firstOrNull { it.sourceId == selectedCategory }
                                        ?.sourceName?.ifBlank { selectedCategory.toString() }
                                    LibraryGroup.BY_STATUS -> {
                                        val statusOrdinal = (selectedCategory - 1).toInt()
                                        when (MangaStatus.entries.getOrNull(statusOrdinal)) {
                                            MangaStatus.UNKNOWN -> stringResource(R.string.manga_status_unknown)
                                            MangaStatus.ONGOING -> stringResource(R.string.manga_status_ongoing)
                                            MangaStatus.COMPLETED -> stringResource(R.string.manga_status_completed)
                                            MangaStatus.LICENSED -> stringResource(R.string.manga_status_licensed)
                                            MangaStatus.PUBLISHING_FINISHED -> stringResource(R.string.manga_status_publishing_finished)
                                            MangaStatus.CANCELLED -> stringResource(R.string.manga_status_cancelled)
                                            MangaStatus.ON_HIATUS -> stringResource(R.string.manga_status_on_hiatus)
                                            null -> null
                                        }
                                    }
                                    else -> state.categories.find { it.id == selectedCategory }?.name
                                } ?: stringResource(R.string.library_title)
                            } else {
                                stringResource(R.string.library_title)
                            }
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val mangaCount = state.mangaList.size
                            if (mangaCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ) {
                                    Text(text = mangaCount.toString())
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.onEvent(LibraryEvent.ToggleIncognito) }) {
                            Icon(
                                imageVector = Icons.Outlined.VisibilityOff,
                                contentDescription = stringResource(R.string.library_toggle_incognito),
                                tint = if (state.incognitoMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        val inListMode = state.displayMode == LibraryDisplayMode.LIST
                        IconButton(onClick = {
                            viewModel.onEvent(
                                LibraryEvent.SetDisplayMode(
                                    if (inListMode) LibraryDisplayMode.GRID else LibraryDisplayMode.LIST
                                )
                            )
                        }) {
                            Icon(
                                imageVector = if (inListMode) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = if (inListMode)
                                    stringResource(R.string.library_view_mode_grid)
                                else
                                    stringResource(R.string.library_view_mode_list),
                            )
                        }
                        IconButton(onClick = { viewModel.onEvent(LibraryEvent.ToggleSearchBar) }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.library_search))
                        }
                        val hasActiveFilter = state.filterGenres.isNotEmpty() ||
                            state.filterMode != LibraryFilterMode.ALL ||
                            state.filterHasNotes ||
                            state.filterSourceId != null ||
                            state.filterReadingListId != null ||
                            state.filterDownloaded != LibraryTriState.DISABLED ||
                            state.filterUnread != LibraryTriState.DISABLED ||
                            state.filterStarted != LibraryTriState.DISABLED ||
                            state.filterTracking != LibraryTriState.DISABLED ||
                            state.filterCompleted != LibraryTriState.DISABLED
                        IconButton(onClick = {
                            viewModel.onEvent(LibraryEvent.SetBottomSheetTab(LibraryBottomSheetTab.FILTER))
                            viewModel.onEvent(LibraryEvent.ToggleBottomSheet)
                        }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.filter_sheet_title),
                                tint = if (hasActiveFilter) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.library_more))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_update_library)) },
                                onClick = { showMenu = false; viewModel.onEvent(LibraryEvent.UpdateLibrary) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_sync_library)) },
                                onClick = { showMenu = false; viewModel.onEvent(LibraryEvent.SyncLibrary) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_update_category)) },
                                onClick = { showMenu = false; viewModel.onEvent(LibraryEvent.UpdateCategory) },
                                enabled = state.selectedCategory != null
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_open_random)) },
                                onClick = { showMenu = false; viewModel.onEvent(LibraryEvent.OpenRandomEntry) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_reindex_downloads)) },
                                onClick = { showMenu = false; viewModel.onEvent(LibraryEvent.ReindexDownloads) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_menu_maintenance)) },
                                onClick = { showMenu = false; onNavigateToMaintenance() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_downloads)) },
                                onClick = { showMenu = false; onNavigateToDownloads() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_settings)) },
                                onClick = { showMenu = false; onNavigateToSettings() }
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
                                onClick = { showMenu = false; onNavigateToCategoryManagement() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.merge_duplicates_menu_item)) },
                                onClick = { showMenu = false; onNavigateToMergeDuplicates() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_share_via_qr)) },
                                onClick = { showMenu = false; onNavigateToShareLibrary() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_scan_qr)) },
                                onClick = { showMenu = false; onNavigateToScanLibrary() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_save_view)) },
                                onClick = {
                                    showMenu = false
                                    viewModel.onEvent(LibraryEvent.ShowSaveViewDialog)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_sync_eh_favorites)) },
                                onClick = {
                                    showMenu = false
                                    viewModel.onEvent(LibraryEvent.SyncEhFavorites)
                                }
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            LibrarySelectionBottomBar(
                visible = state.selectedManga.isNotEmpty(),
                onChangeCategoryClicked = { viewModel.onEvent(LibraryEvent.OpenMoveToCategoryDialog) },
                onMarkAsReadClicked = { viewModel.onEvent(LibraryEvent.MarkSelectedAsRead) },
                onMarkAsUnreadClicked = { viewModel.onEvent(LibraryEvent.MarkSelectedAsUnread) },
                onDownloadClicked = { viewModel.onEvent(LibraryEvent.DownloadSelected) },
                onDeleteClicked = { pendingBulkAction = LibraryEvent.RemoveSelectedFromLibrary },
                onMigrateClicked = { viewModel.onEvent(LibraryEvent.MigrateSelected) },
                onUpdateClicked = { viewModel.onEvent(LibraryEvent.UpdateSelected) },
                onNotifyClicked = { viewModel.onEvent(LibraryEvent.ToggleSelectedNotifications) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Resume FAB (Mihon/Komikku): continues the most recently read chapter. Shown only
            // when there is something to resume and not while selecting or searching, and it
            // animates in/out rather than popping when those modes change.
            val resume = state.continueReadingItems.firstOrNull()
            val showFab = resume != null && state.selectedManga.isEmpty() && !state.showSearchBar
            AnimatedVisibility(
                visible = showFab,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                if (resume != null) {
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.library_resume)) },
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        onClick = {
                            viewModel.onEvent(
                                LibraryEvent.ContinueReadingClick(resume.mangaId)
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        LibraryContent(
            state = state,
            onEvent = viewModel::onEvent,
            onBrowseClick = onBrowseClick,
            modifier = Modifier.padding(padding)
        )
    }

    if (state.showBottomSheet) {
        LibraryBottomSheet(
            state = state,
            onEvent = viewModel::onEvent,
            onDismiss = { viewModel.onEvent(LibraryEvent.ToggleBottomSheet) },
        )
    }

    if (state.showSaveViewDialog) {
        SaveViewDialog(
            name = state.saveViewName,
            onNameChange = { viewModel.onEvent(LibraryEvent.UpdateSaveViewName(it)) },
            onConfirm = { viewModel.onEvent(LibraryEvent.ConfirmSaveView) },
            onDismiss = { viewModel.onEvent(LibraryEvent.HideSaveViewDialog) },
        )
    }

    if (state.showMoveToCategoryDialog) {
        MoveToCategoryDialog(
            categories = state.categories,
            onConfirm = { categoryId ->
                viewModel.onEvent(LibraryEvent.MoveToCategory(state.moveToCategoryMangaIds, categoryId))
            },
            onDismiss = { viewModel.onEvent(LibraryEvent.DismissMoveToCategoryDialog) },
        )
    }
}

/**
 * Thin progress banner shown at the top of the library while a background library update is
 * running, matching Mihon/Komikku's update indicator. Driven by [LibraryState.isLibraryUpdating].
 */
@Composable
private fun LibraryUpdatingBanner(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.library_updating),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LibraryContent(
    state: LibraryState,
    onEvent: (LibraryEvent) -> Unit,
    onBrowseClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val widthSizeClass = rememberWindowWidthSizeClass()
    val isExpandedWidth = widthSizeClass.isExpanded
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val adaptiveColumns = when {
        isLandscape && state.landscapeColumns > 0 -> state.landscapeColumns
        !isLandscape && state.portraitColumns > 0 -> state.portraitColumns
        else -> when (widthSizeClass) {
            WindowWidthSizeClass.Expanded -> (state.gridSize + 2).coerceAtLeast(5)
            WindowWidthSizeClass.Medium -> (state.gridSize + 1).coerceAtLeast(4)
            WindowWidthSizeClass.Compact -> state.gridSize
        }
    }
    var detailManga by remember { mutableStateOf<LibraryMangaItem?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        if (state.incognitoMode) {
            IncognitoBanner()
        }

        if (state.isLibraryUpdating) {
            LibraryUpdatingBanner()
        }

        if (state.showSearchBar) {
            LibrarySearchFiltersRow(state = state, onEvent = onEvent)
        }

        if (state.showSearchBar && state.showAdvancedSearch) {
            AdvancedSearchSheet(
                onApply = { author, tag -> onEvent(LibraryEvent.ApplyAdvancedSearch(author, tag)) },
                onDismiss = { onEvent(LibraryEvent.ToggleAdvancedSearch) },
            )
        }

        val hasActiveFilters = state.filterMode != LibraryFilterMode.ALL ||
            state.filterGenres.isNotEmpty() ||
            state.sortMode != LibrarySortMode.ALPHABETICAL ||
            state.filterHasNotes ||
            state.filterSourceId != null ||
            state.filterReadingListId != null ||
            state.filterDownloaded != LibraryTriState.DISABLED ||
            state.filterUnread != LibraryTriState.DISABLED ||
            state.filterStarted != LibraryTriState.DISABLED ||
            state.filterBookmarked != LibraryTriState.DISABLED ||
            state.filterTracking != LibraryTriState.DISABLED ||
            state.filterCompleted != LibraryTriState.DISABLED
        // Sort doesn't hide items, so it must not trigger the filter-active empty state.
        val hasActiveItemFilters = state.filterMode != LibraryFilterMode.ALL ||
            state.filterGenres.isNotEmpty() ||
            state.filterHasNotes ||
            state.filterSourceId != null ||
            state.filterReadingListId != null ||
            state.filterDownloaded != LibraryTriState.DISABLED ||
            state.filterUnread != LibraryTriState.DISABLED ||
            state.filterStarted != LibraryTriState.DISABLED ||
            state.filterBookmarked != LibraryTriState.DISABLED ||
            state.filterTracking != LibraryTriState.DISABLED ||
            state.filterCompleted != LibraryTriState.DISABLED
        if (hasActiveFilters && !state.showSearchBar) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.filterMode != LibraryFilterMode.ALL) {
                    FilterChip(
                        selected = true,
                        onClick = { onEvent(LibraryEvent.SetFilterMode(LibraryFilterMode.ALL)) },
                        label = { Text(state.filterMode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                    )
                }
                state.filterGenres.forEach { genre ->
                    FilterChip(
                        selected = true,
                        onClick = { onEvent(LibraryEvent.SetGenreFilter(state.filterGenres - genre)) },
                        label = { Text(genre) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                    )
                }
                if (state.sortMode != LibrarySortMode.ALPHABETICAL) {
                    FilterChip(
                        selected = true,
                        onClick = { onEvent(LibraryEvent.SetSortMode(LibrarySortMode.ALPHABETICAL)) },
                        label = {
                            Text(
                                stringResource(
                                    R.string.library_sort_chip,
                                    state.sortMode.name.lowercase().replaceFirstChar { it.uppercase() }.replace('_', ' ')
                                )
                            )
                        },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                    )
                }
                if (state.filterHasNotes) {
                    FilterChip(
                        selected = true,
                        onClick = { onEvent(LibraryEvent.FilterHasNotes(false)) },
                        label = { Text(stringResource(R.string.library_filter_has_notes)) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                    )
                }
                if (state.filterSourceId != null) {
                    FilterChip(
                        selected = true,
                        onClick = { onEvent(LibraryEvent.SetFilterSource(null)) },
                        label = { Text(stringResource(R.string.filter_source)) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                    )
                }
                if (state.filterReadingListId != null) {
                    FilterChip(
                        selected = true,
                        onClick = { onEvent(LibraryEvent.SetFilterReadingList(null)) },
                        label = { Text(stringResource(R.string.filter_reading_list)) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                    )
                }
                if (state.filterDownloaded != LibraryTriState.DISABLED) {
                    FilterChip(
                        selected = true,
                        onClick = { onEvent(LibraryEvent.SetFilterDownloaded(LibraryTriState.DISABLED)) },
                        label = {
                            Text(
                                if (state.filterDownloaded == LibraryTriState.ENABLED_NOT)
                                    stringResource(R.string.filter_not_downloaded)
                                else stringResource(R.string.filter_downloaded)
                            )
                        },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                    )
                }
                if (state.filterUnread != LibraryTriState.DISABLED) {
                    FilterChip(
                        selected = true,
                        onClick = { onEvent(LibraryEvent.SetFilterUnread(LibraryTriState.DISABLED)) },
                        label = {
                            Text(
                                if (state.filterUnread == LibraryTriState.ENABLED_NOT)
                                    stringResource(R.string.filter_not_unread)
                                else stringResource(R.string.filter_unread)
                            )
                        },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                    )
                }
                if (state.filterStarted != LibraryTriState.DISABLED) {
                    FilterChip(
                        selected = true,
                        onClick = { onEvent(LibraryEvent.SetFilterStarted(LibraryTriState.DISABLED)) },
                        label = {
                            Text(
                                if (state.filterStarted == LibraryTriState.ENABLED_NOT)
                                    stringResource(R.string.filter_not_started)
                                else stringResource(R.string.filter_started)
                            )
                        },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                    )
                }
                if (state.filterTracking != LibraryTriState.DISABLED) {
                    FilterChip(
                        selected = true,
                        onClick = { onEvent(LibraryEvent.SetFilterTracking(LibraryTriState.DISABLED)) },
                        label = {
                            Text(
                                if (state.filterTracking == LibraryTriState.ENABLED_NOT)
                                    stringResource(R.string.filter_not_tracking)
                                else stringResource(R.string.filter_tracking)
                            )
                        },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                    )
                }
                if (state.filterCompleted != LibraryTriState.DISABLED) {
                    FilterChip(
                        selected = true,
                        onClick = { onEvent(LibraryEvent.SetFilterCompleted(LibraryTriState.DISABLED)) },
                        label = {
                            Text(
                                if (state.filterCompleted == LibraryTriState.ENABLED_NOT)
                                    stringResource(R.string.filter_not_completed)
                                else stringResource(R.string.filter_completed)
                            )
                        },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                    )
                }
            }
        }

        // Saved views row: shown when there are saved views and search bar is closed.
        if (state.savedViews.isNotEmpty() && !state.showSearchBar) {
            SavedViewsRow(
                savedViews = state.savedViews,
                onApply = { onEvent(LibraryEvent.ApplySavedView(it)) },
                onDelete = { onEvent(LibraryEvent.DeleteSavedView(it)) },
            )
        }

        // Category tabs (Komikku parity): scrollable tab row with optional count badges.
        // For non-default group modes, derive synthetic tabs from the full unfiltered list.
        val context = LocalContext.current
        val statusLabels = remember(context) {
            mapOf(
                MangaStatus.UNKNOWN to context.getString(R.string.manga_status_unknown),
                MangaStatus.ONGOING to context.getString(R.string.manga_status_ongoing),
                MangaStatus.COMPLETED to context.getString(R.string.manga_status_completed),
                MangaStatus.LICENSED to context.getString(R.string.manga_status_licensed),
                MangaStatus.PUBLISHING_FINISHED to context.getString(R.string.manga_status_publishing_finished),
                MangaStatus.CANCELLED to context.getString(R.string.manga_status_cancelled),
                MangaStatus.ON_HIATUS to context.getString(R.string.manga_status_on_hiatus),
            )
        }
        val displayCategories: List<CategoryItem> = remember(
            state.groupType, state.allMangaList, state.categories, statusLabels
        ) {
            when (state.groupType) {
                LibraryGroup.BY_SOURCE -> state.allMangaList
                    .groupBy { it.sourceId }
                    .map { (sourceId, items) ->
                        CategoryItem(
                            id = sourceId,
                            name = items.first().sourceName.ifBlank { sourceId.toString() },
                            count = items.size,
                        )
                    }
                    .sortedBy { it.name }
                LibraryGroup.BY_STATUS -> state.allMangaList
                    .groupBy { it.status }
                    .map { (status, items) ->
                        CategoryItem(
                            id = (status.ordinal + 1).toLong(),
                            name = statusLabels[status] ?: status.name,
                            count = items.size,
                        )
                    }
                    .sortedBy { it.name }
                LibraryGroup.UNGROUPED, LibraryGroup.BY_TRACK_STATUS -> emptyList()
                else -> state.categories
            }
        }
        if (state.showCategoryTabs && displayCategories.isNotEmpty()) {
            LibraryCategoryTabs(
                categories = displayCategories,
                selectedCategoryId = state.selectedCategory,
                showItemCount = state.showCategoryItemCount,
                onTabSelected = { onEvent(LibraryEvent.OnCategorySelected(it)) },
            )
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { if (state.selectedManga.isEmpty()) onEvent(LibraryEvent.Refresh) },
            modifier = Modifier.weight(1f)
        ) {
            when {
                (state.isLoading || state.isSearching) && state.mangaList.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
                state.mangaList.isEmpty() && state.searchQuery.isNotBlank() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        EmptyLibrarySearchMessage(
                            query = state.searchQuery,
                            onBrowseClick = onBrowseClick,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                state.mangaList.isEmpty() && hasActiveItemFilters && state.totalMangaCount > 0 -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        EmptyLibraryFilterMessage(
                            onClearFilters = { onEvent(LibraryEvent.ClearAllFilters) },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                state.mangaList.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        EmptyLibraryMessage(
                            onBrowseClick = onBrowseClick,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                isExpandedWidth -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        MangaGrid(
                            state = state,
                            onEvent = onEvent,
                            adaptiveColumns = adaptiveColumns,
                            onMangaSelect = { manga -> detailManga = manga },
                            modifier = Modifier.weight(0.55f)
                        )
                        VerticalDivider()
                        MangaDetailPanel(
                            manga = detailManga,
                            onOpenFullDetails = { onEvent(LibraryEvent.OnMangaClick(it)) },
                            onClose = { detailManga = null },
                            modifier = Modifier.weight(0.45f)
                        )
                    }
                }
                else -> {
                    MangaGrid(
                        state = state,
                        onEvent = onEvent,
                        adaptiveColumns = adaptiveColumns
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrarySearchFiltersRow(
    state: LibraryState,
    onEvent: (LibraryEvent) -> Unit,
) {
    val allLabel = stringResource(R.string.library_filter_all)
    val unreadLabel = stringResource(R.string.library_filter_unread)
    val downloadedLabel = stringResource(R.string.library_filter_downloaded)
    val completedLabel = stringResource(R.string.library_filter_completed)
    val droppedLabel = stringResource(R.string.library_filter_dropped)
    val statusFilters = remember(allLabel, unreadLabel, downloadedLabel, completedLabel, droppedLabel) {
        listOf(
            LibraryFilterMode.ALL to allLabel,
            LibraryFilterMode.UNREAD to unreadLabel,
            LibraryFilterMode.DOWNLOADED to downloadedLabel,
            LibraryFilterMode.COMPLETED to completedLabel,
            LibraryFilterMode.DROPPED to droppedLabel,
        )
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(statusFilters, key = { it.first.name }) { (mode, label) ->
            FilterChip(
                selected = state.filterMode == mode,
                onClick = { onEvent(LibraryEvent.SetFilterMode(mode)) },
                label = { Text(label) },
            )
        }
        if (state.availableGenres.isNotEmpty()) {
            items(state.availableGenres.take(12), key = { "genre_$it" }) { genre ->
                FilterChip(
                    selected = genre in state.filterGenres,
                    onClick = {
                        val updated = if (genre in state.filterGenres) {
                            state.filterGenres - genre
                        } else {
                            state.filterGenres + genre
                        }
                        onEvent(LibraryEvent.SetGenreFilter(updated))
                    },
                    label = { Text(genre) },
                )
            }
        }
        item(key = "advanced") {
            AssistChip(
                onClick = { onEvent(LibraryEvent.ToggleAdvancedSearch) },
                label = { Text(stringResource(R.string.library_advanced_search_open)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

// ─── Saved views UI ────────────────────────────────────────────────────────────

/**
 * A horizontally scrollable row of [FilterChip]s, one per saved view.
 *
 * - Tap to apply a view (restores filter+sort).
 * - Long-press to delete a saved view.
 *
 * The row is only shown when [savedViews] is non-empty (caller's responsibility).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedViewsRow(
    savedViews: List<SavedLibraryView>,
    onApply: (SavedLibraryView) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.library_saved_views),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(savedViews, key = { it.id }) { view ->
                FilterChip(
                    selected = false,
                    onClick = { onApply(view) },
                    label = { Text(view.name) },
                    modifier = Modifier.combinedClickable(
                        onClick = { onApply(view) },
                        onLongClick = { onDelete(view.id) },
                    ),
                )
            }
        }
    }
}

/**
 * AlertDialog that lets the user type a name for the current filter+sort combination
 * before saving it.
 *
 * @param name Current value of the name text field (from MVI state).
 * @param onNameChange Called on every keystroke.
 * @param onConfirm Called when the user taps Save; the ViewModel snapshots the current state.
 * @param onDismiss Called when the user taps Cancel or dismisses the dialog.
 */
@Composable
private fun SaveViewDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_save_view)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = { Text(stringResource(R.string.library_save_view_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.library_save_view_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun MoveToCategoryDialog(
    categories: List<CategoryItem>,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_move_to_category)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(categories, key = { it.id }) { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedCategoryId == category.id,
                                onClick = { selectedCategoryId = category.id },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedCategoryId == category.id,
                            onClick = { selectedCategoryId = category.id },
                        )
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedCategoryId?.let { onConfirm(it) } },
                enabled = selectedCategoryId != null,
            ) {
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

@Composable
private fun LibrarySelectionBottomBar(
    visible: Boolean,
    onChangeCategoryClicked: () -> Unit,
    onMarkAsReadClicked: () -> Unit,
    onMarkAsUnreadClicked: () -> Unit,
    onDownloadClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onMigrateClicked: () -> Unit,
    onUpdateClicked: () -> Unit,
    onNotifyClicked: () -> Unit,
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
            // Scrollable rather than SpaceEvenly. Eight labelled actions do not fit a narrow
            // phone, and `SpaceEvenly` has no answer for that — it squeezes every label until the
            // ellipsis makes them indistinguishable. Scrolling keeps each action readable and
            // leaves room for the next one.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onChangeCategoryClicked) {
                        Icon(
                            Icons.AutoMirrored.Filled.DriveFileMove,
                            contentDescription = stringResource(R.string.library_move_to_category),
                        )
                    }
                    Text(
                        text = stringResource(R.string.library_move_to_category),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onMarkAsReadClicked) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.library_mark_selected_read),
                        )
                    }
                    Text(
                        text = stringResource(R.string.library_mark_selected_read),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onMarkAsUnreadClicked) {
                        Icon(
                            Icons.Default.RadioButtonUnchecked,
                            contentDescription = stringResource(R.string.library_mark_selected_unread),
                        )
                    }
                    Text(
                        text = stringResource(R.string.library_mark_selected_unread),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onDownloadClicked) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = stringResource(R.string.library_download_selected),
                        )
                    }
                    Text(
                        text = stringResource(R.string.library_download_selected),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onUpdateClicked) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.library_update_selected),
                        )
                    }
                    Text(
                        text = stringResource(R.string.library_update_selected),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onDeleteClicked) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = stringResource(R.string.library_remove_selected),
                        )
                    }
                    Text(
                        text = stringResource(R.string.library_remove_selected),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onMigrateClicked) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = stringResource(R.string.library_context_menu_migrate),
                        )
                    }
                    Text(
                        text = stringResource(R.string.library_context_menu_migrate),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onNotifyClicked) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = stringResource(R.string.library_notify_new_chapters),
                        )
                    }
                    Text(
                        text = stringResource(R.string.library_notify_new_chapters),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Scrollable tab row showing each library category as a tab, matching Komikku's LibraryTabs.
 * The leading "All" tab selects [selectedCategoryId] = null (no category filter).
 * Item-count badges are shown when [showItemCount] is true.
 */
@Composable
private fun LibraryCategoryTabs(
    categories: List<CategoryItem>,
    selectedCategoryId: Long?,
    showItemCount: Boolean,
    onTabSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = remember(categories, selectedCategoryId) {
        if (selectedCategoryId == null) {
            0
        } else {
            val idx = categories.indexOfFirst { it.id == selectedCategoryId }
            if (idx < 0) 0 else idx + 1
        }
    }
    val totalCount = remember(categories, showItemCount) {
        if (showItemCount) categories.sumOf { it.count } else null
    }

    Column(modifier = modifier.zIndex(CATEGORY_TABS_Z_INDEX)) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            edgePadding = 0.dp,
            divider = {},
        ) {
            Tab(
                selected = selectedCategoryId == null,
                onClick = { onTabSelected(null) },
                text = {
                    LibraryTabText(
                        text = stringResource(R.string.library_filter_all),
                        badgeCount = totalCount,
                    )
                },
                unselectedContentColor = MaterialTheme.colorScheme.onSurface,
            )
            categories.forEach { category ->
                Tab(
                    selected = selectedCategoryId == category.id,
                    onClick = { onTabSelected(category.id) },
                    text = {
                        LibraryTabText(
                            text = category.name,
                            badgeCount = if (showItemCount) category.count else null,
                        )
                    },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun LibraryTabText(
    text: String,
    badgeCount: Int? = null,
) {
    if (badgeCount != null && badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(
                        text = badgeCount.toString(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        ) {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
