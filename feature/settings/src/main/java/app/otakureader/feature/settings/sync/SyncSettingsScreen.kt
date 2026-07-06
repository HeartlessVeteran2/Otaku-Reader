package app.otakureader.feature.settings.sync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import app.otakureader.feature.settings.R
import app.otakureader.feature.settings.SectionHeader

/**
 * Sync settings screen — lets the user configure a self-hosted Otaku Reader sync
 * server and manually trigger a sync cycle.
 *
 * **Navigation entry point:** [syncSettingsScreen] extension on [NavGraphBuilder].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SyncSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect one-shot effects and show snackbars.
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SyncSettingsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    // Local draft fields so the user can edit without immediately persisting.
    var serverUrlDraft by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    var bearerTokenDraft by remember(state.bearerToken) { mutableStateOf(state.bearerToken) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_sync)) },
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
        LazyColumn(modifier = Modifier.padding(paddingValues)) {

            // ── Description ──────────────────────────────────────────────
            item(key = "sync_description") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_sync_description)) },
                )
            }

            item(key = "sync_divider_0") { HorizontalDivider() }

            // ── Server configuration ─────────────────────────────────────
            item(key = "sync_server_header") {
                SectionHeader(title = stringResource(R.string.settings_sync))
            }

            item(key = "sync_server_url") {
                ListItem(
                    headlineContent = {
                        OutlinedTextField(
                            value = serverUrlDraft,
                            onValueChange = {
                                serverUrlDraft = it
                                viewModel.onEvent(SyncSettingsEvent.SetServerUrl(it))
                            },
                            label = { Text(stringResource(R.string.settings_sync_server_url)) },
                            singleLine = true,
                        )
                    },
                )
            }

            item(key = "sync_bearer_token") {
                ListItem(
                    headlineContent = {
                        OutlinedTextField(
                            value = bearerTokenDraft,
                            onValueChange = {
                                bearerTokenDraft = it
                                viewModel.onEvent(SyncSettingsEvent.SetBearerToken(it))
                            },
                            label = { Text(stringResource(R.string.settings_sync_bearer_token)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    },
                )
            }

            // ── Save / test buttons ───────────────────────────────────────
            item(key = "sync_buttons") {
                ListItem(
                    headlineContent = {
                        Column {
                            Button(onClick = { viewModel.onEvent(SyncSettingsEvent.SaveSettings) }) {
                                Text(stringResource(R.string.settings_sync_save))
                            }
                            OutlinedButton(
                                onClick = { viewModel.onEvent(SyncSettingsEvent.TestConnection) },
                            ) {
                                Text(stringResource(R.string.settings_sync_test_connection))
                            }
                        }
                    },
                )
            }

            item(key = "sync_divider_1") { HorizontalDivider() }

            // ── Sync now ─────────────────────────────────────────────────
            item(key = "sync_now") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_sync_now)) },
                    supportingContent = {
                        if (state.isSyncing) CircularProgressIndicator()
                        else state.lastSyncResult?.let { Text(it) }
                    },
                    trailingContent = {
                        OutlinedButton(
                            onClick = { viewModel.onEvent(SyncSettingsEvent.SyncNow) },
                            enabled = !state.isSyncing,
                        ) {
                            Text(stringResource(R.string.settings_sync_now))
                        }
                    },
                )
            }

            item(key = "sync_divider_2") { HorizontalDivider() }

            // ── Status chips ─────────────────────────────────────────────
            item(key = "sync_queue_size") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_sync_queue_size)) },
                    trailingContent = {
                        AssistChip(
                            onClick = {},
                            label = { Text("${state.queueSize}") },
                        )
                    },
                )
            }

            item(key = "sync_device_id") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_sync_device_id)) },
                    supportingContent = { Text(state.deviceId.ifBlank { stringResource(R.string.settings_sync_device_id_none) }) },
                )
            }
        }
    }
}

// ─── NavGraph extension ───────────────────────────────────────────────────────

fun NavGraphBuilder.syncSettingsScreen(onNavigateBack: () -> Unit) {
    composable<Route.SettingsSync> {
        SyncSettingsScreen(onNavigateBack = onNavigateBack)
    }
}
