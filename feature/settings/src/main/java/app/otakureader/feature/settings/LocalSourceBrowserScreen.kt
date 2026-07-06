package app.otakureader.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route

/**
 * Converts a SAF tree [uri] (from ACTION_OPEN_DOCUMENT_TREE) to an absolute filesystem path
 * for primary external storage, e.g. `primary:OtakuReader/local` →
 * `/storage/emulated/0/OtakuReader/local`. The local source scans via java.io.File, so it
 * needs a real path. Returns null for non-primary volumes (SD cards) where the mapping isn't
 * reliable — the user can still type those manually.
 */
private fun treeUriToPath(uri: Uri): String? {
    val docId = try {
        DocumentsContract.getTreeDocumentId(uri)
    } catch (_: Exception) {
        return null
    }
    val parts = docId.split(":", limit = 2)
    if (!parts[0].equals("primary", ignoreCase = true)) return null
    val relative = parts.getOrNull(1).orEmpty()
    @Suppress("DEPRECATION")
    val base = Environment.getExternalStorageDirectory().absolutePath
    return if (relative.isEmpty()) base else "$base/$relative"
}

/** True when the app can read arbitrary shared-storage files (needed for the File-based scan). */
private fun hasAllFilesAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

/**
 * Local source browser and configuration screen.
 *
 * This screen surfaces the existing local-source backend that scans folders, CBZ/ZIP archives,
 * EPUB files, ComicInfo.xml metadata, series.json metadata, and image folders from the configured
 * directory. It intentionally avoids duplicating scanner logic; Browse continues to consume the
 * local source through the normal source APIs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSourceBrowserScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var directoryText by remember { mutableStateOf(state.localSourceDirectory) }
    var allFilesAccess by remember { mutableStateOf(hasAllFilesAccess()) }

    LaunchedEffect(state.localSourceDirectory) {
        directoryText = state.localSourceDirectory
    }

    // System folder picker (SAF). We convert the chosen tree to a real path because the local
    // source scans with java.io.File, then save immediately so the user doesn't have to.
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val path = treeUriToPath(uri)
            if (path != null) {
                directoryText = path
                viewModel.onEvent(SettingsEvent.SetLocalSourceDirectory(path))
            }
        }
    }

    // Re-check All-Files-Access when returning from the system settings screen.
    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { allFilesAccess = hasAllFilesAccess() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_local_source)) },
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
        LazyColumn(
            modifier = Modifier.padding(paddingValues),
        ) {
            item(key = "scan_header") {
                SectionHeader(title = stringResource(R.string.settings_scan_directory))
            }
            item(key = "scan_directory") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_directory_path)) },
                    supportingContent = {
                        Column {
                            Text(
                                text = stringResource(R.string.settings_scan_directory_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            // Primary action: pick a folder with the system picker.
                            OutlinedButton(
                                onClick = { folderPicker.launch(null) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(stringResource(R.string.settings_local_source_choose_folder))
                            }
                            // Manual entry remains as a fallback (e.g. SD-card paths the picker
                            // can't map to a filesystem path).
                            OutlinedTextField(
                                value = directoryText,
                                onValueChange = { directoryText = it },
                                label = { Text(stringResource(R.string.settings_directory_path)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                            Button(
                                onClick = { viewModel.onEvent(SettingsEvent.SetLocalSourceDirectory(directoryText)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            ) {
                                Text(stringResource(R.string.settings_save))
                            }
                        }
                    },
                )
            }
            if (!allFilesAccess) {
                item(key = "all_files_access") {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_local_source_grant_access)) },
                        supportingContent = {
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_local_source_grant_access_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                                Uri.parse("package:${context.packageName}"),
                                            )
                                            runCatching { allFilesLauncher.launch(intent) }
                                                .onFailure {
                                                    allFilesLauncher.launch(
                                                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                                                    )
                                                }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.settings_local_source_grant_access_button))
                                }
                            }
                        },
                    )
                }
            }
            item(key = "hidden_folders_divider") { HorizontalDivider() }
            item(key = "hidden_folders") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_local_source_allow_hidden_folders)) },
                    supportingContent = { Text(stringResource(R.string.settings_local_source_allow_hidden_folders_description)) },
                    trailingContent = {
                        Switch(
                            checked = state.allowLocalSourceHiddenFolders,
                            onCheckedChange = { viewModel.onEvent(SettingsEvent.SetAllowLocalSourceHiddenFolders(it)) },
                        )
                    },
                )
            }
            item(key = "formats_divider") { HorizontalDivider() }
            item(key = "formats_header") {
                SectionHeader(title = stringResource(R.string.settings_local_source_supported_formats))
            }
            item(key = "format_archives") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_local_source_archives)) },
                    supportingContent = { Text(stringResource(R.string.settings_local_source_archives_description)) },
                )
            }
            item(key = "format_folders") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_local_source_folders)) },
                    supportingContent = { Text(stringResource(R.string.settings_local_source_folders_description)) },
                )
            }
            item(key = "format_metadata") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_local_source_metadata)) },
                    supportingContent = { Text(stringResource(R.string.settings_local_source_metadata_description)) },
                )
            }
        }
    }
}

fun NavGraphBuilder.localSourceBrowserScreen(onNavigateBack: () -> Unit) {
    composable<Route.LocalSourceBrowser> {
        LocalSourceBrowserScreen(onNavigateBack = onNavigateBack)
    }
}
