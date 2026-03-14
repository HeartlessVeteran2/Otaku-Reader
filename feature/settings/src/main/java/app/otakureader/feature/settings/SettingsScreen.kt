package app.otakureader.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.core.preferences.AiTier
import app.otakureader.core.ui.theme.COLOR_SCHEME_CUSTOM_ACCENT
import app.otakureader.feature.reader.model.ImageQuality
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Settings screen for user-configurable options.
 * Built with Jetpack Compose as specified in the project architecture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMigrationEntry: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // File picker for creating backup
    val backupFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { viewModel.createBackup(it) }
    }

    // File picker for restoring backup
    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.restoreBackup(it) }
    }

    // Collect effects
    LaunchedEffect(Unit) {
        viewModel.onEvent(SettingsEvent.RefreshLocalBackups)
        viewModel.effect.collect { effect ->
            when (effect) {
                is SettingsEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                SettingsEffect.ShowBackupPicker -> {
                    backupFileLauncher.launch("otakureader_backup_${System.currentTimeMillis()}.json")
                }
                SettingsEffect.ShowRestorePicker -> {
                    restoreFileLauncher.launch(arrayOf("application/json"))
                }
                SettingsEffect.NavigateToMigrationEntry -> onNavigateToMigrationEntry()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            AppearanceSection(state = state, onEvent = viewModel::onEvent)
            HorizontalDivider()
            LibrarySection(state = state, onEvent = viewModel::onEvent)
            HorizontalDivider()
            BrowseSection(state = state, onEvent = viewModel::onEvent)
            HorizontalDivider()
            DownloadsSettingsSection(state = state, onEvent = viewModel::onEvent)
            HorizontalDivider()
            ReaderSection(state = state, onEvent = viewModel::onEvent)
            HorizontalDivider()
            LocalSourceSection(state = state, onEvent = viewModel::onEvent)
            HorizontalDivider()
            TrackingSection(state = state, onEvent = viewModel::onEvent)
            HorizontalDivider()
            NotificationsSection(state = state, onEvent = viewModel::onEvent)
            HorizontalDivider()
            ReadingGoalsSection(state = state, onEvent = viewModel::onEvent)
            HorizontalDivider()
            DataStorageSection(state = state, onEvent = viewModel::onEvent)
            HorizontalDivider()
            MigrationSection(state = state, onEvent = viewModel::onEvent)
            HorizontalDivider()
            DiscordSection(state = state, onEvent = viewModel::onEvent)
            HorizontalDivider()
            AiSection(state = state, onEvent = viewModel::onEvent)
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun AppearanceSection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    // ── Appearance ────────────────────────────────────────────────────
            SectionHeader(title = stringResource(R.string.settings_appearance))

            // Theme mode
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_theme)) },
                supportingContent = {
                    Row(modifier = Modifier.selectableGroup()) {
                        val options = listOf(
                            stringResource(R.string.settings_theme_system) to 0,
                            stringResource(R.string.settings_theme_light) to 1,
                            stringResource(R.string.settings_theme_dark) to 2
                        )
                        options.forEach { (label, value) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .selectable(
                                        selected = state.themeMode == value,
                                        onClick = {
                                            onEvent(SettingsEvent.SetThemeMode(value))
                                        },
                                        role = Role.RadioButton
                                    )
                                    .padding(end = 8.dp)
                            ) {
                                RadioButton(
                                    selected = state.themeMode == value,
                                    onClick = null
                                )
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                                )
                            }
                        }
                    }
                }
            )

            // Pure Black dark mode
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_pure_black)) },
                supportingContent = { Text(stringResource(R.string.settings_pure_black_description)) },
                trailingContent = {
                    Switch(
                        checked = state.usePureBlackDarkMode,
                        onCheckedChange = {
                            onEvent(SettingsEvent.SetPureBlackDarkMode(it))
                        }
                    )
                }
            )

            // High contrast mode
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_high_contrast)) },
                supportingContent = { Text(stringResource(R.string.settings_high_contrast_description)) },
                trailingContent = {
                    Switch(
                        checked = state.useHighContrast,
                        onCheckedChange = {
                            onEvent(SettingsEvent.SetHighContrast(it))
                        }
                    )
                }
            )

            // Color scheme picker
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_color_scheme)) },
                supportingContent = {
                    Column(modifier = Modifier.selectableGroup()) {
                        val schemes = listOf(
                            stringResource(R.string.settings_color_scheme_system_default) to 0,
                            stringResource(R.string.settings_color_scheme_dynamic) to 1,
                            stringResource(R.string.settings_color_scheme_green_apple) to 2,
                            stringResource(R.string.settings_color_scheme_lavender) to 3,
                            stringResource(R.string.settings_color_scheme_midnight_dusk) to 4,
                            stringResource(R.string.settings_color_scheme_strawberry_daiquiri) to 5,
                            stringResource(R.string.settings_color_scheme_tako) to 6,
                            stringResource(R.string.settings_color_scheme_teal_turquoise) to 7,
                            stringResource(R.string.settings_color_scheme_tidal_wave) to 8,
                            stringResource(R.string.settings_color_scheme_yotsuba) to 9,
                            stringResource(R.string.settings_color_scheme_yin_yang) to 10,
                            stringResource(R.string.settings_color_scheme_custom_accent) to COLOR_SCHEME_CUSTOM_ACCENT
                        )
                        schemes.forEach { (label, value) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = state.colorScheme == value,
                                        onClick = { onEvent(SettingsEvent.SetColorScheme(value)) },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = state.colorScheme == value,
                                    onClick = null
                                )
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            )

            // Custom accent color picker (shown when "Custom Accent" is selected)
            if (state.colorScheme == COLOR_SCHEME_CUSTOM_ACCENT) {
                AccentColorPicker(
                    selectedColor = state.customAccentColor,
                    onColorSelected = { onEvent(SettingsEvent.SetCustomAccentColor(it)) }
                )
            }

            // Language
            val context = LocalContext.current
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+: delegate to the system per-app language picker
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_language)) },
                    supportingContent = { Text(stringResource(R.string.settings_language_system_settings_hint)) },
                    modifier = Modifier.clickable {
                        val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        }
                    }
                )
            } else {
                // Android 12 and below: in-app language picker
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_language)) },
                    supportingContent = {
                        Column(modifier = Modifier.selectableGroup()) {
                            val localeTags = listOf("", "en", "ja", "ko", "zh-Hans", "es", "fr", "de", "pt", "ru")
                            val systemDefaultLabel = stringResource(R.string.settings_language_system_default)
                            localeTags.forEach { tag ->
                                val label = if (tag.isEmpty()) {
                                    systemDefaultLabel
                                } else {
                                    val loc = Locale.forLanguageTag(tag)
                                    loc.getDisplayName(loc).replaceFirstChar { it.uppercase() }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = state.locale == tag,
                                            onClick = { onEvent(SettingsEvent.SetLocale(tag)) },
                                            role = Role.RadioButton
                                        )
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = state.locale == tag,
                                        onClick = null
                                    )
                                    Text(
                                        text = label,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }

}

@Composable
private fun LibrarySection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    // ── Library ───────────────────────────────────────────────────────
            SectionHeader(title = stringResource(R.string.settings_library))

            // Grid size – use a local slider state so DataStore is written only when
            // the user finishes dragging, not on every intermediate position.
            var sliderPosition by remember(state.libraryGridSize) {
                mutableFloatStateOf(state.libraryGridSize.toFloat())
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_grid_columns, sliderPosition.roundToInt())) },
                supportingContent = {
                    Slider(
                        value = sliderPosition,
                        onValueChange = { sliderPosition = it },
                        onValueChangeFinished = {
                            onEvent(
                                SettingsEvent.SetLibraryGridSize(sliderPosition.roundToInt())
                            )
                        },
                        valueRange = 2f..5f,
                        steps = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )

            // Badges
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_show_unread_badges)) },
                supportingContent = { Text(stringResource(R.string.settings_show_unread_badges_description)) },
                trailingContent = {
                    Switch(
                        checked = state.showBadges,
                        onCheckedChange = {
                            onEvent(SettingsEvent.SetShowBadges(it))
                        }
                    )
                }
            )
}

