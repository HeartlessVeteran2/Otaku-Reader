package app.otakureader.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import app.otakureader.domain.model.Category
import app.otakureader.feature.settings.viewmodel.DownloadViewModel
import kotlin.math.roundToInt

/**
 * Downloads sub-screen: auto-download, Wi-Fi constraints, concurrent downloads,
 * download location, download-ahead, delete-after-reading, save-as-CBZ.
 * Serviced by [DownloadViewModel] backed by [DownloadSettingsDelegate].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDownloadsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDataUsage: () -> Unit = {},
    onNavigateToStorageAnalytics: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DownloadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCbzPasswordDialog by remember { mutableStateOf(false) }

    val downloadLocationContext = androidx.compose.ui.platform.LocalContext.current
    val downloadLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            // Without this the grant is lost on reboot, silently reverting the download
            // location the next time WorkManager/downloads run after a restart.
            runCatching {
                downloadLocationContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.onEvent(SettingsEvent.SetDownloadLocation(uri.toString()))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SettingsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is SettingsEffect.ShowDownloadLocationPicker -> downloadLocationLauncher.launch(null)
                is SettingsEffect.NavigateToDataUsage -> onNavigateToDataUsage()
                is SettingsEffect.NavigateToStorageAnalytics -> onNavigateToStorageAnalytics()
                is SettingsEffect.ShowCbzEncryptionPasswordDialog -> showCbzPasswordDialog = true
                else -> Unit
            }
        }
    }

    if (showCbzPasswordDialog) {
        CbzPasswordDialog(
            onDismiss = { showCbzPasswordDialog = false },
            onConfirm = { password ->
                showCbzPasswordDialog = false
                viewModel.onEvent(SettingsEvent.SetCbzEncryptionPassword(password))
            },
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_downloads)) },
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
            DownloadsContent(state = state, onEvent = viewModel::onEvent)
        }
    }
}

fun NavGraphBuilder.settingsDownloadsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDataUsage: () -> Unit = {},
    onNavigateToStorageAnalytics: () -> Unit = {},
) {
    composable<Route.SettingsDownloads> {
        SettingsDownloadsScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToDataUsage = onNavigateToDataUsage,
            onNavigateToStorageAnalytics = onNavigateToStorageAnalytics,
        )
    }
}

// ─── Section composable ───────────────────────────────────────────────────────

@Suppress("LongMethod")
@Composable
private fun DownloadsContent(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    SectionHeader(title = stringResource(R.string.settings_downloads))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_auto_download_new_chapters)) },
        supportingContent = { Text(stringResource(R.string.settings_auto_download_new_chapters_description)) },
        trailingContent = {
            Switch(
                checked = state.autoDownloadEnabled,
                onCheckedChange = { onEvent(SettingsEvent.SetAutoDownloadEnabled(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_download_only_wifi)) },
        supportingContent = { Text(stringResource(R.string.settings_download_only_wifi_description)) },
        trailingContent = {
            Switch(
                checked = state.downloadOnlyOnWifi,
                onCheckedChange = { onEvent(SettingsEvent.SetDownloadOnlyOnWifi(it)) },
            )
        },
    )

    var autoLimitSlider by remember(state.autoDownloadLimit) {
        mutableFloatStateOf(state.autoDownloadLimit.toFloat())
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_auto_download_limit, autoLimitSlider.roundToInt())) },
        supportingContent = {
            Slider(
                value = autoLimitSlider,
                onValueChange = { autoLimitSlider = it },
                onValueChangeFinished = {
                    onEvent(SettingsEvent.SetAutoDownloadLimit(autoLimitSlider.roundToInt()))
                },
                valueRange = 1f..10f,
                steps = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )

    // Category include/exclude dialogs
    var showIncludeDialog by remember { mutableStateOf(false) }
    var showExcludeDialog by remember { mutableStateOf(false) }

    val includeSummary = when {
        state.autoDownloadCategoryInclude.isEmpty() -> stringResource(R.string.settings_auto_download_category_all)
        else -> stringResource(R.string.settings_auto_download_category_count, state.autoDownloadCategoryInclude.size)
    }
    val excludeSummary = when {
        state.autoDownloadCategoryExclude.isEmpty() -> stringResource(R.string.settings_auto_download_category_none_excluded)
        else -> stringResource(R.string.settings_auto_download_category_count, state.autoDownloadCategoryExclude.size)
    }

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_auto_download_category_include)) },
        supportingContent = { Text(includeSummary) },
        trailingContent = {
            OutlinedButton(onClick = { showIncludeDialog = true }) {
                Text(stringResource(R.string.settings_configure))
            }
        },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_auto_download_category_exclude)) },
        supportingContent = { Text(excludeSummary) },
        trailingContent = {
            OutlinedButton(onClick = { showExcludeDialog = true }) {
                Text(stringResource(R.string.settings_configure))
            }
        },
    )

    if (showIncludeDialog) {
        CategoryPickerDialog(
            title = stringResource(R.string.settings_auto_download_category_include),
            categories = state.availableCategories,
            selected = state.autoDownloadCategoryInclude,
            onDismiss = { showIncludeDialog = false },
            onConfirm = { ids ->
                onEvent(SettingsEvent.SetAutoDownloadCategoryInclude(ids))
                showIncludeDialog = false
            },
        )
    }
    if (showExcludeDialog) {
        CategoryPickerDialog(
            title = stringResource(R.string.settings_auto_download_category_exclude),
            categories = state.availableCategories,
            selected = state.autoDownloadCategoryExclude,
            onDismiss = { showExcludeDialog = false },
            onConfirm = { ids ->
                onEvent(SettingsEvent.SetAutoDownloadCategoryExclude(ids))
                showExcludeDialog = false
            },
        )
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    SectionHeader(title = stringResource(R.string.settings_download_location))

    val locationText = state.downloadLocation?.substringAfterLast("/")
        ?: stringResource(R.string.settings_download_location_default)
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_download_location)) },
        supportingContent = { Text(locationText) },
        trailingContent = {
            OutlinedButton(onClick = { onEvent(SettingsEvent.RequestDownloadLocationPicker) }) {
                Text(stringResource(R.string.settings_change))
            }
        },
        modifier = Modifier.clickable { onEvent(SettingsEvent.RequestDownloadLocationPicker) },
    )

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    SectionHeader(title = stringResource(R.string.settings_download_performance))

    var concurrentSlider by remember(state.concurrentDownloads) {
        mutableFloatStateOf(state.concurrentDownloads.toFloat())
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_concurrent_downloads, concurrentSlider.roundToInt())) },
        supportingContent = {
            Slider(
                value = concurrentSlider,
                onValueChange = { concurrentSlider = it },
                onValueChangeFinished = {
                    onEvent(SettingsEvent.SetConcurrentDownloads(concurrentSlider.roundToInt()))
                },
                valueRange = 1f..5f,
                steps = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    SectionHeader(title = stringResource(R.string.settings_download_ahead))

    var aheadSlider by remember(state.downloadAheadWhileReading) {
        mutableFloatStateOf(state.downloadAheadWhileReading.toFloat())
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_download_ahead_count, aheadSlider.roundToInt())) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.settings_download_ahead_description))
                Slider(
                    value = aheadSlider,
                    onValueChange = { aheadSlider = it },
                    onValueChangeFinished = {
                        onEvent(SettingsEvent.SetDownloadAheadWhileReading(aheadSlider.roundToInt()))
                    },
                    valueRange = 0f..5f,
                    steps = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_download_ahead_wifi_only)) },
        supportingContent = { Text(stringResource(R.string.settings_download_ahead_wifi_only_description)) },
        trailingContent = {
            Switch(
                checked = state.downloadAheadOnlyOnWifi,
                onCheckedChange = { onEvent(SettingsEvent.SetDownloadAheadOnlyOnWifi(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_delete_after_reading)) },
        supportingContent = { Text(stringResource(R.string.settings_delete_after_reading_description)) },
        trailingContent = {
            Switch(
                checked = state.deleteAfterReading,
                onCheckedChange = { onEvent(SettingsEvent.SetDeleteAfterReading(it)) },
            )
        },
    )

    if (state.deleteAfterReading) {
        var slotsSlider by remember(state.removeAfterReadSlots) {
            mutableFloatStateOf(state.removeAfterReadSlots.toFloat())
        }
        val slots = slotsSlider.roundToInt()
        ListItem(
            headlineContent = {
                Text(
                    if (slots == 0) {
                        stringResource(R.string.settings_remove_after_read_immediately)
                    } else {
                        stringResource(R.string.settings_remove_after_read_keep_last, slots)
                    },
                )
            },
            supportingContent = {
                Column {
                    Text(stringResource(R.string.settings_remove_after_read_description))
                    Slider(
                        value = slotsSlider,
                        onValueChange = { slotsSlider = it },
                        onValueChangeFinished = {
                            onEvent(SettingsEvent.SetRemoveAfterReadSlots(slotsSlider.roundToInt()))
                        },
                        valueRange = 0f..4f,
                        steps = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_save_as_cbz)) },
        supportingContent = { Text(stringResource(R.string.settings_save_as_cbz_description)) },
        trailingContent = {
            Switch(
                checked = state.saveAsCbz,
                onCheckedChange = { onEvent(SettingsEvent.SetSaveAsCbz(it)) },
            )
        },
    )

    if (state.saveAsCbz) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_cbz_encryption)) },
            supportingContent = { Text(stringResource(R.string.settings_cbz_encryption_description)) },
            trailingContent = {
                Switch(
                    checked = state.cbzEncryptionEnabled,
                    onCheckedChange = { onEvent(SettingsEvent.SetCbzEncryptionEnabled(it)) },
                )
            },
        )
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    SmartDownloadSection(state = state, onEvent = onEvent)

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    SectionHeader(title = stringResource(R.string.settings_data_usage))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_download_data_saver)) },
        supportingContent = { Text(stringResource(R.string.settings_download_data_saver_desc)) },
        trailingContent = {
            Switch(
                checked = state.downloadDataSaverEnabled,
                onCheckedChange = { onEvent(SettingsEvent.SetDownloadDataSaverEnabled(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_data_usage)) },
        modifier = Modifier.clickable { onEvent(SettingsEvent.NavigateToDataUsage) },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.storage_analytics_title)) },
        supportingContent = { Text(stringResource(R.string.storage_analytics_subtitle)) },
        modifier = Modifier.clickable { onEvent(SettingsEvent.NavigateToStorageAnalytics) },
    )
}

@Suppress("LongMethod")
@Composable
private fun SmartDownloadSection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    SectionHeader(title = stringResource(R.string.settings_smart_download))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_smart_download_enabled)) },
        supportingContent = { Text(stringResource(R.string.settings_smart_download_enabled_description)) },
        trailingContent = {
            Switch(
                checked = state.smartDownloadEnabled,
                onCheckedChange = { onEvent(SettingsEvent.SetSmartDownloadEnabled(it)) },
            )
        },
    )

    if (state.smartDownloadEnabled) {
        var aheadSlider by remember(state.smartDownloadChaptersAhead) {
            mutableFloatStateOf(state.smartDownloadChaptersAhead.toFloat())
        }
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.settings_smart_download_chapters_ahead, aheadSlider.roundToInt()))
            },
            supportingContent = {
                Slider(
                    value = aheadSlider,
                    onValueChange = { aheadSlider = it },
                    onValueChangeFinished = {
                        onEvent(SettingsEvent.SetSmartDownloadChaptersAhead(aheadSlider.roundToInt()))
                    },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )

        var thresholdSlider by remember(state.smartDownloadThreshold) {
            mutableFloatStateOf(state.smartDownloadThreshold)
        }
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.settings_smart_download_threshold, (thresholdSlider * 100).roundToInt()))
            },
            supportingContent = {
                Slider(
                    value = thresholdSlider,
                    onValueChange = { thresholdSlider = it },
                    onValueChangeFinished = {
                        onEvent(SettingsEvent.SetSmartDownloadThreshold(thresholdSlider))
                    },
                    valueRange = 0.5f..0.95f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_smart_download_wifi_only)) },
            trailingContent = {
                Switch(
                    checked = state.smartDownloadWifiOnly,
                    onCheckedChange = { onEvent(SettingsEvent.SetSmartDownloadWifiOnly(it)) },
                )
            },
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_smart_download_favorites_only)) },
            trailingContent = {
                Switch(
                    checked = state.smartDownloadFavoritesOnly,
                    onCheckedChange = { onEvent(SettingsEvent.SetSmartDownloadFavoritesOnly(it)) },
                )
            },
        )

        var storageSlider by remember(state.smartDownloadMinStorageMb) {
            mutableFloatStateOf(state.smartDownloadMinStorageMb.toFloat())
        }
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.settings_smart_download_min_storage, storageSlider.roundToInt()))
            },
            supportingContent = {
                Slider(
                    value = storageSlider,
                    onValueChange = { storageSlider = it },
                    onValueChangeFinished = {
                        onEvent(SettingsEvent.SetSmartDownloadMinStorageMb(storageSlider.roundToInt()))
                    },
                    valueRange = 100f..2000f,
                    steps = 18,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

@Composable
private fun CategoryPickerDialog(
    title: String,
    categories: List<Category>,
    selected: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit,
) {
    var currentSelection by remember(selected) { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (categories.isEmpty()) {
                Text(stringResource(R.string.settings_auto_download_category_all))
            } else {
                LazyColumn {
                    items(categories) { category ->
                        val checked = category.id in currentSelection
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentSelection = if (checked) {
                                        currentSelection - category.id
                                    } else {
                                        currentSelection + category.id
                                    }
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    currentSelection = if (isChecked) {
                                        currentSelection + category.id
                                    } else {
                                        currentSelection - category.id
                                    }
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(category.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentSelection) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun CbzPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_cbz_encryption_password_title)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.settings_cbz_encryption_password_hint)) },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (password.isNotBlank()) onConfirm(password) },
                enabled = password.isNotBlank(),
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
