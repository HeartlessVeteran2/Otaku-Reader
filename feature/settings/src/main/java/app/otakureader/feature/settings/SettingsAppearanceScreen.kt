package app.otakureader.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import app.otakureader.core.ui.theme.COLOR_SCHEME_CUSTOM_ACCENT
import app.otakureader.feature.settings.viewmodel.AppearanceViewModel
import java.util.Locale

/**
 * Appearance sub-screen: theme, color scheme, language, notifications, Browse/NSFW, Discord.
 * All fields are serviced by [AppearanceViewModel] backed by [AppearanceSettingsDelegate].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAppearanceScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_appearance)) },
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
            AppearanceContent(state = state, onEvent = viewModel::onEvent)
            HorizontalDivider()
            NotificationsContent(state = state, onEvent = viewModel::onEvent)
            HorizontalDivider()
            DiscordContent(state = state, onEvent = viewModel::onEvent)
        }
    }
}

fun NavGraphBuilder.settingsAppearanceScreen(onNavigateBack: () -> Unit) {
    composable<Route.SettingsAppearance> {
        SettingsAppearanceScreen(onNavigateBack = onNavigateBack)
    }
}

// ─── Section composables ─────────────────────────────────────────────────────

@Composable
private fun AppearanceContent(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    SectionHeader(title = stringResource(R.string.settings_appearance))

    // Theme mode
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_theme)) },
        supportingContent = {
            Row(modifier = Modifier.selectableGroup()) {
                val options = listOf(
                    stringResource(R.string.settings_theme_system) to 0,
                    stringResource(R.string.settings_theme_light) to 1,
                    stringResource(R.string.settings_theme_dark) to 2,
                )
                options.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .selectable(
                                selected = state.themeMode == value,
                                onClick = { onEvent(SettingsEvent.SetThemeMode(value)) },
                                role = Role.RadioButton,
                            )
                            .padding(end = 8.dp),
                    ) {
                        RadioButton(selected = state.themeMode == value, onClick = null)
                        Text(text = label, modifier = Modifier.padding(start = 4.dp, end = 8.dp))
                    }
                }
            }
        },
    )

    // Pure Black dark mode
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_pure_black)) },
        supportingContent = { Text(stringResource(R.string.settings_pure_black_description)) },
        trailingContent = {
            Switch(
                checked = state.usePureBlackDarkMode,
                onCheckedChange = { onEvent(SettingsEvent.SetPureBlackDarkMode(it)) },
            )
        },
    )

    // High contrast mode
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_high_contrast)) },
        supportingContent = { Text(stringResource(R.string.settings_high_contrast_description)) },
        trailingContent = {
            Switch(
                checked = state.useHighContrast,
                onCheckedChange = { onEvent(SettingsEvent.SetHighContrast(it)) },
            )
        },
    )

    // Visual Effects toggle
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_visual_effects)) },
        supportingContent = { Text(stringResource(R.string.settings_visual_effects_description)) },
        trailingContent = {
            Switch(
                checked = state.visualEffectsEnabled,
                onCheckedChange = { onEvent(SettingsEvent.SetVisualEffectsEnabled(it)) },
            )
        },
    )

    // Dark mode schedule
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_dark_mode_schedule)) },
        supportingContent = { Text(stringResource(R.string.settings_dark_mode_schedule_description)) },
        trailingContent = {
            Switch(
                checked = state.appearance.darkModeScheduleEnabled,
                onCheckedChange = { onEvent(SettingsEvent.SetDarkModeScheduleEnabled(it)) },
            )
        },
    )
    if (state.appearance.darkModeScheduleEnabled) {
        var showStartPicker by remember { mutableStateOf(false) }
        var showEndPicker by remember { mutableStateOf(false) }

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_dark_mode_turn_on_at)) },
            trailingContent = {
                TextButton(onClick = { showStartPicker = true }) {
                    Text(formatMinuteOfDay(state.appearance.darkModeStartMinuteOfDay))
                }
            },
            modifier = androidx.compose.ui.Modifier.clickable { showStartPicker = true },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_dark_mode_turn_off_at)) },
            trailingContent = {
                TextButton(onClick = { showEndPicker = true }) {
                    Text(formatMinuteOfDay(state.appearance.darkModeEndMinuteOfDay))
                }
            },
            modifier = androidx.compose.ui.Modifier.clickable { showEndPicker = true },
        )

        if (showStartPicker) {
            TimePickerDialog(
                initialMinute = state.appearance.darkModeStartMinuteOfDay,
                onConfirm = { onEvent(SettingsEvent.SetDarkModeStartMinute(it)); showStartPicker = false },
                onDismiss = { showStartPicker = false },
            )
        }
        if (showEndPicker) {
            TimePickerDialog(
                initialMinute = state.appearance.darkModeEndMinuteOfDay,
                onConfirm = { onEvent(SettingsEvent.SetDarkModeEndMinute(it)); showEndPicker = false },
                onDismiss = { showEndPicker = false },
            )
        }
    }

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
                    stringResource(R.string.settings_color_scheme_custom_accent) to COLOR_SCHEME_CUSTOM_ACCENT,
                )
                schemes.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.colorScheme == value,
                                onClick = { onEvent(SettingsEvent.SetColorScheme(value)) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = state.colorScheme == value, onClick = null)
                        Text(text = label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
    )

    if (state.colorScheme == COLOR_SCHEME_CUSTOM_ACCENT) {
        AccentColorPicker(
            selectedColor = state.customAccentColor,
            onColorSelected = { onEvent(SettingsEvent.SetCustomAccentColor(it)) },
        )
    }

    // Auto theme color from cover
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_auto_theme_color)) },
        supportingContent = { Text(stringResource(R.string.settings_auto_theme_color_description)) },
        trailingContent = {
            Switch(
                checked = state.autoThemeColor,
                onCheckedChange = { onEvent(SettingsEvent.SetAutoThemeColor(it)) },
            )
        },
    )

    // Language
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
            },
        )
    } else {
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
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(selected = state.locale == tag, onClick = null)
                            Text(text = label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun NotificationsContent(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    SectionHeader(title = stringResource(R.string.settings_notifications))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_enable_notifications)) },
        supportingContent = { Text(stringResource(R.string.settings_enable_notifications_description)) },
        trailingContent = {
            Switch(
                checked = state.notificationsEnabled,
                onCheckedChange = { onEvent(SettingsEvent.SetNotificationsEnabled(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_update_check_interval)) },
        supportingContent = {
            Column(modifier = Modifier.selectableGroup()) {
                val intervals = listOf(
                    stringResource(R.string.settings_update_interval_6h) to 6,
                    stringResource(R.string.settings_update_interval_12h) to 12,
                    stringResource(R.string.settings_update_interval_24h) to 24,
                    stringResource(R.string.settings_update_interval_48h) to 48,
                    stringResource(R.string.settings_update_interval_72h) to 72,
                    stringResource(R.string.settings_update_interval_weekly) to 168,
                )
                intervals.forEach { (label, hours) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.updateCheckInterval == hours,
                                onClick = { onEvent(SettingsEvent.SetUpdateInterval(hours)) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = state.updateCheckInterval == hours, onClick = null)
                        Text(text = label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
    )
}

@Composable
private fun DiscordContent(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    SectionHeader(title = stringResource(R.string.settings_discord))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_discord_rich_presence)) },
        supportingContent = { Text(stringResource(R.string.settings_discord_rich_presence_description)) },
        trailingContent = {
            Switch(
                checked = state.discordRpcEnabled,
                onCheckedChange = { onEvent(SettingsEvent.SetDiscordRpcEnabled(it)) },
            )
        },
    )
}

// ─── Accent color picker ──────────────────────────────────────────────────────

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
    "Blue Grey" to 0xFF546E7AL,
)

@Composable
private fun AccentColorPicker(
    selectedColor: Long,
    onColorSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_accent_color)) },
        supportingContent = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
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
                                        shape = CircleShape,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { onColorSelected(colorValue) }
                            .semantics {
                                contentDescription = name
                                role = Role.RadioButton
                                selected = isSelected
                            },
                    )
                }
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMinute: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.settings_dark_mode_time_picker_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_dark_mode_time_picker_cancel))
            }
        },
        text = { TimePicker(state = state) },
    )
}

private fun formatMinuteOfDay(minuteOfDay: Int): String {
    val h = minuteOfDay / 60
    val m = minuteOfDay % 60
    return "%02d:%02d".format(h, m)
}