@Composable
private fun BrowseSection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    // ── Browse ────────────────────────────────────────────────────────
    SectionHeader(title = stringResource(R.string.settings_browse))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_show_nsfw_sources)) },
        supportingContent = { Text(stringResource(R.string.settings_show_nsfw_sources_description)) },
        trailingContent = {
            Switch(
                checked = state.showNsfwContent,
                onCheckedChange = {
                    onEvent(SettingsEvent.SetShowNsfwContent(it))
                }
            )
        }
    )
}

@Composable
private fun DownloadsSettingsSection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    // ── Downloads ─────────────────────────────────────────────────────
    SectionHeader(title = stringResource(R.string.settings_downloads))

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_remove_after_reading)) },
                supportingContent = { Text(stringResource(R.string.settings_remove_after_reading_description)) },
                trailingContent = {
                    Switch(
                        checked = state.deleteAfterReading,
                        onCheckedChange = {
                            onEvent(SettingsEvent.SetDeleteAfterReading(it))
                        }
                    )
                }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_save_as_cbz)) },
                supportingContent = { Text(stringResource(R.string.settings_save_as_cbz_description)) },
                trailingContent = {
                    Switch(
                        checked = state.saveAsCbz,
                        onCheckedChange = {
                            onEvent(SettingsEvent.SetSaveAsCbz(it))
                        }
                    )
                }
            )
}

