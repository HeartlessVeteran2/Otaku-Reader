@file:Suppress("MaxLineLength")
package app.otakureader.feature.details

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.core.ui.adaptive.isExpanded
import app.otakureader.core.ui.adaptive.rememberWindowWidthSizeClass
import app.otakureader.domain.model.Category
import app.otakureader.domain.model.ReadingList
import app.otakureader.core.ui.component.ErrorScreen
import app.otakureader.core.ui.component.LoadingScreen
import app.otakureader.core.ui.theme.MangaDynamicTheme
import app.otakureader.core.ui.theme.rememberCoverColorScheme
import app.otakureader.feature.details.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import androidx.compose.ui.tooling.preview.Preview
import app.otakureader.core.ui.theme.OtakuReaderTheme

// Two-pane split for the Expanded width class. The info pane is given slightly
// more horizontal space than the chapter list because the manga header and
// description benefit from extra width; the two weights must sum to 1f.
private const val INFO_PANE_WEIGHT = 0.55f
private const val CHAPTER_PANE_WEIGHT = 0.45f

// Controls how far the user must scroll before the TopAppBar reaches full opacity.
private const val HERO_TOP_BAR_FADE_RANGE = 600f

// Genre/tag chip layout tokens.
/** Shared with [AniListMetadataSection]'s tag chips so tags and genres line up on the same grid. */
internal val GENRE_CHIP_SPACING = 8.dp
private val GENRE_CHIP_PADDING_HORIZONTAL = 12.dp
private val GENRE_CHIP_PADDING_VERTICAL = 6.dp

// Corner radius for the ripple on each chapter selection bottom-bar action.
private val CHAPTER_ACTION_CORNER = 8.dp

// "Mark previous as read" only applies to a single anchor chapter, so the action is shown
// only when exactly this many chapters are selected (Komikku parity).
private const val SINGLE_CHAPTER_SELECTION = 1

// Action labels in the selection bottom bar are kept to a single line so the row stays compact.
private const val CHAPTER_ACTION_LABEL_MAX_LINES = 1

// Alpha for action buttons that represent a disabled/inactive state in MangaActionRow.
private const val MANGA_ACTION_INACTIVE_ALPHA = 0.6f

