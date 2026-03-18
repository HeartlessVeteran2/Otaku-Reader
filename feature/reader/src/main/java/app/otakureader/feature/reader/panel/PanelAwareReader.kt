package app.otakureader.feature.reader.panel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.otakureader.feature.reader.R
import app.otakureader.feature.reader.components.ZoomableImage
import app.otakureader.feature.reader.model.ComicPanel
import app.otakureader.feature.reader.model.ImageQuality
import app.otakureader.feature.reader.model.PanelBounds
import app.otakureader.feature.reader.model.ReaderPage
import app.otakureader.feature.reader.model.ReadingDirection
import kotlinx.coroutines.launch

/**
 * Panel-aware reader that provides intelligent panel-by-panel navigation.
 * 
 * This component enhances the reading experience by:
 * - Using Gemini Vision AI for accurate panel detection
 * - Supporting both RTL (manga) and LTR (comics) reading orders
 * - Providing smooth animated transitions between panels
 * - Allowing tap or swipe navigation
 * - Showing panel progress indicators
 * 
 * **Navigation:**
 * - Tap right side: Next panel (respects reading direction)
 * - Tap left side: Previous panel
 * - Tap center: Toggle UI controls
 * - Swipe: Navigate between panels
 * 
 * **Features:**
 * - Smart zoom that focuses on current panel
 * - Smooth spring-based animations
 * - Panel border overlay option
 * - Reading progress tracking
 * 
 * @param page The current reader page with panel data
 * @param panels List of detected panels for the page
 * @param readingDirection Reading direction (RTL for manga, LTR for comics)
 * @param currentPanelIndex Current panel index (0-based)
 * @param onPanelChange Callback when panel changes
 * @param onPageNext Callback to advance to next page
 * @param onPagePrevious Callback to go to previous page
 * @param onMenuRequest Callback when menu is requested
 * @param rotation Current rotation of the page
 * @param showPanelBorders Whether to show panel boundary overlays
 * @param imageQuality Image quality setting
 * @param modifier Modifier for the component
 */