@Composable
private fun ReaderSection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    // ── Reader ────────────────────────────────────────────────────────
            SectionHeader(title = stringResource(R.string.settings_reader))

            // Reader mode – ordinal order matches ReaderMode enum:
            // SINGLE_PAGE=0, DUAL_PAGE=1, WEBTOON=2, SMART_PANELS=3
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_reading_mode)) },
                supportingContent = {
                    Column(modifier = Modifier.selectableGroup()) {
                        val modes = listOf(
                            stringResource(R.string.settings_reading_mode_single_page) to 0,
                            stringResource(R.string.settings_reading_mode_dual_page) to 1,
                            stringResource(R.string.settings_reading_mode_webtoon) to 2,
                            stringResource(R.string.settings_reading_mode_smart_panels) to 3
                        )
                        modes.forEach { (label, value) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = state.readerMode == value,
                                        onClick = {
                                            onEvent(SettingsEvent.SetReaderMode(value))
                                        },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = state.readerMode == value,
                                    onClick = null
                                )
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            )

            // Keep screen on
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_keep_screen_on)) },
                supportingContent = { Text(stringResource(R.string.settings_keep_screen_on_description)) },
                trailingContent = {
                    Switch(
                        checked = state.keepScreenOn,
                        onCheckedChange = {
                            onEvent(SettingsEvent.SetKeepScreenOn(it))
                        }
                    )
                }
            )

            // Incognito mode
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_incognito_mode)) },
                supportingContent = { Text(stringResource(R.string.settings_incognito_mode_description)) },
                trailingContent = {
                    Switch(
                        checked = state.incognitoMode,
                        onCheckedChange = {
                            onEvent(SettingsEvent.SetIncognitoMode(it))
                        }
                    )
                }
            )

            // Page preloading
            var preloadBeforeSlider by remember(state.preloadPagesBefore) {
                mutableFloatStateOf(state.preloadPagesBefore.toFloat())
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_preload_before, preloadBeforeSlider.roundToInt())) },
                supportingContent = {
                    Column {
                        Text(stringResource(R.string.settings_preload_before_description))
                        Slider(
                            value = preloadBeforeSlider,
                            onValueChange = { preloadBeforeSlider = it },
                            onValueChangeFinished = {
                                onEvent(SettingsEvent.SetPreloadPagesBefore(preloadBeforeSlider.roundToInt()))
                            },
                            valueRange = 0f..10f,
                            steps = 9,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            )

            var preloadAfterSlider by remember(state.preloadPagesAfter) {
                mutableFloatStateOf(state.preloadPagesAfter.toFloat())
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_preload_after, preloadAfterSlider.roundToInt())) },
                supportingContent = {
                    Column {
                        Text(stringResource(R.string.settings_preload_after_description))
                        Slider(
                            value = preloadAfterSlider,
                            onValueChange = { preloadAfterSlider = it },
                            onValueChangeFinished = {
                                onEvent(SettingsEvent.SetPreloadPagesAfter(preloadAfterSlider.roundToInt()))
                            },
                            valueRange = 0f..10f,
                            steps = 9,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            )

            // Crop borders
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_crop_borders)) },
                supportingContent = { Text(stringResource(R.string.settings_crop_borders_description)) },
                trailingContent = {
                    Switch(
                        checked = state.cropBordersEnabled,
                        onCheckedChange = {
                            onEvent(SettingsEvent.SetCropBordersEnabled(it))
                        }
                    )
                }
            )

            // Image quality
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_image_quality)) },
                supportingContent = {
                    Column(modifier = Modifier.selectableGroup()) {
                        val qualities = ImageQuality.entries.map { quality ->
                            stringResource(quality.stringRes) to quality.ordinal
                        }
                        qualities.forEach { (label, value) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = state.imageQuality == value,
                                        onClick = {
                                            onEvent(SettingsEvent.SetImageQuality(value))
                                        },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = state.imageQuality == value,
                                    onClick = null
                                )
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            )

            // Data saver mode
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_data_saver)) },
                supportingContent = { Text(stringResource(R.string.settings_data_saver_description)) },
                trailingContent = {
                    Switch(
                        checked = state.dataSaverEnabled,
                        onCheckedChange = {
                            onEvent(SettingsEvent.SetDataSaverEnabled(it))
                        }
                    )
                }
            )
}

