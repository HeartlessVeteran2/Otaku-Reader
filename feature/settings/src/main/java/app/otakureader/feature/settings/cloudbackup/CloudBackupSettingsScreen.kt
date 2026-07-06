package app.otakureader.feature.settings.cloudbackup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import app.otakureader.feature.settings.R

/**
 * Cloud Backup settings screen — lets the user choose a cloud destination (WebDAV) and configure
 * the credentials used by [BackupWorker] to upload automatic backups.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CloudBackupSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is CloudBackupSettingsEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_cloud_backup)) },
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
            CloudBackupContent(state = state, onEvent = viewModel::onEvent)
        }
    }
}

@Composable
private fun CloudBackupContent(
    state: CloudBackupSettingsState,
    onEvent: (CloudBackupSettingsEvent) -> Unit,
) {
    // ─── Destination picker ───────────────────────────────────────────────────

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_cloud_backup_destination)) },
        supportingContent = {
            Column(modifier = Modifier.selectableGroup()) {
                // None option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.destination is CloudBackupDestination.None,
                            onClick = { onEvent(CloudBackupSettingsEvent.SetDestination(CloudBackupDestination.None)) },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 4.dp),
                ) {
                    RadioButton(
                        selected = state.destination is CloudBackupDestination.None,
                        onClick = null,
                    )
                    Text(text = "None", modifier = Modifier.padding(start = 8.dp))
                }

                // WebDAV option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.destination is CloudBackupDestination.WebDav,
                            onClick = { onEvent(CloudBackupSettingsEvent.SetDestination(CloudBackupDestination.WebDav)) },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 4.dp),
                ) {
                    RadioButton(
                        selected = state.destination is CloudBackupDestination.WebDav,
                        onClick = null,
                    )
                    Text(
                        text = stringResource(R.string.settings_cloud_backup_webdav),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
    )

    // Coming-soon providers shown as disabled chips
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("Google Drive", "Dropbox", "OneDrive").forEach { label ->
            SuggestionChip(
                onClick = {},
                label = { Text("$label — coming soon") },
                enabled = false,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }

    HorizontalDivider()

    // ─── WebDAV credential fields ─────────────────────────────────────────────

    if (state.destination is CloudBackupDestination.WebDav) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = state.webDavUrl,
                onValueChange = { onEvent(CloudBackupSettingsEvent.SetWebDavUrl(it)) },
                label = { Text("Server URL") },
                placeholder = { Text("https://nextcloud.example.com/remote.php/dav/files/user/Backups") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.webDavUsername,
                onValueChange = { onEvent(CloudBackupSettingsEvent.SetWebDavUsername(it)) },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.webDavPassword,
                onValueChange = { onEvent(CloudBackupSettingsEvent.SetWebDavPassword(it)) },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Save and Test buttons
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onEvent(CloudBackupSettingsEvent.SaveCredentials) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.settings_cloud_backup_save_credentials))
                }

                Spacer(modifier = Modifier.padding(horizontal = 8.dp))

                Button(
                    onClick = { onEvent(CloudBackupSettingsEvent.TestConnection) },
                    enabled = !state.isTestingConnection && state.webDavUrl.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isTestingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(18.dp)
                                .padding(end = 4.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.settings_cloud_backup_test_connection))
                    }
                }
            }

            // Connection test result
            state.connectionTestResult?.let { result ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.startsWith("Connected")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Clear credentials
            TextButton(
                onClick = { onEvent(CloudBackupSettingsEvent.ClearCredentials) },
            ) {
                Text("Clear credentials", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

fun NavGraphBuilder.cloudBackupSettingsScreen(
    onNavigateBack: () -> Unit,
) {
    composable<Route.SettingsCloudBackup> {
        CloudBackupSettingsScreen(onNavigateBack = onNavigateBack)
    }
}
