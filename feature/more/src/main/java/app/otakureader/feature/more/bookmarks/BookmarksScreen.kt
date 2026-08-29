package app.otakureader.feature.more.bookmarks

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import app.otakureader.domain.model.BookmarkCollection
import app.otakureader.feature.more.R
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest

private const val SWIPE_DISMISS_THRESHOLD = 0.4f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onNavigateBack: () -> Unit,
    onOpenBookmark: (mangaId: Long, chapterId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is BookmarksEffect.NavigateToReader ->
                    onOpenBookmark(effect.mangaId, effect.chapterId)
                is BookmarksEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(effect.message)
                is BookmarksEffect.ExportComplete ->
                    snackbarHostState.showSnackbar(
                        if (effect.failed == 0) {
                            context.getString(R.string.bookmarks_export_complete, effect.saved)
                        } else {
                            // Naming the failures matters: a page whose extension is uninstalled or
                            // whose host is down cannot be exported, and silently reporting only
                            // the successes is how a partial export reads as a complete one.
                            context.getString(
                                R.string.bookmarks_export_partial,
                                effect.saved,
                                effect.failed,
                            )
                        }
                    )
                BookmarksEffect.ExportUnsupported ->
                    snackbarHostState.showSnackbar(context.getString(R.string.bookmarks_export_unsupported))
                is BookmarksEffect.ShareImages -> {
                    val uris = ArrayList(effect.uris.map(Uri::parse))
                    val intent = if (uris.size == 1) {
                        Intent(Intent.ACTION_SEND).apply {
                            type = IMAGE_MIME_TYPE
                            putExtra(Intent.EXTRA_STREAM, uris.first())
                        }
                    } else {
                        // ACTION_SEND with a single-element list is accepted by fewer targets than
                        // ACTION_SEND, and ACTION_SEND_MULTIPLE with one item by fewer still, so
                        // each count gets the action receivers actually advertise.
                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = IMAGE_MIME_TYPE
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        }
                    }
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching {
                        context.startActivity(Intent.createChooser(intent, null))
                    }.onFailure {
                        snackbarHostState.showSnackbar(context.getString(R.string.bookmarks_share_error))
                    }
                    if (effect.failed > 0) {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.bookmarks_share_partial, effect.failed)
                        )
                    }
                }
            }
        }
    }

    if (state.isManageCollectionsVisible) {
        ManageCollectionsDialog(
            collections = state.collections,
            onCreateCollection = { name -> viewModel.onIntent(BookmarksIntent.CreateCollection(name)) },
            onRenameCollection = { id, name -> viewModel.onIntent(BookmarksIntent.RenameCollection(id, name)) },
            onDeleteCollection = { id -> viewModel.onIntent(BookmarksIntent.DeleteCollection(id)) },
            onDismiss = { viewModel.onIntent(BookmarksIntent.HideManageCollections) },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (state.isSelectionMode) {
                SelectionTopBar(
                    selectedCount = state.selectedBookmarkIds.size,
                    onClearSelection = { viewModel.onIntent(BookmarksIntent.ClearSelection) },
                    onSelectAll = { viewModel.onIntent(BookmarksIntent.SelectAllBookmarks) },
                    onExport = { viewModel.onIntent(BookmarksIntent.ExportSelected) },
                    onShare = { viewModel.onIntent(BookmarksIntent.ShareSelected) },
                    isExporting = state.isExporting,
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.bookmarks_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.bookmarks_back),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.onIntent(BookmarksIntent.ShowManageCollections) }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.bookmarks_manage_collections),
                            )
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onIntent(BookmarksIntent.SearchQueryChanged(it)) },
                placeholder = { Text(stringResource(R.string.bookmarks_search_placeholder)) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Bookmark, contentDescription = null)
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewModel.onIntent(BookmarksIntent.SearchQueryChanged(""))
                            },
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.bookmarks_clear_search),
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Collection filter chips — only shown when collections exist.
            if (state.collections.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedCollectionId == null,
                            onClick = { viewModel.onIntent(BookmarksIntent.SelectCollection(null)) },
                            label = { Text(stringResource(R.string.bookmarks_collection_all)) },
                        )
                    }
                    items(state.collections, key = { it.id }) { collection ->
                        FilterChip(
                            selected = state.selectedCollectionId == collection.id,
                            onClick = { viewModel.onIntent(BookmarksIntent.SelectCollection(collection.id)) },
                            label = { Text(collection.name) },
                        )
                    }
                }
            }

            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.isEmpty -> EmptyBookmarksState(
                    hasQuery = state.searchQuery.isNotEmpty(),
                )

                else -> BookmarkGroupedList(
                    groups = state.grouped,
                    selectedBookmarkIds = state.selectedBookmarkIds,
                    isSelectionMode = state.isSelectionMode,
                    onToggleExpand = { mangaId ->
                        viewModel.onIntent(BookmarksIntent.ToggleMangaExpanded(mangaId))
                    },
                    onOpenBookmark = { mangaId, chapterId ->
                        viewModel.onIntent(BookmarksIntent.OpenBookmark(mangaId, chapterId))
                    },
                    onDeleteBookmark = { item ->
                        viewModel.onIntent(BookmarksIntent.DeleteBookmark(item))
                    },
                    onToggleSelection = { id ->
                        viewModel.onIntent(BookmarksIntent.ToggleBookmarkSelection(id))
                    },
                    onLongPressBookmark = { id ->
                        viewModel.onIntent(BookmarksIntent.ToggleBookmarkSelection(id))
                    },
                )
            }
        }
    }
}

