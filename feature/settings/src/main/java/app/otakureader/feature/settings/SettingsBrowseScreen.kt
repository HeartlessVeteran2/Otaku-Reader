package app.otakureader.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import app.otakureader.feature.settings.viewmodel.BrowseSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBrowseScreen(
    onNavigateBack: () -> Unit,
    onNavigateToExtensionRepos: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowseSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_browse)) },
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
            BrowseSettingsContent(
                state = state,
                onEvent = viewModel::onEvent,
                onNavigateToExtensionRepos = onNavigateToExtensionRepos,
            )
        }
    }
}

fun NavGraphBuilder.settingsBrowseScreen(
    onNavigateBack: () -> Unit,
    onNavigateToExtensionRepos: () -> Unit = {},
) {
    composable<Route.SettingsBrowse> {
        SettingsBrowseScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToExtensionRepos = onNavigateToExtensionRepos,
        )
    }
}

@Composable
private fun BrowseSettingsContent(
    state: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
    onNavigateToExtensionRepos: () -> Unit,
) {
    SectionHeader(title = stringResource(R.string.settings_browse_sources))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_extension_repos)) },
        supportingContent = { Text(stringResource(R.string.settings_extension_repos_description)) },
        modifier = Modifier.clickable(onClick = onNavigateToExtensionRepos),
    )

    HorizontalDivider()
    SectionHeader(title = stringResource(R.string.settings_nsfw_content))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_show_nsfw_sources)) },
        supportingContent = { Text(stringResource(R.string.settings_nsfw_requires_restart)) },
        trailingContent = {
            Switch(
                checked = state.showNsfwContent,
                onCheckedChange = { onEvent(SettingsEvent.SetShowNsfwContent(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_nsfw_parental_note)) },
    )
}
