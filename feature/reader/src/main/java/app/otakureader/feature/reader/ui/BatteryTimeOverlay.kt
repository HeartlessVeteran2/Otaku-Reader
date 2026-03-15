package app.otakureader.feature.reader.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Parses battery level percentage and charging state from a battery-status [Intent].
 *
 * @return Pair of (batteryPercent: Float in 0–100, isCharging: Boolean).
 */
private fun parseBatteryIntent(intent: Intent): Pair<Float, Boolean> {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    // Guard against division by zero and clamp to valid range (0-100)
    val percent = if (scale > 0) {
        ((level / scale.toFloat()) * 100f).coerceIn(0f, 100f)
    } else {
        0f
    }
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                   status == BatteryManager.BATTERY_STATUS_FULL
    return percent to charging
}

/**
 * Displays battery level and current system time in the reader.
 * Battery status updates via BroadcastReceiver, time updates every minute.
 *
 * Inspired by Komikku's battery/time overlay for reader convenience.
 */
@Composable
fun BatteryTimeOverlay(
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    val context = LocalContext.current
    var batteryLevel by remember { mutableFloatStateOf(100f) }
    var isCharging by remember { mutableStateOf(false) }
    var currentTime by remember { mutableStateOf("") }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // Register battery status receiver and capture sticky intent for initial value
    DisposableEffect(Unit) {
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val (level, charging) = parseBatteryIntent(intent)
                batteryLevel = level
                isCharging = charging
            }
        }

        // Register receiver and process sticky intent to initialize battery state
        val stickyIntent = ContextCompat.registerReceiver(
            context,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Process sticky intent to initialize battery level immediately
        stickyIntent?.let { intent ->
            val (level, charging) = parseBatteryIntent(intent)
            batteryLevel = level
            isCharging = charging
        }

        onDispose {
            // Guard against IllegalArgumentException if registration failed or receiver already unregistered
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (_: IllegalArgumentException) {
                // Receiver was not registered or already unregistered - safe to ignore
            }
        }
    }

    // Update time every minute (HH:mm does not show seconds)
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = timeFormatter.format(Date())
            delay(60_000)
        }
    }

    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Battery indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isCharging) Icons.Default.BatteryChargingFull
                             else Icons.Default.BatteryFull,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = when {
                    batteryLevel <= 15f -> Color.Red
                    batteryLevel <= 30f -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = "${batteryLevel.toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    batteryLevel <= 15f -> Color.Red
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }

        // Time display
        Text(
            text = currentTime,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
