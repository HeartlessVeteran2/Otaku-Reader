package app.otakureader.feature.reader.viewmodel.delegate

import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.PrefetchStrategy
import app.otakureader.feature.reader.model.ColorFilterMode
import app.otakureader.feature.reader.model.ImageQuality
import app.otakureader.feature.reader.model.ReaderMode
import app.otakureader.feature.reader.model.ReadingDirection
import app.otakureader.feature.reader.repository.ReaderSettingsRepository
import app.otakureader.feature.reader.viewmodel.ReaderState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Loads all reader settings from [ReaderSettingsRepository] in parallel and applies
 * them to the reader state, factoring in per-manga overrides.
 *
 * Extracted from [app.otakureader.feature.reader.viewmodel.UltimateReaderViewModel]
 * to keep the ViewModel focused on event routing and state aggregation.
 *
 * Each `.first()` is a separate suspend point; running them concurrently shaves
 * 50–200 ms from cold reader open time.
 */
class ReaderSettingsLoaderDelegate @Inject constructor(
    private val settingsRepository: ReaderSettingsRepository,
    private val prefetchDelegate: ReaderPrefetchDelegate,
) {

    /**
     * Loads all reader settings and returns a copy of [current] with the loaded
     * values applied. Per-manga overrides on [manga] take precedence over global
     * settings for the fields that support them.
     *
     * Side effect: populates the cached fields on [prefetchDelegate]. The
     * delegate's cache is the canonical place those values live now that the
     * prefetch concern has been split out of the ViewModel.
     */
    suspend fun load(current: ReaderState, manga: Manga?): ReaderState = coroutineScope {
        val modeD = async { settingsRepository.readerMode.first() }
        val brightnessD = async { settingsRepository.brightness.first() }
        val keepScreenOnD = async { settingsRepository.keepScreenOn.first() }
        val showPageNumberD = async { settingsRepository.showPageNumber.first() }
        val directionD = async { settingsRepository.readingDirection.first() }
        val volumeKeysEnabledD = async { settingsRepository.volumeKeysEnabled.first() }
        val volumeKeysInvertedD = async { settingsRepository.volumeKeysInverted.first() }
        val fullscreenD = async { settingsRepository.fullscreen.first() }
        val incognitoModeD = async { settingsRepository.incognitoMode.first() }
        val colorFilterModeD = async { settingsRepository.colorFilterMode.first() }
        val customTintColorD = async { settingsRepository.customTintColor.first() }
        val cropBordersEnabledD = async {
            try { settingsRepository.cropBordersEnabled.first() } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                false
            }
        }
        val imageQualityD = async {
            try { settingsRepository.imageQuality.first() } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ImageQuality.ORIGINAL
            }
        }
        val dataSaverEnabledD = async {
            try { settingsRepository.dataSaverEnabled.first() } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                false
            }
        }
        val showReadingTimerD = async {
            try { settingsRepository.showReadingTimer.first() } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                false
            }
        }
        val showBatteryTimeD = async {
            try { settingsRepository.showBatteryTime.first() } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                false
            }
        }
        val preloadBeforeD = async {
            try { settingsRepository.preloadPagesBefore.first() } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ReaderSettingsRepository.DEFAULT_PRELOAD_PAGES
            }
        }
        val preloadAfterD = async {
            try { settingsRepository.preloadPagesAfter.first() } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                ReaderSettingsRepository.DEFAULT_PRELOAD_PAGES
            }
        }
        val smartPrefetchEnabledD = async {
            try { settingsRepository.smartPrefetchEnabled.first() } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                true
            }
        }
        val prefetchStrategyOrdinalD = async {
            try { settingsRepository.prefetchStrategyOrdinal.first() } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                -1
            }
        }
        val adaptiveLearningEnabledD = async {
            try { settingsRepository.adaptiveLearningEnabled.first() } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                true
            }
        }
        val prefetchAdjacentChaptersD = async {
            try { settingsRepository.prefetchAdjacentChapters.first() } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                false
            }
        }
        val prefetchOnlyOnWiFiD = async {
            try { settingsRepository.prefetchOnlyOnWiFi.first() } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                false
            }
        }
        val showContentInCutoutD = async { settingsRepository.showContentInCutout.first() }
        val backgroundColorD = async { settingsRepository.backgroundColor.first() }
        val animatePageTransitionsD = async { settingsRepository.animatePageTransitions.first() }
        val showReadingModeOverlayD = async { settingsRepository.showReadingModeOverlay.first() }
        val showTapZonesOverlayD = async { settingsRepository.showTapZonesOverlay.first() }
        val readerScaleD = async { settingsRepository.readerScale.first() }
        val autoZoomWideImagesD = async { settingsRepository.autoZoomWideImages.first() }
        val invertTapZonesD = async { settingsRepository.invertTapZones.first() }
        val webtoonSidePaddingD = async { settingsRepository.webtoonSidePadding.first() }
        val webtoonGapDpD = async { settingsRepository.webtoonGapDp.first() }
        val webtoonMenuHideSensitivityD = async { settingsRepository.webtoonMenuHideSensitivity.first() }
        val webtoonDoubleTapZoomD = async { settingsRepository.webtoonDoubleTapZoom.first() }
        val webtoonDisableZoomOutD = async { settingsRepository.webtoonDisableZoomOut.first() }
        val einkFlashOnPageChangeD = async { settingsRepository.einkFlashOnPageChange.first() }
        val einkBlackAndWhiteD = async { settingsRepository.einkBlackAndWhite.first() }
        val skipReadChaptersD = async { settingsRepository.skipReadChapters.first() }
        val skipFilteredChaptersD = async { settingsRepository.skipFilteredChapters.first() }
        val skipDuplicateChaptersD = async { settingsRepository.skipDuplicateChapters.first() }
        val alwaysShowChapterTransitionD = async { settingsRepository.alwaysShowChapterTransition.first() }
        val showActionsOnLongTapD = async { settingsRepository.showActionsOnLongTap.first() }
        val savePagesToSeparateFoldersD = async { settingsRepository.savePagesToSeparateFolders.first() }

        val mode = modeD.await()
        val direction = directionD.await()
        val colorFilterMode = colorFilterModeD.await()
        val customTintColor = customTintColorD.await()

        // Populate prefetch-delegate cache.
        prefetchDelegate.cachedPreloadBefore = preloadBeforeD.await()
        prefetchDelegate.cachedPreloadAfter = preloadAfterD.await()
        prefetchDelegate.cachedSmartPrefetchEnabled = smartPrefetchEnabledD.await()
        val prefetchOrdinal = prefetchStrategyOrdinalD.await()
        prefetchDelegate.cachedPrefetchStrategy =
            if (prefetchOrdinal >= 0) PrefetchStrategy.fromOrdinal(prefetchOrdinal) else PrefetchStrategy.Balanced
        prefetchDelegate.cachedAdaptiveLearningEnabled = adaptiveLearningEnabledD.await()
        prefetchDelegate.cachedPrefetchAdjacentChapters = prefetchAdjacentChaptersD.await()
        prefetchDelegate.cachedPrefetchOnlyOnWiFi = prefetchOnlyOnWiFiD.await()

        // Apply per-manga overrides if they exist (#260).
        val effectiveMode = manga?.readerMode?.let { ReaderMode.entries.getOrNull(it) } ?: mode
        val effectiveDirection = manga?.readerDirection?.let {
            if (it == 0) ReadingDirection.LTR else ReadingDirection.RTL
        } ?: direction
        val effectiveColorFilter = manga?.readerColorFilter?.let {
            ColorFilterMode.entries.getOrNull(it)
        } ?: colorFilterMode
        val effectiveTintColor = manga?.readerCustomTintColor ?: customTintColor

        current.copy(
            mode = effectiveMode,
            brightness = brightnessD.await(),
            keepScreenOn = keepScreenOnD.await(),
            showPageNumber = showPageNumberD.await(),
            readingDirection = effectiveDirection,
            volumeKeysEnabled = volumeKeysEnabledD.await(),
            volumeKeysInverted = volumeKeysInvertedD.await(),
            isFullscreen = fullscreenD.await(),
            incognitoMode = incognitoModeD.await(),
            colorFilterMode = effectiveColorFilter,
            customTintColor = effectiveTintColor,
            showReadingTimer = showReadingTimerD.await(),
            showBatteryTime = showBatteryTimeD.await(),
            cropBordersEnabled = cropBordersEnabledD.await(),
            imageQuality = imageQualityD.await(),
            dataSaverEnabled = dataSaverEnabledD.await(),
            showContentInCutout = showContentInCutoutD.await(),
            backgroundColor = backgroundColorD.await(),
            animatePageTransitions = animatePageTransitionsD.await(),
            showReadingModeOverlay = showReadingModeOverlayD.await(),
            showTapZonesOverlay = showTapZonesOverlayD.await(),
            readerScale = readerScaleD.await(),
            autoZoomWideImages = autoZoomWideImagesD.await(),
            invertTapZones = invertTapZonesD.await(),
            webtoonSidePadding = webtoonSidePaddingD.await(),
            webtoonGapDp = webtoonGapDpD.await(),
            webtoonMenuHideSensitivity = webtoonMenuHideSensitivityD.await(),
            webtoonDoubleTapZoom = webtoonDoubleTapZoomD.await(),
            webtoonDisableZoomOut = webtoonDisableZoomOutD.await(),
            einkFlashOnPageChange = einkFlashOnPageChangeD.await(),
            einkBlackAndWhite = einkBlackAndWhiteD.await(),
            skipReadChapters = skipReadChaptersD.await(),
            skipFilteredChapters = skipFilteredChaptersD.await(),
            skipDuplicateChapters = skipDuplicateChaptersD.await(),
            alwaysShowChapterTransition = alwaysShowChapterTransitionD.await(),
            showActionsOnLongTap = showActionsOnLongTapD.await(),
            savePagesToSeparateFolders = savePagesToSeparateFoldersD.await(),
        )
    }
}
