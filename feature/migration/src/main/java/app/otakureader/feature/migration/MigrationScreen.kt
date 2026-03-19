package app.otakureader.feature.migration

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.domain.model.MangaStatus
import app.otakureader.domain.model.MigrationCandidate
import app.otakureader.domain.model.MigrationMode
import app.otakureader.domain.model.MigrationStatus
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest

/** Converts a [MangaStatus] to a human-readable display string. */
private fun MangaStatus.toDisplayString(): String =
    name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }

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
                    // Summary is shown via showCompletionSummary flag
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.migration_title)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onEvent(MigrationEvent.NavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.migration_back))
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
                    text = stringResource(R.string.migration_select_target_source),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.3f)
                ) {
                    items(state.availableSources, key = { it.id }) { source ->
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
                    text = stringResource(R.string.migration_mode_title),
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
                        Text(stringResource(R.string.migration_mode_move))
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
                        Text(stringResource(R.string.migration_mode_copy))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Migration tasks
                if (state.migrationTasks.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.migration_progress, state.currentTaskIndex + 1, state.migrationTasks.size),
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
                        items(state.migrationTasks, key = { it.manga.id }) { task ->
                            MigrationTaskItem(task = task)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Show "Retry Failed" button when there are failed tasks and not currently migrating
                val hasFailedTasks = state.migrationTasks.any { it.status == MigrationStatus.FAILED }
                val isActive = state.isLoading || state.migrationTasks.any {
                    it.status == MigrationStatus.SEARCHING || it.status == MigrationStatus.MIGRATING
                }
                if (hasFailedTasks && !isActive) {
                    OutlinedButton(
                        onClick = { viewModel.onEvent(MigrationEvent.RetryFailed) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.migration_retry_failed))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Start button
                Button(
                    onClick = { viewModel.onEvent(MigrationEvent.StartMigration) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.selectedTargetSourceId != null && !state.isLoading
                ) {
                    Text(if (state.isLoading) stringResource(R.string.migration_migrating) else stringResource(R.string.migration_start))
                }
            }
        }
    }

    // Confirmation dialog with cover images and metadata
    if (state.showConfirmationDialog && state.currentCandidates.isNotEmpty()) {
        val currentTask = state.migrationTasks.getOrNull(state.currentTaskIndex)
        if (currentTask != null) {
            MigrationConfirmationDialog(
                sourceManga = currentTask.manga,
                candidates = state.currentCandidates,
                onSelect = { candidate ->
                    viewModel.onEvent(
                        MigrationEvent.ConfirmMigration(currentTask.manga.id, candidate)
                    )
                },
                onSkip = { viewModel.onEvent(MigrationEvent.SkipManga(currentTask.manga.id)) },
                onDismiss = { viewModel.onEvent(MigrationEvent.DismissConfirmationDialog) }
            )
        }
    }

    // Completion summary dialog
    if (state.showCompletionSummary) {
        MigrationSummaryDialog(
            completedCount = state.completedCount,
            failedCount = state.failedCount,
            skippedCount = state.skippedCount,
            onDismiss = { viewModel.onEvent(MigrationEvent.DismissCompletionSummary) }
        )
    }

    // Error dialog
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(MigrationEvent.DismissError) },
            title = { Text(stringResource(R.string.migration_error_title)) },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(MigrationEvent.DismissError) }) {
                    Text(stringResource(R.string.migration_ok))
                }
            }
        )
    }
}

@Composable
private fun MigrationConfirmationDialog(
    sourceManga: app.otakureader.domain.model.Manga,
    candidates: List<MigrationCandidate>,
    onSelect: (MigrationCandidate) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.migration_select_target)) },
        text = {
            Column {
                // Source manga info
                Text(
                    text = stringResource(R.string.migration_migrating_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = sourceManga.thumbnailUrl,
                        contentDescription = sourceManga.title,
                        modifier = Modifier
                            .size(width = 56.dp, height = 80.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sourceManga.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        sourceManga.author?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = sourceManga.status.toDisplayString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (candidates.size == 1) stringResource(R.string.migration_choose_target, candidates.size) else stringResource(R.string.migration_choose_target_plural, candidates.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn {
                    items(candidates, key = { it.url }) { candidate ->
                        MigrationCandidateCard(
                            candidate = candidate,
                            onClick = { onSelect(candidate) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.migration_skip))
            }
        }
    )
}

@Composable
private fun MigrationCandidateCard(
    candidate: MigrationCandidate,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Cover image
            AsyncImage(
                model = candidate.thumbnailUrl,
                contentDescription = candidate.title,
                modifier = Modifier
                    .size(width = 56.dp, height = 80.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                candidate.author?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = candidate.status.toDisplayString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (candidate.chapterCount > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.migration_chapter_count, candidate.chapterCount, candidate.chapterCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (candidate.genre.isNotEmpty()) {
                    Text(
                        text = candidate.genre.take(3).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.migration_match_score, (candidate.similarityScore * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        candidate.similarityScore >= 0.9f -> MaterialTheme.colorScheme.primary
                        candidate.similarityScore >= 0.7f -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MigrationSummaryDialog(
    completedCount: Int,
    failedCount: Int,
    skippedCount: Int,
    onDismiss: () -> Unit
) {
    val total = completedCount + failedCount + skippedCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.migration_complete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.migration_processed, total),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.migration_completed_count, completedCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (failedCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.migration_failed_count, failedCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (skippedCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.migration_skipped_count, skippedCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.migration_done))
            }
        }
    )
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
                    contentDescription = stringResource(R.string.migration_status_pending),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                MigrationStatus.SEARCHING, MigrationStatus.MIGRATING -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp)
                )
                MigrationStatus.COMPLETED -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.migration_status_completed),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                MigrationStatus.FAILED, MigrationStatus.SKIPPED -> Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.migration_status_failed, ""),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                MigrationStatus.AWAITING_CONFIRMATION -> Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = stringResource(R.string.migration_status_awaiting),
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

                // Show detailed status message if available, otherwise fall back to generic label
                val displayText = task.statusMessage ?: when (task.status) {
                    MigrationStatus.PENDING -> stringResource(R.string.migration_status_pending)
                    MigrationStatus.SEARCHING -> stringResource(R.string.migration_status_searching)
                    MigrationStatus.AWAITING_CONFIRMATION -> stringResource(R.string.migration_status_awaiting)
                    MigrationStatus.MIGRATING -> stringResource(R.string.migration_status_migrating)
                    MigrationStatus.COMPLETED -> if (task.chaptersMatched > 0) {
                        pluralStringResource(R.plurals.migration_status_completed_chapters, task.chaptersMatched, task.chaptersMatched)
                    } else {
                        stringResource(R.string.migration_status_completed)
                    }
                    MigrationStatus.FAILED -> stringResource(R.string.migration_status_failed, task.errorMessage ?: stringResource(R.string.migration_status_unknown_error))
                    MigrationStatus.SKIPPED -> stringResource(R.string.migration_status_skipped)
                }

                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (task.status) {
                        MigrationStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        MigrationStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                // Show target title when completed
                task.targetCandidate?.let { target ->
                    if (task.status == MigrationStatus.COMPLETED) {
                        Text(
                            text = stringResource(R.string.migration_target_arrow, target.title),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
