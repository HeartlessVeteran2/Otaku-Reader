package app.otakureader.feature.reader.panel

import android.content.Context
import app.otakureader.feature.reader.model.PanelAnalysisException
import app.otakureader.feature.reader.model.PanelAnalysisRequest
import app.otakureader.feature.reader.model.PanelAnalysisResultWrapper
import app.otakureader.domain.model.ReadingDirection
import coil3.ImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op implementation of PanelAnalyzer for FOSS builds.
 *
 * Panel-aware reading requires the Gemini Vision API, which is excluded from
 * FOSS builds. This stub ensures the app compiles and gracefully handles requests
 * by returning "not available" errors.
 */
@Singleton
class PanelAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
    private val cacheService: PanelCacheService
) {
    /**
     * No-op initialization (AI is not available in FOSS builds).
     */
    fun initialize(apiKey: String) {
        // No-op: Gemini client is not available in FOSS builds
    }

    /**
     * Always returns false in FOSS builds.
     */
    fun isInitialized(): Boolean = false

    /**
     * Always returns error indicating AI is not available.
     */
    suspend fun analyzePage(
        request: PanelAnalysisRequest,
        useCache: Boolean = true,
        timeoutMillis: Long = 45_000L
    ): PanelAnalysisResultWrapper {
        return PanelAnalysisResultWrapper.Error(
            PanelAnalysisException.NotAvailableInFoss()
        )
    }

    /**
     * Simplified API - always returns error.
     */
    suspend fun analyzePage(
        imageUrl: String,
        readingDirection: ReadingDirection = ReadingDirection.RTL
    ): PanelAnalysisResultWrapper {
        return PanelAnalysisResultWrapper.Error(
            PanelAnalysisException.NotAvailableInFoss()
        )
    }
}
