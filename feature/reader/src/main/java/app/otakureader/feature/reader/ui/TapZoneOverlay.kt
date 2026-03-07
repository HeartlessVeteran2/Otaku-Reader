package app.otakureader.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import app.otakureader.feature.reader.model.TapZoneConfig
import app.otakureader.feature.reader.viewmodel.TapZone

/**
 * Overlay for tap zone navigation.
 * Divides screen into left, center, and right zones for navigation.
 */
@Composable
fun TapZoneOverlay(
    onTapZone: (TapZone) -> Unit,
    config: TapZoneConfig = TapZoneConfig(),
    isVisible: Boolean = true,
    showDebugZones: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (!config.enabled || !isVisible) return
    
    Row(
        modifier = modifier.fillMaxSize()
    ) {
        // Left zone - Previous page
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(config.leftZoneWidth)
                .then(
                    if (showDebugZones) {
                        Modifier.background(Color.Red.copy(alpha = 0.2f))
                    } else {
                        Modifier
                    }
                )
                .pointerInput(Unit) {
                    detectTapGestures { onTapZone(TapZone.LEFT) }
                }
        )
        
        // Center zone - Toggle menu
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(config.centerZoneWidth)
                .then(
                    if (showDebugZones) {
                        Modifier.background(Color.Green.copy(alpha = 0.2f))
                    } else {
                        Modifier
                    }
                )
                .pointerInput(Unit) {
                    detectTapGestures { onTapZone(TapZone.CENTER) }
                }
        )
        
        // Right zone - Next page
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(config.rightZoneWidth)
                .then(
                    if (showDebugZones) {
                        Modifier.background(Color.Blue.copy(alpha = 0.2f))
                    } else {
                        Modifier
                    }
                )
                .pointerInput(Unit) {
                    detectTapGestures { onTapZone(TapZone.RIGHT) }
                }
        )
    }
}

/**
 * Simple tap zone overlay with customizable zone widths.
 * For RTL reading, zones can be inverted.
 */
@Composable
fun SimpleTapZoneOverlay(
    onLeftTap: () -> Unit,
    onCenterTap: () -> Unit,
    onRightTap: () -> Unit,
    leftZoneWidth: Float = 0.25f,
    centerZoneWidth: Float = 0.5f,
    rightZoneWidth: Float = 0.25f,
    isRtl: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (leftAction, rightAction) = if (isRtl) {
        Pair(onRightTap, onLeftTap)
    } else {
        Pair(onLeftTap, onRightTap)
    }
    
    Row(modifier = modifier.fillMaxSize()) {
        // Left zone
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(leftZoneWidth)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = leftAction
                )
        )
        
        // Center zone
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(centerZoneWidth)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onCenterTap
                )
        )
        
        // Right zone
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(rightZoneWidth)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = rightAction
                )
        )
    }
}