@Composable
private fun DownloadSection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    // ── Downloads ─────────────────────────────────────────────────────
            SectionHeader(title = stringResource(R.string.settings_downloads))

            // Auto-download new chapters
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_auto_download_new_chapters)) },
                supportingContent = { Text(stringResource(R.string.settings_auto_download_new_chapters_description)) },
                trailingContent = {
                    Switch(
                        checked = state.autoDownloadEnabled,
                        onCheckedChange = {
                            onEvent(SettingsEvent.SetAutoDownloadEnabled(it))
                        }
                    )
                }
            )

            // Download only on Wi-Fi
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_download_only_wifi)) },
                supportingContent = { Text(stringResource(R.string.settings_download_only_wifi_description)) },
                trailingContent = {
                    Switch(
                        checked = state.downloadOnlyOnWifi,
                        onCheckedChange = {
                            onEvent(SettingsEvent.SetDownloadOnlyOnWifi(it))
                        }
                    )
                }
            )

            // Auto-download limit
            var sliderPosition by remember(state.autoDownloadLimit) {
                mutableFloatStateOf(state.autoDownloadLimit.toFloat())
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_auto_download_limit, sliderPosition.roundToInt())) },
                supportingContent = {
                    Slider(
                        value = sliderPosition,
                        onValueChange = { sliderPosition = it },
                        onValueChangeFinished = {
                            onEvent(
                                SettingsEvent.SetAutoDownloadLimit(sliderPosition.roundToInt())
                            )
                        },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
}

@Composable
private fun NotificationsSection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    // ── Notifications ─────────────────────────────────────────────────
            SectionHeader(title = stringResource(R.string.settings_notifications))

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_enable_notifications)) },
                supportingContent = { Text(stringResource(R.string.settings_enable_notifications_description)) },
                trailingContent = {
                    Switch(
                        checked = state.notificationsEnabled,
                        onCheckedChange = {
                            onEvent(SettingsEvent.SetNotificationsEnabled(it))
                        }
                    )
                }
            )

            // Update check interval
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_update_check_interval)) },
                supportingContent = {
                    Column(modifier = Modifier.selectableGroup()) {
                        val intervals = listOf(
                            stringResource(R.string.settings_update_interval_6h) to 6,
                            stringResource(R.string.settings_update_interval_12h) to 12,
                            stringResource(R.string.settings_update_interval_24h) to 24
                        )
                        intervals.forEach { (label, hours) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = state.updateCheckInterval == hours,
                                        onClick = {
                                            onEvent(SettingsEvent.SetUpdateInterval(hours))
                                        },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = state.updateCheckInterval == hours,
                                    onClick = null
                                )
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            )
}

