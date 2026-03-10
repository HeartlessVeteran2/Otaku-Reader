package app.otakureader.feature.tracking

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.domain.model.TrackEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    mangaId: Long,
    mangaTitle: String,
    onNavigateBack: () -> Unit,
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(mangaId) {
        viewModel.onEvent(TrackingEvent.LoadTrackers(mangaId, mangaTitle))
    }

    // Collect one-time effects
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is TrackingEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is TrackingEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                is TrackingEffect.OpenOAuth -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(effect.url))
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.tracking_oauth_browser_error)
                            )
                        }
                }
            }
        }
    }

    // Show credential login dialog when requested
    val loginDialogTrackerId = state.loginDialogTrackerId
    if (loginDialogTrackerId != null) {
        val trackerName = state.trackers.find { it.id == loginDialogTrackerId }?.name ?: ""
        CredentialLoginDialog(
            trackerName = trackerName,
            onConfirm = { username, password ->
                viewModel.onEvent(TrackingEvent.Login(loginDialogTrackerId, username, password))
            },
            onDismiss = { viewModel.onEvent(TrackingEvent.DismissLoginDialog) }
        )
    }

    // Show search dialog for the selected tracker
    val selectedTrackerId = state.selectedTracker
    if (selectedTrackerId != null) {
        SearchMangaDialog(
            query = state.searchQuery,
            results = state.searchResults,
            isSearching = state.isSearching,
            onQueryChange = { viewModel.onEvent(TrackingEvent.OnSearchQueryChange(it)) },
            onSearch = {
                viewModel.onEvent(TrackingEvent.Search(selectedTrackerId, state.searchQuery))
            },
            onSelect = { entry ->
                viewModel.onEvent(TrackingEvent.LinkManga(selectedTrackerId, entry.remoteId))
            },
            onDismiss = { viewModel.onEvent(TrackingEvent.ClearSearch) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tracking_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.tracking_back)
                        )
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
        ) {
            Text(
                text = mangaTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.trackers) { tracker ->
                        TrackerCard(
                            tracker = tracker,
                            onLogin = { viewModel.onEvent(TrackingEvent.InitiateLogin(tracker.id)) },
                            onLogout = { viewModel.onEvent(TrackingEvent.Logout(tracker.id)) },
                            onSearch = { viewModel.onEvent(TrackingEvent.OpenSearchDialog(tracker.id)) },
                            onUnlink = { viewModel.onEvent(TrackingEvent.UnlinkManga(tracker.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackerCard(
    tracker: TrackerUiModel,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSearch: () -> Unit,
    onUnlink: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Local brand color badge instead of remote icon URL
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(tracker.brandColor)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tracker.name.first().toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tracker.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (tracker.isLoggedIn)
                            stringResource(R.string.tracking_logged_in)
                        else
                            stringResource(R.string.tracking_not_logged_in),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (tracker.isLoggedIn)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (tracker.isLoggedIn) {
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = stringResource(R.string.tracking_logout),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    IconButton(onClick = onLogin) {
                        Icon(
                            imageVector = Icons.Default.Login,
                            contentDescription = stringResource(R.string.tracking_login),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (tracker.isLoggedIn) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                if (tracker.entry != null) {
                    LinkedMangaInfo(
                        entry = tracker.entry,
                        onUnlink = onUnlink
                    )
                } else {
                    OutlinedButton(
                        onClick = onSearch,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.tracking_search))
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkedMangaInfo(
    entry: TrackEntry,
    onUnlink: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = entry.status.name.replace("_", " "),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(R.string.tracking_chapter_progress, entry.lastChapterRead.toInt()),
                style = MaterialTheme.typography.bodyMedium
            )

            if (entry.score > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "★ ${entry.score}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onUnlink,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(R.string.tracking_unlink), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CredentialLoginDialog(
    trackerName: String,
    onConfirm: (username: String, password: String) -> Unit,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tracking_login_title, trackerName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.tracking_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.tracking_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(username, password) },
                enabled = username.isNotBlank() && password.isNotBlank()
            ) {
                Text(stringResource(R.string.tracking_login))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.tracking_cancel))
            }
        }
    )
}

@Composable
fun SearchMangaDialog(
    query: String,
    results: List<TrackEntry>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (TrackEntry) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tracking_search)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.tracking_search_placeholder)) },
                    trailingIcon = {
                        IconButton(onClick = onSearch) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.tracking_search)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isSearching) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (results.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(results) { entry ->
                            SearchResultItem(
                                entry = entry,
                                onClick = { onSelect(entry) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.tracking_cancel))
            }
        }
    )
}

@Composable
private fun SearchResultItem(
    entry: TrackEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
