package app.otakureader.feature.reader.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * Perfect zoom/pan implementation with butter smooth performance
 * Features: Pinch zoom, pan when zoomed, double tap zoom, fling gestures
 */
@Composable
fun ZoomableImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    zoomState: ZoomableState = rememberZoomableState(),
    minScale: Float = 1f,
    maxScale: Float = 4f,
    doubleTapScale: Float = 2f,
    contentScale: ContentScale = ContentScale.Fit,
    rotation: Float = 0f,
    cropBordersEnabled: Boolean = false,
    onDoubleTap: ((Offset) -> Unit)? = null,
    onTap: ((Offset) -> Unit)? = null,
    onZoomChange: ((Float) -> Unit)? = null,
    resetOnChange: Boolean = true
) {
    val scope = rememberCoroutineScope()

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Reset zoom when image changes
    if (resetOnChange) {
        androidx.compose.runtime.LaunchedEffect(imageUrl) {
            zoomState.reset()
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(imageUrl, minScale, maxScale) {
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        do {
                            val event = awaitPointerEvent()
                            val cancelled = event.changes.any { it.isConsumed }

                            if (!cancelled) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = false)

                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    val originalScale = zoomState.scale
                                    val newScale = (originalScale * zoomChange)
                                        .coerceIn(minScale, maxScale)

                                    val centroidX = centroid.x
                                    val centroidY = centroid.y

                                    scope.launch {
                                        zoomState.onZoom(
                                            newScale,
                                            centroidX,
                                            centroidY
                                        )
                                        if (panChange != Offset.Zero && zoomState.isZoomed) {
                                            zoomState.onPan(panChange)
                                        }
                                    }

                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (!cancelled && event.changes.any { it.pressed })
                    }
                }
            }
            .pointerInput(imageUrl, minScale, maxScale, doubleTapScale) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        scope.launch {
                            val targetScale = if (zoomState.scale >= doubleTapScale * 0.9f) {
                                minScale
                            } else {
                                doubleTapScale
                            }
                            zoomState.animateZoomTo(
                                targetScale,
                                offset.x,
                                offset.y,
                                containerSize.width.toFloat(),
                                containerSize.height.toFloat()
                            )
                        }
                        onDoubleTap?.invoke(offset)
                    },
                    onTap = { offset ->
                        onTap?.invoke(offset)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null) {
            val context = LocalContext.current
            val imageModel = remember(imageUrl, cropBordersEnabled, context) {
                val builder = ImageRequest.Builder(context).data(imageUrl)
                if (cropBordersEnabled) {
                    builder.transformations(CropBorderTransformation())
                }
                builder.build()
            }
            AsyncImage(
                model = imageModel,
                contentDescription = contentDescription,
                modifier = Modifier
                    .let { baseModifier ->
                        if (zoomState.isZoomed) {
                            baseModifier
                                .offset {
                                    IntOffset(
                                        zoomState.offsetX.toInt(),
                                        zoomState.offsetY.toInt()
                                    )
                                }
                        } else {
                            baseModifier
                        }
                    }
                    .graphicsLayer {
                        scaleX = zoomState.scale
                        scaleY = zoomState.scale
                        rotationZ = rotation
                    },
                contentScale = contentScale,
                filterQuality = FilterQuality.High
            )
        }
    }
}

/**
 * Advanced zoomable state with smooth animations and boundary constraints
 */
@Stable
class ZoomableState {
    private val _scale = Animatable(1f)
    private val _offsetX = Animatable(0f)
    private val _offsetY = Animatable(0f)

    val scale: Float get() = _scale.value
    val offsetX: Float get() = _offsetX.value
    val offsetY: Float get() = _offsetY.value
    val isZoomed: Boolean get() = _scale.value > 1.01f

    suspend fun onZoom(newScale: Float, centroidX: Float, centroidY: Float) {
        val previousScale = _scale.value
        _scale.snapTo(newScale)

        // Adjust offset to zoom towards centroid
        val scaleDiff = newScale / previousScale
        val newOffsetX = (_offsetX.value - centroidX) * scaleDiff + centroidX
        val newOffsetY = (_offsetY.value - centroidY) * scaleDiff + centroidY

        _offsetX.snapTo(newOffsetX)
        _offsetY.snapTo(newOffsetY)
    }

    suspend fun onPan(pan: Offset) {
        _offsetX.snapTo(_offsetX.value + pan.x)
        _offsetY.snapTo(_offsetY.value + pan.y)
    }

    suspend fun animateZoomTo(
        targetScale: Float,
        centroidX: Float,
        centroidY: Float,
        containerWidth: Float,
        containerHeight: Float
    ) {
        val previousScale = _scale.value

        // Compute target offsets before animating
        val scaleDiff = targetScale / previousScale
        val targetOffsetX = (_offsetX.value - centroidX) * scaleDiff + centroidX
        val targetOffsetY = (_offsetY.value - centroidY) * scaleDiff + centroidY

        // Constrain to bounds
        val constrained = constrainOffset(
            targetOffsetX, targetOffsetY,
            containerWidth, containerHeight,
            containerWidth * targetScale, containerHeight * targetScale
        )

        // Animate scale and offsets concurrently for smooth zoom
        coroutineScope {
            launch {
                _scale.animateTo(
                    targetScale,
                    animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f)
                )
            }
            launch {
                _offsetX.animateTo(
                    constrained.first,
                    animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f)
                )
            }
            launch {
                _offsetY.animateTo(
                    constrained.second,
                    animationSpec = spring(stiffness = 300f, dampingRatio = 0.8f)
                )
            }
        }
    }

    suspend fun fling(
        velocityX: Float,
        velocityY: Float,
        containerWidth: Float,
        containerHeight: Float,
        contentWidth: Float,
        contentHeight: Float
    ) {
        val decay = exponentialDecay<Float>(frictionMultiplier = 0.95f)

        // Animate with decay and then snap to bounds
        _offsetX.animateDecay(velocityX, decay)
        _offsetY.animateDecay(velocityY, decay)

        // Constrain to bounds after fling
        val constrained = constrainOffset(
            _offsetX.value, _offsetY.value,
            containerWidth, containerHeight,
            contentWidth * _scale.value, contentHeight * _scale.value
        )

        _offsetX.animateTo(
            constrained.first,
            animationSpec = spring(stiffness = 400f, dampingRatio = 1f)
        )
        _offsetY.animateTo(
            constrained.second,
            animationSpec = spring(stiffness = 400f, dampingRatio = 1f)
        )
    }

    suspend fun reset() {
        _scale.animateTo(1f, animationSpec = tween(300))
        _offsetX.animateTo(0f, animationSpec = tween(300))
        _offsetY.animateTo(0f, animationSpec = tween(300))
    }

    private fun constrainOffset(
        offsetX: Float, offsetY: Float,
        containerWidth: Float, containerHeight: Float,
        contentWidth: Float, contentHeight: Float
    ): Pair<Float, Float> {
        val maxOffsetX = max(0f, (contentWidth - containerWidth) / 2)
        val maxOffsetY = max(0f, (contentHeight - containerHeight) / 2)

        val constrainedX = offsetX.coerceIn(-maxOffsetX, maxOffsetX)
        val constrainedY = offsetY.coerceIn(-maxOffsetY, maxOffsetY)

        return Pair(constrainedX, constrainedY)
    }
}

@Composable
fun rememberZoomableState(): ZoomableState {
    return remember { ZoomableState() }
}
