package app.otakureader.feature.updates.errors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.stickyHeader
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.domain.model.UpdateError
import app.otakureader.feature.updates.R
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val errorDateFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

private fun formatErrorTimestamp(epochMs: Long): String = runCatching {
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(errorDateFormatter)
}.getOrDefault("Unknown date")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateErrorsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToManga: (Long) -> Unit,
    onNavigateToMigration: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UpdateErrorsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    var showClearAllConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = state.isSelectionMode) {
        viewModel.onEvent(UpdateErrorsEvent.ClearSelection)
    }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is UpdateErrorsEffect.NavigateToManga -> onNavigateToManga(effect.mangaId)
                is UpdateErrorsEffect.NavigateToMigration -> onNavigateToMigration(effect.mangaIds)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (state.isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.updates_selected_count, state.selectedMangaIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.onEvent(UpdateErrorsEvent.ClearSelection) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.updates_clear_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.onEvent(UpdateErrorsEvent.SelectAll) }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.updates_select_all))
                        }
                        IconButton(onClick = { viewModel.onEvent(UpdateErrorsEvent.InvertSelection) }) {
                            Icon(Icons.Default.FlipToBack, contentDescription = stringResource(R.string.updates_invert_selection))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.updates_error_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.updates_back))
                        }
                    },
                    actions = {
                        if (!state.isEmpty) {
                            TextButton(onClick = { showClearAllConfirm = true }) {
                                Text(stringResource(R.string.updates_error_clear_all))
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (state.isSelectionMode) {
                UpdateErrorsSelectionBar(
                    onMigrate = { viewModel.onEvent(UpdateErrorsEvent.MigrateSelected) },
                    onDelete = { viewModel.onEvent(UpdateErrorsEvent.DeleteSelected) },
                )
            }
        }
    ) { paddingValues ->
        if (state.isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.updates_error_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                state.errorsByMessage.forEach { (message, errors) ->
                    stickyHeader(key = "header_$message") {
                        UpdateErrorGroupHeader(message = message, count = errors.size)
                    }
                    items(errors, key = { it.mangaId }) { error ->
                        UpdateErrorCard(
                            error = error,
                            isSelected = error.mangaId in state.selectedMangaIds,
                            isSelectionMode = state.isSelectionMode,
                            onClick = { viewModel.onEvent(UpdateErrorsEvent.CardClick(error.mangaId)) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.onEvent(UpdateErrorsEvent.ToggleSelection(error.mangaId))
                            },
                            onDismiss = { viewModel.onEvent(UpdateErrorsEvent.DeleteError(error.mangaId)) },
                        )
                    }
                }
            }
        }
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(stringResource(R.string.updates_error_clear_all)) },
            text = { Text(stringResource(R.string.update_errors_clear_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearAllConfirm = false
                    viewModel.onEvent(UpdateErrorsEvent.ClearAll)
                }) {
                    Text(stringResource(R.string.updates_error_clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(stringResource(R.string.downloads_cancel))
                }
            }
        )
    }
}

@Composable
private fun UpdateErrorGroupHeader(message: String, count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateErrorCard(
    error: UpdateError,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.updates_error_clear),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(width = 48.dp, height = 64.dp),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(error.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = error.mangaTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = error.mangaTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (error.timestamp > 0L) {
                    Text(
                        text = formatErrorTimestamp(error.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!isSelectionMode) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = stringResource(R.string.updates_error_clear),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateErrorsSelectionBar(
    onMigrate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SelectionBarAction(
                icon = Icons.AutoMirrored.Filled.CompareArrows,
                label = stringResource(R.string.update_errors_migrate_selected),
                onClick = onMigrate,
            )
            SelectionBarAction(
                icon = Icons.Default.Delete,
                label = stringResource(R.string.update_errors_delete_selected),
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun SelectionBarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = {}).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(imageVector = icon, contentDescription = label)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}
