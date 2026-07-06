package app.otakureader.feature.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedFeedScreen(
    onNavigateBack: () -> Unit,
    viewModel: SavedFeedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAddSheet by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is SavedFeedEffect.ShowSnackbar -> scope.launch {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feed_manage_sources)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.feed_back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.feed_add_source))
            }
        }
    ) { padding ->
        if (state.sources.isEmpty()) {
            EmptyFeedSourcesMessage(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(state.sources, key = { it.sourceId }) { source ->
                    FeedSourceItem(
                        sourceName = source.sourceName,
                        enabled = source.isEnabled,
                        onToggle = { enabled ->
                            viewModel.onEvent(SavedFeedEvent.ToggleSource(source.sourceId, enabled))
                        },
                        onDelete = {
                            viewModel.onEvent(SavedFeedEvent.RemoveSource(source.sourceId))
                        }
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        FeedBuilderBottomSheet(
            onDismiss = { showAddSheet = false },
            onAddSource = { sourceName ->
                viewModel.onEvent(SavedFeedEvent.AddSource(sourceName))
                showAddSheet = false
            }
        )
    }
}

@Composable
private fun FeedSourceItem(
    sourceName: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        ListItem(
            headlineContent = { Text(sourceName) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = enabled,
                        onCheckedChange = onToggle,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.feed_remove_source),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun EmptyFeedSourcesMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.feed_no_sources_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.feed_no_sources_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
