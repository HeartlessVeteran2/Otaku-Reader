package app.otakureader.feature.settings.storage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import app.otakureader.feature.settings.R

fun NavGraphBuilder.storageAnalyticsScreen(onNavigateBack: () -> Unit) {
    composable<Route.StorageAnalytics> {
        StorageAnalyticsScreen(onNavigateBack = onNavigateBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalyticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StorageAnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val deletedLabel = stringResource(R.string.storage_delete_success)
    val deleteFailedLabel = stringResource(R.string.storage_delete_failure)
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is StorageAnalyticsEffect.DeleteSuccess ->
                    snackbarHostState.showSnackbar(deletedLabel.format(effect.mangaTitle))
                is StorageAnalyticsEffect.DeleteFailure ->
                    snackbarHostState.showSnackbar(deleteFailedLabel.format(effect.mangaTitle))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.storage_analytics_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onEvent(StorageAnalyticsEvent.Refresh) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.storage_analytics_refresh))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.sources.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.storage_analytics_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(contentPadding = padding) {
                item {
                    Text(
                        text = stringResource(R.string.storage_analytics_total, formatBytes(state.totalBytes)),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                    HorizontalDivider()
                }
                items(state.sources, key = { it.sourceName }) { source ->
                    SourceRow(
                        entry = source,
                        totalBytes = state.totalBytes,
                        expanded = source.sourceName in state.expandedSources,
                        onClick = { viewModel.onEvent(StorageAnalyticsEvent.ToggleSource(source.sourceName)) },
                        onEvent = { viewModel.onEvent(it) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    entry: SourceStorageEntry,
    totalBytes: Long,
    expanded: Boolean,
    onClick: () -> Unit,
    onEvent: (StorageAnalyticsEvent) -> Unit,
) {
    val barColor = MaterialTheme.colorScheme.primary
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = entry.sourceName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = formatBytes(entry.totalBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            // Mini bar showing proportion of total
            val fraction = if (totalBytes > 0) entry.totalBytes.toFloat() / totalBytes else 0f
            Canvas(modifier = Modifier.width(80.dp).height(8.dp)) {
                drawRect(color = barColor.copy(alpha = 0.2f), size = size)
                drawRect(
                    color = barColor,
                    topLeft = Offset.Zero,
                    size = Size(size.width * fraction.coerceIn(0f, 1f), size.height),
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                entry.manga.forEach { manga ->
                    MangaRow(entry = manga, onEvent = onEvent)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun MangaRow(
    entry: MangaStorageEntry,
    onEvent: (StorageAnalyticsEvent) -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatBytes(entry.totalBytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = { showDeleteConfirm = true }) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.storage_delete_title),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.storage_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.storage_delete_message,
                        entry.title,
                        formatBytes(entry.totalBytes),
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onEvent(StorageAnalyticsEvent.DeleteMangaDownloads(entry.sourceName, entry.title))
                    showDeleteConfirm = false
                }) {
                    Text(stringResource(R.string.storage_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.storage_delete_cancel))
                }
            },
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
