package app.otakureader.feature.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.otakureader.feature.reader.model.ReadingDirection

/**
 * Draggable page slider (seekbar) for the reader.
 *
 * Displays a Material 3 [Slider] that lets users scrub through pages by dragging.
 * For RTL reading direction the slider is horizontally mirrored so that dragging
 * right moves to earlier pages, matching the manga reading flow.
 *
 * @param currentPage   0-based index of the currently displayed page.
 * @param totalPages    Total number of pages in the chapter.
 * @param onPageSeek    Callback invoked with a 0-based page index while the user drags.
 * @param readingDirection Current reading direction; RTL reverses the slider visually.
 * @param isVisible     Controls the animated visibility of the slider.
 * @param modifier      Optional [Modifier] for the outer container.
 */
@Composable
fun PageSlider(
    currentPage: Int,
    totalPages: Int,
    onPageSeek: (Int) -> Unit,
    readingDirection: ReadingDirection = ReadingDirection.LTR,
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Guard against totalPages == 0 (can occur during AnimatedVisibility exit transition).
                if (totalPages <= 0) return@Column

                // Page label row: e.g. "5 / 120"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val firstLabel = if (readingDirection == ReadingDirection.RTL) totalPages.toString() else "1"
                    val lastLabel = if (readingDirection == ReadingDirection.RTL) "1" else totalPages.toString()

                    Text(
                        text = firstLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${currentPage + 1} / $totalPages",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = lastLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Track slider drag value locally so scrubbing feels responsive.
                // We initialize from the external currentPage and keep in sync when
                // the external value changes (e.g. page turned via tap zone).
                var sliderValue by remember(currentPage) { mutableFloatStateOf(currentPage.toFloat()) }

                // Only emit onPageSeek when the integer page index actually changes to
                // avoid unnecessary churn from sub-integer float movements during a drag.
                var lastEmittedPage by remember(currentPage) { mutableIntStateOf(currentPage) }

                val maxValue = (totalPages - 1).coerceAtLeast(0).toFloat()

                // Apply horizontal mirror for RTL so dragging right → earlier pages.
                val sliderModifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (readingDirection == ReadingDirection.RTL) {
                            Modifier.graphicsLayer(scaleX = -1f)
                        } else {
                            Modifier
                        }
                    )

                Slider(
                    value = sliderValue,
                    onValueChange = { newValue ->
                        sliderValue = newValue
                        val newPage = newValue.toInt()
                        if (newPage != lastEmittedPage) {
                            lastEmittedPage = newPage
                            onPageSeek(newPage)
                        }
                    },
                    valueRange = 0f..maxValue,
                    steps = if (totalPages > 2) totalPages - 2 else 0,
                    modifier = sliderModifier
                )
            }
        }
    }
}
