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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme()
private val DarkColorScheme = darkColorScheme()

/** Color scheme ID for the user-defined custom accent color. */
const val COLOR_SCHEME_CUSTOM_ACCENT = 11

/**
 * Otaku Reader app theme.
 * Supports dynamic color (Material You) on Android 12+, custom color schemes,
 * Pure Black (AMOLED) dark mode, custom accent color, and manual dark/light mode.
 *
 * @param darkTheme Whether to use dark theme. Defaults to system setting.
 * @param colorScheme Color scheme selection (0=System Default, 1=Dynamic, 2-10=Custom schemes, [COLOR_SCHEME_CUSTOM_ACCENT]=Custom accent)
 * @param usePureBlack Whether to use Pure Black (#000000) background in dark mode (AMOLED)
 * @param customAccentColor ARGB Long used when [colorScheme] == [COLOR_SCHEME_CUSTOM_ACCENT]
 */
@Composable
fun OtakuReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorScheme: Int = 0,
    usePureBlack: Boolean = false,
    customAccentColor: Long = 0xFF1976D2L,
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
        // COLOR_SCHEME_CUSTOM_ACCENT = Custom accent color
        COLOR_SCHEME_CUSTOM_ACCENT -> {
            val accent = Color(customAccentColor.toInt())
            if (darkTheme) buildCustomDarkScheme(accent) else buildCustomLightScheme(accent)
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

/**
 * Builds a light [ColorScheme] using the given [accent] as primary color.
 * Generates complementary container/surface colors for a cohesive Material 3 theme.
 */
private fun buildCustomLightScheme(accent: Color): ColorScheme {
    val argb = accent.toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    // Lighter variant for containers
    val containerColor = Color(
        red = (r + (255 - r) * 0.7f).toInt().coerceIn(0, 255),
        green = (g + (255 - g) * 0.7f).toInt().coerceIn(0, 255),
        blue = (b + (255 - b) * 0.7f).toInt().coerceIn(0, 255)
    )
    // Darker variant for onPrimaryContainer
    val onContainerColor = Color(
        red = (r * 0.3f).toInt().coerceIn(0, 255),
        green = (g * 0.3f).toInt().coerceIn(0, 255),
        blue = (b * 0.3f).toInt().coerceIn(0, 255)
    )
    return lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = containerColor,
        onPrimaryContainer = onContainerColor,
        secondary = accent,
        onSecondary = Color.White,
        secondaryContainer = containerColor.copy(alpha = 0.5f),
        onSecondaryContainer = onContainerColor,
    )
}

/**
 * Builds a dark [ColorScheme] using the given [accent] as primary color.
 * Generates complementary container/surface colors for a cohesive Material 3 dark theme.
 */
private fun buildCustomDarkScheme(accent: Color): ColorScheme {
    val argb = accent.toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    // Lighter tint for dark mode primary
    val lightTint = Color(
        red = (r + (255 - r) * 0.4f).toInt().coerceIn(0, 255),
        green = (g + (255 - g) * 0.4f).toInt().coerceIn(0, 255),
        blue = (b + (255 - b) * 0.4f).toInt().coerceIn(0, 255)
    )
    // Darker container color for dark mode
    val containerColor = Color(
        red = (r * 0.4f).toInt().coerceIn(0, 255),
        green = (g * 0.4f).toInt().coerceIn(0, 255),
        blue = (b * 0.4f).toInt().coerceIn(0, 255)
    )
    return darkColorScheme(
        primary = lightTint,
        onPrimary = Color(
            red = (r * 0.2f).toInt().coerceIn(0, 255),
            green = (g * 0.2f).toInt().coerceIn(0, 255),
            blue = (b * 0.2f).toInt().coerceIn(0, 255)
        ),
        primaryContainer = containerColor,
        onPrimaryContainer = lightTint,
        secondary = lightTint,
        onSecondary = Color(0xFF1A1A1A),
        secondaryContainer = containerColor.copy(alpha = 0.5f),
        onSecondaryContainer = lightTint,
    )
}
