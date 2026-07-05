package app.otakureader.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import app.otakureader.domain.model.BackupOptions
import app.otakureader.feature.settings.viewmodel.BackupSyncViewModel

/**
 * Backup & Restore sub-screen: create/restore backups, import from Tachiyomi, automatic
 * backup scheduling.
 * Serviced by [BackupSyncViewModel] backed by [BackupSettingsDelegate].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBackupScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMigrationEntry: () -> Unit = {},
    onNavigateToCloudBackup: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: BackupSyncViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEncryptionSetupDialog by remember { mutableStateOf(false) }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var showRestorePasswordDialog by remember { mutableStateOf(false) }
    var pendingBackupUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val backupFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        uri?.let { viewModel.createBackup(it) }
    }

    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        // Route through the pre-restore preflight dialog before restoring.
        uri?.let { viewModel.onEvent(SettingsEvent.ShowRestoreConfirm(it)) }
    }

    val tachiyomiImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { viewModel.onEvent(SettingsEvent.ImportTachiyomiBackupFromUri(it)) }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val backupLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri?.let {
            // Persist read/write access so the background worker can write here later.
            // The grant can fail (SecurityException) if the provider rejects it — don't crash.
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                viewModel.onEvent(SettingsEvent.SetAutoBackupLocation(it.toString()))
            } catch (e: SecurityException) {
                android.util.Log.e("SettingsBackupScreen", "Failed to persist backup location permission", e)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.onEvent(SettingsEvent.RefreshLocalBackups)
        viewModel.effect.collect { effect ->
            when (effect) {
                is SettingsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                SettingsEffect.ShowBackupPicker ->
                    backupFileLauncher.launch("otakureader_backup_${System.currentTimeMillis()}.json")
                SettingsEffect.ShowRestorePicker ->
                    restoreFileLauncher.launch(arrayOf("application/json"))
                SettingsEffect.ShowTachiyomiImportPicker ->
                    tachiyomiImportLauncher.launch(arrayOf("application/json", "*/*"))
                SettingsEffect.ShowAutoBackupLocationPicker ->
                    backupLocationLauncher.launch(null)
                SettingsEffect.NavigateToMigrationEntry -> onNavigateToMigrationEntry()
                SettingsEffect.NavigateToCloudBackup -> onNavigateToCloudBackup()
                SettingsEffect.ShowEncryptionPasswordSetupDialog -> showEncryptionSetupDialog = true
                is SettingsEffect.ShowEncryptionPasswordForBackupDialog -> {
                    pendingBackupUri = effect.uri
                    showBackupPasswordDialog = true
                }
                is SettingsEffect.ShowEncryptionPasswordForRestoreDialog -> {
                    pendingRestoreUri = effect.uri
                    showRestorePasswordDialog = true
                }
                else -> Unit
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_backup)) },
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
            BackupContent(
                state = state,
                onEvent = viewModel::onEvent,
                onNavigateToCloudBackup = onNavigateToCloudBackup,
            )
        }
    }

    // Pre-backup checklist dialog — shown before the file-save picker opens.
    if (state.showBackupChecklist) {
        BackupChecklistDialog(
            mangaCount = state.backupChecklistMangaCount,
            categoryCount = state.backupChecklistCategoryCount,
            opdsCount = state.backupChecklistOpdsCount,
            feedCount = state.backupChecklistFeedCount,
            syncConfigCount = state.backupChecklistSyncConfigCount,
            options = state.backupOptions,
            onOptionsChange = { viewModel.onEvent(SettingsEvent.SetBackupOptions(it)) },
            onConfirm = { viewModel.onEvent(SettingsEvent.ConfirmCreateBackup) },
            onDismiss = { viewModel.onEvent(SettingsEvent.DismissBackupChecklist) },
        )
    }

    // Pre-restore preflight dialog — shown after the restore file is picked.
    if (state.showRestoreConfirm) {
        RestorePreflightDialog(
            fileName = state.pendingRestoreFileName,
            options = state.restoreOptions,
            onOptionsChange = { viewModel.onEvent(SettingsEvent.SetRestoreOptions(it)) },
            onConfirm = {
                val uri = state.pendingRestoreUri
                if (uri != null) {
                    viewModel.onEvent(SettingsEvent.ConfirmRestore(Uri.parse(uri)))
                }
            },
            onDismiss = { viewModel.onEvent(SettingsEvent.DismissRestoreConfirm) },
        )
    }

    state.tachiyomiImportPreview?.let { preview ->
        TachiyomiImportConfirmDialog(
            preview = preview,
            onConfirm = { overwrite -> viewModel.onEvent(SettingsEvent.ConfirmTachiyomiImport(overwrite)) },
            onDismiss = { viewModel.onEvent(SettingsEvent.CancelTachiyomiImport) },
        )
    }

    if (state.isTachiyomiImporting && state.tachiyomiImportTotal > 0) {
        TachiyomiImportProgressDialog(
            current = state.tachiyomiImportProgress,
            total = state.tachiyomiImportTotal,
        )
    }

    if (showEncryptionSetupDialog) {
        EncryptionPasswordSetupDialog(
            onDismiss = { showEncryptionSetupDialog = false },
            onConfirm = { password ->
                viewModel.onEvent(SettingsEvent.SetBackupEncryptionPassword(password))
                showEncryptionSetupDialog = false
            },
        )
    }

    if (showBackupPasswordDialog) {
        pendingBackupUri?.let { uri ->
            EncryptionPasswordDialog(
                title = stringResource(R.string.settings_backup_encryption_enter_password),
                onDismiss = { showBackupPasswordDialog = false; pendingBackupUri = null },
                onConfirm = { password ->
                    viewModel.onEvent(SettingsEvent.CreateEncryptedBackupWithUri(uri, password))
                    showBackupPasswordDialog = false
                    pendingBackupUri = null
                },
            )
        }
    }

    if (showRestorePasswordDialog) {
        pendingRestoreUri?.let { uri ->
            EncryptionPasswordDialog(
                title = stringResource(R.string.settings_backup_encryption_enter_password_restore),
                onDismiss = { showRestorePasswordDialog = false; pendingRestoreUri = null },
                onConfirm = { password ->
                    viewModel.onEvent(SettingsEvent.RestoreEncryptedBackupFromUri(uri, password))
                    showRestorePasswordDialog = false
                    pendingRestoreUri = null
                },
            )
        }
    }
}

