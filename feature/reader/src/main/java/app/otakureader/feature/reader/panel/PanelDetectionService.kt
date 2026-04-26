package app.otakureader.feature.reader.panel

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import app.otakureader.feature.reader.model.ComicPanel
import app.otakureader.domain.model.ReadingDirection
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
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
    // android.util.LruCache is not thread-safe; all reads and writes are synchronized on this lock.
    private val cacheLock = Any()
    private val resultCache = LruCache<String, List<ComicPanel>>(CACHE_SIZE)

    /**
     * Detect panels in a page given its image URL.
     * Results are cached by URL — revisiting a page does not re-invoke ML Kit.
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
            val isEnabled = panelDetectionRepository.panelDetectionEnabled.first()
            if (!isEnabled || imageUrl == null) return@withContext emptyList()

            // Return cached result if available.
            synchronized(cacheLock) { resultCache.get(imageUrl) }?.let { return@withContext it }

            // Load bitmap from URL. allowHardware=false is required: hardware-backed bitmaps
            // cannot be read by ML Kit's image analyzer (pixel data is GPU-only).
            val bitmap = loadBitmapFromUrl(imageUrl) ?: return@withContext emptyList()

            val config = panelDetectionRepository.getPanelDetectionConfig(
                isRightToLeft = readingDirection == ReadingDirection.RTL
            )

            val panels = panelDetector.detectPanels(bitmap, config)

            if (!bitmap.isRecycled) bitmap.recycle()

            synchronized(cacheLock) { resultCache.put(imageUrl, panels) }
            panels
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Load bitmap from image URL using Coil.
     * Hardware bitmaps are explicitly disabled so ML Kit can read pixel data.
     */
    private suspend fun loadBitmapFromUrl(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .build()

            when (val result = imageLoader.execute(request)) {
                is SuccessResult -> result.image.toBitmap()
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val CACHE_SIZE = 50
    }
}
