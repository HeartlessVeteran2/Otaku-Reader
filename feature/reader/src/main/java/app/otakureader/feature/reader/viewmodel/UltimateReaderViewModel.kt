package app.otakureader.feature.reader.viewmodel

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.PageNavigationEvent
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.feature.reader.model.ColorFilterMode
import app.otakureader.feature.reader.model.ReaderMode
import app.otakureader.feature.reader.model.ReaderPage
import app.otakureader.feature.reader.model.ReadingDirection
import app.otakureader.feature.reader.prefetch.ReadingBehaviorTracker
import app.otakureader.feature.reader.repository.ReaderSettingsRepository
import app.otakureader.feature.reader.viewmodel.delegate.ReaderChapterLoaderDelegate
import app.otakureader.feature.reader.viewmodel.delegate.ReaderDiscordDelegate
import app.otakureader.feature.reader.viewmodel.delegate.ReaderDownloadAheadDelegate
import app.otakureader.feature.reader.viewmodel.delegate.ReaderHistoryDelegate
import app.otakureader.feature.reader.viewmodel.delegate.ReaderOcrDelegate
import app.otakureader.feature.reader.viewmodel.delegate.ReaderPanelDetectionDelegate
import app.otakureader.feature.reader.viewmodel.delegate.ReaderPrefetchDelegate
import app.otakureader.feature.reader.viewmodel.delegate.ReaderSettingsLoaderDelegate
import app.otakureader.feature.reader.viewmodel.delegate.ReaderSfxDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Coordinator ViewModel for the Reader feature.
 *
 * Responsibilities are intentionally narrow:
 *  - Aggregate [ReaderState] and emit it via [state].
 *  - Route [ReaderEvent]s to the appropriate handler / delegate.
 *  - Persist mutable user-driven settings as they change.
 *
 * All other concerns are owned by dedicated delegates so they can be tested
 * independently and so this class stays small and focused (see issue #581):
 *
 *  | Delegate                       | Concern                                       |
 *  |--------------------------------|-----------------------------------------------|
 *  | [ReaderSettingsLoaderDelegate] | DataStore reads + per-manga overrides         |
 *  | [ReaderChapterLoaderDelegate]  | Chapter / manga / page loading                |
 *  | [ReaderHistoryDelegate]        | Reading-history recording + WorkManager       |
 *  | [ReaderPrefetchDelegate]       | Prefetch / preload                            |
 *  | [ReaderPanelDetectionDelegate] | Smart-panel detection                         |
 *  | [ReaderSfxDelegate]            | SFX translation jobs                          |
 *  | [ReaderOcrDelegate]            | OCR text-search jobs                          |
 *  | [ReaderDiscordDelegate]        | Discord rich presence                         |
 *  | [ReaderDownloadAheadDelegate]  | Download-ahead trigger                        |
 */
@HiltViewModel
class UltimateReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val settingsRepository: ReaderSettingsRepository,
    private val behaviorTracker: ReadingBehaviorTracker,
    private val settingsLoaderDelegate: ReaderSettingsLoaderDelegate,
    private val chapterLoaderDelegate: ReaderChapterLoaderDelegate,
    private val historyDelegate: ReaderHistoryDelegate,
    private val sfxDelegate: ReaderSfxDelegate,
    private val discordDelegate: ReaderDiscordDelegate,
    private val panelDelegate: ReaderPanelDetectionDelegate,
    private val prefetchDelegate: ReaderPrefetchDelegate,
    private val downloadAheadDelegate: ReaderDownloadAheadDelegate,
    private val ocrDelegate: ReaderOcrDelegate,
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
     * Made internal for [app.otakureader.feature.reader.ui.ReadingTimerOverlay] access within
     * the feature:reader module. This timestamp is never updated and represents the start of
     * the reading session.
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

    /**
     * Load saved reader settings with per-manga overrides (#260, #264).
     */
    private fun loadSettings() {
        viewModelScope.launch {
            // Load manga first to check for per-manga overrides.
            val manga = mangaRepository.getMangaById(mangaId)
            currentManga = manga
            val settingsState = settingsLoaderDelegate.load(_state.value, manga)
            // Merge only settings fields into current state to avoid overwriting
            // pages/chapter data loaded concurrently by loadChapter().
            _state.update { current ->
                current.copy(
                    mode = settingsState.mode,
                    brightness = settingsState.brightness,
                    keepScreenOn = settingsState.keepScreenOn,
                    showPageNumber = settingsState.showPageNumber,
                    readingDirection = settingsState.readingDirection,
                    volumeKeysEnabled = settingsState.volumeKeysEnabled,
                    volumeKeysInverted = settingsState.volumeKeysInverted,
                    isFullscreen = settingsState.isFullscreen,
                    incognitoMode = settingsState.incognitoMode,
                    colorFilterMode = settingsState.colorFilterMode,
                    customTintColor = settingsState.customTintColor,
                    showReadingTimer = settingsState.showReadingTimer,
                    showBatteryTime = settingsState.showBatteryTime,
                    cropBordersEnabled = settingsState.cropBordersEnabled,
                    imageQuality = settingsState.imageQuality,
                    dataSaverEnabled = settingsState.dataSaverEnabled,
                    showContentInCutout = settingsState.showContentInCutout,
                    backgroundColor = settingsState.backgroundColor,
                    animatePageTransitions = settingsState.animatePageTransitions,
                    showReadingModeOverlay = settingsState.showReadingModeOverlay,
                    showTapZonesOverlay = settingsState.showTapZonesOverlay,
                    readerScale = settingsState.readerScale,
                    autoZoomWideImages = settingsState.autoZoomWideImages,
                    invertTapZones = settingsState.invertTapZones,
                    webtoonSidePadding = settingsState.webtoonSidePadding,
                    webtoonGapDp = settingsState.webtoonGapDp,
                    webtoonMenuHideSensitivity = settingsState.webtoonMenuHideSensitivity,
                    webtoonDoubleTapZoom = settingsState.webtoonDoubleTapZoom,
                    webtoonDisableZoomOut = settingsState.webtoonDisableZoomOut,
                    einkFlashOnPageChange = settingsState.einkFlashOnPageChange,
                    einkBlackAndWhite = settingsState.einkBlackAndWhite,
                    skipReadChapters = settingsState.skipReadChapters,
                    skipFilteredChapters = settingsState.skipFilteredChapters,
                    skipDuplicateChapters = settingsState.skipDuplicateChapters,
                    alwaysShowChapterTransition = settingsState.alwaysShowChapterTransition,
                    showActionsOnLongTap = settingsState.showActionsOnLongTap,
                    savePagesToSeparateFolders = settingsState.savePagesToSeparateFolders,
                )
            }
        }
    }

    /**
     * Load chapter pages and initialize reader state.
     */
    private fun loadChapter() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = chapterLoaderDelegate.load(mangaId, chapterId)) {
                is ReaderChapterLoaderDelegate.Result.NotFound -> {
                    _state.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
                is ReaderChapterLoaderDelegate.Result.Failure -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = result.cause.message ?: "Failed to load chapter",
                        )
                    }
                }
                is ReaderChapterLoaderDelegate.Result.Success -> {
                    currentManga = result.manga
                    currentChapter = result.chapter
                    hasTriggeredDeletion = false

                    val pages = result.pages
                    val initialPage = result.chapter.lastPageRead
                        .coerceIn(0, (pages.size - 1).coerceAtLeast(0))

                    _state.update { current ->
                        current.copy(
                            pages = pages,
                            currentPage = initialPage,
                            isLoading = false,
                            chapterTitle = result.chapter.name,
                            readerBackgroundColor = result.manga.readerBackgroundColor,
                        )
                    }

                    // Record history now that the chapter is confirmed to exist.
                    historyDelegate.recordOpen(
                        scope = viewModelScope,
                        chapterId = chapterId,
                        sessionReadAt = sessionReadAt,
                        fallbackIncognito = _state.value.incognitoMode,
                    )
                    // Reset page-change timestamp so first recorded page duration
                    // does not include chapter load time.
                    lastPageChangeMs = SystemClock.elapsedRealtime()

                    // Update Discord Rich Presence with reading info.
                    discordDelegate.updatePresence(
                        result.manga.title,
                        result.chapter.name,
                        pages.size,
                    )

                    // Start preloading adjacent pages.
                    if (pages.isNotEmpty()) {
                        prefetchDelegate.preloadPages(
                            scope = viewModelScope,
                            pages = pages,
                            currentPage = initialPage,
                            mangaId = mangaId,
                            chapterId = chapterId,
                            currentManga = currentManga,
                        )
                    }

                    // Start panel detection when in Smart Panels mode.
                    if (_state.value.mode == ReaderMode.SMART_PANELS && pages.isNotEmpty()) {
                        panelDelegate.detectForPages(
                            scope = viewModelScope,
                            pages = pages,
                            currentPageIndex = initialPage,
                            readingDirection = _state.value.readingDirection,
                            isSmartPanelsMode = { _state.value.mode == ReaderMode.SMART_PANELS },
                            updateState = { _state.update(it) },
                        )
                    }
                }
            }
        }
    }

    /**
     * Set pages directly (useful for testing or when pages are passed from outside).
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
     * Handle all reader events.
     *
     * Events are grouped into domain-specific sealed sub-interfaces (see [ReaderEvent]).
     * This dispatcher routes each event to a focused per-domain handler, so adding a
     * new event only requires touching the relevant handler — and Kotlin's exhaustive
     * `when` over each sealed sub-interface gives compile-time enforcement that every
     * leaf in the domain is wired up.
     */
    fun onEvent(event: ReaderEvent) {
        when (event) {
            is ReaderEvent.Navigation -> handleNavigation(event)
            is ReaderEvent.ZoomControl -> handleZoom(event)
            is ReaderEvent.DisplayControl -> handleDisplay(event)
            is ReaderEvent.OverlayControl -> handleOverlay(event)
            is ReaderEvent.BrightnessControl -> handleBrightness(event)
            is ReaderEvent.AutoScrollControl -> handleAutoScroll(event)
            is ReaderEvent.SettingsControl -> handleSettings(event)
            is ReaderEvent.ColorFilterControl -> handleColorFilter(event)
            is ReaderEvent.SfxControl -> handleSfx(event)
            is ReaderEvent.OcrControl -> handleOcr(event)
            is ReaderEvent.ActionEvent -> handleAction(event)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Per-domain event handlers
    // ──────────────────────────────────────────────────────────────────────────

    private fun handleNavigation(event: ReaderEvent.Navigation) {
        when (event) {
            is ReaderEvent.OnPageChange -> changePage(event.page)
            is ReaderEvent.PageNavigation -> handlePageNavigation(event)
            is ReaderEvent.PanelNavigation -> handlePanelNavigation(event)
            is ReaderEvent.ChapterNavigation -> handleChapterNavigation(event)
        }
    }

    private fun handlePageNavigation(event: ReaderEvent.PageNavigation) {
        when (event) {
            ReaderEvent.NextPage -> navigatePage(1)
            ReaderEvent.PrevPage -> navigatePage(-1)
            ReaderEvent.FirstPage -> changePage(0)
            ReaderEvent.LastPage -> changePage(_state.value.pages.size - 1)
        }
    }

    private fun handlePanelNavigation(event: ReaderEvent.PanelNavigation) {
        when (event) {
            is ReaderEvent.OnPanelChange -> changePanel(event.panel)
            ReaderEvent.NextPanel -> navigatePanel(1)
            ReaderEvent.PrevPanel -> navigatePanel(-1)
            ReaderEvent.FirstPanel -> changePanel(0)
            ReaderEvent.LastPanel -> {
                val currentPage = _state.value.pages.getOrNull(_state.value.currentPage)
                changePanel((currentPage?.panels?.size ?: 0) - 1)
            }
        }
    }

    private fun handleChapterNavigation(event: ReaderEvent.ChapterNavigation) {
        when (event) {
            is ReaderEvent.LoadChapter -> loadChapterById(event.chapterId)
            ReaderEvent.NextChapter -> navigateNextChapter()
            ReaderEvent.PrevChapter -> navigatePreviousChapter()
        }
    }

    private fun handleZoom(event: ReaderEvent.ZoomControl) {
        when (event) {
            is ReaderEvent.OnZoomChange -> updateZoom(event.zoom)
            ReaderEvent.ZoomIn -> updateZoom(_state.value.zoomLevel + ZOOM_INCREMENT)
            ReaderEvent.ZoomOut -> updateZoom(_state.value.zoomLevel - ZOOM_INCREMENT)
            ReaderEvent.ResetZoom -> updateZoom(1f)
            ReaderEvent.ZoomToWidth -> updateZoom(1.5f)
            ReaderEvent.ZoomToHeight -> updateZoom(1.2f)
        }
    }

    private fun handleDisplay(event: ReaderEvent.DisplayControl) {
        when (event) {
            is ReaderEvent.OnModeChange -> changeReaderMode(event.mode)
            is ReaderEvent.OnDirectionChange -> updateReadingDirection(event.direction)
            ReaderEvent.RotateCW -> cyclePageRotation()
            ReaderEvent.ResetRotation -> _state.update { it.copy(pageRotation = PageRotation.NONE) }
        }
    }

    private fun handleOverlay(event: ReaderEvent.OverlayControl) {
        when (event) {
            ReaderEvent.ToggleMenu -> toggleMenu()
            ReaderEvent.ToggleGallery -> toggleGallery()
            is ReaderEvent.SetGalleryColumns -> setGalleryColumns(event.columns)
            ReaderEvent.ToggleFullscreen -> toggleFullscreen()
        }
    }

    private fun handleBrightness(event: ReaderEvent.BrightnessControl) {
        when (event) {
            is ReaderEvent.OnBrightnessChange -> updateBrightness(event.brightness)
            ReaderEvent.BrightnessUp -> updateBrightness(_state.value.brightness + BRIGHTNESS_INCREMENT)
            ReaderEvent.BrightnessDown -> updateBrightness(_state.value.brightness - BRIGHTNESS_INCREMENT)
        }
    }

    private fun handleAutoScroll(event: ReaderEvent.AutoScrollControl) {
        when (event) {
            ReaderEvent.ToggleAutoScroll -> toggleAutoScroll()
            is ReaderEvent.OnAutoScrollSpeedChange -> updateAutoScrollSpeed(event.speed)
            ReaderEvent.AutoScrollSpeedUp ->
                updateAutoScrollSpeed(_state.value.autoScrollSpeed + AUTO_SCROLL_INCREMENT)
            ReaderEvent.AutoScrollSpeedDown ->
                updateAutoScrollSpeed(_state.value.autoScrollSpeed - AUTO_SCROLL_INCREMENT)
        }
    }

    private fun handleSettings(event: ReaderEvent.SettingsControl) {
        when (event) {
            is ReaderEvent.ToggleSetting -> toggleSetting(event.setting)
            is ReaderEvent.UpdateTapZones -> updateTapZones(event.config)
        }
    }

    private fun handleColorFilter(event: ReaderEvent.ColorFilterControl) {
        when (event) {
            is ReaderEvent.SetColorFilterMode -> updateColorFilterMode(event.mode)
            is ReaderEvent.SetCustomTintColor -> updateCustomTintColor(event.color)
            is ReaderEvent.SetReaderBackgroundColor -> updateReaderBackgroundColor(event.color)
        }
    }

    private fun handleSfx(event: ReaderEvent.SfxControl) {
        when (event) {
            ReaderEvent.OpenSfxDialog -> _state.update { it.copy(showSfxDialog = true) }
            ReaderEvent.CloseSfxDialog -> _state.update { it.copy(showSfxDialog = false) }
            is ReaderEvent.TranslateSfx ->
                sfxDelegate.translateManualText(viewModelScope, event.sfxText) { _state.update(it) }
        }
    }

    private fun handleOcr(event: ReaderEvent.OcrControl) {
        when (event) {
            ReaderEvent.OpenOcrSearch -> {
                _state.update { it.copy(showOcrSearch = true, ocrQuery = "") }
                // Start background OCR for all pages, prioritizing the current page.
                ocrDelegate.startBatchOcr(
                    scope = viewModelScope,
                    pages = _state.value.pages,
                    currentPageIndex = _state.value.currentPage,
                    updateState = { _state.update(it) },
                )
            }
            ReaderEvent.CloseOcrSearch -> {
                ocrDelegate.cancelAll()
                _state.update { it.copy(showOcrSearch = false, isOcrRunning = false) }
            }
            is ReaderEvent.UpdateOcrQuery -> {
                _state.update { it.copy(ocrQuery = event.query) }
            }
        }
    }

    private fun handleAction(event: ReaderEvent.ActionEvent) {
        when (event) {
            ReaderEvent.ToggleBookmark -> toggleBookmark()
            ReaderEvent.SharePage -> sharePage()
            ReaderEvent.DismissError -> dismissError()
            ReaderEvent.Retry -> loadChapter()
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

            // Record page view for telemetry only when smart prefetch is active.
            // We record the page the user is leaving (previousPage) — that's the
            // page they actually viewed; the new page hasn't been rendered yet.
            _state.value.pages.getOrNull(previousPage)?.let { viewedPage ->
                prefetchDelegate.recordPageView(viewedPage)
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
        // Capture state before viewModelScope is cancelled so the worker reads
        // a consistent snapshot of incognito / current page / read flags.
        val currentState = _state.value

        // H-5 Fix: Use WorkManager to guarantee history + progress are persisted even if
        // the OS kills the process before a raw coroutine could complete.
        historyDelegate.enqueueExit(
            chapterId = chapterId,
            sessionReadAt = sessionReadAt,
            durationMs = durationMs,
            currentState = currentState,
        )

        discordDelegate.clearPresence(showBrowsing = true)
        autoSaveJob?.cancel()
        prefetchDelegate.cancel()
        panelDelegate.cancel()
        sfxDelegate.clear()
        ocrDelegate.cancelAll()
        prefetchDelegate.clearCache()
    }

    /**
     * Performs the final persistence work when the reader is closed.
     * Extracted to a suspend function so it can be tested directly without
     * going through the protected [onCleared] / WorkManager boundary.
     */
    @androidx.annotation.VisibleForTesting
    suspend fun cleanupOnExit(durationMs: Long, currentState: ReaderState) {
        historyDelegate.cleanupOnExit(
            chapterId = chapterId,
            sessionReadAt = sessionReadAt,
            durationMs = durationMs,
            currentState = currentState,
        )
    }


    companion object {
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