/**
 * Pre-backup checklist dialog.
 * Lets the user pick which data categories to include before the file-save picker opens.
 * Each checkbox label shows the current count for that section (see [BackupOptionCheckboxRow]);
 * chapters/tracking are per-manga data, so they're disabled while libraryEntries is off.
 */
@Composable
private fun BackupChecklistDialog(
    mangaCount: Int,
    categoryCount: Int,
    opdsCount: Int,
    feedCount: Int,
    syncConfigCount: Int,
    options: BackupOptions,
    onOptionsChange: (BackupOptions) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_checklist_title)) },
        text = {
            Column {
                BackupOptionCheckboxRow(
                    checked = options.libraryEntries,
                    onCheckedChange = { onOptionsChange(options.copy(libraryEntries = it)) },
                    label = stringResource(
                        R.string.backup_option_with_count,
                        stringResource(R.string.backup_option_library_entries),
                        mangaCount,
                    ),
                )
                BackupOptionCheckboxRow(
                    checked = options.chapters,
                    enabled = options.libraryEntries,
                    onCheckedChange = { onOptionsChange(options.copy(chapters = it)) },
                    label = stringResource(R.string.backup_option_chapters),
                )
                BackupOptionCheckboxRow(
                    checked = options.categories,
                    onCheckedChange = { onOptionsChange(options.copy(categories = it)) },
                    label = stringResource(
                        R.string.backup_option_with_count,
                        stringResource(R.string.backup_option_categories),
                        categoryCount,
                    ),
                )
                BackupOptionCheckboxRow(
                    checked = options.tracking,
                    enabled = options.libraryEntries,
                    onCheckedChange = { onOptionsChange(options.copy(tracking = it)) },
                    label = stringResource(R.string.backup_option_tracking),
                )
                BackupOptionCheckboxRow(
                    checked = options.preferences,
                    onCheckedChange = { onOptionsChange(options.copy(preferences = it)) },
                    label = stringResource(R.string.backup_option_preferences),
                )
                BackupOptionCheckboxRow(
                    checked = options.opdsServers,
                    onCheckedChange = { onOptionsChange(options.copy(opdsServers = it)) },
                    label = stringResource(
                        R.string.backup_option_with_count,
                        stringResource(R.string.backup_option_opds_servers),
                        opdsCount,
                    ),
                )
                BackupOptionCheckboxRow(
                    checked = options.feed,
                    onCheckedChange = { onOptionsChange(options.copy(feed = it)) },
                    label = stringResource(
                        R.string.backup_option_with_count,
                        stringResource(R.string.backup_option_feed),
                        feedCount,
                    ),
                )
                BackupOptionCheckboxRow(
                    checked = options.syncConfigurations,
                    onCheckedChange = { onOptionsChange(options.copy(syncConfigurations = it)) },
                    label = stringResource(
                        R.string.backup_option_with_count,
                        stringResource(R.string.backup_option_sync_configurations),
                        syncConfigCount,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = options.canCreate()) {
                Text(stringResource(R.string.backup_checklist_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_import_cancel))
            }
        },
    )
}

