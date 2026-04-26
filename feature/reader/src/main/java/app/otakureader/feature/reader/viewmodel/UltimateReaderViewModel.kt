package app.otakureader.feature.reader.viewmodel

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.data.loader.PageLoader
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.feature.reader.model.ColorFilterMode
import app.otakureader.feature.reader.model.ImageQuality
import app.otakureader.feature.reader.model.ReaderMode
import app.otakureader.feature.reader.model.ReaderPage
import app.otakureader.feature.reader.model.ReadingDirection
import app.otakureader.feature.reader.repository.ReaderSettingsRepository
import app.otakureader.feature.reader.prefetch.ReadingBehaviorTracker
import app.otakureader.domain.model.PageNavigationEvent
import app.otakureader.domain.model.PrefetchStrategy
import app.otakureader.data.worker.RecordReadingHistoryWorker
import app.otakureader.feature.reader.viewmodel.delegate.ReaderDiscordDelegate
import app.otakureader.feature.reader.viewmodel.delegate.ReaderDownloadAheadDelegate
import app.otakureader.feature.reader.viewmodel.delegate.ReaderPanelDetectionDelegate
import app.otakureader.feature.reader.viewmodel.delegate.ReaderPrefetchDelegate
import app.otakureader.feature.reader.viewmodel.delegate.ReaderSfxDelegate
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import app.otakureader.sourceapi.SourceChapter

/**
 * Ultimate ViewModel for the Reader feature.
 * Manages all reader modes, page preloading, progress saving, and settings.
 * Integrates with existing Otaku Reader domain repositories.
 */