@Composable
private fun ReadingGoalsSection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    // ── Reading Goals ──────────────────────────────────────────────────
    SectionHeader(title = stringResource(R.string.settings_reading_goals))

    // Daily chapter goal
    var dailyGoalSlider by remember { mutableFloatStateOf(state.dailyChapterGoal.toFloat()) }
    LaunchedEffect(state.dailyChapterGoal) {
        dailyGoalSlider = state.dailyChapterGoal.toFloat()
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_daily_chapter_goal)) },
        supportingContent = {
            Column {
                Text(
                    if (dailyGoalSlider.roundToInt() == 0) stringResource(R.string.settings_goals_disabled)
                    else stringResource(R.string.settings_goals_chapters_per_day, dailyGoalSlider.roundToInt())
                )
                Slider(
                    value = dailyGoalSlider,
                    onValueChange = { dailyGoalSlider = it },
                    onValueChangeFinished = {
                        onEvent(SettingsEvent.SetDailyChapterGoal(dailyGoalSlider.roundToInt()))
                    },
                    valueRange = 0f..20f,
                    steps = 19
                )
            }
        }
    )

    // Weekly chapter goal
    var weeklyGoalSlider by remember { mutableFloatStateOf(state.weeklyChapterGoal.toFloat()) }
    LaunchedEffect(state.weeklyChapterGoal) {
        weeklyGoalSlider = state.weeklyChapterGoal.toFloat()
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_weekly_chapter_goal)) },
        supportingContent = {
            Column {
                Text(
                    if (weeklyGoalSlider.roundToInt() == 0) stringResource(R.string.settings_goals_disabled)
                    else stringResource(R.string.settings_goals_chapters_per_week, weeklyGoalSlider.roundToInt())
                )
                Slider(
                    value = weeklyGoalSlider,
                    onValueChange = { weeklyGoalSlider = it },
                    onValueChangeFinished = {
                        onEvent(SettingsEvent.SetWeeklyChapterGoal(weeklyGoalSlider.roundToInt()))
                    },
                    valueRange = 0f..50f,
                    steps = 49
                )
            }
        }
    )

    // Reading reminders
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_reading_reminders)) },
        supportingContent = { Text(stringResource(R.string.settings_reading_reminders_description)) },
        trailingContent = {
            Switch(
                checked = state.readingRemindersEnabled,
                onCheckedChange = {
                    onEvent(SettingsEvent.SetReadingRemindersEnabled(it))
                }
            )
        }
    )

    // Reminder time
    if (state.readingRemindersEnabled) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_reminder_time)) },
            supportingContent = {
                Column(modifier = Modifier.selectableGroup()) {
                    val hours = listOf(
                        stringResource(R.string.settings_reminder_morning) to 9,
                        stringResource(R.string.settings_reminder_afternoon) to 14,
                        stringResource(R.string.settings_reminder_evening) to 20
                    )
                    hours.forEach { (label, hour) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = state.readingReminderHour == hour,
                                    onClick = {
                                        onEvent(SettingsEvent.SetReadingReminderHour(hour))
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = state.readingReminderHour == hour,
                                onClick = null
                            )
                            Text(
                                text = label,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun DataStorageSection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    // ── Data & Storage ────────────────────────────────────────────────
            SectionHeader(title = stringResource(R.string.settings_backup_restore_migration))

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_create_backup)) },
                supportingContent = { Text(stringResource(R.string.settings_create_backup_description)) },
                trailingContent = {
                    if (state.isBackupInProgress) {
                        CircularProgressIndicator()
                    } else {
                        Button(onClick = { onEvent(SettingsEvent.OnCreateBackup) }) {
                            Text(stringResource(R.string.settings_backup_button))
                        }
                    }
                }
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
                }
            )

            // ── Automatic backups ──
            HorizontalDivider()
            SectionHeader(title = stringResource(R.string.settings_automatic_backups))

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_enable_auto_backup)) },
                supportingContent = { Text(stringResource(R.string.settings_enable_auto_backup_description)) },
                trailingContent = {
                    Switch(
                        checked = state.autoBackupEnabled,
                        onCheckedChange = { onEvent(SettingsEvent.SetAutoBackupEnabled(it)) }
                    )
                }
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
                                stringResource(R.string.settings_backup_frequency_weekly) to 168
                            )
                            options.forEach { (label, hours) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = state.autoBackupIntervalHours == hours,
                                            onClick = { onEvent(SettingsEvent.SetAutoBackupInterval(hours)) },
                                            role = Role.RadioButton
                                        )
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = state.autoBackupIntervalHours == hours,
                                        onClick = null
                                    )
                                    Text(
                                        text = label,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
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
                                            role = Role.RadioButton
                                        )
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = state.autoBackupMaxCount == count,
                                        onClick = null
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_backup_count, count),
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
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
                                        onClick = { onEvent(SettingsEvent.RestoreLocalBackup(fileName)) }
                                    ) {
                                        Text(stringResource(R.string.settings_restore_button))
                                    }
                                }
                            }
                        )
                    }
                } else {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_no_auto_backups)) },
                        supportingContent = { Text(stringResource(R.string.settings_no_auto_backups_description)) }
                    )
                }
            }

            HorizontalDivider()

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_migrate_manga)) },
                supportingContent = { Text(stringResource(R.string.settings_migrate_manga_description)) },
                modifier = Modifier.clickable { onEvent(SettingsEvent.OnNavigateToMigration) }
            )
}

