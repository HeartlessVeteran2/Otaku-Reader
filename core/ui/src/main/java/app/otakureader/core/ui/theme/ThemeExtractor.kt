package app.otakureader.core.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents a dynamic color theme extracted from an image.
 */
data class DynamicThemeColors(
    val primary: Int? = null,
    val primaryDark: Int? = null,
    val secondary: Int? = null,
    val background: Int? = null,
    val surface: Int? = null,
    val onPrimary: Int? = null,
    val onSurface: Int? = null,
    val isDark: Boolean = false
)

/**
 * Utility class for extracting color themes from manga cover images.
 * Uses Android's Palette API for dominant color extraction.
 */
@Singleton
class ThemeExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Extract dynamic theme colors from a cover image URL.
     * Returns null if the image can't be loaded or no suitable colors found.
     */
    suspend fun extractThemeFromCover(coverUrl: String?): DynamicThemeColors? = withContext(Dispatchers.Default) {
        if (coverUrl.isNullOrBlank()) return@withContext null

        try {
            val request = ImageRequest.Builder(context)
                .data(coverUrl)
                .size(200, 300) // Small size for faster processing
                .build()

            val result = context.imageLoader.execute(request)
            if (result !is SuccessResult) return@withContext null

            val bitmap = (result.image as? android.graphics.drawable.BitmapDrawable)?.bitmap
                ?: return@withContext null

            extractColors(bitmap)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract colors from a bitmap using the Palette API.
     */
    private fun extractColors(bitmap: Bitmap): DynamicThemeColors? {
        val palette = Palette.from(bitmap).generate()

        // Try to get vibrant or dominant swatches
        val primary = palette.vibrantSwatch?.rgb
            ?: palette.lightVibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb

        val secondary = palette.darkVibrantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: palette.lightMutedSwatch?.rgb

        val background = palette.lightMutedSwatch?.rgb
            ?: palette.dominantSwatch?.rgb?.let { blendWithWhite(it) }
            ?: 0xFF_FFFFF.toInt()

        val surface = palette.mutedSwatch?.rgb
            ?: blendWithWhite(background)

        if (primary == null && secondary == null) return null

        // Determine if theme should be dark based on background brightness
        val isDark = calculateLuminance(background) < 0.5

        return DynamicThemeColors(
            primary = primary,
            primaryDark = if (isDark) primary else secondary,
            secondary = secondary,
            background = background,
            surface = surface,
            onPrimary = palette.vibrantSwatch?.bodyTextColor,
            onSurface = palette.dominantSwatch?.bodyTextColor,
            isDark = isDark
        )
    }

    /**
     * Blend a color with white to create a lighter variant.
     */
    private fun blendWithWhite(color: Int, ratio: Float = 0.7f): Int {
        val alpha = (color shr 24) and 0xFF
        val red = ((color shr 16) and 0xFF) * (1 - ratio) + 255 * ratio
        val green = ((color shr 8) and 0xFF) * (1 - ratio) + 255 * ratio
        val blue = (color and 0xFF) * (1 - ratio) + 255 * ratio
        return (alpha shl 24) or (red.toInt() shl 16) or (green.toInt() shl 8) or blue.toInt()
    }

    /**
     * Calculate luminance of a color (0.0 = black, 1.0 = white).
     */
    private fun calculateLuminance(color: Int): Double {
        val red = ((color shr 16) and 0xFF) / 255.0
        val green = ((color shr 8) and 0xFF) / 255.0
        val blue = (color and 0xFF) / 255.0

        val r = if (red <= 0.03928) red / 12.92 else Math.pow((red + 0.055) / 1.055, 2.4)
        val g = if (green <= 0.03928) green / 12.92 else Math.pow((green + 0.055) / 1.055, 2.4)
        val b = if (blue <= 0.03928) blue / 12.92 else Math.pow((blue + 0.055) / 1.055, 2.4)

        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
