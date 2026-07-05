package app.otakureader.feature.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import app.otakureader.feature.settings.viewmodel.LibraryUpdateViewModel
import kotlin.math.roundToInt

/**
 * Library sub-screen: grid size, badges, library update interval and behaviour.
 * Serviced by [LibraryUpdateViewModel] backed by [LibrarySettingsDelegate].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsLibraryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryUpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_library)) },
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
            LibraryContent(state = state, onEvent = viewModel::onEvent)
        }
    }
}

fun NavGraphBuilder.settingsLibraryScreen(onNavigateBack: () -> Unit) {
    composable<Route.SettingsLibrary> {
        SettingsLibraryScreen(onNavigateBack = onNavigateBack)
    }
}

// ─── Section composable ───────────────────────────────────────────────────────

@Composable
private fun LibraryContent(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    SectionHeader(title = stringResource(R.string.settings_library))

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
                    onEvent(SettingsEvent.SetLibraryGridSize(sliderPosition.roundToInt()))
                },
                valueRange = 2f..5f,
                steps = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )

    // Grid layout toggle: Standard vs Staggered
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_grid_layout)) },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onEvent(SettingsEvent.SetStaggeredGrid(false)) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (!state.isStaggeredGrid) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    ),
                ) { Text(stringResource(R.string.settings_grid_layout_standard)) }
                OutlinedButton(
                    onClick = { onEvent(SettingsEvent.SetStaggeredGrid(true)) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (state.isStaggeredGrid) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    ),
                ) { Text(stringResource(R.string.settings_grid_layout_staggered)) }
            }
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_show_unread_badges)) },
        supportingContent = { Text(stringResource(R.string.settings_show_unread_badges_description)) },
        trailingContent = {
            Switch(
                checked = state.showBadges,
                onCheckedChange = { onEvent(SettingsEvent.SetShowBadges(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_show_download_badge)) },
        supportingContent = { Text(stringResource(R.string.settings_show_download_badge_description)) },
        trailingContent = {
            Switch(
                checked = state.showDownloadBadge,
                onCheckedChange = { onEvent(SettingsEvent.SetShowDownloadBadge(it)) },
            )
        },
    )

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    SectionHeader(title = stringResource(R.string.settings_library_updates))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_update_wifi_only)) },
        supportingContent = { Text(stringResource(R.string.settings_update_wifi_only_description)) },
        trailingContent = {
            Switch(
                checked = state.updateOnlyOnWifi,
                onCheckedChange = { onEvent(SettingsEvent.SetUpdateOnlyOnWifi(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_update_require_charging)) },
        supportingContent = { Text(stringResource(R.string.settings_update_require_charging_description)) },
        trailingContent = {
            Switch(
                checked = state.updateRequireCharging,
                onCheckedChange = { onEvent(SettingsEvent.SetUpdateRequireCharging(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_auto_refresh)) },
        supportingContent = { Text(stringResource(R.string.settings_auto_refresh_description)) },
        trailingContent = {
            Switch(
                checked = state.autoRefreshOnStart,
                onCheckedChange = { onEvent(SettingsEvent.SetAutoRefreshOnStart(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_show_update_progress)) },
        supportingContent = { Text(stringResource(R.string.settings_show_update_progress_description)) },
        trailingContent = {
            Switch(
                checked = state.showUpdateProgress,
                onCheckedChange = { onEvent(SettingsEvent.SetShowUpdateProgress(it)) },
            )
        },
    )

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    SectionHeader(title = stringResource(R.string.settings_smart_update_skip))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_skip_updates_unread)) },
        supportingContent = { Text(stringResource(R.string.settings_skip_updates_unread_description)) },
        trailingContent = {
            Switch(
                checked = state.skipUpdatesWithUnread,
                onCheckedChange = { onEvent(SettingsEvent.SetSkipUpdatesWithUnread(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_skip_updates_completed)) },
        supportingContent = { Text(stringResource(R.string.settings_skip_updates_completed_description)) },
        trailingContent = {
            Switch(
                checked = state.skipUpdatesWithCompleted,
                onCheckedChange = { onEvent(SettingsEvent.SetSkipUpdatesWithCompleted(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_skip_updates_never_started)) },
        supportingContent = { Text(stringResource(R.string.settings_skip_updates_never_started_description)) },
        trailingContent = {
            Switch(
                checked = state.skipUpdatesNeverStarted,
                onCheckedChange = { onEvent(SettingsEvent.SetSkipUpdatesNeverStarted(it)) },
            )
        },
    )

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    SectionHeader(title = stringResource(R.string.settings_update_check_interval))

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
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                androidx.compose.material3.RadioButton(
                    selected = state.updateCheckInterval == hours,
                    onClick = null,
                )
                Text(text = label, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