/**
 * Pre-restore preflight dialog.
 * Shown after the user selects a backup file but before the restore begins. Lets them pick
 * which sections to apply and confirms they understand the operation will overwrite matching
 * data in their current library.
 */
@Composable
private fun RestorePreflightDialog(
    fileName: String,
    options: BackupOptions,
    onOptionsChange: (BackupOptions) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_preflight_title)) },
        text = {
            Column {
                if (fileName.isNotBlank()) {
                    Text(stringResource(R.string.restore_preflight_filename, fileName))
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(stringResource(R.string.restore_preflight_message))
                Spacer(modifier = Modifier.height(8.dp))
                BackupOptionCheckboxRow(
                    checked = options.libraryEntries,
                    onCheckedChange = { onOptionsChange(options.copy(libraryEntries = it)) },
                    label = stringResource(R.string.backup_option_library_entries),
                )
                BackupOptionCheckboxRow(
                    checked = options.chapters,
                    enabled = options.libraryEntries,
                    onCheckedChange = { onOptionsChange(options.copy(chapters = it)) },
                    label = stringResource(R.string.backup_option_chapters),
                )
                BackupOptionCheckboxRow(
                    checked = options.categories,
                    onCheckedChange = { onOptionsChange(options.copy(categories = it)) },
                    label = stringResource(R.string.backup_option_categories),
                )
                BackupOptionCheckboxRow(
                    checked = options.tracking,
                    enabled = options.libraryEntries,
                    onCheckedChange = { onOptionsChange(options.copy(tracking = it)) },
                    label = stringResource(R.string.backup_option_tracking),
                )
                BackupOptionCheckboxRow(
                    checked = options.preferences,
                    onCheckedChange = { onOptionsChange(options.copy(preferences = it)) },
                    label = stringResource(R.string.backup_option_preferences),
                )
                BackupOptionCheckboxRow(
                    checked = options.opdsServers,
                    onCheckedChange = { onOptionsChange(options.copy(opdsServers = it)) },
                    label = stringResource(R.string.backup_option_opds_servers),
                )
                BackupOptionCheckboxRow(
                    checked = options.feed,
                    onCheckedChange = { onOptionsChange(options.copy(feed = it)) },
                    label = stringResource(R.string.backup_option_feed),
                )
                BackupOptionCheckboxRow(
                    checked = options.syncConfigurations,
                    onCheckedChange = { onOptionsChange(options.copy(syncConfigurations = it)) },
                    label = stringResource(R.string.backup_option_sync_configurations),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = options.canCreate()) {
                Text(stringResource(R.string.restore_preflight_proceed))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_import_cancel))
            }
        },
    )
}

/** A single checkbox row for a [BackupOptions] section, used by both backup/restore dialogs. */
@Composable
private fun BackupOptionCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = checked,
                onClick = { onCheckedChange(!checked) },
                role = Role.Checkbox,
                enabled = enabled,
            )
            .padding(vertical = 4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun TachiyomiImportConfirmDialog(
    preview: app.otakureader.domain.model.TachiyomiBackupPreview,
    onConfirm: (overwriteExisting: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var overwriteExisting by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_import_preview_title)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_import_preview_manga, preview.mangaCount))
                Text(stringResource(R.string.settings_import_preview_categories, preview.categoryCount))
                Text(stringResource(R.string.settings_import_preview_chapters, preview.chapterCount))
                Text(stringResource(R.string.settings_import_preview_tracking, preview.trackingCount))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = overwriteExisting,
                            onClick = { overwriteExisting = !overwriteExisting },
                            role = Role.Checkbox,
                        )
                        .padding(top = 12.dp),
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = overwriteExisting,
                        onCheckedChange = null,
                    )
                    Text(
                        text = stringResource(R.string.settings_import_overwrite_existing),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(overwriteExisting) }) {
                Text(stringResource(R.string.settings_import_button))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_import_cancel))
            }
        },
    )
}

