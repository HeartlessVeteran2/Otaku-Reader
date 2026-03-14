package app.otakureader.feature.reader.panel

import android.content.Context
import android.graphics.Bitmap
import app.otakureader.feature.reader.model.ComicPanel
import app.otakureader.feature.reader.model.ReadingDirection
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for detecting panels in manga pages
 */
@Singleton
class PanelDetectionService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
    private val panelDetector: PanelDetector,
    private val panelDetectionRepository: PanelDetectionRepository
) {
    /**
     * Detect panels in a page given its image URL
     *
     * @param imageUrl URL of the page image
     * @param readingDirection Reading direction (RTL for manga, LTR for comics)
     * @return List of detected panels, or empty list if detection fails or is disabled
     */
    suspend fun detectPanelsFromUrl(
        imageUrl: String?,
        readingDirection: ReadingDirection = ReadingDirection.RTL
    ): List<ComicPanel> = withContext(Dispatchers.IO) {
        try {
            // Check if panel detection is enabled
            val isEnabled = panelDetectionRepository.panelDetectionEnabled.first()

            if (!isEnabled || imageUrl == null) {
                return@withContext emptyList()
            }

            // Load bitmap from URL using Coil
            val bitmap = loadBitmapFromUrl(imageUrl) ?: return@withContext emptyList()

            // Get panel detection config
            val config = panelDetectionRepository.getPanelDetectionConfig(
                isRightToLeft = readingDirection == ReadingDirection.RTL
            )

            // Detect panels
            val panels = panelDetector.detectPanels(bitmap, config)

            // Clean up bitmap if needed
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }

            panels
        } catch (e: Exception) {
            // Log error and return empty list - graceful fallback
            emptyList()
        }
    }

    /**
     * Load bitmap from image URL using Coil
     */
    private suspend fun loadBitmapFromUrl(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .build()

            when (val result = imageLoader.execute(request)) {
                is SuccessResult -> result.image.toBitmap()
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
