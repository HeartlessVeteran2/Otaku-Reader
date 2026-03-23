package app.otakureader.core.ui.modifiers

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Creates a shimmer effect modifier for loading placeholders.
 *
 * @param targetValue The end position of the shimmer animation (default: 2f)
 * @param durationMillis Duration of one shimmer cycle (default: 1500ms)
 */
@Composable
fun Modifier.shimmer(
    targetValue: Float = 2f,
    durationMillis: Int = 1500
): Modifier = composed {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = durationMillis,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnimation * 1000f, y = translateAnimation * 1000f),
        tileMode = TileMode.Clamp
    )

    this.background(brush)
}

/**
 * Modifier for manga card clickable areas with consistent styling.
 *
 * @param onClick The callback when clicked
 * @param enabled Whether the click is enabled
 * @param shape The shape of the ripple
 */
fun Modifier.mangaCardClickable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    shape: Shape? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    this
        .clip(shape ?: MaterialTheme.shapes.medium)
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = shape != null),
            enabled = enabled,
            onClick = onClick
        )
        .padding(4.dp)
}

/**
 * Standard screen padding modifier.
 */
fun Modifier.screenPadding(
    horizontal: Dp = 16.dp,
    vertical: Dp = 16.dp
): Modifier = this.padding(horizontal = horizontal, vertical = vertical)

/**
 * Modifier for standard content padding within screens.
 */
fun Modifier.contentPadding(): Modifier = this.padding(16.dp)

/**
 * Modifier to apply a subtle scrim/overlay gradient.
 *
 * @param alpha The opacity of the scrim (default: 0.3f)
 * @param color The color of the scrim (default: Black)
 */
fun Modifier.scrim(
    alpha: Float = 0.3f,
    color: Color = Color.Black
): Modifier = this.drawBehind {
    drawRect(color = color.copy(alpha = alpha))
}

/**
 * Modifier for fade-in animation placeholder.
 *
 * @param visible Whether the content is visible
 * @param durationMillis Duration of the fade animation
 */
@Composable
fun Modifier.fadeIn(
    visible: Boolean,
    durationMillis: Int = 300
): Modifier = composed {
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = durationMillis),
        label = "fade"
    )
    this.alpha(alpha)
}

/**
 * Modifier for bottom gradient scrim (for text over images).
 *
 * @param heightPercent Percentage of container height for gradient (default: 0.4f)
 * @param startAlpha Starting alpha of gradient (default: 0.0f)
 * @param endAlpha Ending alpha of gradient (default: 0.7f)
 */
fun Modifier.bottomGradientScrim(
    heightPercent: Float = 0.4f,
    startAlpha: Float = 0.0f,
    endAlpha: Float = 0.7f
): Modifier = this.drawBehind {
    val gradientHeight = size.height * heightPercent
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = startAlpha),
                Color.Black.copy(alpha = endAlpha)
            ),
            startY = size.height - gradientHeight,
            endY = size.height
        )
    )
}

// Extension for animateFloatAsState that doesn't require explicit import
@Composable
private fun androidx.compose.animation.core.animateFloatAsState(
    targetValue: Float,
    animationSpec: androidx.compose.animation.core.AnimationSpec<Float>,
    label: String
) = androidx.compose.animation.core.animateFloatAsState(
    targetValue = targetValue,
    animationSpec = animationSpec,
    label = label
)