// Bottom content padding for the phone single-scroll list — enough to clear the FAB
// (56 dp FAB height + 16 dp bottom margin + 16 dp list breathing room).
private val FAB_CONTENT_PADDING_BOTTOM = 88.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    mangaId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToReader: (mangaId: Long, chapterId: Long) -> Unit,
    onNavigateToTracking: (mangaId: Long, mangaTitle: String) -> Unit = { _, _ -> },
    onNavigateToGlobalSearch: (query: String) -> Unit = {},
    onNavigateToSourceSearch: (sourceId: String, query: String) -> Unit = { _, _ -> },
    onNavigateToMigration: (mangaId: Long) -> Unit = {},
    onNavigateToWebViewFallback: (url: String, title: String) -> Unit = { _, _ -> },
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val isExpanded = rememberWindowWidthSizeClass().isExpanded
    val listState = rememberLazyListState()
    val selectedVisibleChapters = remember(state.sortedChapters, state.selectedChapters) {
        state.sortedChapters.filter { it.id in state.selectedChapters }
    }
    val isScrollAllowed = rememberUpdatedState(!isExpanded && selectedVisibleChapters.isEmpty())
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        canScroll = { isScrollAllowed.value },
    )
    val heroScrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0)
                listState.firstVisibleItemScrollOffset.toFloat()
            else
                Float.MAX_VALUE
        }
    }
    // In expanded (tablet) layout there is no parallax hero — keep the TopAppBar fully opaque.
    val topBarAlpha by remember(isExpanded) {
        derivedStateOf {
            if (isExpanded) 1f
            else (heroScrollOffset / HERO_TOP_BAR_FADE_RANGE).coerceIn(0f, 1f)
        }
    }

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
                is DetailsContract.Effect.ShareManga -> {
                    val shareText = if (effect.url.isNotEmpty()) {
                        "${effect.title}\n${effect.url}"
                    } else {
                        effect.title
                    }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_manga)))
                }
                is DetailsContract.Effect.NavigateToTracking -> {
                    onNavigateToTracking(effect.mangaId, effect.mangaTitle)
                }
                is DetailsContract.Effect.NavigateToGlobalSearch -> {
                    onNavigateToGlobalSearch(effect.query)
                }
                is DetailsContract.Effect.NavigateToSourceSearch -> {
                    onNavigateToSourceSearch(effect.sourceId, effect.query)
                }
                is DetailsContract.Effect.NavigateToMigration -> {
                    onNavigateToMigration(effect.mangaId)
                }
                is DetailsContract.Effect.NavigateToWebViewFallback -> {
                    onNavigateToWebViewFallback(effect.url, effect.title)
                }
                is DetailsContract.Effect.OpenInBrowser -> {
                    try {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(effect.url),
                        )
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        snackbarHostState.showSnackbar(context.getString(R.string.details_no_browser))
                    }
                }
                is DetailsContract.Effect.OpenDownloadFolder -> {
                    val externalFilesDir = context.getExternalFilesDir(null)
                    if (externalFilesDir != null) {
                        // Match DownloadProvider.sanitize() which also trims whitespace
                        val safeName = { s: String -> s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim() }
                        val mangaDir = java.io.File(
                            externalFilesDir,
                            "OtakuReader/${safeName(effect.sourceName)}/${safeName(effect.mangaTitle)}"
                        )
                        val uri = withContext(Dispatchers.IO) {
                            if (mangaDir.exists()) {
                                runCatching {
                                    androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        mangaDir,
                                    )
                                }.getOrNull()
                            } else null
                        }
                        if (uri != null) {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                snackbarHostState.showSnackbar(context.getString(R.string.details_no_file_manager))
                            }
                        } else {
                            snackbarHostState.showSnackbar(context.getString(R.string.details_no_downloads))
                        }
                    }
                }
                is DetailsContract.Effect.ShowDeleteDownloadsPrompt -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.details_delete_downloads_prompt),
                        actionLabel = context.getString(R.string.details_delete_downloads_action),
                        withDismissAction = true,
                        // Destructive action — give the user more time to notice and respond
                        // than the default ~4s duration.
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onEvent(DetailsContract.Event.ClearMangaDownloads)
                    }
                }
                else -> { /* no-op */ }
            }
        }
    }

    val dynamicScheme = rememberCoverColorScheme(
        imageUrl = state.manga?.thumbnailUrl,
        darkTheme = androidx.compose.foundation.isSystemInDarkTheme(),
        // Per-manga override (#947) takes precedence over the global autoThemeColor pref.
        enabled = state.manga?.mangaThemeOverride ?: state.autoThemeEnabled
    )

    MangaDynamicTheme(colorScheme = dynamicScheme) {
        BackHandler(enabled = selectedVisibleChapters.isNotEmpty()) {
            viewModel.onEvent(DetailsContract.Event.ClearChapterSelection)
        }
        Scaffold(
            modifier = if (selectedVisibleChapters.isEmpty()) {
                Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
            } else {
                Modifier
            },
            topBar = {
            if (selectedVisibleChapters.isNotEmpty()) {
                ChapterSelectionTopBar(
                    selectedCount = selectedVisibleChapters.size,
                    onClearSelection = { viewModel.onEvent(DetailsContract.Event.ClearChapterSelection) },
                    onSelectAll = { viewModel.onEvent(DetailsContract.Event.SelectAllChapters) },
                    onInvertSelection = { viewModel.onEvent(DetailsContract.Event.InvertChapterSelection) },
                )
            } else {
            TopAppBar(
                title = {
                    Text(
                        text = state.manga?.title ?: stringResource(R.string.details_title_fallback),
                        modifier = Modifier.alpha(topBarAlpha),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.details_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = topBarAlpha),
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior,
                actions = {
                    val filterActive = state.chapterFilter.isActive
                    IconButton(onClick = { viewModel.onEvent(DetailsContract.Event.ShowChapterFilter) }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.details_filter_chapters),
                            tint = if (filterActive) MaterialTheme.colorScheme.primary
                                   else androidx.compose.material3.LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = { viewModel.onEvent(DetailsContract.Event.Refresh) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.details_refresh))
                    }
                    IconButton(onClick = { viewModel.onEvent(DetailsContract.Event.ShareManga) }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.details_share))
                    }
                    var overflowExpanded by remember { mutableStateOf(false) }

                    // System image picker for custom cover art. The picked content:// URI is
                    // passed to the ViewModel, which copies the image into app storage.
                    val coverPickerLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.GetContent(),
                    ) { uri ->
                        if (uri != null) {
                            viewModel.onEvent(DetailsContract.Event.SetCustomCover(uri.toString()))
                        }
                    }
                    IconButton(onClick = { overflowExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.details_more_options))
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.details_download_all_chapters)) },
                            onClick = {
                                viewModel.onEvent(DetailsContract.Event.DownloadAllChapters)
                                overflowExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.details_download_unread_chapters)) },
                            onClick = {
                                viewModel.onEvent(DetailsContract.Event.DownloadUnreadChapters)
                                overflowExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (state.manga?.userCompleted == true) {
                                        stringResource(R.string.details_unmark_completed)
                                    } else {
                                        stringResource(R.string.details_mark_completed)
                                    }
                                )
                            },
                            onClick = {
                                viewModel.onEvent(DetailsContract.Event.ToggleUserCompleted)
                                overflowExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (state.manga?.userDropped == true) {
                                        stringResource(R.string.details_unmark_dropped)
                                    } else {
                                        stringResource(R.string.details_mark_dropped)
                                    }
                                )
                            },
                            onClick = {
                                viewModel.onEvent(DetailsContract.Event.ToggleUserDropped)
                                overflowExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                val label = when (state.manga?.mangaThemeOverride) {
                                    null -> stringResource(R.string.details_theme_inherit)
                                    true -> stringResource(R.string.details_theme_force_on)
                                    false -> stringResource(R.string.details_theme_force_off)
                                }
                                Text(label)
                            },
                            onClick = {
                                viewModel.onEvent(DetailsContract.Event.CycleMangaThemeOverride)
                                overflowExpanded = false
                            },
                        )
                        if (state.isFavorite) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.details_migrate)) },
                                onClick = {
                                    viewModel.onEvent(DetailsContract.Event.MigrateManga)
                                    overflowExpanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.details_add_to_reading_list)) },
                            onClick = {
                                viewModel.onEvent(DetailsContract.Event.ShowReadingListPicker)
                                overflowExpanded = false
                            }
                        )
                        // Always offered, not only when unmatched: the entry point has to exist
                        // both when auto-matching declined to guess (nothing rendered, no clue why)
                        // and when it guessed wrong (something rendered, and it is wrong).
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.details_anilist_link)) },
                            onClick = {
                                viewModel.onEvent(DetailsContract.Event.ShowAniListPicker)
                                overflowExpanded = false
                            }
                        )
                        androidx.compose.material3.HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.details_open_download_folder)) },
                            onClick = {
                                viewModel.onEvent(DetailsContract.Event.OpenDownloadFolder)
                                overflowExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.details_clear_downloads)) },
                            onClick = {
                                viewModel.onEvent(DetailsContract.Event.ClearMangaDownloads)
                                overflowExpanded = false
                            }
                        )
                        androidx.compose.material3.HorizontalDivider()
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            text = { Text(stringResource(R.string.details_edit_info)) },
                            onClick = {
                                viewModel.onEvent(DetailsContract.Event.ShowEditInfoSheet)
                                overflowExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.details_set_custom_cover)) },
                            onClick = {
                                coverPickerLauncher.launch("image/*")
                                overflowExpanded = false
                            }
                        )
                        if (state.manga?.hasCustomCover == true) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.details_remove_custom_cover)) },
                                onClick = {
                                    viewModel.onEvent(DetailsContract.Event.RemoveCustomCover)
                                    overflowExpanded = false
                                }
                            )
                        }
                    }
                }
            )
            }
        },
        bottomBar = {
            ChapterSelectionBottomBar(
                selectedChapters = selectedVisibleChapters,
                onMarkRead = { viewModel.onEvent(DetailsContract.Event.MarkSelectedAsRead) },
                onMarkUnread = { viewModel.onEvent(DetailsContract.Event.MarkSelectedAsUnread) },
                onMarkPreviousAsRead = { chapterId ->
                    viewModel.onEvent(DetailsContract.Event.MarkPreviousAsRead(chapterId))
                },
                onDownload = { viewModel.onEvent(DetailsContract.Event.DownloadSelectedChapters) },
                onDelete = { viewModel.onEvent(DetailsContract.Event.DeleteSelectedChapters) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.hasUnreadChapters && selectedVisibleChapters.isEmpty()) {
                val isFabExpanded by remember(isExpanded) {
                    derivedStateOf { isExpanded || !listState.canScrollBackward }
                }
                ExtendedFloatingActionButton(
                    text = {
                        Text(
                            if (state.hasStartedReading) stringResource(R.string.details_resume_reading)
                            else stringResource(R.string.details_start_reading)
                        )
                    },
                    icon = {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        if (state.hasStartedReading) viewModel.onEvent(DetailsContract.Event.ContinueReading)
                        else viewModel.onEvent(DetailsContract.Event.StartReading)
                    },
                    expanded = isFabExpanded,
                )
            }
        },
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingScreen(modifier = Modifier.padding(paddingValues))
            state.error != null -> ErrorScreen(
                message = state.error ?: stringResource(R.string.details_unknown_error),
                onRetry = { viewModel.onEvent(DetailsContract.Event.Refresh) },
                onOpenInBrowser = if (state.mangaWebUrl != null) {
                    { viewModel.onEvent(DetailsContract.Event.OpenWebViewFallback) }
                } else {
                    null
                },
                modifier = Modifier.padding(paddingValues)
            )
            state.manga != null -> DetailsContent(
                state = state,
                onEvent = viewModel::onEvent,
                listState = listState,
                modifier = Modifier.padding(paddingValues)
            )
            else -> EmptyScreen(modifier = Modifier.padding(paddingValues))
        }
    }
}
}

