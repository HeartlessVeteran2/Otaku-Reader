@file:Suppress("MaxLineLength")
package app.otakureader.feature.details

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.otakureader.core.ui.background.CoverSplashBackground
import app.otakureader.core.ui.theme.LocalOtakuColors
import app.otakureader.feature.details.R
import coil3.compose.AsyncImage

private val MARKDOWN_BOLD_REGEX = Regex("""\*\*(.+?)\*\*""")
private val MARKDOWN_ITALIC_REGEX = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)""")
private val MARKDOWN_LINK_REGEX = Regex("""\[(.+?)]\((.+?)\)""")

// Parallax hero constants
private const val HERO_BG_PARALLAX_FACTOR = 0.4f
private const val HERO_FG_PARALLAX_FACTOR = 0.15f
private const val HERO_BG_SCALE = 1.15f

// Header info-block tokens.
private const val HEADER_TITLE_MAX_LINES = 3
private const val HEADER_SUBTITLE_MAX_LINES = 1
private const val HEADER_SUBTITLE_ALPHA = 0.8f
private val HEADER_SUBTITLE_TOP_SPACING = 6.dp
private val HEADER_STATUS_TOP_SPACING = 4.dp

@Composable
internal fun MangaHeader(
    manga: app.otakureader.domain.model.Manga,
    showPanoramaCover: Boolean,
    onTogglePanoramaCover: () -> Unit,
    onSearchGlobal: (String) -> Unit = {},
    scrollOffset: () -> Float = { 0f },
    modifier: Modifier = Modifier
) {
    val otaku = LocalOtakuColors.current

    // Bloom enter animation — fades the hero in on first composition.
    // The Animatable is remembered so Compose reads its `value` as observable state,
    // driving recomposition on every animation frame. Previously, the Animatable was
    // created inside LaunchedEffect and its value was only copied after animateTo()
    // completed, so the hero jumped from alpha=0 to alpha=1 with no visible transition.
    val bloomAnimatable = remember(manga.id) { Animatable(0f) }
    LaunchedEffect(manga.id) {
        bloomAnimatable.snapTo(0f)
        bloomAnimatable.animateTo(1f, animationSpec = tween(800, easing = FastOutSlowInEasing))
    }

    // Derive splash colors from the Material theme primary/secondary driven by MangaDynamicTheme
    val dominantColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val vibrantColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)

    Column(modifier = modifier.fillMaxWidth()) {
        if (showPanoramaCover) {
            // Panorama mode - wide banner cover
            Box(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = manga.thumbnailUrl,
                    contentDescription = manga.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
                IconButton(
                    onClick = onTogglePanoramaCover,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = stringResource(R.string.details_toggle_panorama)
                    )
                }
            }
        } else {
            // Enhanced hero — 360dp tall, bloom-animated, content-type-aware
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .graphicsLayer { alpha = bloomAnimatable.value }
            ) {
                // Background layer: CoverSplashBackground when cover URL available,
                // fallback gradient otherwise
                if (!manga.thumbnailUrl.isNullOrBlank()) {
                    CoverSplashBackground(
                        dominant = dominantColor,
                        vibrant = vibrantColor,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF12121A), Color(0xFF0A0A0F))
                                )
                            )
                    )
                }

                // Blurred cover image — parallax depth layer
                if (!manga.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = manga.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(24.dp)
                            .alpha(0.35f)
                            .graphicsLayer {
                                val offset = scrollOffset()
                                translationY = offset * HERO_BG_PARALLAX_FACTOR
                                scaleX = HERO_BG_SCALE
                                scaleY = HERO_BG_SCALE
                            }
                    )
                }

                // Bottom gradient fade
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.45f to otaku.bg.copy(alpha = 0.35f),
                                1f to otaku.bg.copy(alpha = 0.95f),
                            )
                        )
                )

                // Foreground content row — cover + info, with subtle parallax
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .graphicsLayer { translationY = scrollOffset() * HERO_FG_PARALLAX_FACTOR },
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // Cover thumbnail with panorama toggle
                    Box {
                        AsyncImage(
                            model = manga.thumbnailUrl,
                            contentDescription = manga.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 100.dp, height = 150.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        IconButton(
                            onClick = onTogglePanoramaCover,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(28.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = stringResource(R.string.details_toggle_panorama),
                                modifier = Modifier.size(14.dp),
                                tint = Color.White,
                            )
                        }
                    }

                    // Clean Mihon/Komikku-style info block: plain bold title, author, artist,
                    // status, and an add-to-library button. No animated title, content-type
                    // toggle, or glow buttons (those Otaku-exclusive flourishes diverged from
                    // Komikku's look and were dropped per the parity pass).
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = manga.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = HEADER_TITLE_MAX_LINES,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onSearchGlobal(manga.title) },
                        )

                        val author = manga.author?.takeIf { it.isNotBlank() }
                        // Show the artist only when it differs from the author (Komikku behaviour).
                        val artist = manga.artist?.takeIf { it.isNotBlank() && it != manga.author }

                        // Only insert the leading gap when at least one subtitle is present, so a
                        // manga with no author/artist doesn't get a double-spacer gap below the title.
                        if (author != null || artist != null) {
                            Spacer(modifier = Modifier.height(HEADER_SUBTITLE_TOP_SPACING))
                            author?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = HEADER_SUBTITLE_ALPHA),
                                    maxLines = HEADER_SUBTITLE_MAX_LINES,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { onSearchGlobal(it) },
                                )
                            }
                            artist?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = HEADER_SUBTITLE_ALPHA),
                                    maxLines = HEADER_SUBTITLE_MAX_LINES,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { onSearchGlobal(it) },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(HEADER_STATUS_TOP_SPACING))

                        Text(
                            text = stringResource(manga.status.displayTextResId()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = HEADER_SUBTITLE_ALPHA),
                        )

                    }
                }
            }
        }

        // When in panorama mode, show title/info below the banner
        if (showPanoramaCover) {
            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onSearchGlobal(manga.title) },
                )

                Spacer(modifier = Modifier.height(4.dp))

                manga.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        text = stringResource(R.string.details_author, author),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onSearchGlobal(author) },
                    )
                }

                manga.artist?.takeIf { it.isNotBlank() && it != manga.author }?.let { artist ->
                    Text(
                        text = stringResource(R.string.details_artist, artist),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onSearchGlobal(artist) },
                    )
                }

                Text(
                    text = stringResource(R.string.details_status, stringResource(manga.status.displayTextResId())),
                    style = MaterialTheme.typography.bodyMedium,
                    color = manga.status.colorValue(LocalOtakuColors.current)
                )
            }
        }
    }
}

/**
 * Renders a subset of Markdown as an [androidx.compose.ui.text.AnnotatedString].
 * Supported syntax:
 *  - `**text**` → bold
 *  - `*text*`   → italic
 *  - `[label](url)` → underlined link text
 */
internal fun renderMarkdown(source: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var remaining = source
        while (remaining.isNotEmpty()) {
            val boldMatch = MARKDOWN_BOLD_REGEX.find(remaining)
            val italicMatch = MARKDOWN_ITALIC_REGEX.find(remaining)
            val linkMatch = MARKDOWN_LINK_REGEX.find(remaining)

            // Find which match comes first
            val firstMatch = listOfNotNull(boldMatch, italicMatch, linkMatch)
                .minByOrNull { it.range.first }

            if (firstMatch == null) {
                append(remaining)
                break
            }

            // Append text before the match
            if (firstMatch.range.first > 0) {
                append(remaining.substring(0, firstMatch.range.first))
            }

            when (firstMatch) {
                boldMatch -> {
                    val boldText = firstMatch.groupValues[1]
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(boldText)
                    pop()
                }
                italicMatch -> {
                    val italicText = firstMatch.groupValues[1]
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(italicText)
                    pop()
                }
                linkMatch -> {
                    val label = firstMatch.groupValues[1]
                    pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                    append(label)
                    pop()
                }
            }

            remaining = remaining.substring(firstMatch.range.last + 1)
        }
    }
}