@HiltViewModel
class UltimateReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val sourceRepository: SourceRepository,
    private val settingsRepository: ReaderSettingsRepository,
    private val pageLoader: PageLoader,
    private val behaviorTracker: ReadingBehaviorTracker,
    private val sfxDelegate: ReaderSfxDelegate,
    private val discordDelegate: ReaderDiscordDelegate,
    private val panelDelegate: ReaderPanelDetectionDelegate,
    private val prefetchDelegate: ReaderPrefetchDelegate,
    private val downloadAheadDelegate: ReaderDownloadAheadDelegate,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val mangaId: Long = checkNotNull(savedStateHandle["mangaId"])
    private val chapterId: Long = checkNotNull(savedStateHandle["chapterId"])

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _effect = Channel<ReaderEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var currentManga: Manga? = null
    private var currentChapter: Chapter? = null
    private var hasTriggeredDeletion = false

    private var autoSaveJob: Job? = null

    /** Timestamp when last page change occurred, for tracking page duration. */
    private var lastPageChangeMs: Long = SystemClock.elapsedRealtime()

    /**
     * Wall-clock timestamp captured at ViewModel creation, used as the `readAt` value when
     * recording history entries (epoch millis, suitable for display and comparison).
     */
    internal val sessionReadAt: Long = System.currentTimeMillis()

    /**
     * Monotonic timestamp captured at ViewModel creation, used for computing reading session
     * duration. Using [SystemClock.elapsedRealtime] (not wall-clock time) ensures the measured
     * duration is unaffected by clock adjustments, timezone changes, or daylight-saving shifts.
     * Made internal for [ReadingTimerOverlay] access within the feature:reader module.
     * This timestamp is never updated and represents the start of the reading session.
     */
    internal val sessionStartMs: Long = SystemClock.elapsedRealtime()

    init {
        loadSettings()
        loadChapter()
        discordDelegate.startObserving(
            scope = viewModelScope,
            getCurrentManga = { currentManga },
            getCurrentChapter = { currentChapter },
            getState = { _state.value },
        )
        sfxDelegate.observeSettings(viewModelScope) { _state.update(it) }
        observeSettingsWriteFailures()
    }

    private fun recordHistoryOpen() {
        // Reset page change timestamp when the chapter is opened so that
        // the first recorded page duration does not include chapter load time.
        lastPageChangeMs = SystemClock.elapsedRealtime()

        viewModelScope.launch {
            // Resolve the incognito flag directly from settings to avoid races with loadSettings()
            val isIncognito = runCatching {
                // Assuming settingsRepository exposes a Flow of settings
                settingsRepository.incognitoMode.first()
            }.getOrElse {
                // Fall back to the current state if settings cannot be read
                _state.value.incognitoMode
            }

            // Don't record history if incognito mode is enabled
            if (isIncognito) return@launch

            runCatching {
                chapterRepository.recordHistory(
                    chapterId = chapterId,
                    readAt = sessionReadAt,
                    readDurationMs = 0L
                )
            }
        }
    }

    /**
     * Load saved reader settings with per-manga overrides (#260, #264)
     */
    private fun loadSettings() {
        viewModelScope.launch {
            // Load manga first to check for per-manga overrides
            val manga = mangaRepository.getMangaById(mangaId)
            currentManga = manga

            // Launch all DataStore reads concurrently — each .first() is a separate suspend
            // point; running them in parallel shaves 50–200 ms from cold reader open time.
            coroutineScope {
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
                    try { settingsRepository.cropBordersEnabled.first() } catch (_: Exception) { false }
                }
                val imageQualityD = async {
                    try { settingsRepository.imageQuality.first() } catch (_: Exception) { ImageQuality.ORIGINAL }
                }
                val dataSaverEnabledD = async {
                    try { settingsRepository.dataSaverEnabled.first() } catch (_: Exception) { false }
                }
                val showReadingTimerD = async {
                    try { settingsRepository.showReadingTimer.first() } catch (_: Exception) { false }
                }
                val showBatteryTimeD = async {
                    try { settingsRepository.showBatteryTime.first() } catch (_: Exception) { false }
                }
                val preloadBeforeD = async {
                    try { settingsRepository.preloadPagesBefore.first() } catch (_: Exception) { ReaderSettingsRepository.DEFAULT_PRELOAD_PAGES }
                }
                val preloadAfterD = async {
                    try { settingsRepository.preloadPagesAfter.first() } catch (_: Exception) { ReaderSettingsRepository.DEFAULT_PRELOAD_PAGES }
                }
                val smartPrefetchEnabledD = async {
                    try { settingsRepository.smartPrefetchEnabled.first() } catch (_: Exception) { true }
                }
                val prefetchStrategyOrdinalD = async {
                    try { settingsRepository.prefetchStrategyOrdinal.first() } catch (_: Exception) { -1 }
                }
                val adaptiveLearningEnabledD = async {
                    try { settingsRepository.adaptiveLearningEnabled.first() } catch (_: Exception) { true }
                }
                val prefetchAdjacentChaptersD = async {
                    try { settingsRepository.prefetchAdjacentChapters.first() } catch (_: Exception) { false }
                }
                val prefetchOnlyOnWiFiD = async {
                    try { settingsRepository.prefetchOnlyOnWiFi.first() } catch (_: Exception) { false }
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
                val brightness = brightnessD.await()
                val keepScreenOn = keepScreenOnD.await()
                val showPageNumber = showPageNumberD.await()
                val direction = directionD.await()
                val volumeKeysEnabled = volumeKeysEnabledD.await()
                val volumeKeysInverted = volumeKeysInvertedD.await()
                val fullscreen = fullscreenD.await()
                val incognitoMode = incognitoModeD.await()
                val colorFilterMode = colorFilterModeD.await()
                val customTintColor = customTintColorD.await()
                val cropBordersEnabled = cropBordersEnabledD.await()
                val imageQuality = imageQualityD.await()
                val dataSaverEnabled = dataSaverEnabledD.await()
                val showReadingTimer = showReadingTimerD.await()
                val showBatteryTime = showBatteryTimeD.await()
                prefetchDelegate.cachedPreloadBefore = preloadBeforeD.await()
                prefetchDelegate.cachedPreloadAfter = preloadAfterD.await()
                prefetchDelegate.cachedSmartPrefetchEnabled = smartPrefetchEnabledD.await()
                val prefetchOrdinal = prefetchStrategyOrdinalD.await()
                prefetchDelegate.cachedPrefetchStrategy = if (prefetchOrdinal >= 0) PrefetchStrategy.fromOrdinal(prefetchOrdinal) else PrefetchStrategy.Balanced
                prefetchDelegate.cachedAdaptiveLearningEnabled = adaptiveLearningEnabledD.await()
                prefetchDelegate.cachedPrefetchAdjacentChapters = prefetchAdjacentChaptersD.await()
                prefetchDelegate.cachedPrefetchOnlyOnWiFi = prefetchOnlyOnWiFiD.await()
                val showContentInCutout = showContentInCutoutD.await()
                val backgroundColor = backgroundColorD.await()
                val animatePageTransitions = animatePageTransitionsD.await()
                val showReadingModeOverlay = showReadingModeOverlayD.await()
                val showTapZonesOverlay = showTapZonesOverlayD.await()
                val readerScale = readerScaleD.await()
                val autoZoomWideImages = autoZoomWideImagesD.await()
                val invertTapZones = invertTapZonesD.await()
                val webtoonSidePadding = webtoonSidePaddingD.await()
                val webtoonGapDp = webtoonGapDpD.await()
                val webtoonMenuHideSensitivity = webtoonMenuHideSensitivityD.await()
                val webtoonDoubleTapZoom = webtoonDoubleTapZoomD.await()
                val webtoonDisableZoomOut = webtoonDisableZoomOutD.await()
                val einkFlashOnPageChange = einkFlashOnPageChangeD.await()
                val einkBlackAndWhite = einkBlackAndWhiteD.await()
                val skipReadChapters = skipReadChaptersD.await()
                val skipFilteredChapters = skipFilteredChaptersD.await()
                val skipDuplicateChapters = skipDuplicateChaptersD.await()
                val alwaysShowChapterTransition = alwaysShowChapterTransitionD.await()
                val showActionsOnLongTap = showActionsOnLongTapD.await()
                val savePagesToSeparateFolders = savePagesToSeparateFoldersD.await()

                // Apply per-manga overrides if they exist (#260)
                val effectiveMode = manga?.readerMode?.let { ReaderMode.entries.getOrNull(it) } ?: mode
                val effectiveDirection = manga?.readerDirection?.let {
                    if (it == 0) ReadingDirection.LTR else ReadingDirection.RTL
                } ?: direction
                val effectiveColorFilter = manga?.readerColorFilter?.let {
                    ColorFilterMode.entries.getOrNull(it)
                } ?: colorFilterMode
                val effectiveTintColor = manga?.readerCustomTintColor ?: customTintColor

                _state.update {
                    it.copy(
                        mode = effectiveMode,
                        brightness = brightness,
                        keepScreenOn = keepScreenOn,
                        showPageNumber = showPageNumber,
                        readingDirection = effectiveDirection,
                        volumeKeysEnabled = volumeKeysEnabled,
                        volumeKeysInverted = volumeKeysInverted,
                        isFullscreen = fullscreen,
                        incognitoMode = incognitoMode,
                        colorFilterMode = effectiveColorFilter,
                        customTintColor = effectiveTintColor,
                        showReadingTimer = showReadingTimer,
                        showBatteryTime = showBatteryTime,
                        cropBordersEnabled = cropBordersEnabled,
                        imageQuality = imageQuality,
                        dataSaverEnabled = dataSaverEnabled,
                        showContentInCutout = showContentInCutout,
                        backgroundColor = backgroundColor,
                        animatePageTransitions = animatePageTransitions,
                        showReadingModeOverlay = showReadingModeOverlay,
                        showTapZonesOverlay = showTapZonesOverlay,
                        readerScale = readerScale,
                        autoZoomWideImages = autoZoomWideImages,
                        invertTapZones = invertTapZones,
                        webtoonSidePadding = webtoonSidePadding,
                        webtoonGapDp = webtoonGapDp,
                        webtoonMenuHideSensitivity = webtoonMenuHideSensitivity,
                        webtoonDoubleTapZoom = webtoonDoubleTapZoom,
                        webtoonDisableZoomOut = webtoonDisableZoomOut,
                        einkFlashOnPageChange = einkFlashOnPageChange,
                        einkBlackAndWhite = einkBlackAndWhite,
                        skipReadChapters = skipReadChapters,
                        skipFilteredChapters = skipFilteredChapters,
                        skipDuplicateChapters = skipDuplicateChapters,
                        alwaysShowChapterTransition = alwaysShowChapterTransition,
                        showActionsOnLongTap = showActionsOnLongTap,
                        savePagesToSeparateFolders = savePagesToSeparateFolders
                    )
                }
            } // end coroutineScope
        }
    }

    /**
     * Load chapter pages and initialize reader state
     */
    private fun loadChapter() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                // Load chapter and manga
                val chapter = chapterRepository.getChapterById(chapterId)
                val manga = mangaRepository.getMangaById(mangaId)

                if (chapter == null) {
                    _state.update {
                        it.copy(isLoading = false, error = "Chapter not found")
                    }
                    return@launch
                }

                if (manga == null) {
                    _state.update {
                        it.copy(isLoading = false, error = "Manga not found")
                    }
                    return@launch
                }

                currentManga = manga
                currentChapter = chapter
                hasTriggeredDeletion = false

                // Fetch pages from source; PageLoader will transparently substitute
                // local file URIs for any page that has already been downloaded.
                val sourceName = manga.sourceId.toString()
                val pages = fetchPagesFromSource(
                    chapterUrl = chapter.url,
                    chapterId = chapter.id,
                    sourceName = sourceName,
                    mangaTitle = manga.title,
                    chapterName = chapter.name
                )

                _state.update { currentState ->
                    currentState.copy(
                        pages = pages,
                        currentPage = chapter.lastPageRead.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
                        isLoading = false,
                        chapterTitle = chapter.name,
                        readerBackgroundColor = manga.readerBackgroundColor
                    )
                }

                // Record history now that the chapter is confirmed to exist.
                recordHistoryOpen()

                // Update Discord Rich Presence with reading info
                discordDelegate.updatePresence(manga.title, chapter.name, pages.size)

                // Start preloading adjacent pages
                if (pages.isNotEmpty()) {
                    prefetchDelegate.preloadPages(
                        scope = viewModelScope,
                        pages = pages,
                        currentPage = _state.value.currentPage,
                        mangaId = mangaId,
                        chapterId = chapterId,
                        currentManga = currentManga,
                    )
                }

                // Start panel detection when in Smart Panels mode
                if (_state.value.mode == ReaderMode.SMART_PANELS && pages.isNotEmpty()) {
                    panelDelegate.detectForPages(
                        scope = viewModelScope,
                        pages = pages,
                        currentPageIndex = _state.value.currentPage,
                        readingDirection = _state.value.readingDirection,
                        isSmartPanelsMode = { _state.value.mode == ReaderMode.SMART_PANELS },
                        updateState = { _state.update(it) },
                    )
                }

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load chapter"
                    )
                }
            }
        }
    }

    /**
     * Fetch pages from the manga source.
     *
     * For each page, [PageLoader.resolveUrl] is called so that already-downloaded
     * pages are served from local storage rather than the network.
     *
     * In a real implementation this would call the SourceManager to obtain the
     * remote page URLs before handing them to [PageLoader].
     */
    private suspend fun fetchPagesFromSource(
        chapterUrl: String,
        chapterId: Long,
        sourceName: String,
        mangaTitle: String,
        chapterName: String
    ): List<ReaderPage> {
        val manga = currentManga ?: return emptyList()
        val sourceChapter = app.otakureader.sourceapi.SourceChapter(
            url = chapterUrl,
            name = chapterName
        )
        val sourceId = manga.sourceId.toString()

        val pages = sourceRepository.getPageList(sourceId, sourceChapter)
            .getOrElse { return emptyList() }

        return pages.mapIndexed { index, page ->
            ReaderPage(
                index = index,
                imageUrl = pageLoader.resolveUrl(
                    page.imageUrl.orEmpty(),
                    sourceName,
                    mangaTitle,
                    chapterName,
                    index
                ),
                chapterName = chapterName
            )
        }
    }

    /**
     * Set pages directly (useful for testing or when pages are passed from outside)
     */
    fun setPages(pages: List<ReaderPage>) {
        _state.update { currentState ->
            currentState.copy(
                pages = pages,
                currentPage = currentState.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
                isLoading = false
            )
        }
    }

    /**
     * Handle all reader events
     */
    fun onEvent(event: ReaderEvent) {
        when (event) {
            is ReaderEvent.OnPageChange -> changePage(event.page)
            is ReaderEvent.OnPanelChange -> changePanel(event.panel)
            is ReaderEvent.OnZoomChange -> updateZoom(event.zoom)
            is ReaderEvent.OnModeChange -> changeReaderMode(event.mode)
            is ReaderEvent.OnBrightnessChange -> updateBrightness(event.brightness)
            is ReaderEvent.OnDirectionChange -> updateReadingDirection(event.direction)
            ReaderEvent.ToggleMenu -> toggleMenu()
            ReaderEvent.ToggleGallery -> toggleGallery()
            is ReaderEvent.SetGalleryColumns -> setGalleryColumns(event.columns)
            ReaderEvent.NextPage -> navigatePage(1)
            ReaderEvent.PrevPage -> navigatePage(-1)
            ReaderEvent.NextPanel -> navigatePanel(1)
            ReaderEvent.PrevPanel -> navigatePanel(-1)
            ReaderEvent.ZoomIn -> updateZoom(_state.value.zoomLevel + ZOOM_INCREMENT)
            ReaderEvent.ZoomOut -> updateZoom(_state.value.zoomLevel - ZOOM_INCREMENT)
            ReaderEvent.ResetZoom -> updateZoom(1f)
            ReaderEvent.ZoomToWidth -> updateZoom(1.5f)
            ReaderEvent.ZoomToHeight -> updateZoom(1.2f)
            ReaderEvent.ToggleFullscreen -> toggleFullscreen()
            ReaderEvent.ToggleAutoScroll -> toggleAutoScroll()
            ReaderEvent.NextChapter -> navigateNextChapter()
            ReaderEvent.PrevChapter -> navigatePreviousChapter()
            ReaderEvent.DismissError -> dismissError()
            ReaderEvent.Retry -> loadChapter()
            is ReaderEvent.OnAutoScrollSpeedChange -> updateAutoScrollSpeed(event.speed)
            is ReaderEvent.ToggleSetting -> toggleSetting(event.setting)
            is ReaderEvent.LoadChapter -> loadChapterById(event.chapterId)
            is ReaderEvent.UpdateTapZones -> updateTapZones(event.config)
            ReaderEvent.ToggleBookmark -> toggleBookmark()
            ReaderEvent.SharePage -> sharePage()
            ReaderEvent.BrightnessUp -> updateBrightness(_state.value.brightness + BRIGHTNESS_INCREMENT)
            ReaderEvent.BrightnessDown -> updateBrightness(_state.value.brightness - BRIGHTNESS_INCREMENT)
            is ReaderEvent.SetColorFilterMode -> updateColorFilterMode(event.mode)
            is ReaderEvent.SetCustomTintColor -> updateCustomTintColor(event.color)
            is ReaderEvent.SetReaderBackgroundColor -> updateReaderBackgroundColor(event.color)
            ReaderEvent.AutoScrollSpeedUp -> updateAutoScrollSpeed(_state.value.autoScrollSpeed + AUTO_SCROLL_INCREMENT)
            ReaderEvent.AutoScrollSpeedDown -> updateAutoScrollSpeed(_state.value.autoScrollSpeed - AUTO_SCROLL_INCREMENT)
            ReaderEvent.FirstPage -> changePage(0)
            ReaderEvent.LastPage -> changePage((_state.value.pages.size - 1).coerceAtLeast(0))
            ReaderEvent.FirstPanel -> changePanel(0)
            ReaderEvent.LastPanel -> {
                val currentPage = _state.value.pages.getOrNull(_state.value.currentPage)
                changePanel((currentPage?.panels?.size ?: 1) - 1)
            }
            ReaderEvent.RotateCW -> cyclePageRotation()
            ReaderEvent.ResetRotation -> _state.update { it.copy(pageRotation = PageRotation.NONE) }

            // SFX Translation
            ReaderEvent.OpenSfxDialog -> _state.update { it.copy(showSfxDialog = true) }
            ReaderEvent.CloseSfxDialog -> _state.update { it.copy(showSfxDialog = false) }
            is ReaderEvent.TranslateSfx -> sfxDelegate.translateManualText(viewModelScope, event.sfxText) { _state.update(it) }
        }
    }

    private fun changePage(page: Int) {
        val validPage = page.coerceIn(0, (_state.value.pages.size - 1).coerceAtLeast(0))
        if (validPage != _state.value.currentPage) {
            val previousPage = _state.value.currentPage

            // Record navigation event for behavior tracking
            if (prefetchDelegate.cachedAdaptiveLearningEnabled) {
                val nowElapsed = SystemClock.elapsedRealtime()
                val pageDuration = nowElapsed - lastPageChangeMs
                lastPageChangeMs = nowElapsed

                // Derive epoch timestamp consistently from sessionReadAt + monotonic offset
                // This ensures timestamp and duration use consistent time bases
                val epochTimestamp = sessionReadAt + (nowElapsed - sessionStartMs)

                val event = PageNavigationEvent(
                    mangaId = mangaId,
                    chapterId = chapterId,
                    fromPage = previousPage,
                    toPage = validPage,
                    pageDurationMs = pageDuration,
                    readerMode = _state.value.mode.ordinal,
                    timestamp = epochTimestamp
                )
                behaviorTracker.recordNavigation(event)
            }

            // Record page view for telemetry only when smart prefetch is active
            if (prefetchDelegate.cachedSmartPrefetchEnabled) {
                val currentPage = _state.value.pages.getOrNull(previousPage)
                if (currentPage != null) {
                    smartPrefetchManager.recordPageView(currentPage)
                }
            }

            _state.update { state ->
                // Reset current panel to 0 when navigating to a new page in Smart Panels mode
                val newPanel = if (state.mode == ReaderMode.SMART_PANELS) 0 else state.currentPanel
                state.copy(currentPage = validPage, currentPanel = newPanel)
            }
            val pages = _state.value.pages
            prefetchDelegate.preloadPages(
                scope = viewModelScope,
                pages = pages,
                currentPage = validPage,
                mangaId = mangaId,
                chapterId = chapterId,
                currentManga = currentManga,
            )
            scheduleProgressSave()
            sfxDelegate.loadTranslationsForPage(
                scope = viewModelScope,
                pageIndex = validPage,
                pageUrl = pages.getOrNull(validPage)?.imageUrl,
                chapterId = chapterId,
                updateState = { _state.update(it) },
            )

            // Update Discord presence with current page
            val manga = currentManga
            val chapter = currentChapter
            if (manga != null && chapter != null) {
                discordDelegate.updatePresence(manga.title, chapter.name, pages.size, validPage + 1)
            }

            if (pages.isNotEmpty() && validPage == pages.lastIndex) {
                maybeDeleteAfterReading()
            }

            // Trigger download-ahead when user is near end of chapter
            downloadAheadDelegate.maybeDownloadNextChapter(
                scope = viewModelScope,
                currentPage = validPage,
                totalPages = pages.size,
                mangaId = mangaId,
                chapterId = chapterId,
                getCurrentManga = { currentManga },
            )
        }
    }

    private fun navigatePage(delta: Int) {
        val currentMode = _state.value.mode
        val pageIncrement = when (currentMode) {
            ReaderMode.DUAL_PAGE -> delta * 2
            else -> delta
        }
        changePage(_state.value.currentPage + pageIncrement)
    }

    private fun changePanel(panel: Int) {
        val currentPage = _state.value.pages.getOrNull(_state.value.currentPage)
        val maxPanels = currentPage?.panels?.size ?: 0
        val validPanel = panel.coerceIn(0, (maxPanels - 1).coerceAtLeast(0))
        _state.update { it.copy(currentPanel = validPanel) }
    }

    private fun navigatePanel(delta: Int) {
        changePanel(_state.value.currentPanel + delta)
    }

    private fun updateZoom(zoom: Float) {
        val clampedZoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        _state.update { it.copy(zoomLevel = clampedZoom) }
    }

    private fun updateBrightness(brightness: Float) {
        val clampedBrightness = brightness.coerceIn(0.1f, 1.5f)
        _state.update { it.copy(brightness = clampedBrightness) }
        
        // Save brightness setting
        viewModelScope.launch {
            settingsRepository.setBrightness(clampedBrightness)
        }
    }

    private fun updateColorFilterMode(mode: ColorFilterMode) {
        _state.update { it.copy(colorFilterMode = mode) }
        viewModelScope.launch {
            settingsRepository.setColorFilterMode(mode)
        }
    }

    private fun updateCustomTintColor(color: Long) {
        _state.update { it.copy(customTintColor = color) }
        viewModelScope.launch {
            settingsRepository.setCustomTintColor(color)
        }
    }

    /**
     * Updates the per-manga reader background color and persists it to the database.
     * Pass null to reset to the default background.
     */
    private fun updateReaderBackgroundColor(color: Long?) {
        _state.update { it.copy(readerBackgroundColor = color) }
        viewModelScope.launch {
            currentManga?.let { manga ->
                mangaRepository.updateManga(manga.copy(readerBackgroundColor = color))
                currentManga = manga.copy(readerBackgroundColor = color)
            }
        }
    }

    private fun updateReadingDirection(direction: ReadingDirection) {
        _state.update { it.copy(readingDirection = direction) }
        viewModelScope.launch {
            settingsRepository.setReadingDirection(direction)
        }
    }

    private fun cyclePageRotation() {
        _state.update { it.copy(pageRotation = it.pageRotation.next()) }
    }

    private fun changeReaderMode(mode: ReaderMode) {
        _state.update { it.copy(mode = mode) }
        
        // Adjust current page for dual page mode
        if (mode == ReaderMode.DUAL_PAGE && _state.value.currentPage % 2 != 0) {
            _state.update { it.copy(currentPage = it.currentPage - 1) }
        }

        // Trigger panel detection when switching to Smart Panels mode
        if (mode == ReaderMode.SMART_PANELS) {
            val pages = _state.value.pages
            if (pages.isNotEmpty()) {
                panelDelegate.detectForPages(
                    scope = viewModelScope,
                    pages = pages,
                    currentPageIndex = _state.value.currentPage,
                    readingDirection = _state.value.readingDirection,
                    isSmartPanelsMode = { _state.value.mode == ReaderMode.SMART_PANELS },
                    updateState = { _state.update(it) },
                )
            }
        } else {
            panelDelegate.cancel()
        }
        
        // Save mode setting
        viewModelScope.launch {
            settingsRepository.setReaderMode(mode)
        }
    }

    private fun toggleMenu() {
        _state.update { it.copy(isMenuVisible = !it.isMenuVisible) }
    }

    private fun toggleGallery() {
        _state.update { it.copy(isGalleryOpen = !it.isGalleryOpen) }
    }

    private fun setGalleryColumns(columns: Int) {
        val clamped = columns.coerceIn(2, 4)
        _state.update { it.copy(galleryColumns = clamped) }
    }

    private fun toggleFullscreen() {
        val newFullscreen = !_state.value.isFullscreen
        _state.update { it.copy(isFullscreen = newFullscreen) }
        
        viewModelScope.launch {
            settingsRepository.setFullscreen(newFullscreen)
        }
    }

    private fun toggleAutoScroll() {
        _state.update { it.copy(isAutoScrollEnabled = !it.isAutoScrollEnabled) }
    }

    private fun updateAutoScrollSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(10f, 500f)
        _state.update { it.copy(autoScrollSpeed = clampedSpeed) }
        
        viewModelScope.launch {
            settingsRepository.setAutoScrollSpeed(clampedSpeed)
        }
    }

    private fun toggleSetting(setting: ReaderSetting) {
        when (setting) {
            ReaderSetting.KEEP_SCREEN_ON -> {
                val newValue = !_state.value.keepScreenOn
                _state.update { it.copy(keepScreenOn = newValue) }
                viewModelScope.launch { settingsRepository.setKeepScreenOn(newValue) }
            }
            ReaderSetting.SHOW_PAGE_NUMBER -> {
                val newValue = !_state.value.showPageNumber
                _state.update { it.copy(showPageNumber = newValue) }
                viewModelScope.launch { settingsRepository.setShowPageNumber(newValue) }
            }
            ReaderSetting.DOUBLE_TAP_ZOOM -> {
                val newValue = !_state.value.doubleTapZoomEnabled
                _state.update { it.copy(doubleTapZoomEnabled = newValue) }
                viewModelScope.launch { settingsRepository.setDoubleTapZoomEnabled(newValue) }
            }
            ReaderSetting.VOLUME_KEY_NAVIGATION -> {
                val newValue = !_state.value.volumeKeysEnabled
                _state.update { it.copy(volumeKeysEnabled = newValue) }
                viewModelScope.launch { settingsRepository.setVolumeKeysEnabled(newValue) }
            }
            ReaderSetting.VOLUME_KEYS_INVERTED -> {
                val newValue = !_state.value.volumeKeysInverted
                _state.update { it.copy(volumeKeysInverted = newValue) }
                viewModelScope.launch { settingsRepository.setVolumeKeysInverted(newValue) }
            }
            ReaderSetting.INCOGNITO_MODE -> {
                val newValue = !_state.value.incognitoMode
                _state.update { it.copy(incognitoMode = newValue) }
                viewModelScope.launch { settingsRepository.setIncognitoMode(newValue) }
            }
            ReaderSetting.CROP_BORDERS -> {
                val newValue = !_state.value.cropBordersEnabled
                _state.update { it.copy(cropBordersEnabled = newValue) }
                viewModelScope.launch { settingsRepository.setCropBordersEnabled(newValue) }
            }
            else -> { /* Other settings not yet implemented */ }
        }
    }

    private fun updateTapZones(config: app.otakureader.feature.reader.model.TapZoneConfig) {
        viewModelScope.launch {
            settingsRepository.setTapZoneConfig(config)
        }
    }

    private fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun loadChapterById(chapterId: String) {
        // Implementation for loading a different chapter
        viewModelScope.launch {
            _effect.send(ReaderEffect.NavigateToChapter(chapterId.toLong()))
        }
    }

    private fun toggleBookmark() {
        // Implementation for toggling bookmark on current page
    }

    private fun sharePage() {
        // Implementation for sharing current page
    }


    /**
     * Schedule auto-save of reading progress with debouncing to prevent excessive database writes.
     * Multiple rapid page changes will only trigger one save after the delay period.
     */
    private fun scheduleProgressSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(PROGRESS_SAVE_DELAY)
            saveCurrentProgress()
        }
    }

    private fun saveCurrentProgress() {
        val currentState = _state.value
        viewModelScope.launch {
            // Don't update progress if incognito mode is enabled
            if (currentState.incognitoMode) return@launch

            chapterRepository.updateChapterProgress(
                chapterId = chapterId,
                read = currentState.isLastPage,
                lastPageRead = currentState.currentPage
            )
        }
    }

    private fun navigatePreviousChapter() {
        viewModelScope.launch {
            _effect.send(ReaderEffect.NavigateBack)
        }
    }

    private fun navigateNextChapter() {
        viewModelScope.launch {
            _effect.send(ReaderEffect.ShowSnackbar("End of available chapters"))
        }
    }

    /**
     * Handle tap zones for navigation
     */
    fun onTapZone(zone: TapZone) {
        when (zone) {
            TapZone.LEFT -> when (_state.value.readingDirection) {
                ReadingDirection.RTL -> navigatePage(1)
                else -> navigatePage(-1)
            }
            TapZone.RIGHT -> when (_state.value.readingDirection) {
                ReadingDirection.RTL -> navigatePage(-1)
                else -> navigatePage(1)
            }
            TapZone.CENTER -> toggleMenu()
        }
    }

    /**
     * Reset zoom to default
     */
    fun resetZoom() {
        _state.update { it.copy(zoomLevel = 1f) }
    }

    /**
     * Jump to specific page (for gallery/thumbnail navigation)
     */
    fun jumpToPage(page: Int) {
        changePage(page)
        _state.update { it.copy(isGalleryOpen = false) }
    }

    private fun maybeDeleteAfterReading() {
        // Delete-after-reading feature has been removed. This method is kept as a placeholder
        // for potential future implementation but currently does nothing.
        if (hasTriggeredDeletion) return
        hasTriggeredDeletion = true
    }


    override fun onCleared() {
        super.onCleared()
        val durationMs = SystemClock.elapsedRealtime() - sessionStartMs
        // Capture state before viewModelScope is cancelled so we can read incognito/page data.
        val currentState = _state.value

        // H-5 Fix: Use WorkManager to guarantee history + progress are persisted even if
        // the OS kills the process before a raw coroutine could complete.
        runCatching {
            val request = RecordReadingHistoryWorker.buildRequest(
                chapterId = chapterId,
                readAt = sessionReadAt,
                durationMs = durationMs,
                isIncognito = currentState.incognitoMode,
                lastPageRead = currentState.currentPage,
                isRead = currentState.isLastPage,
            )
            WorkManager.getInstance(context).enqueue(request)
        }.onFailure { e ->
            android.util.Log.w(TAG, "WorkManager enqueue failed in onCleared", e)
        }

        discordDelegate.clearPresence(showBrowsing = true)
        autoSaveJob?.cancel()
        prefetchDelegate.cancel()
        panelDelegate.cancel()
        sfxDelegate.clear()
        prefetchDelegate.clearCache()
    }

    /**
     * Performs the final persistence work when the reader is closed.
     * Extracted to a suspend function so it can be tested directly without
     * going through the protected [onCleared] / [cleanupScope] boundary.
     */
    @androidx.annotation.VisibleForTesting
    suspend fun cleanupOnExit(durationMs: Long, currentState: ReaderState) {
        val isIncognito = runCatching {
            settingsRepository.incognitoMode.first()
        }.getOrElse {
            currentState.incognitoMode
        }
        // Don't record history or progress if incognito mode is enabled
        if (!isIncognito) {
            runCatching {
                chapterRepository.recordHistory(
                    chapterId = chapterId,
                    readAt = sessionReadAt,
                    readDurationMs = durationMs
                )
            }
            // Save final reading progress here rather than calling saveCurrentProgress()
            // (which uses viewModelScope and would be a no-op after onCleared).
            runCatching {
                chapterRepository.updateChapterProgress(
                    chapterId = chapterId,
                    read = currentState.isLastPage,
                    lastPageRead = currentState.currentPage
                )
            }
        }
    }


    companion object {
        private const val TAG = "UltimateReaderViewModel"
        private const val MIN_ZOOM = 0.5f
        private const val MAX_ZOOM = 5f
        private const val PROGRESS_SAVE_DELAY = 3000L // 3 seconds
        const val ZOOM_INCREMENT = 0.25f
        const val BRIGHTNESS_INCREMENT = 0.1f
        const val AUTO_SCROLL_INCREMENT = 50f
    }

    private fun observeSettingsWriteFailures() {
        settingsRepository.writeFailureEvents
            .onEach {
                _effect.send(
                    ReaderEffect.ShowSnackbar(
                        messageResId = app.otakureader.feature.reader.R.string.reader_settings_save_failed
                    )
                )
            }
            .launchIn(viewModelScope)
    }

}

/**
 * Effects emitted by the reader
 */
sealed interface ReaderEffect {
    data object NavigateBack : ReaderEffect
    data class ShowSnackbar(
        val message: String? = null,
        @androidx.annotation.StringRes val messageResId: Int? = null
    ) : ReaderEffect
    data class NavigateToChapter(val chapterId: Long) : ReaderEffect
}
