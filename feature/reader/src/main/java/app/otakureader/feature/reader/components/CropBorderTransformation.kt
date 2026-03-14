package app.otakureader.feature.reader.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import app.otakureader.feature.reader.model.CropConfig
import coil3.size.Size
import coil3.transform.Transformation

/**
 * Coil [Transformation] that removes white and/or black borders from manga page images.
 *
 * The algorithm scans rows and columns from the edges inward. A row or column is
 * considered a border if the fraction of "border-colored" pixels meets the [CropConfig.threshold].
 * A pixel is "white-border" when all RGB channels are >= 240, and "black-border" when all are <= 15.
 *
 * The final crop is clamped between [CropConfig.minCropPercent] and [CropConfig.maxCropPercent]
 * of the respective dimension, ensuring meaningful content is never removed.
 */
class CropBorderTransformation(private val config: CropConfig = CropConfig()) : Transformation() {

    override val cacheKey: String =
        "CropBorderTransformation-${config.threshold}-${config.minCropPercent}" +
            "-${config.maxCropPercent}-${config.detectWhiteBorders}-${config.detectBlackBorders}" +
            "-${config.enabled}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (!config.enabled) return input

        val width = input.width
        val height = input.height
        if (width == 0 || height == 0) return input

        // If neither white nor black border detection is enabled, there is nothing to do.
        if (!config.detectWhiteBorders && !config.detectBlackBorders) return input

        // Helper to check whether a pixel is considered part of the border (white and/or black).
        fun isBorderPixel(color: Int): Boolean {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            val isWhite = config.detectWhiteBorders && r >= 240 && g >= 240 && b >= 240
            val isBlack = config.detectBlackBorders && r <= 15 && g <= 15 && b <= 15

            return isWhite || isBlack
        }

        // Helper to determine whether a full row/column buffer qualifies as a border line.
        fun isBorderLine(buffer: IntArray): Boolean {
            if (buffer.isEmpty()) return false
            var borderCount = 0
            for (pixel in buffer) {
                if (isBorderPixel(pixel)) {
                    borderCount++
                }
            }
            val fraction = borderCount.toFloat() / buffer.size.toFloat()
            return fraction >= config.threshold
        }

        // Reusable buffers for scanning edges without loading the entire bitmap into memory.
        val rowBuffer = IntArray(width)
        val colBuffer = IntArray(height)

        // Scan from the top edge downward.
        val top = run {
            var t = 0
            val maxTop = (height * config.maxCropPercent).toInt().coerceAtMost(height)
            while (t < maxTop) {
                input.getPixels(rowBuffer, 0, width, 0, t, width, 1)
                if (!isBorderLine(rowBuffer)) {
                    break
                }
                t++
            }
            t
        }

        // Scan from the bottom edge upward.
        val bottom = run {
            var b = 0
            val maxBottom = (height * config.maxCropPercent).toInt().coerceAtMost(height - top)
            while (b < maxBottom) {
                val y = height - 1 - b
                if (y <= top) break
                input.getPixels(rowBuffer, 0, width, 0, y, width, 1)
                if (!isBorderLine(rowBuffer)) {
                    break
                }
                b++
            }
            b
        }

        // Scan from the left edge rightward.
        val left = run {
            var l = 0
            val maxLeft = (width * config.maxCropPercent).toInt().coerceAtMost(width)
            while (l < maxLeft) {
                input.getPixels(colBuffer, 0, 1, l, 0, 1, height)
                if (!isBorderLine(colBuffer)) {
                    break
                }
                l++
            }
            l
        }

        // Scan from the right edge leftward.
        val right = run {
            var r = 0
            val maxRight = (width * config.maxCropPercent).toInt().coerceAtMost(width - left)
            while (r < maxRight) {
                val x = width - 1 - r
                if (x <= left) break
                input.getPixels(colBuffer, 0, 1, x, 0, 1, height)
                if (!isBorderLine(colBuffer)) {
                    break
                }
                r++
            }
            r
        }

        // Clamp total crop amounts per dimension to configured limits
        val maxHeightCrop = (height * config.maxCropPercent).toInt()
        val maxWidthCrop = (width * config.maxCropPercent).toInt()

        val rawVertical = top + bottom
        val rawHorizontal = left + right

        val verticalScale = when {
            rawVertical <= 0 -> 0f
            rawVertical > maxHeightCrop -> maxHeightCrop.toFloat() / rawVertical.toFloat()
            else -> 1f
        }
        val horizontalScale = when {
            rawHorizontal <= 0 -> 0f
            rawHorizontal > maxWidthCrop -> maxWidthCrop.toFloat() / rawHorizontal.toFloat()
            else -> 1f
        }

        val clampedTop = (top * verticalScale).toInt()
        val clampedBottom = (bottom * verticalScale).toInt()
        val clampedLeft = (left * horizontalScale).toInt()
        val clampedRight = (right * horizontalScale).toInt()

        // Skip trivial crops (smaller than minCropPercent of the full dimension)
        val minHeightCrop = (height * config.minCropPercent).toInt()
        val minWidthCrop = (width * config.minCropPercent).toInt()
        val effectiveTop = if (clampedTop + clampedBottom >= minHeightCrop) clampedTop else 0
        val effectiveBottom = if (clampedTop + clampedBottom >= minHeightCrop) clampedBottom else 0
        val effectiveLeft = if (clampedLeft + clampedRight >= minWidthCrop) clampedLeft else 0
        val effectiveRight = if (clampedLeft + clampedRight >= minWidthCrop) clampedRight else 0

        // If nothing to crop, return original
        if (effectiveTop == 0 && effectiveBottom == 0 &&
            effectiveLeft == 0 && effectiveRight == 0
        ) {
            return input
        }

        val newWidth = width - effectiveLeft - effectiveRight
        val newHeight = height - effectiveTop - effectiveBottom

        // Validate crop rectangle is within bitmap bounds and non-empty.
        if (effectiveLeft < 0 || effectiveTop < 0 ||
            effectiveLeft >= width || effectiveTop >= height ||
            newWidth <= 0 || newHeight <= 0
        ) {
            return input
        }

        // Return the same bitmap if nothing actually changed
        if (newWidth == width && newHeight == height) return input

        // Detach from the source bitmap's pixel buffer to avoid sharing memory with `input`,
        // which may be reused or recycled by Coil after this transformation.
        val outputConfig = input.config ?: Bitmap.Config.ARGB_8888
        val output = Bitmap.createBitmap(newWidth, newHeight, outputConfig)
        val canvas = Canvas(output)
        val srcRect = Rect(
            effectiveLeft,
            effectiveTop,
            effectiveLeft + newWidth,
            effectiveTop + newHeight
        )
        val dstRect = Rect(0, 0, newWidth, newHeight)
        canvas.drawBitmap(input, srcRect, dstRect, null)

        return output
    }
}