/**
 * Contextual app bar shown while one or more chapters are selected. Matching Mihon/Komikku's
 * `MangaToolbar` selection mode, this only carries the count plus select-all / invert-selection;
 * the actual batch operations (mark read/unread, download, delete) live in the animated
 * [ChapterSelectionBottomBar] at the bottom of the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterSelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.details_selected_count, selectedCount)) },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.details_clear_selection))
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.details_select_all))
            }
            IconButton(onClick = onInvertSelection) {
                Icon(Icons.Default.FlipToBack, contentDescription = stringResource(R.string.details_invert_selection))
            }
        },
    )
}

/**
 * Komikku-style chapter selection action menu. Slides up from the bottom while chapters are
 * selected and only surfaces the actions that make sense for the current selection — mirroring
 * Mihon/Komikku's `MangaBottomActionMenu`:
 *  - Mark as read: shown when any selected chapter is unread.
 *  - Mark as unread: shown when any selected chapter is read (or partially read).
 *  - Mark previous as read: shown only when exactly one chapter is selected.
 *  - Download: shown when any selected chapter is not yet downloaded.
 *  - Delete: shown when any selected chapter is downloaded.
 */
@Composable
private fun ChapterSelectionBottomBar(
    selectedChapters: List<DetailsContract.ChapterItem>,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onMarkPreviousAsRead: (Long) -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = selectedChapters.isNotEmpty(),
        enter = expandVertically(expandFrom = Alignment.Bottom),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
        modifier = modifier,
    ) {
        val showMarkRead = selectedChapters.any { !it.read }
        val showMarkUnread = selectedChapters.any { it.read || it.lastPageRead > 0 }
        val showMarkPrevious = selectedChapters.size == SINGLE_CHAPTER_SELECTION
        val showDownload = selectedChapters.any { it.downloadStatus != DetailsContract.DownloadStatus.DOWNLOADED }
        val showDelete = selectedChapters.any { it.downloadStatus == DetailsContract.DownloadStatus.DOWNLOADED }

        Surface(
            shape = MaterialTheme.shapes.large.copy(
                bottomEnd = androidx.compose.foundation.shape.ZeroCornerSize,
                bottomStart = androidx.compose.foundation.shape.ZeroCornerSize,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (showMarkRead) {
                    ChapterSelectionAction(
                        icon = Icons.Default.DoneAll,
                        label = stringResource(R.string.details_mark_as_read),
                        onClick = onMarkRead,
                    )
                }
                if (showMarkUnread) {
                    ChapterSelectionAction(
                        icon = Icons.Default.RemoveDone,
                        label = stringResource(R.string.details_mark_as_unread),
                        onClick = onMarkUnread,
                    )
                }
                if (showMarkPrevious) {
                    val previousAnchorId = selectedChapters.first().id
                    ChapterSelectionAction(
                        icon = Icons.Default.Done,
                        label = stringResource(R.string.details_mark_previous_as_read),
                        onClick = { onMarkPreviousAsRead(previousAnchorId) },
                    )
                }
                if (showDownload) {
                    ChapterSelectionAction(
                        icon = Icons.Default.Download,
                        label = stringResource(R.string.details_download_selected),
                        onClick = onDownload,
                    )
                }
                if (showDelete) {
                    ChapterSelectionAction(
                        icon = Icons.Default.Delete,
                        label = stringResource(R.string.details_delete_selected),
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterSelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(CHAPTER_ACTION_CORNER))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = CHAPTER_ACTION_LABEL_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailsContent(
    state: DetailsContract.State,
    onEvent: (DetailsContract.Event) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val manga = state.manga ?: return
    val widthSizeClass = rememberWindowWidthSizeClass()
    val scrollOffset: () -> Float = {
        if (listState.firstVisibleItemIndex == 0)
            listState.firstVisibleItemScrollOffset.toFloat()
        else Float.MAX_VALUE
    }

    if (widthSizeClass.isExpanded) {
        // Tablet / DeX / desktop: split the screen so the chapter list isn't
        // wasted vertical space below a long header. Each pane scrolls
        // independently. We give the info pane slightly more room because
        // the manga header and description benefit from extra width.
        Row(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(INFO_PANE_WEIGHT)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                detailsInfoItems(manga = manga, state = state, onEvent = onEvent)
            }
            VerticalDivider()
            LazyColumn(
                modifier = Modifier
                    .weight(CHAPTER_PANE_WEIGHT)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                detailsChapterItems(state = state, onEvent = onEvent)
            }
        }
    } else {
        // Phone: single scrolling LazyColumn — Komikku parity (no tabs)
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = FAB_CONTENT_PADDING_BOTTOM),
        ) {
            item(key = "header") {
                MangaHeader(
                    manga = manga,
                    showPanoramaCover = state.showPanoramaCover,
                    onTogglePanoramaCover = { onEvent(DetailsContract.Event.TogglePanoramaCover) },
                    onSearchGlobal = { query -> onEvent(DetailsContract.Event.SearchGlobally(query)) },
                    sourceName = state.sourceName,
                    onSourceClick = { onEvent(DetailsContract.Event.SourceClick) },
                    scrollOffset = scrollOffset,
                )
            }
            item(key = "action_row") {
                MangaActionRow(
                    isFavorite = state.isFavorite,
                    trackingCount = state.trackingCount,
                    webUrl = state.mangaWebUrl,
                    onToggleFavorite = { onEvent(DetailsContract.Event.ToggleFavorite) },
                    onOpenTracking = { onEvent(DetailsContract.Event.OpenTracking) },
                    onOpenWebView = { onEvent(DetailsContract.Event.OpenWebView) },
                )
            }
            item(key = "stats") { DetailsStatsRow(state = state) }
            detailsInfoTabItems(manga = manga, state = state, onEvent = onEvent)
            item(key = "chapter_divider") {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            detailsChapterItems(state = state, onEvent = onEvent)
        }
    }

    if (state.noteEditorVisible) {
        NoteEditorDialog(
            noteText = state.noteEditorText,
            onTextChange = { onEvent(DetailsContract.Event.UpdateNoteText(it)) },
            onSave = { onEvent(DetailsContract.Event.SaveNote) },
            onDismiss = { onEvent(DetailsContract.Event.HideNoteEditor) }
        )
    }

    if (state.chapterNoteEditorChapterId != null) {
        NoteEditorDialog(
            noteText = state.chapterNoteEditorText,
            onTextChange = { onEvent(DetailsContract.Event.UpdateChapterNoteText(it)) },
            onSave = { onEvent(DetailsContract.Event.SaveChapterNote) },
            onDismiss = { onEvent(DetailsContract.Event.HideChapterNoteEditor) },
            titleRes = R.string.chapter_notes_editor_dialog_title
        )
    }

    if (state.showChapterFilter) {
        ChapterFilterDialog(
            filter = state.chapterFilter,
            scanlators = state.chapters.mapNotNull { it.scanlator }.distinct().sorted(),
            onApply = { newFilter -> onEvent(DetailsContract.Event.SetChapterFilter(newFilter)) },
            onDismiss = { onEvent(DetailsContract.Event.HideChapterFilter) }
        )
    }

    if (state.showCategoryPickerDialog) {
        CategoryPickerDialog(
            categories = state.libraryCategories,
            selectedIds = state.categoryPickerSelection,
            onToggle = { categoryId -> onEvent(DetailsContract.Event.ToggleCategoryPickerSelection(categoryId)) },
            onConfirm = { onEvent(DetailsContract.Event.ConfirmCategoryPicker) },
            onDismiss = { onEvent(DetailsContract.Event.DismissCategoryPicker) }
        )
    }

    if (state.showReadingListPickerDialog) {
        ReadingListPickerDialog(
            readingLists = state.readingLists,
            selectedIds = state.readingListPickerSelection,
            onToggle = { listId -> onEvent(DetailsContract.Event.ToggleReadingListPickerSelection(listId)) },
            onDismiss = { onEvent(DetailsContract.Event.DismissReadingListPicker) }
        )
    }

    state.anilistPicker?.let { picker ->
        AniListPickerDialog(
            picker = picker,
            onQueryChange = { onEvent(DetailsContract.Event.SetAniListPickerQuery(it)) },
            onSearch = { onEvent(DetailsContract.Event.SubmitAniListPickerSearch) },
            onSelect = { onEvent(DetailsContract.Event.SelectAniListCandidate(it)) },
            onDismiss = { onEvent(DetailsContract.Event.DismissAniListPicker) },
        )
    }

    if (state.isEditInfoSheetVisible) {
        EditMangaInfoSheet(
            manga = manga,
            onSave = { title, description, author, artist, thumbnailUrl, genres, status ->
                onEvent(
                    DetailsContract.Event.SaveMangaInfo(
                        title = title,
                        description = description,
                        author = author,
                        artist = artist,
                        thumbnailUrl = thumbnailUrl,
                        genres = genres,
                        status = status,
                    )
                )
            },
            onReset = { onEvent(DetailsContract.Event.ResetMangaInfo) },
            onDismiss = { onEvent(DetailsContract.Event.HideEditInfoSheet) },
        )
    }
}

/** Manga header, description, notes, source suggestions, and per-manga options (tablet two-pane). */
private fun LazyListScope.detailsInfoItems(
    manga: app.otakureader.domain.model.Manga,
    state: DetailsContract.State,
    onEvent: (DetailsContract.Event) -> Unit,
    scrollOffset: () -> Float = { 0f },
) {
    item {
        MangaHeader(
            manga = manga,
            showPanoramaCover = state.showPanoramaCover,
            onTogglePanoramaCover = { onEvent(DetailsContract.Event.TogglePanoramaCover) },
            onSearchGlobal = { query -> onEvent(DetailsContract.Event.SearchGlobally(query)) },
            sourceName = state.sourceName,
            onSourceClick = { onEvent(DetailsContract.Event.SourceClick) },
            scrollOffset = scrollOffset,
        )
    }
    item {
        MangaActionRow(
            isFavorite = state.isFavorite,
            trackingCount = state.trackingCount,
            webUrl = state.mangaWebUrl,
            onToggleFavorite = { onEvent(DetailsContract.Event.ToggleFavorite) },
            onOpenTracking = { onEvent(DetailsContract.Event.OpenTracking) },
            onOpenWebView = { onEvent(DetailsContract.Event.OpenWebView) },
        )
    }
    detailsInfoTabItems(manga = manga, state = state, onEvent = onEvent)
}

/** Info tab content: description, notes, suggestions, and reader settings (without MangaHeader). */
private fun LazyListScope.detailsInfoTabItems(
    manga: app.otakureader.domain.model.Manga,
    state: DetailsContract.State,
    onEvent: (DetailsContract.Event) -> Unit,
) {
    if (state.trackEntries.isNotEmpty()) {
        item(key = "tracker_chips") {
            TrackerChips(
                entries = state.trackEntries,
                trackerNames = state.trackerNames,
                onOpenTracking = { onEvent(DetailsContract.Event.OpenTracking) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }

    item {
        // AniList's description fills in only where the source left a gap. Showing both would put
        // a third party's synopsis directly beneath one the user may have edited by hand.
        MangaDescription(
            description = manga.description?.takeIf { it.isNotBlank() } ?: state.metadata?.description,
            expanded = state.descriptionExpanded,
            onToggle = { onEvent(DetailsContract.Event.ToggleDescription) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    // Same rule for genres: the source's list wins, AniList's is the fallback, and the two are
    // never concatenated — a chip row of duplicates with different spellings helps nobody.
    val genres = manga.genre.ifEmpty { state.metadata?.genres.orEmpty() }
    if (genres.isNotEmpty()) {
        item {
            MangaGenreChips(
                genres = genres,
                onGenreClick = { onEvent(DetailsContract.Event.GenreClick(it)) },
                onGenreLongClick = { onEvent(DetailsContract.Event.GenreLongClick(it)) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }

    item(key = "anilist_metadata") {
        AniListMetadataSection(
            metadata = state.metadata,
            isLoading = state.isMetadataLoading,
            // A tag behaves exactly like a genre: tap searches this source, long-press searches
            // every source. Reusing the events keeps that promise true by construction.
            onTagClick = { onEvent(DetailsContract.Event.GenreClick(it)) },
            onTagLongClick = { onEvent(DetailsContract.Event.GenreLongClick(it)) },
            // A relation is an AniList media id with no local record, so the only honest action is
            // to go looking for it across the user's sources — same effect the tag chips use.
            onRelationClick = { onEvent(DetailsContract.Event.RelatedMangaClick(it)) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    item {
        MangaNotes(
            notes = manga.notes,
            onEditClick = { onEvent(DetailsContract.Event.ShowNoteEditor) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }

    item {
        SourceSuggestionsSection(
            suggestions = state.sourceSuggestions,
            isLoading = state.isLoadingSourceSuggestions,
            error = state.sourceSuggestionsError,
            onSuggestionClick = { suggestion ->
                onEvent(DetailsContract.Event.OnSourceSuggestionClick(suggestion))
            },
            onLoadClick = { onEvent(DetailsContract.Event.LoadSourceSuggestions) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }

    detailsOptionItems(manga = manga, state = state, onEvent = onEvent)
}

/**
 * The per-manga settings that live below the info content: download retention, notifications, and
 * reader overrides. Split out of [detailsInfoTabItems] because they are settings rather than
 * information about the manga, and keeping them together made that function too long to read.
 */
private fun LazyListScope.detailsOptionItems(
    manga: app.otakureader.domain.model.Manga,
    state: DetailsContract.State,
    onEvent: (DetailsContract.Event) -> Unit,
) {
    item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }

    item {
        DeleteAfterReadOption(
            override = state.deleteAfterReadOverride,
            globalEnabled = state.globalDeleteAfterRead,
            onChange = { onEvent(DetailsContract.Event.SetDeleteAfterReadOverride(it)) }
        )
    }

    item {
        NotificationOption(
            notifyEnabled = manga.notifyNewChapters,
            onToggle = { onEvent(DetailsContract.Event.ToggleNotifications) }
        )
    }

    item {
        ReaderSettingsSection(
            manga = manga,
            onEvent = onEvent
        )
    }
}

@Composable
private fun MangaActionRow(
    isFavorite: Boolean,
    trackingCount: Int,
    webUrl: String?,
    onToggleFavorite: () -> Unit,
    onOpenTracking: () -> Unit,
    onOpenWebView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = MANGA_ACTION_INACTIVE_ALPHA)
    val primaryColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, end = 16.dp),
    ) {
        MangaActionButton(
            title = if (isFavorite) stringResource(R.string.details_in_library)
                    else stringResource(R.string.details_add_to_library),
            icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            color = if (isFavorite) primaryColor else inactiveColor,
            onClick = onToggleFavorite,
        )
        MangaActionButton(
            title = if (trackingCount > 0)
                pluralStringResource(R.plurals.details_tracking_count, trackingCount, trackingCount)
            else
                stringResource(R.string.details_action_tracking),
            icon = if (trackingCount > 0) Icons.Default.Done else Icons.Default.QueryStats,
            color = if (trackingCount > 0) primaryColor else inactiveColor,
            onClick = onOpenTracking,
        )
        if (webUrl != null) {
            MangaActionButton(
                title = stringResource(R.string.details_action_webview),
                icon = Icons.Default.Language,
                color = inactiveColor,
                onClick = onOpenWebView,
            )
        }
    }
}

@Composable
private fun RowScope.MangaActionButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = color,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailsStatsRow(state: DetailsContract.State, modifier: Modifier = Modifier) {
    val totalChapters = state.chapters.size
    val unreadCount = state.chapters.count { !it.read }
    val progressFraction = if (totalChapters > 0) {
        (totalChapters - unreadCount).coerceAtLeast(0).toFloat() / totalChapters
    } else 0f

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier.fillMaxWidth().padding(vertical = 16.dp),
    ) {
        DetailsStatColumn(label = stringResource(R.string.details_stats_chapters), value = totalChapters.toString())
        DetailsStatColumn(label = stringResource(R.string.details_stats_unread), value = unreadCount.toString())
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier.size(52.dp),
                strokeWidth = 4.dp,
            )
            Text(
                text = "${(progressFraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun DetailsStatColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Chapter list header followed by the sorted chapter rows. */
private fun LazyListScope.detailsChapterItems(
    state: DetailsContract.State,
    onEvent: (DetailsContract.Event) -> Unit,
) {
    item {
        ChapterListHeader(
            chapterCount = state.chapters.size,
            sortOrder = state.chapterSortOrder,
            isFilterActive = state.chapterFilter.isActive,
            estimatedRemainingTimeMs = state.estimatedRemainingTimeMs,
            missingChapterCount = state.missingChapterCount,
            chapterSearchQuery = state.chapterFilter.chapterSearchQuery,
            onSearchQueryChange = { q -> onEvent(DetailsContract.Event.SetChapterSearchQuery(q)) },
            onToggleSort = { onEvent(DetailsContract.Event.ToggleSortOrder) },
            onShowFilter = { onEvent(DetailsContract.Event.ShowChapterFilter) }
        )
    }

    items(state.sortedChapters, key = { it.id }) { chapter ->
        ChapterListItem(
            chapter = chapter,
            isSelected = state.selectedChapters.contains(chapter.id),
            onClick = { onEvent(DetailsContract.Event.ChapterClick(chapter.id)) },
            onLongClick = { onEvent(DetailsContract.Event.ChapterLongClick(chapter.id)) },
            onToggleRead = { onEvent(DetailsContract.Event.ToggleChapterRead(chapter.id)) },
            onDownload = { onEvent(DetailsContract.Event.DownloadChapter(chapter.id)) },
            onDeleteDownload = { onEvent(DetailsContract.Event.DeleteChapterDownload(chapter.id)) },
            onMarkPreviousRead = { onEvent(DetailsContract.Event.MarkPreviousAsRead(chapter.id)) },
            onExportAsCbz = { onEvent(DetailsContract.Event.ExportChapterAsCbz(chapter.id)) },
            onEditNote = { onEvent(DetailsContract.Event.ShowChapterNoteEditor(chapter.id)) },
            onLoadThumbnail = { onEvent(DetailsContract.Event.LoadChapterThumbnail(chapter.id)) },
        )
    }
}

@Composable
private fun MangaNotes(
    notes: String?,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.notes_section_title),
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.notes_edit_content_description)
                )
            }
        }

        if (!notes.isNullOrBlank()) {
            Text(
                text = renderMarkdown(notes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        } else {
            Text(
                text = stringResource(R.string.notes_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoteEditorDialog(
    noteText: String,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    @StringRes titleRes: Int = R.string.notes_editor_dialog_title
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(
                value = noteText,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.notes_editor_placeholder)) },
                minLines = 5,
                maxLines = 12
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.notes_editor_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_editor_cancel)) }
        }
    )
}

/**
 * Shown right after favoriting when the user has at least one category, so the manga can be
 * filed away immediately instead of always landing uncategorized (matches Mihon/Komikku's
 * "choose a category" dialog on add-to-library).
 */
@Composable
private fun CategoryPickerDialog(
    categories: List<Category>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_category_picker_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(categories, key = { it.id }) { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(category.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = category.id in selectedIds,
                            onCheckedChange = { onToggle(category.id) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = category.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.details_category_picker_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.details_category_picker_skip)) }
        }
    )
}

/**
 * Opened on demand from the overflow menu's "Add to Reading List" item. Unlike
 * [CategoryPickerDialog], each checkbox toggle persists immediately (add/remove from the list)
 * rather than batching on a confirm button — [selectedIds] reflects live membership.
 */
@Composable
private fun ReadingListPickerDialog(
    readingLists: List<ReadingList>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_reading_list_picker_title)) },
        text = {
            if (readingLists.isEmpty()) {
                Text(stringResource(R.string.details_reading_list_picker_empty))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(readingLists, key = { it.id }) { list ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(list.id) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = list.id in selectedIds,
                                onCheckedChange = { onToggle(list.id) },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = list.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.details_reading_list_picker_done)) }
        }
    )
}

/**
 * Genre/tag chips shown under the description. Matches Mihon/Komikku: short-press searches the
 * tag within the manga's source, long-press searches it across all sources (global search).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MangaGenreChips(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
    onGenreLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (genres.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GENRE_CHIP_SPACING),
        verticalArrangement = Arrangement.spacedBy(GENRE_CHIP_SPACING),
    ) {
        genres.forEach { genre ->
            GenreChip(
                text = genre,
                onClick = { onGenreClick(genre) },
                onLongClick = { onGenreLongClick(genre) },
            )
        }
    }
}

/** Also used for AniList tag chips, so a tag and a genre are visually the same kind of thing. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GenreChip(
    text: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(
                horizontal = GENRE_CHIP_PADDING_HORIZONTAL,
                vertical = GENRE_CHIP_PADDING_VERTICAL,
            ),
        )
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
                Text(if (expanded) stringResource(R.string.details_show_less) else stringResource(R.string.details_show_more))
            }
        }
    }
}


@Preview(showBackground = true, backgroundColor = 0xFF12121A)
@Composable
private fun MangaDescriptionPreview() {
    OtakuReaderTheme {
        MangaDescription(
            description = "A young man is reincarnated into another world as an overpowered mage. " +
                "He must navigate a dangerous world of monsters and politics while hiding his true power.",
            expanded = false,
            onToggle = {}
        )
    }
}