@Composable
private fun TachiyomiImportProgressDialog(
    current: Int,
    total: Int,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_import_progress_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { if (total > 0) current.toFloat() / total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_import_progress_count, current, total),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

fun NavGraphBuilder.settingsBackupScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMigrationEntry: () -> Unit = {},
    onNavigateToCloudBackup: () -> Unit = {},
) {
    composable<Route.SettingsBackup> {
        SettingsBackupScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToMigrationEntry = onNavigateToMigrationEntry,
            onNavigateToCloudBackup = onNavigateToCloudBackup,
        )
    }
}

// ─── Section composable ───────────────────────────────────────────────────────

@Suppress("LongMethod")
@Composable
private fun BackupContent(
    state: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
    onNavigateToCloudBackup: () -> Unit = {},
) {
    SectionHeader(title = stringResource(R.string.settings_backup_restore_migration))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_create_backup)) },
        supportingContent = { Text(stringResource(R.string.settings_create_backup_description)) },
        trailingContent = {
            if (state.isBackupInProgress) {
                CircularProgressIndicator()
            } else {
                Button(onClick = { onEvent(SettingsEvent.ShowBackupChecklist) }) {
                    Text(stringResource(R.string.settings_backup_button))
                }
            }
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_restore_backup)) },
        supportingContent = { Text(stringResource(R.string.settings_restore_backup_description)) },
        trailingContent = {
            if (state.isRestoreInProgress) {
                CircularProgressIndicator()
            } else {
                Button(onClick = { onEvent(SettingsEvent.OnRestoreBackup) }) {
                    Text(stringResource(R.string.settings_restore_button))
                }
            }
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_import_tachiyomi)) },
        supportingContent = {
            if (state.isTachiyomiImporting && state.tachiyomiImportTotal > 0) {
                Text(
                    stringResource(
                        R.string.settings_import_progress,
                        state.tachiyomiImportProgress,
                        state.tachiyomiImportTotal,
                    )
                )
            } else {
                Text(stringResource(R.string.settings_import_tachiyomi_description))
            }
        },
        trailingContent = {
            if (state.isRestoreInProgress) {
                CircularProgressIndicator()
            } else {
                Button(onClick = { onEvent(SettingsEvent.OnImportTachiyomiBackup) }) {
                    Text(stringResource(R.string.settings_import_button))
                }
            }
        },
    )

    HorizontalDivider()
    SectionHeader(title = stringResource(R.string.settings_automatic_backups))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_enable_auto_backup)) },
        supportingContent = { Text(stringResource(R.string.settings_enable_auto_backup_description)) },
        trailingContent = {
            Switch(
                checked = state.autoBackupEnabled,
                onCheckedChange = { onEvent(SettingsEvent.SetAutoBackupEnabled(it)) },
            )
        },
    )

    if (state.autoBackupEnabled) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_backup_frequency)) },
            supportingContent = {
                Column(modifier = Modifier.selectableGroup()) {
                    val options = listOf(
                        stringResource(R.string.settings_backup_frequency_6h) to 6,
                        stringResource(R.string.settings_backup_frequency_12h) to 12,
                        stringResource(R.string.settings_backup_frequency_daily) to 24,
                        stringResource(R.string.settings_backup_frequency_2days) to 48,
                        stringResource(R.string.settings_backup_frequency_weekly) to 168,
                        stringResource(R.string.settings_backup_frequency_biweekly) to 336,
                        stringResource(R.string.settings_backup_frequency_monthly) to 720,
                    )
                    options.forEach { (label, hours) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = state.autoBackupIntervalHours == hours,
                                    onClick = { onEvent(SettingsEvent.SetAutoBackupInterval(hours)) },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(
                                selected = state.autoBackupIntervalHours == hours,
                                onClick = null,
                            )
                            Text(text = label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_backups_to_keep)) },
            supportingContent = {
                Column(modifier = Modifier.selectableGroup()) {
                    listOf(3, 5, 10).forEach { count ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = state.autoBackupMaxCount == count,
                                    onClick = { onEvent(SettingsEvent.SetAutoBackupMaxCount(count)) },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(
                                selected = state.autoBackupMaxCount == count,
                                onClick = null,
                            )
                            Text(
                                text = stringResource(R.string.settings_backup_count, count),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            },
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_backup_location)) },
            supportingContent = {
                val uri = state.autoBackupLocationUri
                Text(
                    if (uri.isBlank()) {
                        stringResource(R.string.settings_backup_location_default)
                    } else {
                        Uri.parse(uri).lastPathSegment ?: uri
                    },
                )
            },
            trailingContent = {
                OutlinedButton(onClick = { onEvent(SettingsEvent.RequestAutoBackupLocationPicker) }) {
                    Text(stringResource(R.string.settings_backup_location_choose))
                }
            },
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_backup_last)) },
            supportingContent = {
                val ts = state.lastAutoBackupTimestamp
                Text(
                    if (ts <= 0L) {
                        stringResource(R.string.settings_backup_last_never)
                    } else {
                        java.text.DateFormat.getDateTimeInstance(
                            java.text.DateFormat.MEDIUM,
                            java.text.DateFormat.SHORT,
                        ).format(java.util.Date(ts))
                    },
                )
            },
        )

        if (state.localBackupFiles.isNotEmpty()) {
            SectionHeader(title = stringResource(R.string.settings_restore_from_auto))
            state.localBackupFiles.forEach { fileName ->
                val isRestoringThisFile = state.restoringBackupFileName == fileName
                ListItem(
                    headlineContent = { Text(fileName) },
                    trailingContent = {
                        if (isRestoringThisFile) {
                            CircularProgressIndicator()
                        } else {
                            OutlinedButton(
                                enabled = !state.isRestoreInProgress,
                                onClick = { onEvent(SettingsEvent.RestoreLocalBackup(fileName)) },
                            ) {
                                Text(stringResource(R.string.settings_restore_button))
                            }
                        }
                    },
                )
            }
        } else {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_no_auto_backups)) },
                supportingContent = { Text(stringResource(R.string.settings_no_auto_backups_description)) },
            )
        }
    }

    HorizontalDivider()
    SectionHeader(title = stringResource(R.string.settings_cloud_backup))
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_cloud_backup)) },
        supportingContent = { Text(stringResource(R.string.settings_cloud_backup_destination)) },
        trailingContent = {
            OutlinedButton(onClick = onNavigateToCloudBackup) {
                Text(stringResource(R.string.settings_cloud_backup_configure))
            }
        },
    )

    HorizontalDivider()
    SectionHeader(title = stringResource(R.string.settings_backup_encryption))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_backup_encryption_enabled)) },
        supportingContent = { Text(stringResource(R.string.settings_backup_encryption_enabled_description)) },
        trailingContent = {
            Switch(
                checked = state.backupEncryptionEnabled,
                onCheckedChange = { onEvent(SettingsEvent.SetBackupEncryptionEnabled(it)) },
            )
        },
    )

    if (state.backupEncryptionEnabled) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_backup_encryption_password)) },
            supportingContent = {
                Text(
                    if (state.backupEncryptionPasswordSet)
                        stringResource(R.string.settings_backup_encryption_password_set)
                    else
                        stringResource(R.string.settings_backup_encryption_password_not_set),
                )
            },
            trailingContent = {
                OutlinedButton(onClick = { onEvent(SettingsEvent.RequestSetBackupPassword) }) {
                    Text(
                        if (state.backupEncryptionPasswordSet)
                            stringResource(R.string.settings_backup_encryption_change_password)
                        else
                            stringResource(R.string.settings_backup_encryption_set_password),
                    )
                }
            },
        )
    }
}

@Composable
private fun EncryptionPasswordSetupDialog(
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm  by remember { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && password != confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_backup_encryption_setup_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.settings_backup_encryption_new_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text(stringResource(R.string.settings_backup_encryption_confirm_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        { Text(stringResource(R.string.settings_backup_encryption_passwords_mismatch)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (password.isNotEmpty() && !mismatch) onConfirm(password) },
                enabled = password.isNotEmpty() && !mismatch,
            ) {
                Text(stringResource(R.string.settings_backup_encryption_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_import_cancel))
            }
        },
    )
}

@Composable
private fun EncryptionPasswordDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.settings_backup_encryption_password_label)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (password.isNotEmpty()) onConfirm(password) },
                enabled = password.isNotEmpty(),
            ) {
                Text(stringResource(R.string.settings_import_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_import_cancel))
            }
        },
    )
}
