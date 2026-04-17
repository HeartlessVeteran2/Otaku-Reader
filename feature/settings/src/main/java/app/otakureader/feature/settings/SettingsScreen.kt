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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
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
    onNavigateToAbout: () -> Unit = {},
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

    // Directory picker for download location
    val downloadLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.onEvent(SettingsEvent.SetDownloadLocation(it.toString())) }
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
                SettingsEffect.NavigateToAbout -> onNavigateToAbout()
                is SettingsEffect.ShowDownloadLocationPicker -> {
                    downloadLocationLauncher.launch(null)
                }
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
            DownloadSection(state = state, onEvent = viewModel::onEvent)
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
            HorizontalDivider()
            AboutSection(onEvent = viewModel::onEvent)
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

            // Auto theme color from cover
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_auto_theme_color)) },
                supportingContent = { Text(stringResource(R.string.settings_auto_theme_color_description)) },
                trailingContent = {
                    Switch(
                        checked = state.autoThemeColor,
                        onCheckedChange = { onEvent(SettingsEvent.SetAutoThemeColor(it)) }
                    )
                }
            )

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

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SectionHeader(title = stringResource(R.string.settings_library_updates))

            // Update only on Wi-Fi
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_update_wifi_only)) },
                supportingContent = { Text(stringResource(R.string.settings_update_wifi_only_description)) },
                trailingContent = {
                    Switch(
                        checked = state.updateOnlyOnWifi,
                        onCheckedChange = { onEvent(SettingsEvent.SetUpdateOnlyOnWifi(it)) }
                    )
                }
            )

            // Update only pinned categories
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_update_pinned_only)) },
                supportingContent = { Text(stringResource(R.string.settings_update_pinned_only_description)) },
                trailingContent = {
                    Switch(
                        checked = state.updateOnlyPinnedCategories,
                        onCheckedChange = { onEvent(SettingsEvent.SetUpdateOnlyPinnedCategories(it)) }
                    )
                }
            )

            // Auto refresh on start
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_auto_refresh)) },
                supportingContent = { Text(stringResource(R.string.settings_auto_refresh_description)) },
                trailingContent = {
                    Switch(
                        checked = state.autoRefreshOnStart,
                        onCheckedChange = { onEvent(SettingsEvent.SetAutoRefreshOnStart(it)) }
                    )
                }
            )

            // Show update progress
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_show_update_progress)) },
                supportingContent = { Text(stringResource(R.string.settings_show_update_progress_description)) },
                trailingContent = {
                    Switch(
                        checked = state.showUpdateProgress,
                        onCheckedChange = { onEvent(SettingsEvent.SetShowUpdateProgress(it)) }
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
                            stringResource(quality.stringRes) to quality.name
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

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SectionHeader(title = stringResource(R.string.settings_reader_display))

            // Fullscreen
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_fullscreen)) },
                supportingContent = { Text(stringResource(R.string.settings_fullscreen_description)) },
                trailingContent = {
                    Switch(
                        checked = state.fullscreen,
                        onCheckedChange = { onEvent(SettingsEvent.SetFullscreen(it)) }
                    )
                }
            )

            // Show content in cutout
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_show_cutout)) },
                supportingContent = { Text(stringResource(R.string.settings_show_cutout_description)) },
                trailingContent = {
                    Switch(
                        checked = state.showContentInCutout,
                        onCheckedChange = { onEvent(SettingsEvent.SetShowContentInCutout(it)) }
                    )
                }
            )

            // Show page number
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_show_page_number)) },
                supportingContent = { Text(stringResource(R.string.settings_show_page_number_description)) },
                trailingContent = {
                    Switch(
                        checked = state.showPageNumber,
                        onCheckedChange = { onEvent(SettingsEvent.SetShowPageNumber(it)) }
                    )
                }
            )

            // Background color
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_background_color)) },
                supportingContent = {
                    Column(modifier = Modifier.selectableGroup()) {
                        val colors = listOf(
                            stringResource(R.string.settings_bg_black) to 0,
                            stringResource(R.string.settings_bg_white) to 1,
                            stringResource(R.string.settings_bg_gray) to 2,
                            stringResource(R.string.settings_bg_auto) to 3
                        )
                        colors.forEach { (label, value) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = state.backgroundColor == value,
                                        onClick = { onEvent(SettingsEvent.SetBackgroundColor(value)) },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(selected = state.backgroundColor == value, onClick = null)
                                Text(text = label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            )

            // Animate page transitions
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_animate_transitions)) },
                supportingContent = { Text(stringResource(R.string.settings_animate_transitions_description)) },
                trailingContent = {
                    Switch(
                        checked = state.animatePageTransitions,
                        onCheckedChange = { onEvent(SettingsEvent.SetAnimatePageTransitions(it)) }
                    )
                }
            )

            // Show reading mode overlay
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_show_mode_overlay)) },
                supportingContent = { Text(stringResource(R.string.settings_show_mode_overlay_description)) },
                trailingContent = {
                    Switch(
                        checked = state.showReadingModeOverlay,
                        onCheckedChange = { onEvent(SettingsEvent.SetShowReadingModeOverlay(it)) }
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SectionHeader(title = stringResource(R.string.settings_reader_scale))

            // Reader scale type
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_scale_type)) },
                supportingContent = {
                    Column(modifier = Modifier.selectableGroup()) {
                        val scales = listOf(
                            stringResource(R.string.settings_scale_fit_screen) to 0,
                            stringResource(R.string.settings_scale_fit_width) to 1,
                            stringResource(R.string.settings_scale_fit_height) to 2,
                            stringResource(R.string.settings_scale_original) to 3,
                            stringResource(R.string.settings_scale_smart_fit) to 4
                        )
                        scales.forEach { (label, value) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = state.readerScale == value,
                                        onClick = { onEvent(SettingsEvent.SetReaderScale(value)) },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(selected = state.readerScale == value, onClick = null)
                                Text(text = label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            )

            // Auto zoom wide images
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_auto_zoom_wide)) },
                supportingContent = { Text(stringResource(R.string.settings_auto_zoom_wide_description)) },
                trailingContent = {
                    Switch(
                        checked = state.autoZoomWideImages,
                        onCheckedChange = { onEvent(SettingsEvent.SetAutoZoomWideImages(it)) }
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SectionHeader(title = stringResource(R.string.settings_tap_zones))

            // Tap zone configuration
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_tap_zone_config)) },
                supportingContent = {
                    Column(modifier = Modifier.selectableGroup()) {
                        val configs = listOf(
                            stringResource(R.string.settings_tap_default) to 0,
                            stringResource(R.string.settings_tap_left_handed) to 1,
                            stringResource(R.string.settings_tap_kindle) to 2,
                            stringResource(R.string.settings_tap_edge) to 3
                        )
                        configs.forEach { (label, value) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = state.tapZoneConfig == value,
                                        onClick = { onEvent(SettingsEvent.SetTapZoneConfig(value)) },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(selected = state.tapZoneConfig == value, onClick = null)
                                Text(text = label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            )

            // Invert tap zones
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_invert_tap_zones)) },
                supportingContent = { Text(stringResource(R.string.settings_invert_tap_zones_description)) },
                trailingContent = {
                    Switch(
                        checked = state.invertTapZones,
                        onCheckedChange = { onEvent(SettingsEvent.SetInvertTapZones(it)) }
                    )
                }
            )

            // Show tap zones overlay
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_show_tap_zones)) },
                supportingContent = { Text(stringResource(R.string.settings_show_tap_zones_description)) },
                trailingContent = {
                    Switch(
                        checked = state.showTapZonesOverlay,
                        onCheckedChange = { onEvent(SettingsEvent.SetShowTapZonesOverlay(it)) }
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SectionHeader(title = stringResource(R.string.settings_volume_keys))

            // Volume keys enabled
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_volume_keys_paging)) },
                supportingContent = { Text(stringResource(R.string.settings_volume_keys_paging_description)) },
                trailingContent = {
                    Switch(
                        checked = state.volumeKeysEnabled,
                        onCheckedChange = { onEvent(SettingsEvent.SetVolumeKeysEnabled(it)) }
                    )
                }
            )

            // Volume keys inverted
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_volume_keys_inverted)) },
                supportingContent = { Text(stringResource(R.string.settings_volume_keys_inverted_description)) },
                trailingContent = {
                    Switch(
                        checked = state.volumeKeysInverted,
                        onCheckedChange = { onEvent(SettingsEvent.SetVolumeKeysInverted(it)) },
                        enabled = state.volumeKeysEnabled
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SectionHeader(title = stringResource(R.string.settings_reader_interaction))

            // Double tap animation speed
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_double_tap_speed)) },
                supportingContent = {
                    Column(modifier = Modifier.selectableGroup()) {
                        val speeds = listOf(
                            stringResource(R.string.settings_speed_slow) to 0,
                            stringResource(R.string.settings_speed_normal) to 1,
                            stringResource(R.string.settings_speed_fast) to 2
                        )
                        speeds.forEach { (label, value) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = state.doubleTapAnimationSpeed == value,
                                        onClick = { onEvent(SettingsEvent.SetDoubleTapAnimationSpeed(value)) },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(selected = state.doubleTapAnimationSpeed == value, onClick = null)
                                Text(text = label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            )

            // Show actions on long tap
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_long_tap_actions)) },
                supportingContent = { Text(stringResource(R.string.settings_long_tap_actions_description)) },
                trailingContent = {
                    Switch(
                        checked = state.showActionsOnLongTap,
                        onCheckedChange = { onEvent(SettingsEvent.SetShowActionsOnLongTap(it)) }
                    )
                }
            )

            // Save pages to separate folders
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_save_separate_folders)) },
                supportingContent = { Text(stringResource(R.string.settings_save_separate_folders_description)) },
                trailingContent = {
                    Switch(
                        checked = state.savePagesToSeparateFolders,
                        onCheckedChange = { onEvent(SettingsEvent.SetSavePagesToSeparateFolders(it)) }
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SectionHeader(title = stringResource(R.string.settings_webtoon))

            // Webtoon side padding
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_webtoon_padding)) },
                supportingContent = {
                    Column(modifier = Modifier.selectableGroup()) {
                        val paddings = listOf(
                            stringResource(R.string.settings_padding_none) to 0,
                            stringResource(R.string.settings_padding_small) to 1,
                            stringResource(R.string.settings_padding_medium) to 2,
                            stringResource(R.string.settings_padding_large) to 3
                        )
                        paddings.forEach { (label, value) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = state.webtoonSidePadding == value,
                                        onClick = { onEvent(SettingsEvent.SetWebtoonSidePadding(value)) },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(selected = state.webtoonSidePadding == value, onClick = null)
                                Text(text = label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            )

            // Menu hide sensitivity
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_menu_sensitivity)) },
                supportingContent = {
                    Column(modifier = Modifier.selectableGroup()) {
                        val sensitivities = listOf(
                            stringResource(R.string.settings_sensitivity_low) to 0,
                            stringResource(R.string.settings_sensitivity_medium) to 1,
                            stringResource(R.string.settings_sensitivity_high) to 2
                        )
                        sensitivities.forEach { (label, value) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = state.webtoonMenuHideSensitivity == value,
                                        onClick = { onEvent(SettingsEvent.SetWebtoonMenuHideSensitivity(value)) },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(selected = state.webtoonMenuHideSensitivity == value, onClick = null)
                                Text(text = label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            )

            // Double tap zoom
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_webtoon_double_tap)) },
                supportingContent = { Text(stringResource(R.string.settings_webtoon_double_tap_description)) },
                trailingContent = {
                    Switch(
                        checked = state.webtoonDoubleTapZoom,
                        onCheckedChange = { onEvent(SettingsEvent.SetWebtoonDoubleTapZoom(it)) }
                    )
                }
            )

            // Disable zoom out
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_disable_zoom_out)) },
                supportingContent = { Text(stringResource(R.string.settings_disable_zoom_out_description)) },
                trailingContent = {
                    Switch(
                        checked = state.webtoonDisableZoomOut,
                        onCheckedChange = { onEvent(SettingsEvent.SetWebtoonDisableZoomOut(it)) }
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SectionHeader(title = stringResource(R.string.settings_eink))

            // Flash on page change
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_eink_flash)) },
                supportingContent = { Text(stringResource(R.string.settings_eink_flash_description)) },
                trailingContent = {
                    Switch(
                        checked = state.einkFlashOnPageChange,
                        onCheckedChange = { onEvent(SettingsEvent.SetEinkFlashOnPageChange(it)) }
                    )
                }
            )

            // Black and white mode
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_eink_bw)) },
                supportingContent = { Text(stringResource(R.string.settings_eink_bw_description)) },
                trailingContent = {
                    Switch(
                        checked = state.einkBlackAndWhite,
                        onCheckedChange = { onEvent(SettingsEvent.SetEinkBlackAndWhite(it)) }
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SectionHeader(title = stringResource(R.string.settings_reader_behavior))

            // Skip read chapters
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_skip_read)) },
                supportingContent = { Text(stringResource(R.string.settings_skip_read_description)) },
                trailingContent = {
                    Switch(
                        checked = state.skipReadChapters,
                        onCheckedChange = { onEvent(SettingsEvent.SetSkipReadChapters(it)) }
                    )
                }
            )

            // Skip filtered chapters
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_skip_filtered)) },
                supportingContent = { Text(stringResource(R.string.settings_skip_filtered_description)) },
                trailingContent = {
                    Switch(
                        checked = state.skipFilteredChapters,
                        onCheckedChange = { onEvent(SettingsEvent.SetSkipFilteredChapters(it)) }
                    )
                }
            )

            // Skip duplicate chapters
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_skip_duplicates)) },
                supportingContent = { Text(stringResource(R.string.settings_skip_duplicates_description)) },
                trailingContent = {
                    Switch(
                        checked = state.skipDuplicateChapters,
                        onCheckedChange = { onEvent(SettingsEvent.SetSkipDuplicateChapters(it)) }
                    )
                }
            )

            // Always show chapter transition
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_show_chapter_transition)) },
                supportingContent = { Text(stringResource(R.string.settings_show_chapter_transition_description)) },
                trailingContent = {
                    Switch(
                        checked = state.alwaysShowChapterTransition,
                        onCheckedChange = { onEvent(SettingsEvent.SetAlwaysShowChapterTransition(it)) }
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

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SectionHeader(title = stringResource(R.string.settings_download_location))

            // Download location
            val locationText = state.downloadLocation?.let { 
                it.substringAfterLast("/") 
            } ?: stringResource(R.string.settings_download_location_default)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_download_location)) },
                supportingContent = { Text(locationText) },
                trailingContent = {
                    OutlinedButton(onClick = { onEvent(SettingsEvent.SetDownloadLocation(null)) }) {
                        Text(stringResource(R.string.settings_change))
                    }
                },
                modifier = Modifier.clickable {
                    onEvent(SettingsEvent.SetDownloadLocation(null))
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SectionHeader(title = stringResource(R.string.settings_download_performance))

            // Concurrent downloads
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SectionHeader(title = stringResource(R.string.settings_download_ahead))

            // Download ahead while reading
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
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            )

            // Download ahead only on Wi-Fi
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_download_ahead_wifi_only)) },
                supportingContent = { Text(stringResource(R.string.settings_download_ahead_wifi_only_description)) },
                trailingContent = {
                    Switch(
                        checked = state.downloadAheadOnlyOnWifi,
                        onCheckedChange = { onEvent(SettingsEvent.SetDownloadAheadOnlyOnWifi(it)) }
                    )
                }
            )

            // Delete after reading
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_delete_after_reading)) },
                supportingContent = { Text(stringResource(R.string.settings_delete_after_reading_description)) },
                trailingContent = {
                    Switch(
                        checked = state.deleteAfterReading,
                        onCheckedChange = { onEvent(SettingsEvent.SetDeleteAfterReading(it)) }
                    )
                }
            )

            // Save as CBZ
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_save_as_cbz)) },
                supportingContent = { Text(stringResource(R.string.settings_save_as_cbz_description)) },
                trailingContent = {
                    Switch(
                        checked = state.saveAsCbz,
                        onCheckedChange = { onEvent(SettingsEvent.SetSaveAsCbz(it)) }
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

            HorizontalDivider()
            SectionHeader(title = stringResource(R.string.settings_data_management))

            // Clear image cache
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_clear_image_cache)) },
                supportingContent = { Text(stringResource(R.string.settings_clear_image_cache_desc)) },
                trailingContent = {
                    OutlinedButton(onClick = { onEvent(SettingsEvent.ClearImageCache) }) {
                        Text(stringResource(R.string.settings_clear_button))
                    }
                }
            )

            // Clear reading history
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_clear_history)) },
                supportingContent = { Text(stringResource(R.string.settings_clear_history_desc)) },
                trailingContent = {
                    OutlinedButton(
                        onClick = { onEvent(SettingsEvent.ClearHistory) },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.settings_clear_button))
                    }
                }
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
        val isKeyFormatValid = apiKeyInput.isBlank() || isGeminiApiKeyFormatValid(apiKeyInput)

        // Remove API key confirmation dialog
        if (state.showRemoveApiKeyDialog) {
            AlertDialog(
                onDismissRequest = { onEvent(SettingsEvent.DismissRemoveApiKeyDialog) },
                title = { Text(stringResource(R.string.settings_ai_remove_key_dialog_title)) },
                text = { Text(stringResource(R.string.settings_ai_remove_key_dialog_text)) },
                confirmButton = {
                    Button(onClick = { onEvent(SettingsEvent.ConfirmRemoveAiApiKey) }) {
                        Text(stringResource(R.string.settings_ai_remove_key_confirm))
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { onEvent(SettingsEvent.DismissRemoveApiKeyDialog) }) {
                        Text(stringResource(R.string.settings_ai_remove_key_cancel))
                    }
                }
            )
        }

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
                        isError = !isKeyFormatValid,
                        supportingText = if (!isKeyFormatValid) {
                            { Text(stringResource(R.string.settings_ai_api_key_invalid_format)) }
                        } else null,
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
                        enabled = apiKeyInput.isNotBlank() && isKeyFormatValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.settings_ai_save_api_key))
                    }
                    if (state.aiApiKeySet) {
                        OutlinedButton(
                            onClick = { onEvent(SettingsEvent.RemoveAiApiKey) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Text(stringResource(R.string.settings_ai_remove_key))
                        }
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

@Composable
private fun AboutSection(onEvent: (SettingsEvent) -> Unit) {
    SectionHeader(title = "About")
    ListItem(
        headlineContent = { Text("About Otaku Reader") },
        supportingContent = { Text("App info, FAQ, licenses") },
        leadingContent = {
            Icon(Icons.Default.Info, contentDescription = null)
        },
        modifier = androidx.compose.ui.Modifier.clickable {
            onEvent(SettingsEvent.NavigateToAbout)
        }
    )
}
