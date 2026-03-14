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
            SectionHeader(title = "Appearance")

            // Theme mode
            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = {
                    Row(modifier = Modifier.selectableGroup()) {
                        val options = listOf("System" to 0, "Light" to 1, "Dark" to 2)
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
                headlineContent = { Text("Pure Black (AMOLED)") },
                supportingContent = { Text("Use pure black background in dark mode") },
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
                headlineContent = { Text("Color Scheme") },
                supportingContent = {
                    Column(modifier = Modifier.selectableGroup()) {
                        val schemes = listOf(
                            "System Default" to 0,
                            "Dynamic (Material You)" to 1,
                            "Green Apple" to 2,
                            "Lavender" to 3,
                            "Midnight Dusk" to 4,
                            "Strawberry Daiquiri" to 5,
                            "Tako" to 6,
                            "Teal & Turquoise" to 7,
                            "Tidal Wave" to 8,
                            "Yotsuba" to 9,
                            "Yin & Yang" to 10,
                            "Custom Accent" to COLOR_SCHEME_CUSTOM_ACCENT
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
            SectionHeader(title = "Library")

            // Grid size – use a local slider state so DataStore is written only when
            // the user finishes dragging, not on every intermediate position.
            var sliderPosition by remember(state.libraryGridSize) {
                mutableFloatStateOf(state.libraryGridSize.toFloat())
            }
            ListItem(
                headlineContent = { Text("Grid Columns: ${sliderPosition.roundToInt()}") },
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
                headlineContent = { Text("Show Unread Badges") },
                supportingContent = { Text("Display unread chapter count on covers") },
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
    SectionHeader(title = "Browse")

    ListItem(
        headlineContent = { Text("Show NSFW Sources") },
        supportingContent = { Text("Display adult (18+) extensions and sources in Browse") },
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
    SectionHeader(title = "Downloads")

            ListItem(
                headlineContent = { Text("Remove chapter after reading") },
                supportingContent = { Text("Automatically delete downloaded chapters once finished") },
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
                headlineContent = { Text("Save as CBZ") },
                supportingContent = { Text("Compress downloaded chapters into CBZ archives") },
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
            SectionHeader(title = "Reader")

            // Reader mode – ordinal order matches ReaderMode enum:
            // SINGLE_PAGE=0, DUAL_PAGE=1, WEBTOON=2, SMART_PANELS=3
            ListItem(
                headlineContent = { Text("Reading Mode") },
                supportingContent = {
                    Column(modifier = Modifier.selectableGroup()) {
                        val modes = listOf(
                            "Single Page" to 0,
                            "Dual Page" to 1,
                            "Webtoon" to 2,
                            "Smart Panels" to 3
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
                headlineContent = { Text("Keep Screen On") },
                supportingContent = { Text("Prevent the screen from sleeping while reading") },
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
                headlineContent = { Text("Incognito Mode") },
                supportingContent = { Text("Reading history and progress are not saved while enabled") },
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
                headlineContent = { Text("Preload Pages Before: ${preloadBeforeSlider.roundToInt()}") },
                supportingContent = {
                    Column {
                        Text("Number of pages to preload before the current page")
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
                headlineContent = { Text("Preload Pages After: ${preloadAfterSlider.roundToInt()}") },
                supportingContent = {
                    Column {
                        Text("Number of pages to preload after the current page")
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
}

@Composable
private fun DownloadSection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    // ── Downloads ─────────────────────────────────────────────────────
            SectionHeader(title = "Downloads")

            // Auto-download new chapters
            ListItem(
                headlineContent = { Text("Auto-Download New Chapters") },
                supportingContent = { Text("Automatically download new chapters found during library updates") },
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
                headlineContent = { Text("Download Only on Wi-Fi") },
                supportingContent = { Text("Restrict downloads to Wi-Fi connections only") },
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
                headlineContent = { Text("Auto-Download Limit: ${sliderPosition.roundToInt()} chapters") },
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
            SectionHeader(title = "Notifications")

            ListItem(
                headlineContent = { Text("Enable Notifications") },
                supportingContent = { Text("Get notified when new chapters are available") },
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
                headlineContent = { Text("Update Check Interval") },
                supportingContent = {
                    Column(modifier = Modifier.selectableGroup()) {
                        val intervals = listOf("6 hours" to 6, "12 hours" to 12, "24 hours" to 24)
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
    SectionHeader(title = "Reading Goals")

    // Daily chapter goal
    var dailyGoalSlider by remember { mutableFloatStateOf(state.dailyChapterGoal.toFloat()) }
    LaunchedEffect(state.dailyChapterGoal) {
        dailyGoalSlider = state.dailyChapterGoal.toFloat()
    }
    ListItem(
        headlineContent = { Text("Daily Chapter Goal") },
        supportingContent = {
            Column {
                Text(if (state.dailyChapterGoal == 0) "Disabled" else "${state.dailyChapterGoal} chapters/day")
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
        headlineContent = { Text("Weekly Chapter Goal") },
        supportingContent = {
            Column {
                Text(if (state.weeklyChapterGoal == 0) "Disabled" else "${state.weeklyChapterGoal} chapters/week")
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
        headlineContent = { Text("Reading Reminders") },
        supportingContent = { Text("Get a daily reminder to read") },
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
            headlineContent = { Text("Reminder Time") },
            supportingContent = {
                Column(modifier = Modifier.selectableGroup()) {
                    val hours = listOf("Morning (9 AM)" to 9, "Afternoon (2 PM)" to 14, "Evening (8 PM)" to 20)
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
            SectionHeader(title = "Backup, Restore & Migration")

            ListItem(
                headlineContent = { Text("Create Backup") },
                supportingContent = { Text("Export your library and settings") },
                trailingContent = {
                    if (state.isBackupInProgress) {
                        CircularProgressIndicator()
                    } else {
                        Button(onClick = { onEvent(SettingsEvent.OnCreateBackup) }) {
                            Text("Backup")
                        }
                    }
                }
            )

            ListItem(
                headlineContent = { Text("Restore Backup") },
                supportingContent = { Text("Import library and settings from a backup file") },
                trailingContent = {
                    if (state.isRestoreInProgress) {
                        CircularProgressIndicator()
                    } else {
                        Button(onClick = { onEvent(SettingsEvent.OnRestoreBackup) }) {
                            Text("Restore")
                        }
                    }
                }
            )

            // ── Automatic backups ──
            HorizontalDivider()
            SectionHeader(title = "Automatic Backups")

            ListItem(
                headlineContent = { Text("Enable automatic backups") },
                supportingContent = { Text("Periodically save a backup to device storage") },
                trailingContent = {
                    Switch(
                        checked = state.autoBackupEnabled,
                        onCheckedChange = { onEvent(SettingsEvent.SetAutoBackupEnabled(it)) }
                    )
                }
            )

            if (state.autoBackupEnabled) {
                ListItem(
                    headlineContent = { Text("Backup frequency") },
                    supportingContent = {
                        Column(modifier = Modifier.selectableGroup()) {
                            val options = listOf(
                                "Every 6 hours" to 6,
                                "Every 12 hours" to 12,
                                "Daily" to 24,
                                "Every 2 days" to 48,
                                "Weekly" to 168
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
                    headlineContent = { Text("Backups to keep") },
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
                                        text = "$count backups",
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                )

                if (state.localBackupFiles.isNotEmpty()) {
                    SectionHeader(title = "Restore from automatic backup")
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
                                        Text("Restore")
                                    }
                                }
                            }
                        )
                    }
                } else {
                    ListItem(
                        headlineContent = { Text("No automatic backups yet") },
                        supportingContent = { Text("Backups will appear here once created") }
                    )
                }
            }

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Migrate manga") },
                supportingContent = { Text("Move manga from one source to another") },
                modifier = Modifier.clickable { onEvent(SettingsEvent.OnNavigateToMigration) }
            )
}

@Composable
private fun LocalSourceSection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    // ── Local Source ──────────────────────────────────────────────────
    SectionHeader(title = "Local Source")

    var directoryText by remember(state.localSourceDirectory) {
        mutableStateOf(state.localSourceDirectory)
    }

    ListItem(
        headlineContent = { Text("Scan Directory") },
        supportingContent = {
            Column {
                Text(
                    text = "Directory scanned for local manga (CBZ, ZIP, EPUB and image folders).",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = directoryText,
                    onValueChange = { directoryText = it },
                    label = { Text("Directory path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Button(
                            onClick = {
                                onEvent(SettingsEvent.SetLocalSourceDirectory(directoryText))
                            }
                        ) {
                            Text("Save")
                        }
                    }
                )
                Text(
                    text = "Supported: CBZ, ZIP (including ComicInfo.xml), EPUB, and plain image folders.",
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
    SectionHeader(title = "Tracking")

    if (state.trackers.isEmpty()) {
        ListItem(
            headlineContent = { Text("No tracker services available") },
            supportingContent = { Text("Tracker services will appear here once registered") }
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
                    Text("Connected", color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("Not connected")
                }
            },
            trailingContent = {
                if (state.trackingLoginInProgress) {
                    CircularProgressIndicator()
                } else if (tracker.isLoggedIn) {
                    OutlinedButton(onClick = { onEvent(SettingsEvent.LogoutTracker(tracker.id)) }) {
                        Text("Logout")
                    }
                } else {
                    Button(onClick = { showLogin = !showLogin }) {
                        Text("Login")
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
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
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
                    Text("Connect ${tracker.name}")
                }
            }
        }
    }
}

@Composable
private fun MigrationSection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    // ── Migration ─────────────────────────────────────────────────────
    SectionHeader(title = "Migration")

    // Similarity threshold slider
    var thresholdSlider by remember(state.migrationSimilarityThreshold) {
        mutableFloatStateOf(state.migrationSimilarityThreshold)
    }
    ListItem(
        headlineContent = {
            Text("Similarity Threshold: ${(thresholdSlider * 100).roundToInt()}%")
        },
        supportingContent = {
            Column {
                Text(
                    text = "Minimum score to auto-migrate without confirmation",
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
        headlineContent = { Text("Always Show Confirmation") },
        supportingContent = { Text("Always ask before migrating, even when confidence is high") },
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
                if (minChaptersSlider.roundToInt() == 0) "Min. Chapter Count: No filter"
                else "Min. Chapter Count: ${minChaptersSlider.roundToInt()}"
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = "Ignore candidates with fewer chapters than this threshold",
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
    SectionHeader(title = "Discord")

    ListItem(
        headlineContent = { Text("Rich Presence") },
        supportingContent = { Text("Show current reading activity as your Discord status") },
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
    SectionHeader(title = "AI Features")

    // Master toggle
    ListItem(
        headlineContent = { Text("Enable AI Features") },
        supportingContent = { Text("Powered by Gemini. Requires an API key.") },
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
            headlineContent = { Text("Gemini API Key") },
            supportingContent = {
                Column {
                    if (state.aiApiKeySet) {
                        Text(
                            text = "API key is set. Enter a new key to replace it.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = if (apiKeyVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Icon(
                                    imageVector = if (apiKeyVisible) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                    contentDescription = if (apiKeyVisible) "Hide key" else "Show key"
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
                        Text("Save API Key")
                    }
                }
            }
        )

        // Tier selection
        ListItem(
            headlineContent = { Text("Service Tier") },
            supportingContent = {
                Row(modifier = Modifier.selectableGroup()) {
                    listOf(
                        "Free" to AiTier.FREE,
                        "Standard" to AiTier.STANDARD,
                        "Pro" to AiTier.PRO
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
        SectionHeader(title = "Feature Toggles")

        // Individual feature toggles
        val features = listOf(
            AiFeatureToggle("Reading Insights", "AI-powered reading statistics", state.aiReadingInsights) { SettingsEvent.SetAiReadingInsights(it) },
            AiFeatureToggle("Smart Search", "Natural language search queries", state.aiSmartSearch) { SettingsEvent.SetAiSmartSearch(it) },
            AiFeatureToggle("Recommendations", "Personalised manga suggestions", state.aiRecommendations) { SettingsEvent.SetAiRecommendations(it) },
            AiFeatureToggle("Panel-Aware Reader", "Gemini Vision panel detection", state.aiPanelReader) { SettingsEvent.SetAiPanelReader(it) },
            AiFeatureToggle("SFX Translation", "Translate sound effects in pages", state.aiSfxTranslation) { SettingsEvent.SetAiSfxTranslation(it) },
            AiFeatureToggle("Summary Translation", "Auto-translate chapter summaries", state.aiSummaryTranslation) { SettingsEvent.SetAiSummaryTranslation(it) },
            AiFeatureToggle("Source Intelligence", "Score and rank sources automatically", state.aiSourceIntelligence) { SettingsEvent.SetAiSourceIntelligence(it) },
            AiFeatureToggle("Smart Notifications", "Context-aware update summaries", state.aiSmartNotifications) { SettingsEvent.SetAiSmartNotifications(it) },
            AiFeatureToggle("Auto-Categorization", "Categorise new manga automatically", state.aiAutoCategorization) { SettingsEvent.SetAiAutoCategorization(it) }
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
        SectionHeader(title = "Usage")

        // Token usage
        val usageLabel = if (state.aiTokenTrackingPeriod.isNotBlank()) {
            "${state.aiTokensUsedThisMonth} tokens used (${state.aiTokenTrackingPeriod})"
        } else {
            "${state.aiTokensUsedThisMonth} tokens used this month"
        }
        ListItem(
            headlineContent = { Text("Monthly Token Usage") },
            supportingContent = { Text(usageLabel) }
        )

        // Clear AI cache
        ListItem(
            headlineContent = { Text("Clear AI Cache") },
            supportingContent = { Text("Remove cached AI responses to free up space") },
            trailingContent = {
                OutlinedButton(onClick = { onEvent(SettingsEvent.ClearAiCache) }) {
                    Text("Clear")
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
        headlineContent = { Text("Accent Color") },
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
