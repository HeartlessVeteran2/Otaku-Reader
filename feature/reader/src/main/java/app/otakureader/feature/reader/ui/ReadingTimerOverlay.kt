package app.otakureader.feature.reader.ui

import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import android.os.SystemClock
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.layout.Row
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.foundation.layout.Spacer
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

import androidx.compose.material.icons.filled.Timer
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.material3.Icon
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.material3.MaterialTheme
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.material3.Surface
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.material3.Text
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.runtime.Composable
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.runtime.LaunchedEffect
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.runtime.getValue
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.runtime.mutableLongStateOf
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.runtime.remember
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.runtime.setValue
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.ui.Alignment
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.ui.Modifier
import app.otakureader.feature.reader.PageRotation
import app.otakureader.feature.reader.TapZone

import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Displays the current reading session duration in HH:MM:SS format.
 * Updates every second while visible.
 *
 * Inspired by Komikku's ReadingTimerOverlay for session time tracking.
 */
@Composable
fun ReadingTimerOverlay(
    isVisible: Boolean,
    sessionStartMs: Long,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    var currentTimeMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    // Update current time every second
    // Restart the effect if sessionStartMs changes (e.g., new reading session)
    LaunchedEffect(sessionStartMs) {
        currentTimeMs = SystemClock.elapsedRealtime()
        while (true) {
            delay(1000)
            currentTimeMs = SystemClock.elapsedRealtime()
        }
    }

    val durationMs = currentTimeMs - sessionStartMs
    val hours = durationMs / 3600000
    val minutes = (durationMs % 3600000) / 60000
    val seconds = (durationMs % 60000) / 1000

    Surface(
        modifier = modifier.padding(16.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = "Timer",
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = String.format("%02d:%02d:%02d", hours, minutes, seconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
