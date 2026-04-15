package app.otakureader.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import app.otakureader.core.ui.R
import app.otakureader.core.ui.modifiers.bottomGradientScrim

/**
 * Enhanced manga card with loading states, error handling, and improved visuals.
 *
 * @param title The manga title to display
 * @param coverUrl The URL of the manga cover image
 * @param onClick Callback when the card is clicked
 * @param modifier Modifier for customizing the layout
 * @param badge Optional composable to display as a badge (e.g., unread count)
 * @param contentDescription Accessibility description for the cover image
 */
@Composable
fun MangaCard(
    title: String,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: @Composable (() -> Unit)? = null,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    var imageLoadFailed by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box {
            // Cover image with loading and error states
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription
                    ?: stringResource(R.string.manga_cover_content_description, title),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
                loading = {
                    MangaCardShimmer()
                },
                error = {
                    imageLoadFailed = true
                    MangaCardError()
                },
                success = {
                    imageLoadFailed = false
                }
            )

            // Gradient scrim for text readability (only show if image loaded)
            if (!imageLoadFailed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .bottomGradientScrim(
                            heightPercent = 0.35f,
                            startAlpha = 0.0f,
                            endAlpha = 0.75f
                        )
                )
            }

            // Title overlay at bottom
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = if (imageLoadFailed) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    Color.White
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .fillMaxWidth(),
            )

            // Optional badge (e.g., unread count)
            badge?.let {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    it()
                }
            }
        }
    }
}

/**
 * Placeholder shown while the manga cover is loading.
 * Alternative to MangaCardShimmer for static placeholder display.
 */
@Suppress("UnusedPrivateMember")
@Composable
private fun MangaCardPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        // Shimmer effect could be added here
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(80.dp)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small
                )
        )
    }
}

/**
 * Error state shown when the manga cover fails to load.
 */
@Composable
private fun MangaCardError() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "?",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}
