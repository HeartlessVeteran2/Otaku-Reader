package app.otakureader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import java.util.Calendar

/**
 * Security settings: optional biometric / device-credential app lock and its grace period.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSecurityScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_security)) },
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
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_biometric_lock)) },
                supportingContent = { Text(stringResource(R.string.settings_biometric_lock_description)) },
                trailingContent = {
                    Switch(
                        checked = state.biometricLockEnabled,
                        onCheckedChange = { viewModel.onEvent(SettingsEvent.SetBiometricLockEnabled(it)) },
                    )
                },
            )

            if (state.biometricLockEnabled) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_lock_timeout)) },
                    supportingContent = {
                        Column(modifier = Modifier.selectableGroup()) {
                            val options = listOf(
                                stringResource(R.string.settings_lock_timeout_immediate) to 0,
                                stringResource(R.string.settings_lock_timeout_1min) to 1,
                                stringResource(R.string.settings_lock_timeout_5min) to 5,
                                stringResource(R.string.settings_lock_timeout_15min) to 15,
                            )
                            options.forEach { (label, minutes) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = state.biometricLockTimeoutMinutes == minutes,
                                            onClick = { viewModel.onEvent(SettingsEvent.SetBiometricLockTimeout(minutes)) },
                                            role = Role.RadioButton,
                                        )
                                        .padding(vertical = 4.dp),
                                ) {
                                    RadioButton(
                                        selected = state.biometricLockTimeoutMinutes == minutes,
                                        onClick = null,
                                    )
                                    Text(text = label, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    },
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SectionHeader(title = stringResource(R.string.settings_lock_schedule))

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_lock_schedule_enabled)) },
                    supportingContent = { Text(stringResource(R.string.settings_lock_schedule_enabled_description)) },
                    trailingContent = {
                        Switch(
                            checked = state.biometricLockScheduleEnabled,
                            onCheckedChange = {
                                viewModel.onEvent(SettingsEvent.SetBiometricLockScheduleEnabled(it))
                            },
                        )
                    },
                )

                if (state.biometricLockScheduleEnabled) {
                    BiometricScheduleSection(state = state, onEvent = viewModel::onEvent)
                }
            }
        }
    }
}

fun NavGraphBuilder.settingsSecurityScreen(onNavigateBack: () -> Unit) {
    composable<Route.SettingsSecurity> {
        SettingsSecurityScreen(onNavigateBack = onNavigateBack)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BiometricScheduleSection(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    var startSlider by remember(state.biometricLockStartHour) {
        mutableFloatStateOf(state.biometricLockStartHour.toFloat())
    }
    ListItem(
        headlineContent = {
            Text(stringResource(R.string.settings_lock_schedule_start, startSlider.toInt()))
        },
        supportingContent = {
            Slider(
                value = startSlider,
                onValueChange = { startSlider = it },
                onValueChangeFinished = {
                    onEvent(SettingsEvent.SetBiometricLockStartHour(startSlider.toInt()))
                },
                valueRange = 0f..23f,
                steps = 22,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )

    var endSlider by remember(state.biometricLockEndHour) {
        mutableFloatStateOf(state.biometricLockEndHour.toFloat())
    }
    ListItem(
        headlineContent = {
            Text(stringResource(R.string.settings_lock_schedule_end, endSlider.toInt()))
        },
        supportingContent = {
            Slider(
                value = endSlider,
                onValueChange = { endSlider = it },
                onValueChangeFinished = {
                    onEvent(SettingsEvent.SetBiometricLockEndHour(endSlider.toInt()))
                },
                valueRange = 0f..23f,
                steps = 22,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )

    val days = listOf(
        stringResource(R.string.day_sun) to Calendar.SUNDAY,
        stringResource(R.string.day_mon) to Calendar.MONDAY,
        stringResource(R.string.day_tue) to Calendar.TUESDAY,
        stringResource(R.string.day_wed) to Calendar.WEDNESDAY,
        stringResource(R.string.day_thu) to Calendar.THURSDAY,
        stringResource(R.string.day_fri) to Calendar.FRIDAY,
        stringResource(R.string.day_sat) to Calendar.SATURDAY,
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_lock_schedule_days)) },
        supportingContent = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                days.forEach { (label, calDay) ->
                    val selected = state.biometricLockActiveDays.isEmpty() ||
                        calDay in state.biometricLockActiveDays
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val current = state.biometricLockActiveDays.ifEmpty {
                                days.map { it.second }.toSet()
                            }
                            val updated = if (calDay in current) current - calDay else current + calDay
                            onEvent(SettingsEvent.SetBiometricLockActiveDays(updated))
                        },
                        label = { Text(label) },
                    )
                }
            }
        },
    )
}
