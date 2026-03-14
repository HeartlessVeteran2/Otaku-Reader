package app.otakureader.feature.reader.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.otakureader.feature.reader.R
import app.otakureader.feature.reader.model.ComicPanel
import app.otakureader.feature.reader.model.ReaderPage
import kotlinx.coroutines.launch

/**
 * Panel navigation view that displays and zooms into detected comic panels
 */
@Composable
fun PanelNavigationView(
    page: ReaderPage,
    panel: ComicPanel,
    currentPanelIndex: Int,
    totalPanels: Int,
    onPanelChange: (Int) -> Unit,
    onTap: (Offset) -> Unit,
    rotation: Float = 0f,
    cropBordersEnabled: Boolean = false,
    dataSaverEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    // Animated scale and offset for smooth panel transitions
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    // Animate to panel bounds when panel changes
    LaunchedEffect(panel.id, containerSize) {
        if (containerSize.width > 0 && containerSize.height > 0) {
            val bounds = panel.bounds

            // Calculate zoom scale to fit panel in view
            val panelWidth = bounds.width
            val panelHeight = bounds.height
            val targetScale = minOf(
                1f / panelWidth.coerceAtLeast(0.1f),
                1f / panelHeight.coerceAtLeast(0.1f)
            ).coerceIn(1f, 4f)

            // Calculate offset to center panel
            val panelCenterX = bounds.centerX - 0.5f
            val panelCenterY = bounds.centerY - 0.5f
            val targetOffsetX = -panelCenterX * containerSize.width * targetScale
            val targetOffsetY = -panelCenterY * containerSize.height * targetScale

            // Animate to panel
            launch {
                scale.animateTo(targetScale, spring())
            }
            launch {
                offsetX.animateTo(targetOffsetX, spring())
            }
            launch {
                offsetY.animateTo(targetOffsetY, spring())
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                containerSize = coordinates.size
            }
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Display the page image with zoom/pan to current panel
        ZoomableImage(
            imageUrl = page.imageUrl,
            contentDescription = stringResource(R.string.reader_page_content, page.pageNumber),
            contentScale = ContentScale.Fit,
            rotation = rotation,
            cropBordersEnabled = cropBordersEnabled,
            dataSaverEnabled = dataSaverEnabled,
            onTap = { offset ->
                // Handle tap for panel navigation
                val tapX = offset.x / containerSize.width
                val tapY = offset.y / containerSize.height

                // Navigate to next panel on right tap, previous on left tap
                if (tapX > 0.7f && currentPanelIndex < totalPanels - 1) {
                    onPanelChange(currentPanelIndex + 1)
                } else if (tapX < 0.3f && currentPanelIndex > 0) {
                    onPanelChange(currentPanelIndex - 1)
                } else {
                    onTap(offset)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offsetX.value
                    translationY = offsetY.value
                }
        )

        // Panel indicator
        PanelIndicator(
            currentPanel = currentPanelIndex + 1,
            totalPanels = totalPanels,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )
    }
}

/**
 * Indicator showing current panel number
 */
@Composable
private fun PanelIndicator(
    currentPanel: Int,
    totalPanels: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "$currentPanel / $totalPanels",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
