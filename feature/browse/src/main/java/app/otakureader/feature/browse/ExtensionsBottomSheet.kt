package app.otakureader.feature.browse

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.InstallStatus
import app.otakureader.core.ui.component.ErrorScreen
import app.otakureader.core.ui.component.LoadingScreen
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsBottomSheet(
    onDismiss: () -> Unit,
    viewModel: ExtensionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxSize()
    ) {
        ExtensionsContent(
            state = state,
            onEvent = viewModel::onEvent,
            onClose = {
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionsContent(
    state: ExtensionsState,
    onEvent: (ExtensionsEvent) -> Unit,
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Installed", "Available", "Updates")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extensions") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(ExtensionsEvent.Refresh) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { /* TODO: Settings */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            TextField(
                value = state.searchQuery,
                onValueChange = { onEvent(ExtensionsEvent.OnSearchQueryChange(it)) },
                placeholder = { Text("Search extensions...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            when (index) {
                                2 -> if (state.updateCount > 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(title)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.error
                                        ) {
                                            Text(
                                                text = state.updateCount.toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onError,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                } else Text(title)
                                else -> Text(title)
                            }
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> ExtensionsList(
                    extensions = state.installedExtensions,
                    isLoading = state.isLoading,
                    error = state.error,
                    onInstall = { /* Already installed */ },
                    onUninstall = { onEvent(ExtensionsEvent.UninstallExtension(it)) },
                    onUpdate = { onEvent(ExtensionsEvent.UpdateExtension(it)) },
                    onRefresh = { onEvent(ExtensionsEvent.Refresh) }
                )
                1 -> ExtensionsList(
                    extensions = state.availableExtensions,
                    isLoading = state.isLoading,
                    error = state.error,
                    onInstall = { onEvent(ExtensionsEvent.InstallExtension(it)) },
                    onUninstall = { /* Not installed */ },
                    onUpdate = { /* No update */ },
                    onRefresh = { onEvent(ExtensionsEvent.Refresh) }
                )
                2 -> ExtensionsList(
                    extensions = state.extensionsWithUpdates,
                    isLoading = state.isLoading,
                    error = state.error,
                    onInstall = { /* Already installed */ },
                    onUninstall = { onEvent(ExtensionsEvent.UninstallExtension(it)) },
                    onUpdate = { onEvent(ExtensionsEvent.UpdateExtension(it)) },
                    onRefresh = { onEvent(ExtensionsEvent.Refresh) }
                )
            }
        }
    }
}

@Composable
private fun ExtensionsList(
    extensions: List<Extension>,
    isLoading: Boolean,
    error: String?,
    onInstall: (Extension) -> Unit,
    onUninstall: (Extension) -> Unit,
    onUpdate: (Extension) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        isLoading -> LoadingScreen(modifier = modifier)
        error != null -> ErrorScreen(
            message = error,
            onRetry = onRefresh,
            modifier = modifier
        )
        extensions.isEmpty() -> EmptyExtensionsView(modifier = modifier)
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(extensions, key = { it.id }) { extension ->
                ExtensionItem(
                    extension = extension,
                    onInstall = { onInstall(extension) },
                    onUninstall = { onUninstall(extension) },
                    onUpdate = { onUpdate(extension) }
                )
            }
        }
    }
}

@Composable
private fun EmptyExtensionsView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No extensions found",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExtensionItem(
    extension: Extension,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Extension icon
            AsyncImage(
                model = extension.iconUrl,
                contentDescription = extension.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = extension.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "v${extension.versionName} • ${extension.lang.uppercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (extension.sources.isNotEmpty()) {
                    Text(
                        text = "${extension.sources.size} source(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action buttons based on status
            when (extension.status) {
                InstallStatus.INSTALLED -> {
                    IconButton(onClick = onUninstall) {
                        Icon(Icons.Default.Delete, contentDescription = "Uninstall")
                    }
                }
                InstallStatus.HAS_UPDATE -> {
                    IconButton(onClick = onUpdate) {
                        Icon(Icons.Default.Update, contentDescription = "Update", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                InstallStatus.AVAILABLE -> {
                    IconButton(onClick = onInstall) {
                        Icon(Icons.Default.Download, contentDescription = "Install", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                InstallStatus.INSTALLING, InstallStatus.UPDATING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                else -> {
                    // Error or other states - show install button
                    IconButton(onClick = onInstall) {
                        Icon(Icons.Default.Download, contentDescription = "Install")
                    }
                }
            }
        }
    }
}
