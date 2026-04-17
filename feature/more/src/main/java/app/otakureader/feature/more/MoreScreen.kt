package app.otakureader.feature.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BackupTable
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * More screen providing access to settings, downloads, statistics, and about.
 * This is the fifth tab in the bottom navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToBackup: () -> Unit = {},
    onNavigateToExtensions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.more_title)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
        ) {
            // Settings
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_settings)) },
                supportingContent = { Text(stringResource(R.string.more_settings_desc)) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                },
                modifier = Modifier.clickable { onNavigateToSettings() }
            )

            HorizontalDivider()

            // Extensions
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_extensions)) },
                supportingContent = { Text(stringResource(R.string.more_extensions_desc)) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                },
                modifier = Modifier.clickable { onNavigateToExtensions() }
            )

            HorizontalDivider()

            // Backup & Restore
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_backup)) },
                supportingContent = { Text(stringResource(R.string.more_backup_desc)) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.BackupTable,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                },
                modifier = Modifier.clickable { onNavigateToBackup() }
            )

            HorizontalDivider()

            // Downloads
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_downloads)) },
                supportingContent = { Text(stringResource(R.string.more_downloads_desc)) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                },
                modifier = Modifier.clickable { onNavigateToDownloads() }
            )

            HorizontalDivider()

            // Statistics
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_statistics)) },
                supportingContent = { Text(stringResource(R.string.more_statistics_desc)) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.QueryStats,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                },
                modifier = Modifier.clickable { onNavigateToStatistics() }
            )

            HorizontalDivider()

            // About
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_about)) },
                supportingContent = { Text(stringResource(R.string.more_about_desc)) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                },
                modifier = Modifier.clickable { onNavigateToAbout() }
            )
        }
    }
}

