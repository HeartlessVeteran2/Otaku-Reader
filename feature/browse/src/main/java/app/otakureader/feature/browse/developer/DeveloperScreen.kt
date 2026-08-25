package app.otakureader.feature.browse.developer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.feature.browse.R
import kotlinx.coroutines.flow.collectLatest

/**
 * The hidden developer screen.
 *
 * Reached only from the About screen's version line — see `DeveloperUnlock` for what that gate is
 * and, more importantly, what it is not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    onNavigateBack: () -> Unit,
    viewModel: DeveloperViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is DeveloperEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    // Leaves as soon as the flag clears — whether that came from the Lock button here or from the
    // flag being cleared elsewhere. Explicitly `== false` rather than `!= true`, so the null
    // "not yet read" state does not pop the screen on its first frame.
    LaunchedEffect(state.isUnlocked) {
        if (state.isUnlocked == false) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.developer_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.developer_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onEvent(DeveloperEvent.Lock) }) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = stringResource(R.string.developer_lock),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(paddingValues))
            state.hasLoadError -> MessageState(
                title = stringResource(R.string.developer_load_error_title),
                body = stringResource(R.string.developer_load_error_body),
                modifier = Modifier.padding(paddingValues),
            )
            state.hasNoSeeds -> EmptyState(Modifier.padding(paddingValues))
            else -> SeedList(
                state = state,
                onAddAll = { viewModel.onEvent(DeveloperEvent.AddAllSeeds) },
                onAdd = { viewModel.onEvent(DeveloperEvent.AddSeed(it)) },
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Shown when the build carries no `dev-repos.txt`.
 *
 * Worded as a setup instruction rather than an error, because this is the correct and expected
 * state for any build made from a fresh clone — the file is gitignored on purpose.
 */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    MessageState(
        title = stringResource(R.string.developer_no_seeds_title),
        body = stringResource(R.string.developer_no_seeds_body, DeveloperRepoSeeds.ASSET_NAME),
        modifier = modifier,
    )
}

@Composable
private fun MessageState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SeedList(
    state: DeveloperState,
    onAddAll: () -> Unit,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.developer_gate_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        Button(
            onClick = onAddAll,
            enabled = state.pendingCount > 0,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(stringResource(R.string.developer_add_all, state.pendingCount))
        }

        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.seeds, key = { it.url }) { seed ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = seed.url,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    trailingContent = {
                        if (seed.isAlreadyAdded) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(R.string.developer_already_added),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            IconButton(onClick = { onAdd(seed.url) }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.developer_add),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}
