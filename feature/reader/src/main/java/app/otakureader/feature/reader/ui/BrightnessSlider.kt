package app.otakureader.feature.reader.ui

import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.animation.AnimatedVisibility
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.animation.fadeIn
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.animation.fadeOut
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.background
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.layout.Box
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.layout.fillMaxHeight
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.layout.padding
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.layout.width
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.shape.RoundedCornerShape
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.material.icons.Icons
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.material.icons.filled.BrightnessHigh
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.material.icons.filled.BrightnessLow
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.material3.Icon
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.material3.MaterialTheme
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.material3.Slider
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.material3.SliderDefaults
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.runtime.Composable
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.ui.Alignment
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.ui.Modifier
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.ui.draw.clip
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.ui.graphics.Color
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.ui.graphics.graphicsLayer
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.ui.res.stringResource
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.ui.unit.dp
import app.otakureader.feature.reader.R

/**
 * Brightness slider overlay that appears on the left side of the screen.
 * Allows quick brightness adjustment while reading.
 */
@Composable
fun BrightnessSliderOverlay(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(64.dp)
                .padding(vertical = 64.dp, horizontal = 8.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            // Brightness icon at top
            Icon(
                imageVector = Icons.Default.BrightnessHigh,
                contentDescription = stringResource(R.string.reader_brightness_increase),
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            )
            
            // Vertical slider
            VerticalSlider(
                value = brightness,
                onValueChange = onBrightnessChange,
                valueRange = 0.1f..1.5f,
                modifier = Modifier.padding(vertical = 48.dp)
            )
            
            // Low brightness icon at bottom
            Icon(
                imageVector = Icons.Default.BrightnessLow,
                contentDescription = stringResource(R.string.reader_brightness_decrease),
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

/**
 * Vertical slider implementation using rotation.
 */
@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    // Rotate a horizontal slider to make it vertical
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(alpha = 0.8f),
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .width(200.dp) // Length of the vertical slider
                .graphicsLayer { rotationZ = 270f }
        )
    }
}

/**
 * Simple brightness indicator showing current level as an overlay.
 */
@Composable
fun BrightnessIndicator(
    brightness: Float,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (brightness > 1.0f) {
                    Icons.Default.BrightnessHigh
                } else {
                    Icons.Default.BrightnessLow
                },
                contentDescription = "Brightness",
                tint = Color.White,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}
