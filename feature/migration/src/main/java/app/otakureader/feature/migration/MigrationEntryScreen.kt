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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest

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
                        Text("${state.selectedIds.size} selected")
                    } else {
                        Text("Select Manga to Migrate")
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
                            Text("Clear")
                        }
                    } else if (filtered.isNotEmpty()) {
                        TextButton(onClick = { viewModel.onEvent(MigrationEntryEvent.SelectAll) }) {
                            Text("All")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (state.selectedIds.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = { viewModel.onEvent(MigrationEntryEvent.OnStartMigration) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Migrate ${state.selectedIds.size} manga")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onEvent(MigrationEntryEvent.OnSearchQueryChange(it)) },
                placeholder = { Text("Search library…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.migration_search)) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onEvent(MigrationEntryEvent.OnSearchQueryChange("")) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.migration_clear_search))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.error!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            OutlinedButton(
                                onClick = { viewModel.onEvent(MigrationEntryEvent.Retry) },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
                filtered.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (state.searchQuery.isBlank()) "Your library is empty" else "No manga match your search",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filtered, key = { it.id }) { manga ->
                            MigrationEntryMangaRow(
                                manga = manga,
                                isSelected = manga.id in state.selectedIds,
                                onToggle = { viewModel.onEvent(MigrationEntryEvent.OnMangaToggle(manga.id)) }
                            )
                        }
                    }
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

        Text(
            text = manga.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Checkbox(
            checked = isSelected,
            onCheckedChange = null
        )
    }
}