@Composable
private fun LocalSourceSection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    // ── Local Source ──────────────────────────────────────────────────
    SectionHeader(title = stringResource(R.string.settings_local_source))

    var directoryText by remember(state.localSourceDirectory) {
        mutableStateOf(state.localSourceDirectory)
    }

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_scan_directory)) },
        supportingContent = {
            Column {
                Text(
                    text = stringResource(R.string.settings_scan_directory_description),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = directoryText,
                    onValueChange = { directoryText = it },
                    label = { Text(stringResource(R.string.settings_directory_path)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Button(
                            onClick = {
                                onEvent(SettingsEvent.SetLocalSourceDirectory(directoryText))
                            }
                        ) {
                            Text(stringResource(R.string.settings_save))
                        }
                    }
                )
                Text(
                    text = stringResource(R.string.settings_scan_directory_supported),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    )
}

@Composable
private fun TrackingSection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    // ── Tracking ──────────────────────────────────────────────────────
    SectionHeader(title = stringResource(R.string.settings_tracking))

    if (state.trackers.isEmpty()) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_no_tracker_services)) },
            supportingContent = { Text(stringResource(R.string.settings_no_tracker_services_description)) }
        )
        return
    }

    state.trackers.forEach { tracker ->
        var showLogin by remember(tracker.id) { mutableStateOf(false) }
        var username by remember(tracker.id) { mutableStateOf("") }
        var password by remember(tracker.id) { mutableStateOf("") }

        ListItem(
            headlineContent = { Text(tracker.name) },
            supportingContent = {
                if (tracker.isLoggedIn) {
                    Text(stringResource(R.string.settings_tracker_connected), color = MaterialTheme.colorScheme.primary)
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
            }
        )

        if (showLogin && !tracker.isLoggedIn) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.settings_tracker_username)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.settings_tracker_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                Button(
                    onClick = {
                        onEvent(SettingsEvent.LoginTracker(tracker.id, username, password))
                        showLogin = false
                        username = ""
                        password = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_tracker_connect, tracker.name))
                }
            }
        }
    }
}

@Composable
private fun MigrationSection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    // ── Migration ─────────────────────────────────────────────────────
    SectionHeader(title = stringResource(R.string.settings_migration))

    // Similarity threshold slider
    var thresholdSlider by remember(state.migrationSimilarityThreshold) {
        mutableFloatStateOf(state.migrationSimilarityThreshold)
    }
    ListItem(
        headlineContent = {
            Text(stringResource(R.string.settings_similarity_threshold, (thresholdSlider * 100).roundToInt()))
        },
        supportingContent = {
            Column {
                Text(
                    text = stringResource(R.string.settings_similarity_threshold_description),
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = thresholdSlider,
                    onValueChange = { thresholdSlider = it },
                    onValueChangeFinished = {
                        onEvent(
                            SettingsEvent.SetMigrationSimilarityThreshold(thresholdSlider)
                        )
                    },
                    valueRange = 0.5f..1.0f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )

    // Always confirm toggle
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_always_show_confirmation)) },
        supportingContent = { Text(stringResource(R.string.settings_always_show_confirmation_description)) },
        trailingContent = {
            Switch(
                checked = state.migrationAlwaysConfirm,
                onCheckedChange = {
                    onEvent(SettingsEvent.SetMigrationAlwaysConfirm(it))
                }
            )
        }
    )

    // Minimum chapter count slider
    var minChaptersSlider by remember(state.migrationMinChapterCount) {
        mutableFloatStateOf(state.migrationMinChapterCount.toFloat())
    }
    ListItem(
        headlineContent = {
            Text(
                if (minChaptersSlider.roundToInt() == 0) stringResource(R.string.settings_min_chapter_count_no_filter)
                else stringResource(R.string.settings_min_chapter_count, minChaptersSlider.roundToInt())
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = stringResource(R.string.settings_min_chapter_count_description),
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = minChaptersSlider,
                    onValueChange = { minChaptersSlider = it },
                    onValueChangeFinished = {
                        onEvent(
                            SettingsEvent.SetMigrationMinChapterCount(minChaptersSlider.roundToInt())
                        )
                    },
                    valueRange = 0f..50f,
                    steps = 49,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

@Composable
private fun DiscordSection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    // ── Discord ───────────────────────────────────────────────────────
    SectionHeader(title = stringResource(R.string.settings_discord))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_discord_rich_presence)) },
        supportingContent = { Text(stringResource(R.string.settings_discord_rich_presence_description)) },
        trailingContent = {
            Switch(
                checked = state.discordRpcEnabled,
                onCheckedChange = { onEvent(SettingsEvent.SetDiscordRpcEnabled(it)) }
            )
        }
    )
}

