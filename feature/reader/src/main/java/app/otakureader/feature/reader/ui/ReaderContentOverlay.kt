package app.otakureader.feature.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.otakureader.core.ui.theme.OtakuReaderTheme
import app.otakureader.feature.reader.R

private const val READER_TOP_BAR_SLIDE_MS = 200
private const val READER_TOP_BAR_FADE_MS = 150
private val READER_TOP_BAR_ELEVATION = 3.dp
private const val BAR_ALPHA_DARK = 0.9f
private const val BAR_ALPHA_LIGHT = 0.95f

private val readerTopBarSlide = tween<IntOffset>(READER_TOP_BAR_SLIDE_MS)
private val readerTopBarFade = tween<Float>(READER_TOP_BAR_FADE_MS)

/**
 * Reader top app bar shown while the menu is visible: back, manga title + chapter subtitle, and
 * optional download / bookmark / settings actions.
 *
 * This is the clean Mihon/Komikku-style top chrome — plain Material 3 widgets only. Page
 * scrubbing and previous/next-chapter navigation live in the bottom bar (`PageSlider`, Komikku's
 * ChapterNavigator) and thumbnails in `PageThumbnailStrip`, so this overlay intentionally carries
 * no slider, filmstrip, or chapter buttons (they would duplicate the bottom controls). It
 * previously branched on content type into ink/panel and neon/glassmorphism variants; those
 * Otaku-exclusive styles were dropped for visual parity with Komikku.
 *
 * @param title         Manga series title displayed in the top bar.
 * @param chapterTitle  Current chapter title displayed below the series title.
 * @param isVisible     Whether the overlay is shown.
 * @param onDismiss     Called when the back arrow is tapped — typically navigates back.
 * @param onTitleClick Called when the title/chapter area is tapped; null makes the area non-interactive.
 * @param onDownloadChapter Called when the download icon is tapped; null hides the button.
 * @param isCurrentChapterDownloaded When true, the download button is hidden.
 * @param onBookmarkPage Called when the bookmark icon is tapped; null hides the button.
 * @param isCurrentPageBookmarked When true, shows a solid bookmark; when false, shows an outlined bookmark border.
 * @param onOpenComments Called when the comments icon is tapped; null hides the button.
 */
@Composable
fun ReaderContentOverlay(
    title: String,
    chapterTitle: String,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    onDownloadChapter: (() -> Unit)? = null,
    isCurrentChapterDownloaded: Boolean = false,
    onBookmarkPage: (() -> Unit)? = null,
    isCurrentPageBookmarked: Boolean = false,
    onOpenComments: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }, animationSpec = readerTopBarSlide) +
            fadeIn(animationSpec = readerTopBarFade),
        exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = readerTopBarSlide) +
            fadeOut(animationSpec = readerTopBarFade),
        modifier = modifier
    ) {
        val backgroundColor = MaterialTheme.colorScheme
            .surfaceColorAtElevation(READER_TOP_BAR_ELEVATION)
            .copy(alpha = if (isSystemInDarkTheme()) BAR_ALPHA_DARK else BAR_ALPHA_LIGHT)
        val onSurface = MaterialTheme.colorScheme.onSurface
        val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.reader_back),
                    tint = onSurface
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick)
                        else Modifier
                    )
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    chapterTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onDownloadChapter != null && !isCurrentChapterDownloaded) {
                IconButton(onClick = onDownloadChapter) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = stringResource(R.string.reader_download_chapter),
                        tint = onSurfaceVariant
                    )
                }
            }
            if (onBookmarkPage != null) {
                IconButton(onClick = onBookmarkPage) {
                    Icon(
                        imageVector = if (isCurrentPageBookmarked) {
                            Icons.Outlined.Bookmark
                        } else {
                            Icons.Outlined.BookmarkBorder
                        },
                        contentDescription = stringResource(R.string.reader_bookmark_page),
                        tint = if (isCurrentPageBookmarked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            onSurfaceVariant
                        }
                    )
                }
            }
            if (onOpenComments != null) {
                IconButton(onClick = onOpenComments) {
                    Icon(
                        Icons.AutoMirrored.Filled.Comment,
                        contentDescription = stringResource(R.string.reader_comments_title),
                        tint = onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReaderContentOverlayPreview() {
    OtakuReaderTheme {
        ReaderContentOverlay(
            title = "Berserk",
            chapterTitle = "Chapter 364: The Elf King",
            isVisible = true,
            onDismiss = {},
            onDownloadChapter = {},
            onBookmarkPage = {}
        )
    }
}
