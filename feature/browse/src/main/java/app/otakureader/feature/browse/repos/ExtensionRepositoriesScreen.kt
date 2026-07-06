package app.otakureader.feature.browse.repos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.feature.browse.R
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionRepositoriesScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExtensionRepositoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var urlInput by remember { mutableStateOf("") }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is ExtensionRepositoriesEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.repos_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.repos_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    AddRepositoryRow(
                        url = urlInput,
                        validationError = state.urlValidationError,
                        onUrlChange = {
                            urlInput = it
                            viewModel.onEvent(ExtensionRepositoriesEvent.ValidateUrl(it))
                        },
                        onAdd = {
                            viewModel.onEvent(ExtensionRepositoriesEvent.AddRepository(urlInput))
                            if (state.urlValidationError == null) urlInput = ""
                        },
                    )
                    if (state.repositories.isEmpty()) {
                        EmptyRepos(modifier = Modifier.weight(1f))
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(state.repositories, key = { it.url }) { repo ->
                                RepositoryRow(
                                    item = repo,
                                    onRemove = {
                                        viewModel.onEvent(ExtensionRepositoriesEvent.RemoveRepository(repo.url))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRepositoryRow(
    url: String,
    validationError: String?,
    onUrlChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text(stringResource(R.string.repos_add_url_label)) },
            placeholder = { Text(stringResource(R.string.repos_add_url_placeholder)) },
            supportingText = {
                Text(validationError ?: stringResource(R.string.repos_add_url_helper))
            },
            isError = validationError != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onAdd,
                enabled = url.isNotBlank() && validationError == null,
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(stringResource(R.string.repos_add_button))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepositoryRow(
    item: RepositoryItem,
    onRemove: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(text = item.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = if (item.isDefault) {
            {
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(R.string.repos_default_badge)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                )
            }
        } else null,
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.repos_remove))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EmptyRepos(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.repos_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.repos_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
