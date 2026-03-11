package app.otakureader.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.core.ui.component.ErrorScreen
import app.otakureader.core.ui.component.LoadingScreen
import app.otakureader.feature.details.R
import app.otakureader.core.preferences.DeleteAfterReadMode
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest

private val MARKDOWN_BOLD_REGEX = Regex("""\*\*(.+?)\*\*""")
private val MARKDOWN_ITALIC_REGEX = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)""")
private val MARKDOWN_LINK_REGEX = Regex("""\[(.+?)]\((.+?)\)""")

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
    val context = LocalContext.current

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
                    IconButton(onClick = { viewModel.onEvent(DetailsContract.Event.ShareManga) }) {
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
            MangaNotes(
                notes = manga.notes,
                onEditClick = { onEvent(DetailsContract.Event.ShowNoteEditor) }
            )
        }

        item {
            HorizontalDivider()
        }

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
                isSelected = state.selectedChapters.contains(chapter.id),
                onClick = { onEvent(DetailsContract.Event.ChapterClick(chapter.id)) },
                onLongClick = { onEvent(DetailsContract.Event.ChapterLongClick(chapter.id)) },
                onExportAsCbz = { onEvent(DetailsContract.Event.ExportChapterAsCbz(chapter.id)) }
            )
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
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notes_editor_dialog_title)) },
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
 * Renders a subset of Markdown as an [androidx.compose.ui.text.AnnotatedString].
 * Supported syntax:
 *  - `**text**` → bold
 *  - `*text*`   → italic
 *  - `[label](url)` → underlined link text
 */
private fun renderMarkdown(source: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var remaining = source
        while (remaining.isNotEmpty()) {
            val boldMatch = MARKDOWN_BOLD_REGEX.find(remaining)
            val italicMatch = MARKDOWN_ITALIC_REGEX.find(remaining)
            val linkMatch = MARKDOWN_LINK_REGEX.find(remaining)

            // Find which match comes first
            val firstMatch = listOfNotNull(boldMatch, italicMatch, linkMatch)
                .minByOrNull { it.range.first }

            if (firstMatch == null) {
                append(remaining)
                break
            }

            // Append text before the match
            if (firstMatch.range.first > 0) {
                append(remaining.substring(0, firstMatch.range.first))
            }

            when (firstMatch) {
                boldMatch -> {
                    val boldText = firstMatch.groupValues[1]
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(boldText)
                    pop()
                }
                italicMatch -> {
                    val italicText = firstMatch.groupValues[1]
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(italicText)
                    pop()
                }
                linkMatch -> {
                    val label = firstMatch.groupValues[1]
                    pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                    append(label)
                    pop()
                }
            }

            remaining = remaining.substring(firstMatch.range.last + 1)
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
private fun DeleteAfterReadOption(
    override: DeleteAfterReadMode,
    globalEnabled: Boolean,
    onChange: (DeleteAfterReadMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        ListItem(
            headlineContent = { Text("Delete downloads after reading") },
            supportingContent = {
                Column(modifier = Modifier.selectableGroup()) {
                    val options = listOf(
                        "Follow global (${if (globalEnabled) "On" else "Off"})" to DeleteAfterReadMode.INHERIT,
                        "On for this manga" to DeleteAfterReadMode.ENABLED,
                        "Off for this manga" to DeleteAfterReadMode.DISABLED
                    )
                    options.forEach { (label, value) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = override == value,
                                    onClick = { onChange(value) },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = override == value,
                                onClick = null
                            )
                            Text(
                                text = label,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun NotificationOption(
    notifyEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text("Notify for new chapters") },
        supportingContent = {
            Text(
                if (notifyEnabled) "You will be notified when new chapters are found"
                else "Notifications are muted for this manga"
            )
        },
        leadingContent = {
            Icon(
                imageVector = if (notifyEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                contentDescription = null,
                tint = if (notifyEnabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = notifyEnabled,
                onCheckedChange = { onToggle() }
            )
        },
        modifier = modifier
    )
}

/**
 * Reader settings section for per-manga configuration (#260, #264)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderSettingsSection(
    manga: app.otakureader.domain.model.Manga,
    onEvent: (DetailsContract.Event) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        ListItem(
            headlineContent = { Text("Reader Settings") },
            supportingContent = {
                val hasOverrides = manga.readerDirection != null ||
                                   manga.readerMode != null ||
                                   manga.readerColorFilter != null ||
                                   manga.readerCustomTintColor != null ||
                                   manga.readerBackgroundColor != null ||
                                   manga.preloadPagesBefore != null ||
                                   manga.preloadPagesAfter != null
                Text(
                    if (hasOverrides) "Custom settings applied"
                    else "Using default settings"
                )
            },
            trailingContent = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
        )

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Reading Direction
                Text(
                    text = "Reading Direction",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.selectableGroup()) {
                    DirectionOption("Left to Right", 0, manga.readerDirection, onEvent)
                    DirectionOption("Right to Left", 1, manga.readerDirection, onEvent)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reader Mode
                Text(
                    text = "Reader Mode",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReaderModeOption("Single Page", 0, manga.readerMode, onEvent)
                    ReaderModeOption("Dual Page", 1, manga.readerMode, onEvent)
                    ReaderModeOption("Webtoon", 2, manga.readerMode, onEvent)
                    ReaderModeOption("Smart Panels", 3, manga.readerMode, onEvent)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Color Filter
                Text(
                    text = "Color Filter",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ColorFilterOption("None", 0, manga.readerColorFilter, onEvent)
                    ColorFilterOption("Sepia", 1, manga.readerColorFilter, onEvent)
                    ColorFilterOption("Greyscale", 2, manga.readerColorFilter, onEvent)
                    ColorFilterOption("Invert", 3, manga.readerColorFilter, onEvent)
                    ColorFilterOption("Custom Tint", 4, manga.readerColorFilter, onEvent)
                }

                // Custom Tint Color Picker (shown when Custom Tint is selected)
                if (manga.readerColorFilter == 4) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CustomTintColorPicker(
                        currentColor = manga.readerCustomTintColor,
                        onColorChange = { onEvent(DetailsContract.Event.SetReaderCustomTintColor(it)) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Background Color
                Text(
                    text = "Background Color",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                BackgroundColorPicker(
                    currentColor = manga.readerBackgroundColor,
                    onColorChange = { onEvent(DetailsContract.Event.SetReaderBackgroundColor(it)) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Preload Pages
                Text(
                    text = "Page Preloading",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PreloadOption(
                    label = "Pages before current",
                    value = manga.preloadPagesBefore,
                    onChange = { onEvent(DetailsContract.Event.SetPreloadPagesBefore(it)) }
                )
                PreloadOption(
                    label = "Pages after current",
                    value = manga.preloadPagesAfter,
                    onChange = { onEvent(DetailsContract.Event.SetPreloadPagesAfter(it)) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Reset button
                TextButton(
                    onClick = {
                        onEvent(DetailsContract.Event.SetReaderDirection(null))
                        onEvent(DetailsContract.Event.SetReaderMode(null))
                        onEvent(DetailsContract.Event.SetReaderColorFilter(null))
                        onEvent(DetailsContract.Event.SetReaderCustomTintColor(null))
                        onEvent(DetailsContract.Event.SetReaderBackgroundColor(null))
                        onEvent(DetailsContract.Event.SetPreloadPagesBefore(null))
                        onEvent(DetailsContract.Event.SetPreloadPagesAfter(null))
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Reset to defaults")
                }
            }
        }
    }
}

@Composable
private fun DirectionOption(
    label: String,
    value: Int,
    currentValue: Int?,
    onEvent: (DetailsContract.Event) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .selectable(
                selected = currentValue == value,
                onClick = { onEvent(DetailsContract.Event.SetReaderDirection(value)) },
                role = Role.RadioButton
            )
            .padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        RadioButton(
            selected = currentValue == value,
            onClick = null
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun PreloadOption(
    label: String,
    value: Int?,
    onChange: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { onChange((value ?: 0).coerceAtLeast(0) - 1) },
                enabled = (value ?: 0) > 0
            ) {
                Text("-")
            }
            Text(
                text = (value ?: 0).toString(),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            TextButton(
                onClick = { onChange((value ?: 0).coerceAtMost(9) + 1) },
                enabled = (value ?: 0) < 10
            ) {
                Text("+")
            }
        }
    }
}

@Composable
private fun ReaderModeOption(
    label: String,
    value: Int,
    currentValue: Int?,
    onEvent: (DetailsContract.Event) -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = currentValue == value,
        onClick = { onEvent(DetailsContract.Event.SetReaderMode(value)) },
        label = { Text(label) },
        modifier = modifier
    )
}

@Composable
private fun ColorFilterOption(
    label: String,
    value: Int,
    currentValue: Int?,
    onEvent: (DetailsContract.Event) -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = currentValue == value,
        onClick = { onEvent(DetailsContract.Event.SetReaderColorFilter(value)) },
        label = { Text(label) },
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomTintColorPicker(
    currentColor: Long?,
    onColorChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Custom Tint Color",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presetColors = listOf(
                0xFFFF6B6B to "Red",
                0xFFFFA500 to "Orange",
                0xFFFFD93D to "Yellow",
                0xFF6BCB77 to "Green",
                0xFF4D96FF to "Blue",
                0xFF9D84B7 to "Purple",
                0xFFFFB6C1 to "Pink"
            )
            presetColors.forEach { (color, name) ->
                ColorChip(
                    color = color,
                    label = name,
                    selected = currentColor == color,
                    onClick = { onColorChange(color) }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(
            onClick = { onColorChange(null) },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Reset")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundColorPicker(
    currentColor: Long?,
    onColorChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Override reader background",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presetColors = listOf(
                0xFF000000 to "Black",
                0xFF1A1A1A to "Dark Gray",
                0xFF808080 to "Gray",
                0xFFFFFFFF to "White",
                0xFFFFF8DC to "Cream"
            )
            presetColors.forEach { (color, name) ->
                ColorChip(
                    color = color,
                    label = name,
                    selected = currentColor == color,
                    onClick = { onColorChange(color) }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(
            onClick = { onColorChange(null) },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Reset")
        }
    }
}

@Composable
private fun ColorChip(
    color: Long,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        color = Color(color),
                        shape = CircleShape
                    )
            )
        },
        modifier = modifier
    )
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
