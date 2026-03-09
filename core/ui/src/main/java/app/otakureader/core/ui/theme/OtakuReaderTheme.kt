package app.otakureader.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme()
private val DarkColorScheme = darkColorScheme()

/**
 * Otaku Reader app theme.
 * Supports dynamic color (Material You) on Android 12+, custom color schemes,
 * Pure Black (AMOLED) dark mode, and manual dark/light mode.
 *
 * @param darkTheme Whether to use dark theme. Defaults to system setting.
 * @param colorScheme Color scheme selection (0=System Default, 1=Dynamic, 2-10=Custom schemes)
 * @param usePureBlack Whether to use Pure Black (#000000) background in dark mode (AMOLED)
 */
@Composable
fun OtakuReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorScheme: Int = 0,
    usePureBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val baseColorScheme: ColorScheme = when (colorScheme) {
        // 0 = System Default (use dynamic on Android 12+ if available, otherwise default)
        0 -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
        // 1 = Dynamic (Material You - forced, only on Android 12+)
        1 -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            // Fallback to default if dynamic not available
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
        // 2-10 = Custom color schemes
        else -> {
            ColorSchemes[colorScheme]?.let { (light, dark) ->
                if (darkTheme) dark else light
            } ?: if (darkTheme) DarkColorScheme else LightColorScheme
        }
    }

    // Apply Pure Black background if enabled and in dark mode
    val finalColorScheme = if (usePureBlack && darkTheme) {
        baseColorScheme.copy(
            background = PureBlack,
            surface = PureBlack,
            surfaceVariant = Color(0xFF1A1A1A),
            surfaceContainer = Color(0xFF0D0D0D),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF262626),
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainerLowest = PureBlack,
        )
    } else {
        baseColorScheme
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = OtakuReaderTypography,
        content = content
    )
}
