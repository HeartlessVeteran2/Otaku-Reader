package app.otakureader.feature.more

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.core.ui.R as CoreUiR
import app.otakureader.core.ui.components.GlassCard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.otakureader.core.ui.theme.LocalOtakuColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToBookmarks: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToExtensions: () -> Unit = {},
    onNavigateToFeed: () -> Unit = {},
    onNavigateToShareLibrary: () -> Unit = {},
    onNavigateToScanLibrary: () -> Unit = {},
    onNavigateToUpdateErrors: () -> Unit = {},
    onNavigateToReadingLists: () -> Unit = {},
    onNavigateToCategories: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: ""
        } catch (_: PackageManager.NameNotFoundException) {
            ""
        }
    }

    val viewModel: MoreViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

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
            AppLogoCard(versionName = versionName)
            StatsSummaryCard(
                currentStreak = state.currentStreak,
                todayChaptersRead = state.todayChaptersRead,
                dailyGoal = state.dailyGoal,
                onClick = onNavigateToStatistics,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )

            // ── Quick toggles (Downloaded Only + Incognito) ────────────────────
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_downloaded_only)) },
                supportingContent = { Text(stringResource(R.string.more_downloaded_only_desc)) },
                leadingContent = {
                    Icon(Icons.Default.CloudOff, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = state.downloadedOnly,
                        onCheckedChange = { checked -> viewModel.onEvent(MoreEvent.SetDownloadedOnly(checked)) },
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_incognito_mode)) },
                supportingContent = { Text(stringResource(R.string.more_incognito_mode_desc)) },
                leadingContent = {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = if (state.incognitoMode) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.incognitoMode,
                        onCheckedChange = { checked -> viewModel.onEvent(MoreEvent.SetIncognitoMode(checked)) },
                    )
                },
            )
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))

            // ── Library Tools ──────────────────────────────────────────────────
            MoreSectionHeader(stringResource(R.string.more_section_library_tools))
            MoreListItem(
                icon = Icons.Default.Extension,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                headline = stringResource(R.string.more_extensions),
                supporting = stringResource(R.string.more_extensions_desc),
                onClick = onNavigateToExtensions
            )
            HorizontalDivider()
            MoreListItem(
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                headline = stringResource(R.string.more_reading_lists),
                supporting = stringResource(R.string.more_reading_lists_desc),
                onClick = onNavigateToReadingLists,
            )
            HorizontalDivider()
            MoreListItem(
                icon = Icons.AutoMirrored.Filled.Label,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                headline = stringResource(R.string.more_categories),
                supporting = stringResource(R.string.more_categories_desc),
                onClick = onNavigateToCategories,
            )
            HorizontalDivider()
            MoreListItem(
                icon = Icons.Default.Download,
                iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                headline = stringResource(R.string.more_downloads),
                supporting = when (val queueState = state.downloadQueueState) {
                    DownloadQueueDisplayState.Stopped -> stringResource(R.string.more_downloads_desc)
                    is DownloadQueueDisplayState.Paused -> pluralStringResource(
                        R.plurals.more_download_queue_paused,
                        queueState.pending,
                        queueState.pending,
                    )
                    is DownloadQueueDisplayState.Downloading -> pluralStringResource(
                        R.plurals.more_download_queue_downloading,
                        queueState.pending,
                        queueState.pending,
                    )
                },
                onClick = onNavigateToDownloads
            )
            HorizontalDivider()
            MoreListItem(
                icon = Icons.Default.PhotoCamera,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                headline = stringResource(R.string.more_scan_library),
                supporting = stringResource(R.string.more_scan_library_desc),
                onClick = onNavigateToScanLibrary
            )

            // ── Backup & Sync ──────────────────────────────────────────────────
            MoreSectionHeader(stringResource(R.string.more_section_backup_sync))
            MoreListItem(
                icon = Icons.Default.CloudUpload,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                headline = stringResource(R.string.more_backup),
                supporting = stringResource(R.string.more_backup_desc),
                onClick = onNavigateToBackup
            )
            HorizontalDivider()
            MoreListItem(
                icon = Icons.Default.QrCode,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                headline = stringResource(R.string.more_share_library),
                supporting = stringResource(R.string.more_share_library_desc),
                onClick = onNavigateToShareLibrary
            )
            HorizontalDivider()
            MoreListItem(
                icon = Icons.Default.ErrorOutline,
                iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                iconTint = MaterialTheme.colorScheme.onErrorContainer,
                headline = stringResource(R.string.more_update_errors),
                supporting = stringResource(R.string.more_update_errors_desc),
                onClick = onNavigateToUpdateErrors
            )

            // ── Personalization ────────────────────────────────────────────────
            MoreSectionHeader(stringResource(R.string.more_section_personalization))
            MoreListItem(
                icon = Icons.Default.Notifications,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                headline = stringResource(R.string.more_feed),
                supporting = stringResource(R.string.more_feed_desc),
                onClick = onNavigateToFeed
            )
            HorizontalDivider()
            MoreListItem(
                icon = Icons.Default.Bookmark,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                headline = stringResource(R.string.more_bookmarks),
                supporting = stringResource(R.string.more_bookmarks_description),
                onClick = onNavigateToBookmarks
            )
            HorizontalDivider()
            MoreListItem(
                icon = Icons.Default.QueryStats,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                headline = stringResource(R.string.more_statistics),
                supporting = stringResource(R.string.more_statistics_desc),
                onClick = onNavigateToStatistics
            )

            // ── Info & Settings ────────────────────────────────────────────────
            MoreSectionHeader(stringResource(R.string.more_section_info_settings))
            MoreListItem(
                icon = Icons.Default.Settings,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                headline = stringResource(R.string.more_settings),
                supporting = stringResource(R.string.more_settings_desc),
                onClick = onNavigateToSettings
            )
            HorizontalDivider()
            MoreListItem(
                icon = Icons.Default.Info,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                headline = stringResource(R.string.more_about),
                supporting = stringResource(R.string.more_about_desc),
                onClick = onNavigateToAbout
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * A glassy card widget showing the user's current reading streak and today's chapter
 * goal progress.  Tapping it navigates to the full Statistics screen.
 *
 * When [dailyGoal] is 0 (goal disabled), only the raw chapter count is shown without
 * a progress bar, so we don't show a meaningless 0/0 indicator.
 */
@Composable
private fun StatsSummaryCard(
    currentStreak: Int,
    todayChaptersRead: Int,
    dailyGoal: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardCd = stringResource(R.string.more_stats_card_cd)
    GlassCard(
        modifier = modifier
            .semantics { contentDescription = cardCd }
            .clickable(onClick = onClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.more_stats_streak, currentStreak),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            if (dailyGoal > 0) {
                LinearProgressIndicator(
                    // Lambda form avoids unnecessary recomposition on each frame
                    progress = {
                        (todayChaptersRead.toFloat() / dailyGoal).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        R.string.more_stats_chapters_today,
                        todayChaptersRead,
                        dailyGoal,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                )
            } else {
                Text(
                    text = stringResource(R.string.more_stats_no_goal, todayChaptersRead),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
        }
    }
}

private val SectionDividerTopPadding = 8.dp
private val SectionTitleStartPadding = 16.dp
private val SectionTitleTopPadding = 12.dp
private val SectionTitleBottomPadding = 4.dp

@Composable
private fun MoreSectionHeader(title: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        HorizontalDivider(modifier = Modifier.padding(top = SectionDividerTopPadding))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                start = SectionTitleStartPadding,
                top = SectionTitleTopPadding,
                bottom = SectionTitleBottomPadding,
            ),
        )
    }
}

@Composable
private fun AppLogoCard(
    versionName: String,
    modifier: Modifier = Modifier,
) {
    val otaku = LocalOtakuColors.current
    val gradientEnd = Color.hsl(270f, 0.65f, if (otaku.isDark) 0.30f else 0.68f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(otaku.accent, gradientEnd),
                    )
                )
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(CoreUiR.drawable.ic_otaku_logo),
                        contentDescription = stringResource(R.string.more_app_name),
                        modifier = Modifier.size(44.dp),
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.more_app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    if (versionName.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.more_version_label, versionName),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreListItem(
    icon: ImageVector,
    iconContainerColor: Color,
    iconTint: Color,
    headline: String,
    supporting: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text(supporting) },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = headline,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        },
        modifier = modifier.clickable { onClick() }
    )
}
