package app.otakureader.feature.library.readinglist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.domain.model.ReadingList
import app.otakureader.feature.library.R
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingListsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToList: (Long) -> Unit,
    viewModel: ReadingListsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingList by remember { mutableStateOf<ReadingList?>(null) }
    var listPendingDeletion by remember { mutableStateOf<ReadingList?>(null) }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is ReadingListsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is ReadingListsEffect.NavigateToListDetail -> onNavigateToList(effect.listId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reading_lists_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reading_lists_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.reading_lists_create))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.lists.isEmpty() -> EmptyReadingLists(modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.lists, key = { it.id }) { list ->
                        ListRow(
                            list = list,
                            onClick = { viewModel.onEvent(ReadingListsEvent.OpenList(list.id)) },
                            onEdit = { editingList = list },
                            onDelete = { listPendingDeletion = list },
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        ReadingListEditorDialog(
            initial = null,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, desc ->
                viewModel.onEvent(ReadingListsEvent.CreateList(name, desc))
                showCreateDialog = false
            },
        )
    }

    editingList?.let { list ->
        ReadingListEditorDialog(
            initial = list,
            onDismiss = { editingList = null },
            onConfirm = { name, desc ->
                viewModel.onEvent(ReadingListsEvent.RenameList(list.id, name, desc))
                editingList = null
            },
        )
    }

    listPendingDeletion?.let { list ->
        AlertDialog(
            onDismissRequest = { listPendingDeletion = null },
            title = { Text(stringResource(R.string.reading_lists_delete_title)) },
            text = { Text(stringResource(R.string.reading_lists_delete_confirm, list.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onEvent(ReadingListsEvent.DeleteList(list.id))
                    listPendingDeletion = null
                }) { Text(stringResource(R.string.reading_lists_delete_confirm_button)) }
            },
            dismissButton = {
                TextButton(onClick = { listPendingDeletion = null }) {
                    Text(stringResource(R.string.reading_lists_cancel))
                }
            },
        )
    }
}

@Composable
private fun ListRow(
    list: ReadingList,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(list.name) },
        supportingContent = {
            Column {
                if (!list.description.isNullOrBlank()) {
                    Text(
                        text = list.description!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.reading_lists_item_count, list.itemCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.reading_lists_edit))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.reading_lists_delete))
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
private fun EmptyReadingLists(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.reading_lists_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.reading_lists_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingListEditorDialog(
    initial: ReadingList?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String?) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) stringResource(R.string.reading_lists_create_title)
                else stringResource(R.string.reading_lists_edit_title),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.reading_lists_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.reading_lists_description_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, description.ifBlank { null }) }) {
                Text(stringResource(R.string.reading_lists_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.reading_lists_cancel))
            }
        },
    )
}
