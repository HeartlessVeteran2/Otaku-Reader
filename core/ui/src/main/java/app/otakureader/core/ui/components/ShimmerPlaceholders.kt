package app.otakureader.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.otakureader.core.ui.modifiers.shimmer

/**
 * Shimmer placeholder for manga cards during loading.
 */
@Composable
fun MangaCardShimmer(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(8.dp))
            .shimmer()
    )
}

/**
 * Shimmer placeholder for list items.
 */
@Composable
fun ListItemShimmer(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Thumbnail placeholder
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmer()
        )
    }
}

/**
 * Shimmer placeholder for text lines.
 *
 * @param lines Number of shimmer lines to show
 * @param lineHeight Height of each line
 * @param lastLineWidthPercent Width of last line as percentage (0.0-1.0)
 */
@Composable
fun TextShimmer(
    lines: Int = 2,
    lineHeight: Int = 16,
    lastLineWidthPercent: Float = 0.6f,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        repeat(lines) { index ->
            val isLastLine = index == lines - 1
            val widthFraction = if (isLastLine) lastLineWidthPercent else 1f

            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(lineHeight.dp)
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
        }
    }
}

/**
 * Full-screen shimmer loading for lists.
 *
 * @param itemCount Number of shimmer items to show
 * @param columns Number of columns for grid layout (1 for list)
 */
@Composable
fun GridShimmer(
    itemCount: Int = 6,
    columns: Int = 2,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        val rows = (itemCount + columns - 1) / columns

        repeat(rows) { row ->
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                repeat(columns) { col ->
                    val itemIndex = row * columns + col
                    if (itemIndex < itemCount) {
                        Box(modifier = Modifier.weight(1f)) {
                            MangaCardShimmer()
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
            if (row < rows - 1) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
