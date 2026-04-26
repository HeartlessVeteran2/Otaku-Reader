package app.otakureader.feature.settings.delegate

import app.otakureader.core.preferences.ReaderPreferences
import app.otakureader.feature.reader.model.ImageQuality
import app.otakureader.feature.reader.repository.ReaderSettingsRepository
import app.otakureader.feature.settings.SettingsEffect
import app.otakureader.feature.settings.SettingsEvent
import app.otakureader.feature.settings.SettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReaderSettingsDelegate @Inject constructor(
    private val readerPreferences: ReaderPreferences,
    private val readerSettingsRepository: ReaderSettingsRepository,
) {

    fun startObserving(
        scope: CoroutineScope,
        updateState: ((SettingsState) -> SettingsState) -> Unit,
    ) {
        scope.launch {
            combine(
                readerPreferences.readerMode,
                readerPreferences.keepScreenOn,
                readerPreferences.fullscreen,
                readerPreferences.showContentInCutout,
                readerPreferences.showPageNumber,
            ) { readerMode, keepScreenOn, fullscreen, showCutout, showPageNum ->
                updateState { it.copy(
                    readerMode = readerMode,
                    keepScreenOn = keepScreenOn,
                    fullscreen = fullscreen,
                    showContentInCutout = showCutout,
                    showPageNumber = showPageNum,
                ) }
            }.collect { }
        }
        scope.launch {
            combine(
                readerPreferences.backgroundColor,
                readerPreferences.animatePageTransitions,
                readerPreferences.showReadingModeOverlay,
                readerPreferences.showTapZonesOverlay,
                readerPreferences.readerScale,
            ) { bgColor, animate, showMode, showZones, scale ->
                updateState { it.copy(
                    backgroundColor = bgColor,
                    animatePageTransitions = animate,
                    showReadingModeOverlay = showMode,
                    showTapZonesOverlay = showZones,
                    readerScale = scale,
                ) }
            }.collect { }
        }
        scope.launch {
            combine(
                readerPreferences.autoZoomWideImages,
                readerPreferences.tapZoneConfig,
                readerPreferences.invertTapZones,
                readerPreferences.volumeKeysEnabled,
                readerPreferences.volumeKeysInverted,
            ) { autoZoom, tapConfig, invertZones, volKeys, volInvert ->
                updateState { it.copy(
                    autoZoomWideImages = autoZoom,
                    tapZoneConfig = tapConfig,
                    invertTapZones = invertZones,
                    volumeKeysEnabled = volKeys,
                    volumeKeysInverted = volInvert,
                ) }
            }.collect { }
        }
        scope.launch {
            combine(
                readerPreferences.doubleTapAnimationSpeed,
                readerPreferences.showActionsOnLongTap,
                readerPreferences.savePagesToSeparateFolders,
                readerPreferences.webtoonSidePadding,
                readerPreferences.webtoonMenuHideSensitivity,
            ) { animSpeed, longTap, separateFolders, sidePadding, menuSensitivity ->
                updateState { it.copy(
                    doubleTapAnimationSpeed = animSpeed,
                    showActionsOnLongTap = longTap,
                    savePagesToSeparateFolders = separateFolders,
                    webtoonSidePadding = sidePadding,
                    webtoonMenuHideSensitivity = menuSensitivity,
                ) }
            }.collect { }
        }
        scope.launch {
            combine(
                readerPreferences.webtoonDoubleTapZoom,
                readerPreferences.webtoonDisableZoomOut,
                readerPreferences.einkFlashOnPageChange,
                readerPreferences.einkBlackAndWhite,
                readerPreferences.skipReadChapters,
            ) { dtZoom, disableZoomOut, einkFlash, einkBw, skipRead ->
                updateState { it.copy(
                    webtoonDoubleTapZoom = dtZoom,
                    webtoonDisableZoomOut = disableZoomOut,
                    einkFlashOnPageChange = einkFlash,
                    einkBlackAndWhite = einkBw,
                    skipReadChapters = skipRead,
                ) }
            }.collect { }
        }
        scope.launch {
            combine(
                readerPreferences.skipFilteredChapters,
                readerPreferences.skipDuplicateChapters,
                readerPreferences.alwaysShowChapterTransition,
                readerSettingsRepository.incognitoMode,
                readerSettingsRepository.preloadPagesBefore,
            ) { skipFiltered, skipDupes, showTransition, incognito, preloadBefore ->
                updateState { it.copy(
                    skipFilteredChapters = skipFiltered,
                    skipDuplicateChapters = skipDupes,
                    alwaysShowChapterTransition = showTransition,
                    incognitoMode = incognito,
                    preloadPagesBefore = preloadBefore,
                ) }
            }.collect { }
        }
        scope.launch {
            combine(
                readerSettingsRepository.preloadPagesAfter,
                readerSettingsRepository.cropBordersEnabled,
                readerSettingsRepository.imageQuality,
                readerSettingsRepository.dataSaverEnabled,
            ) { preloadAfter, cropBorders, imageQuality, dataSaver ->
                updateState { it.copy(
                    preloadPagesAfter = preloadAfter,
                    cropBordersEnabled = cropBorders,
                    imageQuality = imageQuality.name,
                    dataSaverEnabled = dataSaver,
                ) }
            }.collect { }
        }
    }

    suspend fun handleEvent(
        event: SettingsEvent,
        @Suppress("UNUSED_PARAMETER") sendEffect: suspend (SettingsEffect) -> Unit,
    ): Boolean = when (event) {
        is SettingsEvent.SetReaderMode -> { readerPreferences.setReaderMode(event.mode); true }
        is SettingsEvent.SetKeepScreenOn -> { readerPreferences.setKeepScreenOn(event.enabled); true }
        is SettingsEvent.SetFullscreen -> { readerPreferences.setFullscreen(event.enabled); true }
        is SettingsEvent.SetShowContentInCutout -> { readerPreferences.setShowContentInCutout(event.enabled); true }
        is SettingsEvent.SetShowPageNumber -> { readerPreferences.setShowPageNumber(event.enabled); true }
        is SettingsEvent.SetBackgroundColor -> { readerPreferences.setBackgroundColor(event.color); true }
        is SettingsEvent.SetAnimatePageTransitions -> { readerPreferences.setAnimatePageTransitions(event.enabled); true }
        is SettingsEvent.SetShowReadingModeOverlay -> { readerPreferences.setShowReadingModeOverlay(event.enabled); true }
        is SettingsEvent.SetShowTapZonesOverlay -> { readerPreferences.setShowTapZonesOverlay(event.enabled); true }
        is SettingsEvent.SetReaderScale -> { readerPreferences.setReaderScale(event.scale); true }
        is SettingsEvent.SetAutoZoomWideImages -> { readerPreferences.setAutoZoomWideImages(event.enabled); true }
        is SettingsEvent.SetTapZoneConfig -> { readerPreferences.setTapZoneConfig(event.config); true }
        is SettingsEvent.SetInvertTapZones -> { readerPreferences.setInvertTapZones(event.enabled); true }
        is SettingsEvent.SetVolumeKeysEnabled -> { readerPreferences.setVolumeKeysEnabled(event.enabled); true }
        is SettingsEvent.SetVolumeKeysInverted -> { readerPreferences.setVolumeKeysInverted(event.enabled); true }
        is SettingsEvent.SetDoubleTapAnimationSpeed -> { readerPreferences.setDoubleTapAnimationSpeed(event.speed); true }
        is SettingsEvent.SetShowActionsOnLongTap -> { readerPreferences.setShowActionsOnLongTap(event.enabled); true }
        is SettingsEvent.SetSavePagesToSeparateFolders -> { readerPreferences.setSavePagesToSeparateFolders(event.enabled); true }
        is SettingsEvent.SetWebtoonSidePadding -> { readerPreferences.setWebtoonSidePadding(event.padding); true }
        is SettingsEvent.SetWebtoonMenuHideSensitivity -> { readerPreferences.setWebtoonMenuHideSensitivity(event.sensitivity); true }
        is SettingsEvent.SetWebtoonDoubleTapZoom -> { readerPreferences.setWebtoonDoubleTapZoom(event.enabled); true }
        is SettingsEvent.SetWebtoonDisableZoomOut -> { readerPreferences.setWebtoonDisableZoomOut(event.enabled); true }
        is SettingsEvent.SetEinkFlashOnPageChange -> { readerPreferences.setEinkFlashOnPageChange(event.enabled); true }
        is SettingsEvent.SetEinkBlackAndWhite -> { readerPreferences.setEinkBlackAndWhite(event.enabled); true }
        is SettingsEvent.SetSkipReadChapters -> { readerPreferences.setSkipReadChapters(event.enabled); true }
        is SettingsEvent.SetSkipFilteredChapters -> { readerPreferences.setSkipFilteredChapters(event.enabled); true }
        is SettingsEvent.SetSkipDuplicateChapters -> { readerPreferences.setSkipDuplicateChapters(event.enabled); true }
        is SettingsEvent.SetAlwaysShowChapterTransition -> { readerPreferences.setAlwaysShowChapterTransition(event.enabled); true }
        is SettingsEvent.SetIncognitoMode -> { readerSettingsRepository.setIncognitoMode(event.enabled); true }
        is SettingsEvent.SetPreloadPagesBefore -> { readerSettingsRepository.setPreloadPagesBefore(event.count); true }
        is SettingsEvent.SetPreloadPagesAfter -> { readerSettingsRepository.setPreloadPagesAfter(event.count); true }
        is SettingsEvent.SetCropBordersEnabled -> { readerSettingsRepository.setCropBordersEnabled(event.enabled); true }
        is SettingsEvent.SetImageQuality -> {
            val normalizedInput = event.quality.trim().uppercase()
            val quality = ImageQuality.entries.firstOrNull {
                it.name.equals(normalizedInput, ignoreCase = true)
            } ?: ImageQuality.ORIGINAL
            readerSettingsRepository.setImageQuality(quality)
            true
        }
        is SettingsEvent.SetDataSaverEnabled -> { readerSettingsRepository.setDataSaverEnabled(event.enabled); true }
        else -> false
    }
}
