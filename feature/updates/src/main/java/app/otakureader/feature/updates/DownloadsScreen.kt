package app.otakureader.feature.updates

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.domain.model.DownloadItem
import app.otakureader.domain.model.DownloadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.selectedItems.isNotEmpty()) {
                        Text(stringResource(R.string.downloads_selected_count, state.selectedItems.size))
                    } else {
                        Text(stringResource(R.string.downloads_title))
                    }
                },
                navigationIcon = {
                    if (state.selectedItems.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onEvent(DownloadsEvent.ClearSelection) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.downloads_clear_selection))
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.downloads_back)
                            )
                        }
                    }
                },
                actions = {
                    if (state.selectedItems.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onEvent(DownloadsEvent.PauseSelected) }) {
                            Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.downloads_pause_selected))
                        }
                        IconButton(onClick = { viewModel.onEvent(DownloadsEvent.ResumeSelected) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.downloads_resume_selected))
                        }
                        IconButton(onClick = { viewModel.onEvent(DownloadsEvent.CancelSelected) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.downloads_cancel_selected))
                        }
                    } else {
                        if (state.hasDownloads) {
                            IconButton(onClick = { viewModel.onEvent(DownloadsEvent.SelectAll) }) {
                                Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.downloads_select_all))
                            }
                            TextButton(onClick = { viewModel.onEvent(DownloadsEvent.ClearAll) }) {
                                Text(stringResource(R.string.downloads_clear_all))
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.items.isEmpty()) {
            EmptyDownloadsPlaceholder(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = state.items,
                    key = { it.id }
                ) { item ->
                    DownloadListItem(
                        item = item,
                        isSelected = state.selectedItems.contains(item.id),
                        onClick = { viewModel.onEvent(DownloadsEvent.OnItemClick(item.id)) },
                        onLongClick = { viewModel.onEvent(DownloadsEvent.OnItemLongClick(item.id)) },
                        onPause = { viewModel.onEvent(DownloadsEvent.Pause(it)) },
                        onResume = { viewModel.onEvent(DownloadsEvent.Resume(it)) },
                        onCancel = { viewModel.onEvent(DownloadsEvent.Cancel(it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadListItem(
    item: DownloadItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isSelected) {
                    Checkbox(
                        checked = true,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.mangaTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.chapterTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isSelected) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        when (item.status) {
                            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                                IconButton(onClick = { onPause(item.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Pause,
                                        contentDescription = stringResource(R.string.downloads_pause),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            DownloadStatus.PAUSED -> {
                                IconButton(onClick = { onResume(item.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = stringResource(R.string.downloads_resume),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            else -> Unit
                        }

                        if (item.status != DownloadStatus.COMPLETED) {
                            IconButton(onClick = { onCancel(item.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.downloads_cancel),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            LinearProgressIndicator(
                progress = item.progress / 100f,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = statusLabel(item.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = when (item.status) {
                        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        DownloadStatus.PAUSED -> MaterialTheme.colorScheme.secondary
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DownloadDone,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${item.progress}%",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDownloadsPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.DownloadDone,
            contentDescription = stringResource(R.string.downloads_empty_icon),
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.downloads_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.downloads_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun statusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.QUEUED -> stringResource(R.string.downloads_status_queued)
    DownloadStatus.DOWNLOADING -> stringResource(R.string.downloads_status_downloading)
    DownloadStatus.PAUSED -> stringResource(R.string.downloads_status_paused)
    DownloadStatus.COMPLETED -> stringResource(R.string.downloads_status_completed)
    DownloadStatus.FAILED -> stringResource(R.string.downloads_status_failed)
    DownloadStatus.CANCELED -> stringResource(R.string.downloads_status_canceled)
}