// ─── Selection top bar ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    isExporting: Boolean,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.bookmarks_selected_count, selectedCount)) },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.bookmarks_clear_selection))
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.bookmarks_select_all))
            }
            // A page that is not downloaded is fetched from its source, so a large selection can
            // take seconds. Showing progress here — and refusing a second tap meanwhile — is what
            // keeps these from looking like the do-nothing buttons they used to be.
            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.size(EXPORT_PROGRESS_SIZE).padding(end = 8.dp))
            }
            IconButton(onClick = onExport, enabled = !isExporting) {
                Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.bookmarks_export))
            }
            IconButton(onClick = onShare, enabled = !isExporting) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.bookmarks_share))
            }
        },
    )
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyBookmarksState(
    hasQuery: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (hasQuery) {
                stringResource(R.string.bookmarks_no_results)
            } else {
                stringResource(R.string.bookmarks_empty)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Grouped list ─────────────────────────────────────────────────────────────

@Composable
private fun BookmarkGroupedList(
    groups: List<BookmarkGroup>,
    selectedBookmarkIds: Set<Long>,
    isSelectionMode: Boolean,
    onToggleExpand: (mangaId: Long) -> Unit,
    onOpenBookmark: (mangaId: Long, chapterId: Long) -> Unit,
    onDeleteBookmark: (BookmarkItem) -> Unit,
    onToggleSelection: (id: Long) -> Unit,
    onLongPressBookmark: (id: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        groups.forEach { group ->
            item(key = "header_${group.mangaId}") {
                MangaGroupHeader(
                    group = group,
                    onToggleExpand = { onToggleExpand(group.mangaId) },
                )
            }

            if (group.isExpanded) {
                group.chapters.forEach { chapterGroup ->
                    item(key = "chapter_${chapterGroup.chapterId}") {
                        ChapterSubHeader(chapterName = chapterGroup.chapterName)
                    }

                    items(
                        items = chapterGroup.bookmarks,
                        key = { bm -> "bookmark_${bm.id}" },
                    ) { bookmark ->
                        if (isSelectionMode) {
                            SelectableBookmarkRow(
                                bookmark = bookmark,
                                isSelected = bookmark.id in selectedBookmarkIds,
                                onToggleSelection = { onToggleSelection(bookmark.id) },
                            )
                        } else {
                            SwipeToDismissBookmarkRow(
                                bookmark = bookmark,
                                onOpen = { onOpenBookmark(bookmark.mangaId, bookmark.chapterId) },
                                onDelete = { onDeleteBookmark(bookmark) },
                                onLongPress = { onLongPressBookmark(bookmark.id) },
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }

            item(key = "divider_${group.mangaId}") {
                HorizontalDivider(thickness = 2.dp)
            }
        }
    }
}

// ─── Manga group header ───────────────────────────────────────────────────────

@Composable
private fun MangaGroupHeader(
    group: BookmarkGroup,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(width = 40.dp, height = 56.dp),
        ) {
            AsyncImage(
                model = group.mangaCoverUrl,
                contentDescription = group.mangaTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(5f / 7f)
                    .clip(MaterialTheme.shapes.small),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.mangaTitle.ifBlank {
                    stringResource(R.string.bookmarks_unknown_manga)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
            )
            val count = group.chapters.sumOf { it.bookmarks.size }
            Text(
                text = stringResource(R.string.bookmarks_count, count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            imageVector = if (group.isExpanded) {
                Icons.Default.KeyboardArrowUp
            } else {
                Icons.Default.KeyboardArrowDown
            },
            contentDescription = if (group.isExpanded) {
                stringResource(R.string.bookmarks_collapse)
            } else {
                stringResource(R.string.bookmarks_expand)
            },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Chapter sub-header ───────────────────────────────────────────────────────

@Composable
private fun ChapterSubHeader(
    chapterName: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = chapterName.ifBlank { stringResource(R.string.bookmarks_chapter_fallback) },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 72.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
    )
}

// ─── Selectable row (multi-select mode) ──────────────────────────────────────

@Composable
private fun SelectableBookmarkRow(
    bookmark: BookmarkItem,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onToggleSelection),
        headlineContent = {
            Text(stringResource(R.string.bookmarks_page, bookmark.pageIndex + 1))
        },
        supportingContent = if (!bookmark.note.isNullOrBlank()) {
            { Text(bookmark.note, maxLines = 2) }
        } else null,
        trailingContent = {
            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection() })
        },
    )
}

// ─── Swipe-to-dismiss bookmark row ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissBookmarkRow(
    bookmark: BookmarkItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                false
            } else {
                false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * SWIPE_DISMISS_THRESHOLD },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.bookmarks_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        BookmarkRow(
            bookmark = bookmark,
            onOpen = onOpen,
            onDelete = onDelete,
            onLongPress = onLongPress,
        )
    }
}

// ─── Bookmark row ─────────────────────────────────────────────────────────────

@Composable
private fun BookmarkRow(
    bookmark: BookmarkItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.combinedClickable(
            onClick = onOpen,
            onLongClick = onLongPress,
        ),
        headlineContent = {
            Text(stringResource(R.string.bookmarks_page, bookmark.pageIndex + 1))
        },
        supportingContent = if (!bookmark.note.isNullOrBlank()) {
            { Text(bookmark.note, maxLines = 2) }
        } else {
            null
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.bookmarks_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

// ─── Manage Collections dialog ────────────────────────────────────────────────

@Composable
private fun ManageCollectionsDialog(
    collections: List<BookmarkCollection>,
    onCreateCollection: (String) -> Unit,
    onRenameCollection: (Long, String) -> Unit,
    onDeleteCollection: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var newCollectionName by remember { mutableStateOf("") }
    var renamingId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bookmarks_manage_collections)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Create new collection
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newCollectionName,
                        onValueChange = { newCollectionName = it },
                        placeholder = { Text(stringResource(R.string.bookmarks_new_collection_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (newCollectionName.isNotBlank()) {
                                    onCreateCollection(newCollectionName.trim())
                                    newCollectionName = ""
                                }
                            },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            if (newCollectionName.isNotBlank()) {
                                onCreateCollection(newCollectionName.trim())
                                newCollectionName = ""
                            }
                        },
                    ) { Text(stringResource(R.string.bookmarks_collection_create)) }
                }

                if (collections.isNotEmpty()) {
                    HorizontalDivider()
                    collections.forEach { collection ->
                        if (renamingId == collection.id) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = renameText,
                                    onValueChange = { renameText = it },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (renameText.isNotBlank()) {
                                                onRenameCollection(collection.id, renameText.trim())
                                                renamingId = null
                                            }
                                        },
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = {
                                    if (renameText.isNotBlank()) {
                                        onRenameCollection(collection.id, renameText.trim())
                                        renamingId = null
                                    }
                                }) { Text(stringResource(R.string.bookmarks_collection_rename_save)) }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = collection.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = {
                                    renamingId = collection.id
                                    renameText = collection.name
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.bookmarks_collection_rename))
                                }
                                IconButton(onClick = { onDeleteCollection(collection.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.bookmarks_collection_delete),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.bookmarks_collection_done))
            }
        },
    )
}

// ─── Navigation extension ─────────────────────────────────────────────────────

fun NavGraphBuilder.bookmarksScreen(
    onNavigateBack: () -> Unit,
    onOpenBookmark: (mangaId: Long, chapterId: Long) -> Unit,
) {
    composable<Route.Bookmarks> {
        BookmarksScreen(onNavigateBack = onNavigateBack, onOpenBookmark = onOpenBookmark)
    }
}

/** Everything the sharesheet is offered is an image; the exporter only produces image files. */
private const val IMAGE_MIME_TYPE = "image/*"

private val EXPORT_PROGRESS_SIZE = 20.dp
