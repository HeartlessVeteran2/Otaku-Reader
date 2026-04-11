package app.otakureader.feature.reader.viewmodel

import android.content.Context
import android.os.SystemClock
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
import app.otakureader.feature.reader.prefetch.SmartPrefetchManager
import app.otakureader.feature.reader.prefetch.AdaptiveChapterPrefetcher
import app.otakureader.domain.model.PageNavigationEvent
import app.otakureader.domain.model.PrefetchStrategy
import app.otakureader.core.discord.DiscordRpcService
import app.otakureader.core.discord.ReadingStatus
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.data.download.ChapterDownloadRequest
import app.otakureader.data.download.DownloadManager
import app.otakureader.feature.reader.panel.PanelDetectionService
import coil3.ImageLoader
import coil3.request.ImageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

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
    private val imageLoader: ImageLoader,
    private val downloadManager: DownloadManager,
    private val downloadPreferences: DownloadPreferences,
    private val discordRpcService: DiscordRpcService,
    private val generalPreferences: GeneralPreferences,
    private val behaviorTracker: ReadingBehaviorTracker,
    private val smartPrefetchManager: SmartPrefetchManager,
    private val chapterPrefetcher: AdaptiveChapterPrefetcher,
    private val panelDetectionService: PanelDetectionService,
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

    /** Cached global preload settings, loaded once during init to avoid repeated DataStore reads. */
    private var cachedPreloadBefore: Int = ReaderSettingsRepository.DEFAULT_PRELOAD_PAGES
    private var cachedPreloadAfter: Int = ReaderSettingsRepository.DEFAULT_PRELOAD_PAGES

    /** Cached smart prefetch settings. */
    private var cachedSmartPrefetchEnabled: Boolean = false
    private var cachedPrefetchStrategy: PrefetchStrategy = PrefetchStrategy.Balanced
    private var cachedAdaptiveLearningEnabled: Boolean = false
    private var cachedPrefetchAdjacentChapters: Boolean = false
    private var cachedPrefetchOnlyOnWiFi: Boolean = true

    /** Cached Discord RPC enabled state, loaded once to avoid DataStore reads on every page change. */
    private var cachedDiscordRpcEnabled: Boolean = false

    private var autoSaveJob: Job? = null
    private var preloadJob: Job? = null
    private var panelDetectionJob: Job? = null

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

    /** Independent scope used for cleanup work that must survive viewModelScope cancellation. */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        loadSettings()
        loadChapter()
        cacheDiscordPreference()
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

            // Load all settings concurrently
            val mode = settingsRepository.readerMode.first()
            val brightness = settingsRepository.brightness.first()
            val keepScreenOn = settingsRepository.keepScreenOn.first()
            val showPageNumber = settingsRepository.showPageNumber.first()
            val direction = settingsRepository.readingDirection.first()
            val volumeKeysEnabled = settingsRepository.volumeKeysEnabled.first()
            val volumeKeysInverted = settingsRepository.volumeKeysInverted.first()
            val fullscreen = settingsRepository.fullscreen.first()
            val incognitoMode = settingsRepository.incognitoMode.first()
            val colorFilterMode = settingsRepository.colorFilterMode.first()
            val customTintColor = settingsRepository.customTintColor.first()
            val cropBordersEnabled = try {
                settingsRepository.cropBordersEnabled.first()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            val imageQuality = try {
                settingsRepository.imageQuality.first()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ImageQuality.ORIGINAL
            }
            val dataSaverEnabled = try {
                settingsRepository.dataSaverEnabled.first()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }

            // Load overlay settings from DataStore
            val showReadingTimer = try {
                settingsRepository.showReadingTimer.first()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            val showBatteryTime = try {
                settingsRepository.showBatteryTime.first()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }

            // Cache preload settings so preloadPages() doesn't read DataStore per page change (#264)
            cachedPreloadBefore = try {
                settingsRepository.preloadPagesBefore.first()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ReaderSettingsRepository.DEFAULT_PRELOAD_PAGES
            }
            cachedPreloadAfter = try {
                settingsRepository.preloadPagesAfter.first()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ReaderSettingsRepository.DEFAULT_PRELOAD_PAGES
            }

            // Cache smart prefetch settings
            cachedSmartPrefetchEnabled = try {
                settingsRepository.smartPrefetchEnabled.first()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                true
            }
            cachedPrefetchStrategy = try {
                val ordinal = settingsRepository.prefetchStrategyOrdinal.first()
                PrefetchStrategy.fromOrdinal(ordinal)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                PrefetchStrategy.Balanced
            }
            cachedAdaptiveLearningEnabled = try {
                settingsRepository.adaptiveLearningEnabled.first()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                true
            }
            cachedPrefetchAdjacentChapters = try {
                settingsRepository.prefetchAdjacentChapters.first()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                false
            }
            cachedPrefetchOnlyOnWiFi = try {
                settingsRepository.prefetchOnlyOnWiFi.first()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                false
            }

            // Load new settings with safe fallbacks
            val showContentInCutout = settingsRepository.showContentInCutout.first()
            val backgroundColor = settingsRepository.backgroundColor.first()
            val animatePageTransitions = settingsRepository.animatePageTransitions.first()
            val showReadingModeOverlay = settingsRepository.showReadingModeOverlay.first()
            val showTapZonesOverlay = settingsRepository.showTapZonesOverlay.first()
            val readerScale = settingsRepository.readerScale.first()
            val autoZoomWideImages = settingsRepository.autoZoomWideImages.first()
            val invertTapZones = settingsRepository.invertTapZones.first()
            val webtoonSidePadding = settingsRepository.webtoonSidePadding.first()
            val webtoonMenuHideSensitivity = settingsRepository.webtoonMenuHideSensitivity.first()
            val webtoonDoubleTapZoom = settingsRepository.webtoonDoubleTapZoom.first()
            val webtoonDisableZoomOut = settingsRepository.webtoonDisableZoomOut.first()
            val einkFlashOnPageChange = settingsRepository.einkFlashOnPageChange.first()
            val einkBlackAndWhite = settingsRepository.einkBlackAndWhite.first()
            val skipReadChapters = settingsRepository.skipReadChapters.first()
            val skipFilteredChapters = settingsRepository.skipFilteredChapters.first()
            val skipDuplicateChapters = settingsRepository.skipDuplicateChapters.first()
            val alwaysShowChapterTransition = settingsRepository.alwaysShowChapterTransition.first()
            val showActionsOnLongTap = settingsRepository.showActionsOnLongTap.first()
            val savePagesToSeparateFolders = settingsRepository.savePagesToSeparateFolders.first()

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
                updateDiscordPresence(manga.title, chapter.name, pages.size)

                // Start preloading adjacent pages
                if (pages.isNotEmpty()) {
                    preloadPages(_state.value.currentPage)
                }

                // Start panel detection when in Smart Panels mode
                if (_state.value.mode == ReaderMode.SMART_PANELS && pages.isNotEmpty()) {
                    detectPanelsForPages(pages)
                }

            } catch (e: Exception) {
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
        }
    }

    private fun changePage(page: Int) {
        val validPage = page.coerceIn(0, (_state.value.pages.size - 1).coerceAtLeast(0))
        if (validPage != _state.value.currentPage) {
            val previousPage = _state.value.currentPage

            // Record navigation event for behavior tracking
            if (cachedAdaptiveLearningEnabled) {
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
            if (cachedSmartPrefetchEnabled) {
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
            preloadPages(validPage)
            scheduleProgressSave()

            // Update Discord presence with current page
            val manga = currentManga
            val chapter = currentChapter
            if (manga != null && chapter != null) {
                updateDiscordPresence(
                    manga.title, chapter.name, _state.value.pages.size, validPage + 1
                )
            }

            val pages = _state.value.pages
            if (pages.isNotEmpty() && validPage == pages.lastIndex) {
                maybeDeleteAfterReading()
            }

            // Trigger download-ahead when user is near end of chapter
            maybeDownloadNextChapter(validPage, pages.size)
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
                detectPanelsForPages(pages)
            }
        } else {
            // Cancel any in-progress panel detection when leaving Smart Panels mode
            panelDetectionJob?.cancel()
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
     * Detect panels for a list of pages when Smart Panels mode is active.
     *
     * Pages are processed in order of proximity to the current page so the user
     * sees panel navigation as soon as possible. Detection continues in the
     * background for the remaining pages until the mode changes or the ViewModel
     * is cleared.
     */
    private fun detectPanelsForPages(pages: List<ReaderPage>) {
        panelDetectionJob?.cancel()
        panelDetectionJob = viewModelScope.launch {
            val readingDirection = _state.value.readingDirection

            // Process pages nearest to the current page first for a fast first result
            val currentPageIndex = _state.value.currentPage
            val sortedIndices = pages.indices.sortedBy { abs(it - currentPageIndex) }

            for (index in sortedIndices) {
                // Stop if the user has left Smart Panels mode
                if (_state.value.mode != ReaderMode.SMART_PANELS) break

                val page = _state.value.pages.getOrNull(index) ?: continue
                // Skip pages that already have panels detected or lack an image URL
                if (page.panels.isNotEmpty() || page.imageUrl == null) continue

                val detectedPanels = panelDetectionService.detectPanelsFromUrl(
                    imageUrl = page.imageUrl,
                    readingDirection = readingDirection
                )

                if (detectedPanels.isNotEmpty()) {
                    _state.update { currentState ->
                        if (index >= currentState.pages.size) return@update currentState
                        currentState.copy(
                            pages = currentState.pages.mapIndexed { i, p ->
                                if (i == index) p.copy(panels = detectedPanels) else p
                            }
                        )
                    }
                }
            }
        }
    }

    /**
     * Preload pages ahead and behind current page for smooth scrolling.
     * Uses smart prefetch if enabled, otherwise falls back to manual preload settings.
     * Integrates with Coil's image prefetch to warm up the image cache for upcoming pages.
     */
    private fun preloadPages(currentPage: Int) {
        preloadJob?.cancel()
        preloadJob = viewModelScope.launch {
            val pages = _state.value.pages
            val manga = currentManga

            if (cachedSmartPrefetchEnabled) {
                // Use smart prefetch manager with behavior-based strategy
                val behavior = behaviorTracker.getBehaviorForManga(mangaId)
                smartPrefetchManager.prefetchPages(
                    pages = pages,
                    currentPage = currentPage,
                    strategy = cachedPrefetchStrategy,
                    behavior = behavior,
                    onlyOnWiFi = cachedPrefetchOnlyOnWiFi,
                    scope = viewModelScope
                )

                // Prefetch adjacent chapters if enabled
                if (cachedPrefetchAdjacentChapters) {
                    chapterPrefetcher.prefetchAdjacentChapters(
                        currentChapterId = chapterId,
                        mangaId = mangaId,
                        currentPage = currentPage,
                        totalPages = pages.size,
                        strategy = cachedPrefetchStrategy,
                        behavior = behavior,
                        scope = viewModelScope,
                        sourceId = currentManga?.sourceId?.toString()
                    )
                }
            } else {
                // Fallback to manual preload settings (legacy behavior)
                val preloadBefore = manga?.preloadPagesBefore ?: cachedPreloadBefore
                val preloadAfter = manga?.preloadPagesAfter ?: cachedPreloadAfter

                val preloadRange = (currentPage - preloadBefore)..(currentPage + preloadAfter)

                preloadRange.forEach { index ->
                    if (index in pages.indices && index != currentPage) {
                        val page = pages[index]
                        val imageUrl = page.imageUrl

                        // Prefetch image using Coil's prefetch API
                        if (!imageUrl.isNullOrBlank()) {
                            try {
                                val request = ImageRequest.Builder(context)
                                    .data(imageUrl)
                                    .build()

                                // Enqueue prefetch request (non-blocking, returns immediately)
                                imageLoader.enqueue(request)
                            } catch (e: Exception) {
                                // Silently ignore prefetch failures - they're not critical
                                // The image will be loaded on-demand when the user navigates to the page
                            }
                        }
                    }
                }
            }
        }
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

    /**
     * Downloads the next chapter when the user is near the end of the current chapter
     * and download-ahead preference is enabled.
     */
    private fun maybeDownloadNextChapter(currentPage: Int, totalPages: Int) {
        if (totalPages == 0) return

        // Only trigger when user is in the last 20% of the chapter
        val progressThreshold = 0.8
        val currentProgress = currentPage.toFloat() / totalPages
        if (currentProgress < progressThreshold) return

        viewModelScope.launch {
            val downloadAheadEnabled = downloadPreferences.downloadAheadWhileReading.first()
            if (!downloadAheadEnabled) return@launch

            // Check WiFi requirement if set
            val onlyOnWifi = downloadPreferences.downloadAheadOnlyOnWifi.first()
            if (onlyOnWifi) {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
                    as android.net.ConnectivityManager
                val networkInfo = connectivityManager.activeNetworkInfo
                val isWifi = networkInfo?.type == android.net.ConnectivityManager.TYPE_WIFI
                if (!isWifi) return@launch
            }

            // Get all chapters to find the next one
            val chapters = chapterRepository.getChaptersByMangaId(mangaId).first()
            val currentChapterIndex = chapters.indexOfFirst { it.id == chapterId }
            if (currentChapterIndex == -1 || currentChapterIndex >= chapters.size - 1) return@launch
            
            val nextChapter = chapters[currentChapterIndex + 1]

            // Check if already downloaded or queued
            val existingDownload = downloadManager.downloads.first()
                .find { it.chapterId == nextChapter.id }
            if (existingDownload != null) return@launch

            // Check if already downloaded to storage
            val manga = currentManga ?: mangaRepository.getMangaById(mangaId).first() ?: return@launch
            val source = sourceRepository.getSourceById(manga.sourceId) ?: return@launch
            
            val isDownloaded = DownloadProvider.isChapterDownloaded(
                context, source.name, manga.title, nextChapter.name
            )
            if (isDownloaded) return@launch

            // Get pages for the chapter
            val pages = runCatching {
                pageLoader.loadPages(nextChapter.id, nextChapter.url)
            }.getOrNull() ?: return@launch

            val pageUrls = pages.mapNotNull { it.imageUrl }
            if (pageUrls.isEmpty()) return@launch

            // Queue the download
            val request = ChapterDownloadRequest(
                chapterId = nextChapter.id,
                mangaId = mangaId,
                chapterTitle = nextChapter.name,
                mangaTitle = manga.title,
                sourceName = source.name,
                pageUrls = pageUrls
            )
            downloadManager.enqueue(request)
        }
    }

    override fun onCleared() {
        super.onCleared()
        val durationMs = SystemClock.elapsedRealtime() - sessionStartMs
        // Capture state before the ViewModel is torn down so the cleanupScope coroutine can
        // safely read it after viewModelScope (and therefore state updates) are cancelled.
        val currentState = _state.value
        // Use cleanupScope (not viewModelScope) so the coroutine is not cancelled along with the ViewModel.
        cleanupScope.launch {
            cleanupOnExit(durationMs, currentState)
        }
        // Clear Discord Rich Presence when reader closes
        discordRpcService.clearReadingPresence(showBrowsing = true)
        autoSaveJob?.cancel()
        preloadJob?.cancel()
        panelDetectionJob?.cancel()
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

    /**
     * Update Discord Rich Presence if the feature is enabled.
     * Uses the cached preference value to avoid DataStore reads on every call.
     */
    private fun updateDiscordPresence(
        mangaTitle: String,
        chapterName: String,
        totalPages: Int,
        currentPage: Int? = null
    ) {
        if (!cachedDiscordRpcEnabled) return
        if (currentPage == null) {
            discordRpcService.resetSessionTimer()
        }
        discordRpcService.updateReadingPresence(
            mangaTitle = mangaTitle,
            chapterName = chapterName,
            status = ReadingStatus.READING,
            page = currentPage,
            totalPages = totalPages
        )
    }

    /** Load Discord RPC preference once to avoid repeated DataStore reads. */
    private fun cacheDiscordPreference() {
        viewModelScope.launch {
            generalPreferences.discordRpcEnabled.collectLatest { enabled ->
                cachedDiscordRpcEnabled = enabled

                if (!enabled) {
                    discordRpcService.clearReadingPresence(showBrowsing = false)
                    return@collectLatest
                }

                val manga = currentManga
                val chapter = currentChapter
                val pages = _state.value.pages
                if (manga != null && chapter != null) {
                    val page = if (pages.isNotEmpty()) _state.value.currentPage + 1 else null
                    updateDiscordPresence(
                        mangaTitle = manga.title,
                        chapterName = chapter.name,
                        totalPages = pages.size,
                        currentPage = page
                    )
                }
            }
        }
    }

    companion object {
        private const val MIN_ZOOM = 0.5f
        private const val MAX_ZOOM = 5f
        private const val PROGRESS_SAVE_DELAY = 3000L // 3 seconds
        const val ZOOM_INCREMENT = 0.25f
        const val BRIGHTNESS_INCREMENT = 0.1f
        const val AUTO_SCROLL_INCREMENT = 50f
    }
}

/**
 * Effects emitted by the reader
 */
sealed interface ReaderEffect {
    data object NavigateBack : ReaderEffect
    data class ShowSnackbar(val message: String) : ReaderEffect
    data class NavigateToChapter(val chapterId: Long) : ReaderEffect
}
