package app.otakureader.feature.opds

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.domain.model.OpdsEntry
import app.otakureader.domain.model.OpdsServer
import coil3.compose.AsyncImage

@Composable
fun OpdsScreen(
    onNavigateBack: () -> Unit,
    viewModel: OpdsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is OpdsEffect.ShowSnackbar -> {
                    android.widget.Toast.makeText(context, effect.message, android.widget.Toast.LENGTH_SHORT).show()
                }
                is OpdsEffect.NavigateToMangaDetail -> {
                    // Future: navigate to manga detail
                }
            }
        }
    }

    val onEvent = viewModel::onEvent

    if (state.currentServer != null) {
        // Intercept the system back button to navigate within the catalog
        // instead of popping the NavHost route.
        BackHandler { onEvent(OpdsEvent.NavigateBack) }
        CatalogBrowserScreen(
            state = state,
            onEvent = onEvent
        )
    } else {
        ServerListScreen(
            state = state,
            onEvent = onEvent,
            onNavigateBack = onNavigateBack
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerListScreen(
    state: OpdsState,
    onEvent: (OpdsEvent) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.opds_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.opds_back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEvent(OpdsEvent.ShowAddServerDialog) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.opds_add_server))
            }
        }
    ) { paddingValues ->
        if (state.servers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.opds_no_servers),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(state.servers, key = { it.id }) { server ->
                    ServerListItem(
                        server = server,
                        onBrowse = { onEvent(OpdsEvent.BrowseServer(server)) },
                        onEdit = { onEvent(OpdsEvent.ShowEditServerDialog(server)) },
                        onDelete = { onEvent(OpdsEvent.ShowDeleteConfirmation(server)) }
                    )
                }
            }
        }
    }

    if (state.showAddServerDialog) {
        AddServerDialog(
            editingServer = state.editingServer,
            onSave = { name, url, username, password ->
                onEvent(OpdsEvent.SaveServer(name, url, username, password))
            },
            onDismiss = { onEvent(OpdsEvent.DismissServerDialog) }
        )
    }

    state.showDeleteConfirmation?.let { server ->
        AlertDialog(
            onDismissRequest = { onEvent(OpdsEvent.DismissDeleteConfirmation) },
            title = { Text(stringResource(R.string.opds_delete_server)) },
            text = { Text(stringResource(R.string.opds_delete_confirm, server.name)) },
            confirmButton = {
                TextButton(onClick = { onEvent(OpdsEvent.ConfirmDeleteServer(server.id)) }) {
                    Text(stringResource(R.string.opds_delete_server))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(OpdsEvent.DismissDeleteConfirmation) }) {
                    Text(stringResource(R.string.opds_cancel))
                }
            }
        )
    }
}

@Composable
private fun ServerListItem(
    server: OpdsServer,
    onBrowse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = { Text(server.name) },
        supportingContent = { Text(server.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            Icon(
                Icons.Default.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.opds_edit_server))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.opds_delete_server))
                }
            }
        },
        modifier = Modifier.clickable(onClick = onBrowse)
    )
}

@Composable
private fun AddServerDialog(
    editingServer: OpdsServer?,
    onSave: (name: String, url: String, username: String, password: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(editingServer?.id) { mutableStateOf(editingServer?.name ?: "") }
    var url by remember(editingServer?.id) { mutableStateOf(editingServer?.url ?: "") }
    var username by remember(editingServer?.id) { mutableStateOf(editingServer?.username ?: "") }
    var password by remember(editingServer?.id) { mutableStateOf(editingServer?.password ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (editingServer != null) R.string.opds_edit_server
                    else R.string.opds_add_server
                )
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.opds_server_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.opds_server_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.opds_server_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.opds_server_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), url.trim(), username.trim(), password) },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text(stringResource(R.string.opds_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.opds_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogBrowserScreen(
    state: OpdsState,
    onEvent: (OpdsEvent) -> Unit
) {
    var showSearch by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (showSearch) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { onEvent(OpdsEvent.OnSearchQueryChange(it)) },
                            placeholder = { Text(stringResource(R.string.opds_search_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { onEvent(OpdsEvent.PerformSearch) }
                            ),
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onEvent(OpdsEvent.OnSearchQueryChange("")) }) {
                                        Icon(Icons.Default.Clear, contentDescription = null)
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            showSearch = false
                            if (state.isSearching) {
                                onEvent(OpdsEvent.ClearSearch)
                            }
                        }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.opds_back)
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            state.feedTitle.ifBlank { state.currentServer?.name ?: "" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { onEvent(OpdsEvent.NavigateBack) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.opds_back)
                            )
                        }
                    },
                    actions = {
                        if (state.searchUrl != null) {
                            IconButton(onClick = { showSearch = true }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.opds_search)
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.opds_error),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = {
                            val lastUrl = state.navigationStack.lastOrNull()
                            if (lastUrl != null && state.currentServer != null) {
                                onEvent(OpdsEvent.NavigateToFeed(lastUrl))
                            }
                        }) {
                            Text(stringResource(R.string.opds_retry))
                        }
                    }
                }
            }
            state.entries.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.opds_empty_feed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(
                        state.entries,
                        key = { index, entry -> entry.id.ifBlank { "entry_$index" } }
                    ) { _, entry ->
                        OpdsEntryItem(
                            entry = entry,
                            onClick = { onEvent(OpdsEvent.OnEntryClick(entry)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OpdsEntryItem(
    entry: OpdsEntry,
    onClick: () -> Unit
) {
    val hasNavLink = entry.links.any { it.isNavigation }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (entry.thumbnailUrl != null) {
                AsyncImage(
                    model = entry.thumbnailUrl,
                    contentDescription = entry.title,
                    modifier = Modifier
                        .width(80.dp)
                        .aspectRatio(0.7f)
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.author.isNotBlank()) {
                    Text(
                        text = entry.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (entry.summary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.summary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (hasNavLink) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.opds_navigate),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
