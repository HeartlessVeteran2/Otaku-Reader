package app.otakureader.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import app.otakureader.feature.settings.viewmodel.TrackerSettingsViewModel

/**
 * Tracking sub-screen: connect, configure and disconnect manga trackers (AniList, MAL, Kitsu,
 * MangaUpdates).
 * Serviced by [TrackerSettingsViewModel] backed by [TrackerSyncSettingsDelegate].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTrackingScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrackerSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SettingsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_tracking)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            TrackingContent(state = state, onEvent = viewModel::onEvent)
        }
    }
}

fun NavGraphBuilder.settingsTrackingScreen(onNavigateBack: () -> Unit) {
    composable<Route.SettingsTracking> {
        SettingsTrackingScreen(onNavigateBack = onNavigateBack)
    }
}

// ─── Section composable ───────────────────────────────────────────────────────

@Composable
private fun TrackingContent(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    SectionHeader(title = stringResource(R.string.settings_tracking))

    if (state.trackers.isEmpty()) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_no_tracker_services)) },
            supportingContent = { Text(stringResource(R.string.settings_no_tracker_services_description)) },
        )
        return
    }

    if (state.trackers.any { it.isLoggedIn }) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_tracker_sync_all)) },
            supportingContent = { Text(stringResource(R.string.settings_tracker_sync_all_description)) },
            trailingContent = {
                if (state.batchSyncInProgress) {
                    CircularProgressIndicator()
                } else {
                    Button(onClick = { onEvent(SettingsEvent.SyncAllTrackers) }) {
                        Text(stringResource(R.string.settings_tracker_sync_all_button))
                    }
                }
            },
        )
    }

    state.batchSyncSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { onEvent(SettingsEvent.DismissTrackerSyncSummary) },
            title = { Text(stringResource(R.string.settings_tracker_sync_summary_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_tracker_sync_summary_body,
                        summary.attempted,
                        summary.successful,
                        summary.failed,
                        summary.conflicts,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { onEvent(SettingsEvent.DismissTrackerSyncSummary) }) {
                    Text(stringResource(R.string.settings_tracker_sync_summary_dismiss))
                }
            },
        )
    }

    state.trackers.forEach { tracker ->
        var showLogin by remember(tracker.id) { mutableStateOf(false) }
        var username by remember(tracker.id) { mutableStateOf("") }
        var password by remember(tracker.id) { mutableStateOf("") }
        var passwordVisible by remember(tracker.id) { mutableStateOf(false) }

        ListItem(
            headlineContent = { Text(tracker.name) },
            supportingContent = {
                if (tracker.isLoggedIn) {
                    Text(
                        text = stringResource(R.string.settings_tracker_connected),
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(stringResource(R.string.settings_tracker_not_connected))
                }
            },
            trailingContent = {
                if (state.trackingLoginInProgress) {
                    CircularProgressIndicator()
                } else if (tracker.isLoggedIn) {
                    OutlinedButton(onClick = { onEvent(SettingsEvent.LogoutTracker(tracker.id)) }) {
                        Text(stringResource(R.string.settings_tracker_logout))
                    }
                } else {
                    Button(onClick = { showLogin = !showLogin }) {
                        Text(stringResource(R.string.settings_tracker_login))
                    }
                }
            },
        )

        if (tracker.isLoggedIn) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_tracker_sync_on_read)) },
                supportingContent = { Text(stringResource(R.string.settings_tracker_sync_on_read_description)) },
                trailingContent = {
                    Switch(
                        // No config row yet = domain default (true)
                        checked = state.trackerSyncOnChapterRead[tracker.id] ?: true,
                        onCheckedChange = { onEvent(SettingsEvent.SetTrackerSyncOnChapterRead(tracker.id, it)) },
                    )
                },
                modifier = Modifier.padding(start = 16.dp),
            )
        }

        if (showLogin && !tracker.isLoggedIn) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.settings_tracker_username)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.settings_tracker_password)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (passwordVisible) {
                                    stringResource(R.string.settings_tracker_hide_password)
                                } else {
                                    stringResource(R.string.settings_tracker_show_password)
                                },
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
                Button(
                    onClick = {
                        onEvent(SettingsEvent.LoginTracker(tracker.id, username, password))
                        showLogin = false
                        username = ""
                        password = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_tracker_connect, tracker.name))
                }
            }
        }
    }
}