@Composable
private fun AiSection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    // ── AI ────────────────────────────────────────────────────────────
    SectionHeader(title = stringResource(R.string.settings_ai_features))

    // Master toggle
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_ai_enable)) },
        supportingContent = { Text(stringResource(R.string.settings_ai_enable_description)) },
        trailingContent = {
            Switch(
                checked = state.aiEnabled,
                onCheckedChange = { onEvent(SettingsEvent.SetAiEnabled(it)) }
            )
        }
    )

    // API Key input (only shown when AI is enabled)
    if (state.aiEnabled) {
        var apiKeyInput by remember { mutableStateOf("") }
        var apiKeyVisible by remember { mutableStateOf(false) }

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_ai_gemini_api_key)) },
            supportingContent = {
                Column {
                    if (state.aiApiKeySet) {
                        Text(
                            text = stringResource(R.string.settings_ai_api_key_set),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text(stringResource(R.string.settings_ai_api_key_label)) },
                        singleLine = true,
                        visualTransformation = if (apiKeyVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Icon(
                                    imageVector = if (apiKeyVisible) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                    contentDescription = if (apiKeyVisible) stringResource(R.string.settings_ai_hide_key)
                                    else stringResource(R.string.settings_ai_show_key)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onEvent(SettingsEvent.SetAiApiKey(apiKeyInput))
                            apiKeyInput = ""
                        },
                        enabled = apiKeyInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.settings_ai_save_api_key))
                    }
                }
            }
        )

        // Tier selection
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_ai_service_tier)) },
            supportingContent = {
                Row(modifier = Modifier.selectableGroup()) {
                    listOf(
                        stringResource(R.string.settings_ai_tier_free) to AiTier.FREE,
                        stringResource(R.string.settings_ai_tier_standard) to AiTier.STANDARD,
                        stringResource(R.string.settings_ai_tier_pro) to AiTier.PRO
                    ).forEach { (label, tier) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .selectable(
                                    selected = state.aiTier == tier,
                                    onClick = { onEvent(SettingsEvent.SetAiTier(tier)) },
                                    role = Role.RadioButton
                                )
                                .padding(end = 8.dp)
                        ) {
                            RadioButton(selected = state.aiTier == tier, onClick = null)
                            Text(text = label, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        SectionHeader(title = stringResource(R.string.settings_ai_feature_toggles))

        // Individual feature toggles
        val features = listOf(
            AiFeatureToggle(stringResource(R.string.settings_ai_reading_insights), stringResource(R.string.settings_ai_reading_insights_desc), state.aiReadingInsights) { SettingsEvent.SetAiReadingInsights(it) },
            AiFeatureToggle(stringResource(R.string.settings_ai_smart_search), stringResource(R.string.settings_ai_smart_search_desc), state.aiSmartSearch) { SettingsEvent.SetAiSmartSearch(it) },
            AiFeatureToggle(stringResource(R.string.settings_ai_recommendations), stringResource(R.string.settings_ai_recommendations_desc), state.aiRecommendations) { SettingsEvent.SetAiRecommendations(it) },
            AiFeatureToggle(stringResource(R.string.settings_ai_panel_reader), stringResource(R.string.settings_ai_panel_reader_desc), state.aiPanelReader) { SettingsEvent.SetAiPanelReader(it) },
            AiFeatureToggle(stringResource(R.string.settings_ai_sfx_translation), stringResource(R.string.settings_ai_sfx_translation_desc), state.aiSfxTranslation) { SettingsEvent.SetAiSfxTranslation(it) },
            AiFeatureToggle(stringResource(R.string.settings_ai_summary_translation), stringResource(R.string.settings_ai_summary_translation_desc), state.aiSummaryTranslation) { SettingsEvent.SetAiSummaryTranslation(it) },
            AiFeatureToggle(stringResource(R.string.settings_ai_source_intelligence), stringResource(R.string.settings_ai_source_intelligence_desc), state.aiSourceIntelligence) { SettingsEvent.SetAiSourceIntelligence(it) },
            AiFeatureToggle(stringResource(R.string.settings_ai_smart_notifications), stringResource(R.string.settings_ai_smart_notifications_desc), state.aiSmartNotifications) { SettingsEvent.SetAiSmartNotifications(it) },
            AiFeatureToggle(stringResource(R.string.settings_ai_auto_categorization), stringResource(R.string.settings_ai_auto_categorization_desc), state.aiAutoCategorization) { SettingsEvent.SetAiAutoCategorization(it) }
        )

        features.forEach { feature ->
            ListItem(
                headlineContent = { Text(feature.label) },
                supportingContent = { Text(feature.description) },
                trailingContent = {
                    Switch(
                        checked = feature.enabled,
                        onCheckedChange = { onEvent(feature.makeEvent(it)) }
                    )
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        SectionHeader(title = stringResource(R.string.settings_ai_usage))

        // Token usage
        val usageLabel = if (state.aiTokenTrackingPeriod.isNotBlank()) {
            stringResource(R.string.settings_ai_tokens_used_period, state.aiTokensUsedThisMonth, state.aiTokenTrackingPeriod)
        } else {
            stringResource(R.string.settings_ai_tokens_used_month, state.aiTokensUsedThisMonth)
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_ai_monthly_token_usage)) },
            supportingContent = { Text(usageLabel) }
        )

        // Clear AI cache
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_ai_clear_cache)) },
            supportingContent = { Text(stringResource(R.string.settings_ai_clear_cache_description)) },
            trailingContent = {
                OutlinedButton(onClick = { onEvent(SettingsEvent.ClearAiCache) }) {
                    Text(stringResource(R.string.settings_ai_clear))
                }
            }
        )
    }
}

