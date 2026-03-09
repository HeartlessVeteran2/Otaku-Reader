package app.otakureader.feature.migration

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.domain.model.MigrationMode
import app.otakureader.domain.model.MigrationStatus
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationScreen(
    selectedMangaIds: List<Long>,
    onNavigateBack: () -> Unit,
    viewModel: MigrationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(selectedMangaIds) {
        viewModel.onEvent(MigrationEvent.Initialize(selectedMangaIds))
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is MigrationEffect.NavigateBack -> onNavigateBack()
                is MigrationEffect.ShowError -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is MigrationEffect.MigrationCompleted -> {
                    snackbarHostState.showSnackbar("Migration completed!")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mass Migration") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onEvent(MigrationEvent.NavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (state.isLoading && state.migrationTasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // Source selection
                Text(
                    text = "Select Target Source",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.3f)
                ) {
                    items(state.availableSources) { source ->
                        SourceSelectionItem(
                            source = source,
                            isSelected = source.id == state.selectedTargetSourceId,
                            onSelect = {
                                viewModel.onEvent(MigrationEvent.SelectTargetSource(source.id))
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Migration mode selection
                Text(
                    text = "Migration Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            viewModel.onEvent(MigrationEvent.SelectMigrationMode(MigrationMode.MOVE))
                        }
                    ) {
                        RadioButton(
                            selected = state.migrationMode == MigrationMode.MOVE,
                            onClick = {
                                viewModel.onEvent(MigrationEvent.SelectMigrationMode(MigrationMode.MOVE))
                            }
                        )
                        Text("Move")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            viewModel.onEvent(MigrationEvent.SelectMigrationMode(MigrationMode.COPY))
                        }
                    ) {
                        RadioButton(
                            selected = state.migrationMode == MigrationMode.COPY,
                            onClick = {
                                viewModel.onEvent(MigrationEvent.SelectMigrationMode(MigrationMode.COPY))
                            }
                        )
                        Text("Copy")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Migration tasks
                if (state.migrationTasks.isNotEmpty()) {
                    Text(
                        text = "Migration Progress (${state.currentTaskIndex + 1}/${state.migrationTasks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = {
                            if (state.migrationTasks.isEmpty()) 0f
                            else (state.currentTaskIndex + 1).toFloat() / state.migrationTasks.size
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier.weight(0.7f)
                    ) {
                        items(state.migrationTasks) { task ->
                            MigrationTaskItem(task = task)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Start button
                Button(
                    onClick = { viewModel.onEvent(MigrationEvent.StartMigration) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.selectedTargetSourceId != null && !state.isLoading
                ) {
                    Text(if (state.isLoading) "Migrating..." else "Start Migration")
                }
            }
        }
    }

    // Confirmation dialog
    if (state.showConfirmationDialog && state.currentCandidates.isNotEmpty()) {
        val currentTask = state.migrationTasks.getOrNull(state.currentTaskIndex)
        if (currentTask != null) {
            AlertDialog(
                onDismissRequest = { viewModel.onEvent(MigrationEvent.DismissConfirmationDialog) },
                title = { Text("Select Migration Target") },
                text = {
                    Column {
                        Text("Select the target manga for: ${currentTask.manga.title}")
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn {
                            items(state.currentCandidates) { candidate ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            viewModel.onEvent(
                                                MigrationEvent.ConfirmMigration(
                                                    currentTask.manga.id,
                                                    candidate
                                                )
                                            )
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = candidate.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Similarity: ${(candidate.similarityScore * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.onEvent(MigrationEvent.SkipManga(currentTask.manga.id))
                    }) {
                        Text("Skip")
                    }
                }
            )
        }
    }

    // Error dialog
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(MigrationEvent.DismissError) },
            title = { Text("Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(MigrationEvent.DismissError) }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun SourceSelectionItem(
    source: SourceItem,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = source.lang,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MigrationTaskItem(task: MigrationTaskItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            when (task.status) {
                MigrationStatus.PENDING -> Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Pending",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                MigrationStatus.SEARCHING, MigrationStatus.MIGRATING -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp)
                )
                MigrationStatus.COMPLETED -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                MigrationStatus.FAILED, MigrationStatus.SKIPPED -> Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Failed",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                MigrationStatus.AWAITING_CONFIRMATION -> Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Awaiting confirmation",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.manga.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val statusText = when (task.status) {
                    MigrationStatus.PENDING -> "Pending"
                    MigrationStatus.SEARCHING -> "Searching..."
                    MigrationStatus.AWAITING_CONFIRMATION -> "Awaiting confirmation"
                    MigrationStatus.MIGRATING -> "Migrating..."
                    MigrationStatus.COMPLETED -> "Completed (${task.chaptersMatched} chapters matched)"
                    MigrationStatus.FAILED -> "Failed: ${task.errorMessage}"
                    MigrationStatus.SKIPPED -> "Skipped"
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (task.status) {
                        MigrationStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        MigrationStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
