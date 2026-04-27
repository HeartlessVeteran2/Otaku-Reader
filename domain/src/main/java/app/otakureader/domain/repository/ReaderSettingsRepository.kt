package app.otakureader.domain.repository

import app.otakureader.domain.model.ColorFilterMode
import app.otakureader.domain.model.ImageQuality
import app.otakureader.domain.model.ReaderMode
import app.otakureader.domain.model.ReadingDirection
import kotlinx.coroutines.flow.Flow

/**
 * Read-only view of persisted reader settings consumed by the feature layer.
 *
 * All mutations remain in the data-layer implementation; delegates in
 * `:feature:reader` only need to observe settings, not write them.
 */
interface ReaderSettingsRepository {

    val readerMode: Flow<ReaderMode>
    val readingDirection: Flow<ReadingDirection>
    val brightness: Flow<Float>
    val keepScreenOn: Flow<Boolean>
    val showPageNumber: Flow<Boolean>
    val volumeKeysEnabled: Flow<Boolean>
    val volumeKeysInverted: Flow<Boolean>
    val fullscreen: Flow<Boolean>
    val incognitoMode: Flow<Boolean>
    val colorFilterMode: Flow<ColorFilterMode>
    val customTintColor: Flow<Int>
    val cropBordersEnabled: Flow<Boolean>
    val imageQuality: Flow<ImageQuality>
    val dataSaverEnabled: Flow<Boolean>
    val showReadingTimer: Flow<Boolean>
    val showBatteryTime: Flow<Boolean>
    val preloadPagesBefore: Flow<Int>
    val preloadPagesAfter: Flow<Int>
    val smartPrefetchEnabled: Flow<Boolean>
    val prefetchStrategyOrdinal: Flow<Int>
    val adaptiveLearningEnabled: Flow<Boolean>
    val prefetchAdjacentChapters: Flow<Boolean>
    val prefetchOnlyOnWiFi: Flow<Boolean>
    val showContentInCutout: Flow<Boolean>
    val backgroundColor: Flow<Int>
    val animatePageTransitions: Flow<Boolean>
    val showReadingModeOverlay: Flow<Boolean>
    val showTapZonesOverlay: Flow<Boolean>
    val readerScale: Flow<Float>
    val autoZoomWideImages: Flow<Boolean>
    val invertTapZones: Flow<Boolean>
    val webtoonSidePadding: Flow<Int>
    val webtoonGapDp: Flow<Int>
    val webtoonMenuHideSensitivity: Flow<Float>
    val webtoonDoubleTapZoom: Flow<Boolean>
    val webtoonDisableZoomOut: Flow<Boolean>
    val einkFlashOnPageChange: Flow<Boolean>
    val einkBlackAndWhite: Flow<Boolean>
    val skipReadChapters: Flow<Boolean>
    val skipFilteredChapters: Flow<Boolean>
    val skipDuplicateChapters: Flow<Boolean>
    val alwaysShowChapterTransition: Flow<Boolean>
    val showActionsOnLongTap: Flow<Boolean>
    val savePagesToSeparateFolders: Flow<Boolean>

    companion object {
        const val DEFAULT_PRELOAD_PAGES = 3
    }
}