/**
 * Holds display metadata and the toggle state for a single AI feature switch.
 */
private data class AiFeatureToggle(
    val label: String,
    val description: String,
    val enabled: Boolean,
    val makeEvent: (Boolean) -> SettingsEvent
)

/**
 * Preset accent colors for the custom accent color picker.
 * Each pair is (display name, ARGB Long).
 */
private val AccentColorPresets: List<Pair<String, Long>> = listOf(
    "Red" to 0xFFE53935L,
    "Pink" to 0xFFD81B60L,
    "Purple" to 0xFF8E24AAL,
    "Deep Purple" to 0xFF5E35B1L,
    "Indigo" to 0xFF3949ABL,
    "Blue" to 0xFF1E88E5L,
    "Light Blue" to 0xFF039BE5L,
    "Cyan" to 0xFF00ACC1L,
    "Teal" to 0xFF00897BL,
    "Green" to 0xFF43A047L,
    "Light Green" to 0xFF7CB342L,
    "Lime" to 0xFFC0CA33L,
    "Yellow" to 0xFFFDD835L,
    "Amber" to 0xFFFFB300L,
    "Orange" to 0xFFFB8C00L,
    "Deep Orange" to 0xFFF4511EL,
    "Brown" to 0xFF6D4C41L,
    "Blue Grey" to 0xFF546E7AL
)

/**
 * A grid of color swatches for selecting a custom accent color.
 * Uses FlowRow instead of LazyVerticalGrid to avoid infinite-height measurement
 * inside the parent scrollable Column.
 */
@Composable
private fun AccentColorPicker(
    selectedColor: Long,
    onColorSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_accent_color)) },
        supportingContent = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                AccentColorPresets.forEach { (name, colorValue) ->
                    val isSelected = selectedColor == colorValue
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(colorValue.toInt()))
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 3.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    )
                                } else Modifier
                            )
                            .clickable { onColorSelected(colorValue) }
                            .semantics {
                                contentDescription = name
                                role = Role.RadioButton
                                selected = isSelected
                            }
                    )
                }
            }
        },
        modifier = modifier
    )
}