@Composable
fun PanelAwareReader(
    page: ReaderPage,
    panels: List<ComicPanel>,
    readingDirection: ReadingDirection,
    currentPanelIndex: Int,
    onPanelChange: (Int) -> Unit,
    onPageNext: () -> Unit,
    onPagePrevious: () -> Unit,
    onMenuRequest: () -> Unit,
    rotation: Float = 0f,
    showPanelBorders: Boolean = false,
    imageQuality: ImageQuality = ImageQuality.ORIGINAL,
    modifier: Modifier = Modifier
) {
    if (panels.isEmpty()) {
        // Fallback to regular view if no panels
        EmptyPanelView(
            page = page,
            onPageNext = onPageNext,
            onPagePrevious = onPagePrevious,
            onMenuRequest = onMenuRequest,
            rotation = rotation,
            imageQuality = imageQuality,
            modifier = modifier
        )
        return
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    // Ensure panel index is valid
    val validPanelIndex = currentPanelIndex.coerceIn(0, panels.size - 1)
    val currentPanel = panels[validPanelIndex]

    // Animation states for smooth panel transitions
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    // Track drag state for swipe gestures
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // Animate to current panel when it changes
    LaunchedEffect(validPanelIndex, containerSize) {
        if (containerSize.width > 0 && containerSize.height > 0) {
            animateToPanel(
                panel = currentPanel.bounds,
                containerSize = containerSize,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                alpha = alpha
            )
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
        // Main content with zoom/pan
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offsetX.value
                    translationY = offsetY.value
                    transformOrigin = TransformOrigin.Center
                }
        ) {
            // Page image
            ZoomableImage(
                imageUrl = page.imageUrl,
                contentDescription = stringResource(
                    R.string.reader_page_content,
                    page.pageNumber
                ),
                contentScale = ContentScale.Fit,
                rotation = rotation,
                imageQuality = imageQuality,
                onTap = { offset ->
                    // Handled by tap zones below
                },
                modifier = Modifier.fillMaxSize()
            )

            // Panel border overlay
            if (showPanelBorders) {
                PanelBorderOverlay(
                    panels = panels,
                    currentPanelIndex = validPanelIndex,
                    containerSize = containerSize
                )
            }
        }

        // Tap zones for navigation
        TapZones(
            readingDirection = readingDirection,
            onNext = {
                if (validPanelIndex < panels.size - 1) {
                    scope.launch {
                        // Fade out effect
                        alpha.animateTo(0.7f, spring(stiffness = Spring.StiffnessHigh))
                        onPanelChange(validPanelIndex + 1)
                        alpha.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
                    }
                } else {
                    onPageNext()
                }
            },
            onPrevious = {
                if (validPanelIndex > 0) {
                    scope.launch {
                        alpha.animateTo(0.7f, spring(stiffness = Spring.StiffnessHigh))
                        onPanelChange(validPanelIndex - 1)
                        alpha.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
                    }
                } else {
                    onPagePrevious()
                }
            },
            onMenu = onMenuRequest,
            modifier = Modifier.fillMaxSize()
        )

        // UI overlay
        PanelReaderOverlay(
            currentPanel = validPanelIndex + 1,
            totalPanels = panels.size,
            readingDirection = readingDirection,
            onNext = {
                if (validPanelIndex < panels.size - 1) {
                    onPanelChange(validPanelIndex + 1)
                } else {
                    onPageNext()
                }
            },
            onPrevious = {
                if (validPanelIndex > 0) {
                    onPanelChange(validPanelIndex - 1)
                } else {
                    onPagePrevious()
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Transition fade overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.background.copy(
                        alpha = (1f - alpha.value) * 0.5f
                    )
                )
        )
    }
}

/**
 * Animate zoom and pan to focus on a specific panel.
 */
private suspend fun animateToPanel(
    panel: PanelBounds,
    containerSize: IntSize,
    scale: Animatable<Float, *>,
    offsetX: Animatable<Float, *>,
    offsetY: Animatable<Float, *>,
    alpha: Animatable<Float, *>
) {
    // Calculate target scale to fit panel with some padding
    val paddingPercent = 0.05f
    val targetScaleX = (1f / panel.width.coerceAtLeast(0.1f)) * (1f - paddingPercent * 2)
    val targetScaleY = (1f / panel.height.coerceAtLeast(0.1f)) * (1f - paddingPercent * 2)
    val targetScale = minOf(targetScaleX, targetScaleY).coerceIn(1f, 4f)

    // Calculate offset to center the panel
    val panelCenterX = panel.centerX - 0.5f
    val panelCenterY = panel.centerY - 0.5f
    val targetOffsetX = -panelCenterX * containerSize.width * targetScale
    val targetOffsetY = -panelCenterY * containerSize.height * targetScale

    // Animate with spring physics for natural feel
    kotlinx.coroutines.coroutineScope {
        launch {
            scale.animateTo(
                targetValue = targetScale,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            offsetX.animateTo(
                targetValue = targetOffsetX,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        launch {
            offsetY.animateTo(
                targetValue = targetOffsetY,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            )
        }
    }
}

/**
 * Tap zones for navigation.
 */
@Composable
private fun TapZones(
    readingDirection: ReadingDirection,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Define tap zones based on reading direction
    val (nextZoneAlignment, prevZoneAlignment) = when (readingDirection) {
        ReadingDirection.RTL -> Alignment.CenterEnd to Alignment.CenterStart
        ReadingDirection.LTR -> Alignment.CenterStart to Alignment.CenterEnd
        ReadingDirection.VERTICAL -> Alignment.BottomCenter to Alignment.TopCenter
    }

    Box(modifier = modifier) {
        // Next panel zone
        Box(
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .fillMaxWidth(0.25f)
                .align(nextZoneAlignment)
                .pointerInput(Unit) {
                    detectTapGestures { onNext() }
                }
        )

        // Previous panel zone
        Box(
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .fillMaxWidth(0.25f)
                .align(prevZoneAlignment)
                .pointerInput(Unit) {
                    detectTapGestures { onPrevious() }
                }
        )

        // Menu zone (center)
        Box(
            modifier = Modifier
                .fillMaxHeight(0.6f)
                .fillMaxWidth(0.4f)
                .align(Alignment.Center)
                .pointerInput(Unit) {
                    detectTapGestures { onMenu() }
                }
        )
    }
}

/**
 * Overlay with panel navigation controls.
 */
@Composable
private fun PanelReaderOverlay(
    currentPanel: Int,
    totalPanels: Int,
    readingDirection: ReadingDirection,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top row: Panel indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            PanelIndicatorChip(
                currentPanel = currentPanel,
                totalPanels = totalPanels
            )
        }

        // Middle: Navigation arrows (subtle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous button
            if (currentPanel > 1) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = stringResource(R.string.reader_previous_panel),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            } else {
                // Spacer for alignment
                Box(modifier = Modifier.size(48.dp))
            }

            // Next button
            if (currentPanel < totalPanels) {
                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ArrowForward,
                        contentDescription = stringResource(R.string.reader_next_panel),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            } else {
                Box(modifier = Modifier.size(48.dp))
            }
        }

        // Bottom: Progress bar
        LinearProgressIndicator(
            progress = { currentPanel / totalPanels.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 * Panel indicator chip showing current panel / total.
 */
@Composable
private fun PanelIndicatorChip(
    currentPanel: Int,
    totalPanels: Int
) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "$currentPanel / $totalPanels",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Overlay showing panel boundaries.
 */
@Composable
private fun PanelBorderOverlay(
    panels: List<ComicPanel>,
    currentPanelIndex: Int,
    containerSize: IntSize
) {
    if (containerSize == IntSize.Zero) return

    val density = LocalDensity.current

    Box(modifier = Modifier.fillMaxSize()) {
        panels.forEachIndexed { index, panel ->
            val isCurrent = index == currentPanelIndex
            val bounds = panel.bounds

            Box(
                modifier = Modifier
                    .then(
                        with(density) {
                            Modifier
                                .fillMaxWidth((bounds.width * 100).toInt().toDp().value / 100f)
                                .fillMaxHeight((bounds.height * 100).toInt().toDp().value / 100f)
                        }
                    )
                    .align(
                        androidx.compose.ui.BiasAlignment(
                            (bounds.centerX - 0.5f) * 2,
                            (bounds.centerY - 0.5f) * 2
                        )
                    )
                    .background(
                        color = if (isCurrent) {
                            Color.Yellow.copy(alpha = 0.1f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .padding(2.dp)
                    .background(
                        color = if (isCurrent) {
                            Color.Yellow.copy(alpha = 0.3f)
                        } else {
                            Color.White.copy(alpha = 0.1f)
                        },
                        shape = MaterialTheme.shapes.small
                    )
            )
        }
    }
}

/**
 * Fallback view when no panels are detected.
 */
@Composable
private fun EmptyPanelView(
    page: ReaderPage,
    onPageNext: () -> Unit,
    onPagePrevious: () -> Unit,
    onMenuRequest: () -> Unit,
    rotation: Float,
    imageQuality: ImageQuality,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        ZoomableImage(
            imageUrl = page.imageUrl,
            contentDescription = stringResource(R.string.reader_page_content, page.pageNumber),
            contentScale = ContentScale.Fit,
            rotation = rotation,
            imageQuality = imageQuality,
            onTap = { offset ->
                // Simple tap handling - left/right thirds
                when {
                    offset.x < 0.33f -> onPagePrevious()
                    offset.x > 0.66f -> onPageNext()
                    else -> onMenuRequest()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Loading state for panel analysis.
 */
@Composable
fun PanelAnalysisLoading(
    progress: Float? = null,
    message: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (progress != null) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = message ?: stringResource(R.string.panel_analysis_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

/**
 * Error state for panel analysis.
 */
@Composable
fun PanelAnalysisError(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.panel_analysis_error_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.reader_continue_without_panels))
                }

                androidx.compose.material3.Button(onClick = onRetry) {
                    Text(stringResource(R.string.reader_retry))
                }
            }
        }
    }
}
