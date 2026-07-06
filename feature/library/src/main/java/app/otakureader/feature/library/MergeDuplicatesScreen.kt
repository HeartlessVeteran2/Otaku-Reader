package app.otakureader.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeDuplicatesScreen(
    onNavigateBack: () -> Unit,
    viewModel: MergeDuplicatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is MergeDuplicatesEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is MergeDuplicatesEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.merge_duplicates_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.merge_duplicates_back),
                        )
                    }
                },
                actions = {
                    if (state.duplicateGroups.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { viewModel.onEvent(MergeDuplicatesEvent.MergeAll) },
                            enabled = !state.isMerging,
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(stringResource(R.string.merge_duplicates_merge_all))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.duplicateGroups.isEmpty() -> EmptyDuplicatesState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.isMerging) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Merging…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                itemsIndexed(state.duplicateGroups, key = { _, g -> g.entries.first().id }) { index, group ->
                    DuplicateGroupCard(
                        group = group,
                        sourceNames = state.sourceNames,
                        linkedAlternatives = state.linkedAlternatives,
                        onSelectPrimary = { primaryId ->
                            viewModel.onEvent(MergeDuplicatesEvent.SelectPrimary(index, primaryId))
                        },
                        onMerge = { viewModel.onEvent(MergeDuplicatesEvent.MergeGroup(group)) },
                        onLinkAsAlternative = { altId ->
                            viewModel.onEvent(MergeDuplicatesEvent.LinkAsAlternative(group.primaryId, altId))
                        },
                        onUnlinkAlternative = { altId ->
                            viewModel.onEvent(MergeDuplicatesEvent.UnlinkAlternative(group.primaryId, altId))
                        },
                        onFillMissingChapters = { altId ->
                            viewModel.onEvent(MergeDuplicatesEvent.FillMissingChapters(group.primaryId, altId))
                        },
                        enabled = !state.isMerging,
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    sourceNames: Map<Long, String>,
    linkedAlternatives: Map<Long, Set<Long>>,
    onSelectPrimary: (Long) -> Unit,
    onMerge: () -> Unit,
    onLinkAsAlternative: (altId: Long) -> Unit,
    onUnlinkAlternative: (altId: Long) -> Unit,
    onFillMissingChapters: (altId: Long) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.merge_duplicates_group_header, group.entries.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (group.isCrossSource) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.merge_duplicates_cross_source)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            group.entries.forEach { manga ->
                val isPrimary = manga.id == group.primaryId
                MangaEntryRow(
                    manga = manga,
                    sourceName = sourceNames[manga.sourceId],
                    isPrimary = isPrimary,
                    onSelect = { onSelectPrimary(manga.id) },
                    enabled = enabled,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onMerge,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.MergeType, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.merge_duplicates_merge_group))
            }

            // Cross-source alternative linking actions (non-destructive)
            if (group.isCrossSource) {
                val altEntries = group.entries.filter { it.id != group.primaryId }
                altEntries.forEach { alt ->
                    val isLinked = linkedAlternatives[group.primaryId]?.contains(alt.id) == true
                    Spacer(Modifier.height(4.dp))
                    if (isLinked) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onUnlinkAlternative(alt.id) },
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    Icons.Default.LinkOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.merge_duplicates_unlink_alt))
                            }
                            OutlinedButton(
                                onClick = { onFillMissingChapters(alt.id) },
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.merge_duplicates_fill_chapters))
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onLinkAsAlternative(alt.id) },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(
                                    R.string.merge_duplicates_link_alt,
                                    sourceNames[alt.sourceId] ?: alt.sourceId.toString(),
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaEntryRow(
    manga: app.otakureader.domain.model.Manga,
    sourceName: String?,
    isPrimary: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val primaryBorder = if (isPrimary) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else null

    Card(
        onClick = onSelect,
        enabled = enabled,
        border = primaryBorder,
        colors = if (isPrimary) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        } else {
            CardDefaults.cardColors()
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AsyncImage(
                model = manga.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp, 64.dp)
                    .clip(MaterialTheme.shapes.small),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                )
                Text(
                    text = sourceName ?: stringResource(R.string.merge_duplicates_source_id, manga.sourceId),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.merge_duplicates_unread, manga.unreadCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isPrimary) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text(stringResource(R.string.merge_duplicates_primary_badge)) },
                )
            } else {
                OutlinedButton(
                    onClick = onSelect,
                    enabled = enabled,
                ) {
                    Text(stringResource(R.string.merge_duplicates_keep_label))
                }
            }
        }
    }
}

@Composable
private fun EmptyDuplicatesState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.MergeType,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.merge_duplicates_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.merge_duplicates_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
