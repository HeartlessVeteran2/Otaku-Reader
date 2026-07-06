package app.otakureader.feature.library.maintenance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.feature.library.R
import kotlinx.coroutines.flow.collectLatest

/**
 * Library Maintenance screen.
 *
 * Displays a list of housekeeping actions the user can run against their library:
 * - Refresh covers
 * - Refresh metadata
 * - Reindex downloads
 * - Scan / delete orphaned files
 *
 * Each card shows a title, description, an action button, an in-progress indicator while
 * the task is running, and a result summary once the task completes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryMaintenanceScreen(
    onNavigateBack: () -> Unit,
    viewModel: LibraryMaintenanceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is LibraryMaintenanceEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_maintenance_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.merge_duplicates_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                MaintenanceCard(
                    title = stringResource(R.string.library_maintenance_refresh_covers),
                    description = stringResource(R.string.library_maintenance_refresh_covers_desc),
                    buttonLabel = stringResource(R.string.library_maintenance_refresh_covers),
                    isRunning = state.coverRefreshRunning,
                    result = state.coverRefreshResult,
                    onAction = { viewModel.onEvent(LibraryMaintenanceEvent.RefreshCovers) },
                )
            }

            item {
                MaintenanceCard(
                    title = stringResource(R.string.library_maintenance_refresh_metadata),
                    description = stringResource(R.string.library_maintenance_refresh_metadata_desc),
                    buttonLabel = stringResource(R.string.library_maintenance_refresh_metadata),
                    isRunning = state.metadataRefreshRunning,
                    result = state.metadataRefreshResult,
                    onAction = { viewModel.onEvent(LibraryMaintenanceEvent.RefreshMetadata) },
                )
            }

            item {
                MaintenanceCard(
                    title = stringResource(R.string.library_maintenance_reindex),
                    description = stringResource(R.string.library_maintenance_reindex_desc),
                    buttonLabel = stringResource(R.string.library_maintenance_reindex),
                    isRunning = state.reindexRunning,
                    result = state.reindexResult,
                    onAction = { viewModel.onEvent(LibraryMaintenanceEvent.ReindexDownloads) },
                )
            }

            item {
                OrphanCard(
                    isRunning = state.orphanScanRunning,
                    result = state.orphanScanResult,
                    orphanCount = state.orphanCount,
                    onScan = { viewModel.onEvent(LibraryMaintenanceEvent.ScanOrphans) },
                    onDelete = { viewModel.onEvent(LibraryMaintenanceEvent.DeleteOrphans) },
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * A card for a single-action maintenance task.
 *
 * @param title Heading shown in the card.
 * @param description Short explanation shown below the heading.
 * @param buttonLabel Text for the action button.
 * @param isRunning Whether the task is currently executing.
 * @param result Non-null when the last run has completed; displayed as a status line.
 * @param onAction Called when the user taps the action button.
 */
@Composable
private fun MaintenanceCard(
    title: String,
    description: String,
    buttonLabel: String,
    isRunning: Boolean,
    result: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (isRunning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!isRunning && result != null) {
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = onAction,
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(buttonLabel)
            }
        }
    }
}

/**
 * A card for the two-step orphan cleanup task (scan first, then delete).
 *
 * The Delete button is only shown when a scan has completed and found orphans.
 */
@Composable
private fun OrphanCard(
    isRunning: Boolean,
    result: String?,
    orphanCount: Int,
    onScan: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.library_maintenance_scan_orphans),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.library_maintenance_scan_orphans_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (isRunning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!isRunning && result != null) {
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onScan,
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.library_maintenance_scan_orphans))
                }

                // Show the delete button only after a scan has found orphaned files.
                if (orphanCount > 0) {
                    OutlinedButton(
                        onClick = onDelete,
                        enabled = !isRunning,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.library_maintenance_delete_orphans))
                    }
                }
            }
        }
    }
}
