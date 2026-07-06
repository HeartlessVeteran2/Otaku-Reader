package app.otakureader.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import app.otakureader.feature.settings.viewmodel.AppearanceViewModel

/**
 * Dedicated Discord settings screen.
 *
 * The setting remains backed by [AppearanceViewModel] and [SettingsEvent.SetDiscordRpcEnabled]
 * so this screen is only a navigation and presentation split from the appearance screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDiscordScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_discord)) },
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
            item(key = "discord_header") {
                SectionHeader(title = stringResource(R.string.settings_discord))
            }
            item(key = "discord_rich_presence_toggle") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_discord_rich_presence)) },
                    supportingContent = { Text(stringResource(R.string.settings_discord_rich_presence_description)) },
                    trailingContent = {
                        Switch(
                            checked = state.discordRpcEnabled,
                            onCheckedChange = { viewModel.onEvent(SettingsEvent.SetDiscordRpcEnabled(it)) },
                        )
                    },
                )
            }
            item(key = "discord_divider") { HorizontalDivider() }
            item(key = "discord_status_info") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_discord_status_title)) },
                    supportingContent = {
                        Text(
                            text = stringResource(R.string.settings_discord_status_description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}

fun NavGraphBuilder.settingsDiscordScreen(onNavigateBack: () -> Unit) {
    composable<Route.SettingsDiscord> {
        SettingsDiscordScreen(onNavigateBack = onNavigateBack)
    }
}
