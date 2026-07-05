package app.otakureader.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import kotlin.math.roundToInt

/**
 * Smart notification batching settings (#940): grouping cooldown, summary threshold, and quiet
 * hours. Backed by [NotificationSettingsViewModel] over [app.otakureader.core.preferences.NotificationPreferences].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNotificationsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_notifications)) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_notif_batching)) },
                supportingContent = { Text(stringResource(R.string.settings_notif_batching_description)) },
                trailingContent = {
                    Switch(
                        checked = state.smartBatchingEnabled,
                        onCheckedChange = viewModel::setSmartBatching,
                    )
                },
            )

            if (state.smartBatchingEnabled) {
                var cooldown by remember(state.perMangaCooldownMinutes) {
                    mutableFloatStateOf(state.perMangaCooldownMinutes.toFloat())
                }
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.settings_notif_cooldown, cooldown.roundToInt()))
                    },
                    supportingContent = {
                        Slider(
                            value = cooldown,
                            onValueChange = { cooldown = it },
                            onValueChangeFinished = { viewModel.setCooldown(cooldown.roundToInt()) },
                            valueRange = 15f..720f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )

                var maxIndividual by remember(state.maxIndividualNotifications) {
                    mutableFloatStateOf(state.maxIndividualNotifications.toFloat())
                }
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.settings_notif_max_individual, maxIndividual.roundToInt()))
                    },
                    supportingContent = {
                        Slider(
                            value = maxIndividual,
                            onValueChange = { maxIndividual = it },
                            onValueChangeFinished = { viewModel.setMaxIndividual(maxIndividual.roundToInt()) },
                            valueRange = 1f..10f,
                            steps = 8,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
            }

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_notif_hide_content)) },
                supportingContent = { Text(stringResource(R.string.settings_notif_hide_content_description)) },
                trailingContent = {
                    Switch(
                        checked = state.hideNotificationContent,
                        onCheckedChange = viewModel::setHideNotificationContent,
                    )
                },
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_notif_quiet_hours)) },
                supportingContent = { Text(stringResource(R.string.settings_notif_quiet_hours_description)) },
                trailingContent = {
                    Switch(
                        checked = state.respectQuietHours,
                        onCheckedChange = viewModel::setRespectQuietHours,
                    )
                },
            )

            if (state.respectQuietHours) {
                var start by remember(state.quietHoursStart) { mutableFloatStateOf(state.quietHoursStart.toFloat()) }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_notif_quiet_start, start.roundToInt())) },
                    supportingContent = {
                        Slider(
                            value = start,
                            onValueChange = { start = it },
                            onValueChangeFinished = { viewModel.setQuietHoursStart(start.roundToInt()) },
                            valueRange = 0f..23f,
                            steps = 22,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
                var end by remember(state.quietHoursEnd) { mutableFloatStateOf(state.quietHoursEnd.toFloat()) }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_notif_quiet_end, end.roundToInt())) },
                    supportingContent = {
                        Slider(
                            value = end,
                            onValueChange = { end = it },
                            onValueChangeFinished = { viewModel.setQuietHoursEnd(end.roundToInt()) },
                            valueRange = 0f..23f,
                            steps = 22,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
            }

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_ext_auto_update)) },
                supportingContent = { Text(stringResource(R.string.settings_ext_auto_update_description)) },
                trailingContent = {
                    Switch(
                        checked = state.extensionAutoUpdateEnabled,
                        onCheckedChange = viewModel::setExtensionAutoUpdate,
                    )
                },
            )
            if (state.extensionAutoUpdateEnabled) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_ext_auto_update_wifi_only)) },
                    trailingContent = {
                        Switch(
                            checked = state.extensionAutoUpdateWifiOnly,
                            onCheckedChange = viewModel::setExtensionAutoUpdateWifiOnly,
                        )
                    },
                )
            }
        }
    }
}

fun NavGraphBuilder.settingsNotificationsScreen(onNavigateBack: () -> Unit) {
    composable<Route.SettingsNotifications> {
        SettingsNotificationsScreen(onNavigateBack = onNavigateBack)
    }
}
