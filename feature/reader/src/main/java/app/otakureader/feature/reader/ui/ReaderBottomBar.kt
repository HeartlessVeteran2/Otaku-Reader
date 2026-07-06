package app.otakureader.feature.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.outlined.BurstMode
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.otakureader.domain.model.ReaderMode
import app.otakureader.feature.reader.R
import app.otakureader.feature.reader.ReaderState

private const val READER_BAR_SLIDE_MS = 200
private const val READER_BAR_FADE_MS = 150
private val READER_BAR_ELEVATION = 3.dp
private const val BAR_ALPHA_DARK = 0.9f
private const val BAR_ALPHA_LIGHT = 0.95f

private val readerBarSlide = tween<IntOffset>(READER_BAR_SLIDE_MS)
private val readerBarFade = tween<Float>(READER_BAR_FADE_MS)

private val ReaderMode.icon: ImageVector get() = when (this) {
    ReaderMode.SINGLE_PAGE -> Icons.AutoMirrored.Filled.MenuBook
    ReaderMode.DUAL_PAGE -> Icons.AutoMirrored.Filled.NavigateNext
    ReaderMode.WEBTOON -> Icons.Default.FitScreen
    ReaderMode.SMART_PANELS -> Icons.Default.GridView
}

@Composable
fun ReaderBottomBar(
    state: ReaderState,
    onChapterList: () -> Unit,
    onModeClick: () -> Unit,
    onOrientationClick: () -> Unit,
    onToggleCropBorders: () -> Unit,
    onToggleThumbnailStrip: () -> Unit,
    onSettings: () -> Unit,
    isVisible: Boolean,
    onWebView: (() -> Unit)? = null,
    onBrowser: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onPageLayout: (() -> Unit)? = null,
    onShiftPage: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = readerBarSlide) +
            fadeIn(animationSpec = readerBarFade),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = readerBarSlide) +
            fadeOut(animationSpec = readerBarFade),
        modifier = modifier,
    ) {
        val backgroundColor = MaterialTheme.colorScheme
            .surfaceColorAtElevation(READER_BAR_ELEVATION)
            .copy(alpha = if (isSystemInDarkTheme()) BAR_ALPHA_DARK else BAR_ALPHA_LIGHT)
        val iconColor = MaterialTheme.colorScheme.primary
        val dimColor = MaterialTheme.colorScheme.onSurfaceVariant

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onChapterList) {
                Icon(
                    imageVector = Icons.Outlined.FormatListNumbered,
                    contentDescription = stringResource(R.string.reader_chapter_list_title),
                    tint = iconColor,
                )
            }
            if (onWebView != null) {
                IconButton(onClick = onWebView) {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = stringResource(R.string.reader_open_in_web_view),
                        tint = iconColor,
                    )
                }
            }
            if (onBrowser != null) {
                IconButton(onClick = onBrowser) {
                    Icon(
                        imageVector = Icons.Outlined.Explore,
                        contentDescription = stringResource(R.string.reader_open_in_browser),
                        tint = iconColor,
                    )
                }
            }
            if (onShare != null) {
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.reader_share),
                        tint = iconColor,
                    )
                }
            }
            IconButton(onClick = onModeClick) {
                Icon(
                    imageVector = state.mode.icon,
                    contentDescription = stringResource(R.string.reader_mode_title),
                    tint = iconColor,
                )
            }
            IconButton(onClick = onOrientationClick) {
                Icon(
                    imageVector = Icons.Default.ScreenRotation,
                    contentDescription = stringResource(R.string.reader_orientation_title),
                    tint = iconColor,
                )
            }
            IconButton(onClick = onToggleCropBorders) {
                Icon(
                    imageVector = Icons.Outlined.Crop,
                    contentDescription = stringResource(R.string.reader_crop_borders),
                    tint = if (state.cropBordersEnabled) iconColor else dimColor,
                )
            }
            if (onPageLayout != null) {
                IconButton(onClick = onPageLayout) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = stringResource(R.string.reader_page_layout),
                        tint = iconColor,
                    )
                }
            }
            if (onShiftPage != null) {
                IconButton(onClick = onShiftPage) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = stringResource(R.string.reader_shift_double_pages),
                        tint = iconColor,
                    )
                }
            }
            IconButton(onClick = onToggleThumbnailStrip) {
                Icon(
                    imageVector = Icons.Outlined.BurstMode,
                    contentDescription = stringResource(R.string.reader_toggle_thumbnail_strip),
                    tint = if (state.showPageThumbnailStrip) iconColor else dimColor,
                )
            }
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.reader_settings),
                    tint = iconColor,
                )
            }
        }
    }
}
