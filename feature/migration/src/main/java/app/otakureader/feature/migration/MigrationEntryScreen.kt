@file:Suppress("MaxLineLength")
package app.otakureader.feature.migration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest

/**
 * Scaffold-free content for embedding in Browse's Migrate tab.
 * Callers are responsible for handling [MigrationEntryEffect] (navigation, errors).
 */
@Composable
fun MigrationEntryContent(
    state: MigrationEntryState,
    filtered: List<MigrationEntryItem>,
    onEvent: (MigrationEntryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { onEvent(MigrationEntryEvent.OnSearchQueryChange(it)) },
            placeholder = { Text(stringResource(R.string.migration_entry_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.migration_search)) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onEvent(MigrationEntryEvent.OnSearchQueryChange("")) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.migration_clear_search))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(
                            onClick = { onEvent(MigrationEntryEvent.Retry) },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(stringResource(R.string.migration_entry_retry))
                        }
                    }
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.searchQuery.isBlank()) {
                            stringResource(R.string.migration_entry_library_empty)
                        } else {
                            stringResource(R.string.migration_entry_no_results)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Only when something is actually broken. A permanent banner would train the
                    // user to ignore it, which is exactly when it needs to be read.
                    if (state.strandedCount > 0) {
                        item(key = "stranded-banner") {
                            StrandedBanner(state = state, onEvent = onEvent)
                        }
                    }
                    items(filtered, key = { it.id }) { manga ->
                        MigrationEntryMangaRow(
                            manga = manga,
                            isSelected = manga.id in state.selectedIds,
                            onToggle = { onEvent(MigrationEntryEvent.OnMangaToggle(manga.id)) },
                        )
                    }
                }
            }
        }

        if (state.selectedIds.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = { onEvent(MigrationEntryEvent.OnStartMigration) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        androidx.compose.ui.res.pluralStringResource(
                            R.plurals.migration_entry_migrate_count,
                            state.selectedIds.size,
                            state.selectedIds.size,
                        )
                    )
                }
            }
        }
    }
}

/**
 * Entry-point screen for migration that allows users to pick manga from their library
 * before proceeding to the [MigrationScreen].
 *
 * Accessible from Settings → Data & Storage → Migrate manga.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationEntryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMigration: (List<Long>) -> Unit,
    viewModel: MigrationEntryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filtered = remember(state.mangaList, state.searchQuery) {
        viewModel.filteredList(state)
    }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is MigrationEntryEffect.NavigateToMigration -> onNavigateToMigration(effect.selectedMangaIds)
                MigrationEntryEffect.NavigateBack -> onNavigateBack()
                is MigrationEntryEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (state.selectedIds.isNotEmpty()) {
                        Text(
                            androidx.compose.ui.res.pluralStringResource(
                                R.plurals.migration_entry_selected,
                                state.selectedIds.size,
                                state.selectedIds.size
                            )
                        )
                    } else {
                        Text(stringResource(R.string.migration_entry_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onEvent(MigrationEntryEvent.NavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.migration_back))
                    }
                },
                actions = {
                    if (state.selectedIds.isNotEmpty()) {
                        TextButton(onClick = { viewModel.onEvent(MigrationEntryEvent.ClearSelection) }) {
                            Text(stringResource(R.string.migration_entry_clear))
                        }
                    } else if (filtered.isNotEmpty()) {
                        TextButton(onClick = { viewModel.onEvent(MigrationEntryEvent.SelectAll) }) {
                            Text(stringResource(R.string.migration_entry_all))
                        }
                    }
                }
            )
        },
    ) { paddingValues ->
        MigrationEntryContent(
            state = state,
            filtered = filtered,
            onEvent = viewModel::onEvent,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        )
    }
}

/**
 * Surfaces entries no loaded source can serve, with the two actions that resolve them.
 *
 * Selecting is offered separately from filtering because they answer different questions — "fix
 * these" versus "let me look at these" — and a user who only wants the first should not have to
 * change what the list shows to get it.
 */
@Composable
private fun StrandedBanner(
    state: MigrationEntryState,
    onEvent: (MigrationEntryEvent) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = androidx.compose.ui.res.pluralStringResource(
                    R.plurals.migration_entry_stranded_banner,
                    state.strandedCount,
                    state.strandedCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onEvent(MigrationEntryEvent.SelectAllStranded) }) {
                    Text(stringResource(R.string.migration_entry_select_stranded))
                }
                TextButton(onClick = { onEvent(MigrationEntryEvent.ToggleStrandedFilter) }) {
                    Text(
                        stringResource(
                            if (state.showOnlyStranded) {
                                R.string.migration_entry_show_all_entries
                            } else {
                                R.string.migration_entry_show_stranded_only
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MigrationEntryMangaRow(
    manga: MigrationEntryItem,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = isSelected,
                onValueChange = { onToggle() },
                role = Role.Checkbox
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        AsyncImage(
            model = manga.thumbnailUrl,
            contentDescription = manga.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(48.dp)
                .aspectRatio(3f / 4f)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = manga.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // The source line is the whole point of showing this screen before a migration: an
            // entry whose source cannot be resolved is unreadable, and nothing else in the app
            // says so. Rendered in the error colour rather than as a separate icon so the state
            // survives being skimmed.
            Text(
                text = manga.sourceName ?: stringResource(R.string.migration_entry_source_missing),
                style = MaterialTheme.typography.bodySmall,
                color = if (manga.isStranded) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Checkbox(
            checked = isSelected,
            onCheckedChange = null
        )
    }
}
