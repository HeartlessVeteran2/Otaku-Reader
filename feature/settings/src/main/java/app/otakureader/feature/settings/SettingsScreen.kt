package app.otakureader.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Settings screen for user-configurable options.
 * Built with Jetpack Compose as specified in the project architecture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {

            // --- Appearance ---
            Text(
                text = "Appearance",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ListItem(
                headlineContent = { Text("Dynamic Color (Material You)") },
                supportingContent = { Text("Use wallpaper-based colors on Android 12+") },
                trailingContent = {
                    Switch(
                        checked = state.useDynamicColor,
                        onCheckedChange = {
                            viewModel.onEvent(SettingsEvent.SetDynamicColor(it))
                        }
                    )
                }
            )

            HorizontalDivider()

            // --- Notifications ---
            ListItem(
                headlineContent = { Text("Enable Notifications") },
                supportingContent = { Text("Get notified when new chapters are available") },
                trailingContent = {
                    Switch(
                        checked = state.notificationsEnabled,
                        onCheckedChange = {
                            viewModel.onEvent(SettingsEvent.SetNotificationsEnabled(it))
                        }
                    )
                }
            )
        }
    }
}
