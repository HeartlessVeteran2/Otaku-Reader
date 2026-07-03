package app.otakureader.feature.reader

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.ReaderPreferences
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.model.PageNavigationEvent
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.PageBookmarkRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.domain.model.PageBookmark
import app.otakureader.domain.model.ColorFilterMode
import app.otakureader.domain.model.ReaderMode
import app.otakureader.domain.model.ReaderOrientation
import app.otakureader.feature.reader.model.ReaderPage
import app.otakureader.domain.model.ReadingDirection
import app.otakureader.domain.model.ReaderComment
import app.otakureader.domain.repository.ReaderCommentRepository
import app.otakureader.domain.repository.ReaderSettingsRepository
import app.otakureader.domain.repository.TrackerSyncRepository
import app.otakureader.domain.tracking.TrackManager
import app.otakureader.domain.tracking.TrackRepository
import app.otakureader.feature.reader.prefetch.ReadingBehaviorTracker
import app.otakureader.feature.reader.viewmodel.delegate.ReaderChapterLoaderDelegate
import app.otakureader.feature.reader.viewmodel.delegate.ReaderDeleteAfterReadDelegate
import app.otakureader.feature.reader.viewmodel.delegate.ReaderDiscordDelegate
import app.otakureader.feature.reader.viewmodel.delegate.ReaderDisplayDelegate
import app.otakureader.feature.reader.viewmodel.delegate.ReaderDownloadAheadDelegate
import app.otakureader.feature.reader.viewmodel.delegate.ReaderHistoryDelegate
import app.otakureader.feature.reader.viewmodel.delegate.ReaderPrefetchDelegate
import app.otakureader.feature.reader.viewmodel.delegate.ReaderSettingsLoaderDelegate
import app.otakureader.feature.reader.R
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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
 *  | [ReaderDiscordDelegate]        | Discord rich presence                         |
 *  | [ReaderDownloadAheadDelegate]  | Download-ahead trigger                        |
 */
@HiltViewModel
@Suppress("LargeClass")
class ReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val pageBookmarkRepository: PageBookmarkRepository,
    private val settingsRepository: ReaderSettingsRepository,
    private val generalPreferences: GeneralPreferences,
    private val behaviorTracker: ReadingBehaviorTracker,
    private val settingsLoaderDelegate: ReaderSettingsLoaderDelegate,
    private val chapterLoaderDelegate: ReaderChapterLoaderDelegate,
    private val historyDelegate: ReaderHistoryDelegate,
    private val discordDelegate: ReaderDiscordDelegate,
    private val prefetchDelegate: ReaderPrefetchDelegate,
    private val downloadAheadDelegate: ReaderDownloadAheadDelegate,
    private val deleteAfterReadDelegate: ReaderDeleteAfterReadDelegate,
    private val trackerSyncRepository: TrackerSyncRepository,
    private val readerCommentRepository: ReaderCommentRepository,
    private val trackRepository: TrackRepository,
    private val trackManager: TrackManager,
    private val displayDelegate: ReaderDisplayDelegate,
    private val readerPreferences: ReaderPreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val mangaId: Long = checkNotNull(savedStateHandle["mangaId"])
    private var chapterId: Long = checkNotNull(savedStateHandle["chapterId"])

    private val _state = MutableStateFlow(ReaderState(currentChapterId = chapterId))
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _effect = Channel<ReaderEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private val sharing = SharingStarted.WhileSubscribed(5_000L)
    val pageState: StateFlow<PageState> = state.map { it.pageState }.stateIn(viewModelScope, sharing, ReaderState().pageState)
    val overlayState: StateFlow<OverlayState> = state.map { it.overlayState }.stateIn(viewModelScope, sharing, ReaderState().overlayState)
    val displayState: StateFlow<DisplayState> = state.map { it.displayState }.stateIn(viewModelScope, sharing, ReaderState().displayState)
    val webtoonState: StateFlow<WebtoonState> = state.map { it.webtoonState }.stateIn(viewModelScope, sharing, ReaderState().webtoonState)

    /** All chapters for this manga — used by the chapter-list overlay. */
    val chapters: StateFlow<List<Chapter>> = chapterRepository
        .getChaptersByMangaId(mangaId)
        .stateIn(viewModelScope, sharing, emptyList())

    // ── Reader comments overlay ─────────────────────────────────────────────

    /** Book-level comments — visible from any chapter of this manga. */
    val bookComments: StateFlow<List<ReaderComment>> = readerCommentRepository
        .observeBookComments(mangaId)
        .stateIn(viewModelScope, sharing, emptyList())

    /**
     * Comments for the chapter currently on screen. flatMapLatest re-subscribes on
     * every chapter switch, so navigating chapters changes the thread automatically.
     */
    val chapterComments: StateFlow<List<ReaderComment>> = state
        .map { it.currentChapterId }
        .distinctUntilChanged()
        .flatMapLatest { id -> readerCommentRepository.observeChapterComments(id) }
        .stateIn(viewModelScope, sharing, emptyList())

    /** The current chapter's private note (ChapterEntity.userNotes), editable in the overlay. */
    val currentChapterNote: StateFlow<String> = combine(
        chapters,
        state.map { it.currentChapterId }.distinctUntilChanged(),
    ) { allChapters, id ->
        allChapters.find { it.id == id }?.userNotes.orEmpty()
    }.stateIn(viewModelScope, sharing, "")

    /** "Open <tracker>" buttons for every linked tracker that has a remote page URL. */
    val externalDiscussionLinks: StateFlow<List<ExternalDiscussionLink>> = trackRepository
        .observeEntriesForManga(mangaId)
        .map { entries ->
            entries.mapNotNull { entry ->
                val name = trackManager.get(entry.trackerId)?.name
                if (name != null && entry.remoteUrl.isNotBlank()) {
                    ExternalDiscussionLink(trackerName = name, url = entry.remoteUrl)
                } else {
                    null
                }
            }
        }
        .stateIn(viewModelScope, sharing, emptyList())

    fun addComment(body: String, chapterScoped: Boolean) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            readerCommentRepository.addComment(
                mangaId = mangaId,
                chapterId = if (chapterScoped) _state.value.currentChapterId else null,
                body = trimmed,
            )
        }
    }

    fun deleteComment(comment: ReaderComment) {
        viewModelScope.launch { readerCommentRepository.deleteComment(comment.id) }
    }

    fun saveChapterNote(text: String) {
        val targetChapterId = _state.value.currentChapterId
        viewModelScope.launch {
            chapterRepository.updateChapterNotes(targetChapterId, text.trim().ifEmpty { null })
        }
    }

    private var currentManga: Manga? = null
    private var currentChapter: Chapter? = null

    private var autoSaveJob: Job? = null

    /**
     * The in-flight chapter load. Tracked so that switching chapters cancels the previous
     * load — otherwise two concurrent loads race and the slower (stale) one can overwrite
     * the newer chapter's pages in state.
     */
    private var loadChapterJob: Job? = null

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
        observePageBookmarks()
        observeVisualEffects()
        discordDelegate.startObserving(
            scope = viewModelScope,
            getCurrentManga = { currentManga },
            getCurrentChapter = { currentChapter },
            getState = { _state.value },
        )
        observeSettingsWriteFailures()
        readerPreferences.presets
            .onEach { presets -> _state.update { it.copy(presets = presets) } }
            .launchIn(viewModelScope)
        // Wire display delegate to shared state and scope
        displayDelegate.stateFlow = _state
        displayDelegate.scope = viewModelScope
    }

    /**
     * Observe bookmark status for the current chapter and update state reactively.
     */
    private fun observePageBookmarks() {
        viewModelScope.launch {
            _state
                .map { it.currentPage }
                .distinctUntilChanged()
                .flatMapLatest { page ->
                    pageBookmarkRepository.isPageBookmarked(chapterId, page)
                }
                .collect { isBookmarked ->
                    _state.update { it.copy(isCurrentPageBookmarked = isBookmarked) }
                }
        }
    }

    /**
     * Load saved reader settings with per-manga overrides (#260, #264).
     */
    @Suppress("LongMethod")
    private fun loadSettings() {
        viewModelScope.launch {
            // Load manga first to check for per-manga overrides.
            val manga = mangaRepository.getMangaById(mangaId)
            currentManga = manga
            val settingsState = settingsLoaderDelegate.load(_state.value, manga)
            // Read orientation here rather than in ReaderSettingsLoaderDelegate.load(): that
            // method's coroutineScope lambda is already near the JVM 64KB method-size limit, so
            // adding another async read there overflows it ("Method too large").
            val orientation = try {
                settingsRepository.readerOrientation.first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReaderOrientation.DEFAULT
            }
            // Merge only settings fields into current state to avoid overwriting
            // pages/chapter data loaded concurrently by loadChapter().
            _state.update { current ->
                current.copy(
                    mode = settingsState.mode,
                    readerOrientation = orientation,
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
                    landscapeZoomScaleType = settingsState.landscapeZoomScaleType,
                    pageLayout = settingsState.pageLayout,
                    webtoonSidePadding = settingsState.webtoonSidePadding,
                    webtoonGapDp = settingsState.webtoonGapDp,
                    webtoonMenuHideSensitivity = settingsState.webtoonMenuHideSensitivity,
                    webtoonDoubleTapZoom = settingsState.webtoonDoubleTapZoom,
                    webtoonDisableZoomOut = settingsState.webtoonDisableZoomOut,
                    webtoonPinchToZoomEnabled = settingsState.webtoonPinchToZoomEnabled,
                    webtoonScaleType = settingsState.webtoonScaleType,
                    einkFlashOnPageChange = settingsState.einkFlashOnPageChange,
                    einkBlackAndWhite = settingsState.einkBlackAndWhite,
                    skipReadChapters = settingsState.skipReadChapters,
                    skipFilteredChapters = settingsState.skipFilteredChapters,
                    skipDuplicateChapters = settingsState.skipDuplicateChapters,
                    alwaysShowChapterTransition = settingsState.alwaysShowChapterTransition,
                    showActionsOnLongTap = settingsState.showActionsOnLongTap,
                    savePagesToSeparateFolders = settingsState.savePagesToSeparateFolders,
                    secureScreen = settingsState.secureScreen,
                    navigationModePager = settingsState.navigationModePager,
                    navigationModeWebtoon = settingsState.navigationModeWebtoon,
                    tapInvertModePager = settingsState.tapInvertModePager,
                    tapInvertModeWebtoon = settingsState.tapInvertModeWebtoon,
                    smallerTapZone = settingsState.smallerTapZone,
                )
            }
        }
    }

    private fun loadChapter() {
        loadChapterJob?.cancel()
        // Capture the target ID now so the coroutine can guard against stale writes.
        // If loadChapterById() is called again before this job finishes, the new job
        // updates this.chapterId — the old job detects the mismatch and discards its result.
        val targetChapterId = chapterId
        loadChapterJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = chapterLoaderDelegate.load(mangaId, targetChapterId)) {
                is ReaderChapterLoaderDelegate.Result.NotFound -> {
                    if (targetChapterId != chapterId) return@launch
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
                is ReaderChapterLoaderDelegate.Result.Failure -> {
                    if (targetChapterId != chapterId) return@launch
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = result.cause.message ?: "Failed to load chapter",
                        )
                    }
                }
                is ReaderChapterLoaderDelegate.Result.Success ->
                    handleChapterLoadSuccess(result, targetChapterId)
            }
        }
    }

    private suspend fun handleChapterLoadSuccess(
        result: ReaderChapterLoaderDelegate.Result.Success,
        targetChapterId: Long,
    ) {
        // Discard the result if the user navigated to a different chapter while
        // this load was in flight — prevents stale pages overwriting the new chapter.
        if (targetChapterId != chapterId) return
        currentManga = result.manga
        currentChapter = result.chapter
        observeContentType(result.manga)

        val pages = result.pages
        val initialPage = result.chapter.lastPageRead
            .coerceIn(0, (pages.size - 1).coerceAtLeast(0))

        _state.update { current ->
            current.copy(
                pages = pages,
                currentPage = initialPage,
                isLoading = false,
                mangaTitle = result.manga.title,
                chapterTitle = result.chapter.name,
                readerBackgroundColor = result.manga.readerBackgroundColor,
            )
        }

        // Update download badge so the overlay can show/hide the download button.
        val isDownloaded = try {
            downloadAheadDelegate.isChapterDownloaded(result.manga, result.chapter)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (chapterId != _state.value.currentChapterId) return
        _state.update { it.copy(isCurrentChapterDownloaded = isDownloaded) }

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
                is ReaderEvent.SkipPages -> navigatePage(event.count)
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
            is ReaderEvent.OnZoomChange -> displayDelegate.updateZoom(event.zoom)
            ReaderEvent.ZoomIn -> displayDelegate.updateZoom(_state.value.zoomLevel + ZOOM_INCREMENT)
            ReaderEvent.ZoomOut -> displayDelegate.updateZoom(_state.value.zoomLevel - ZOOM_INCREMENT)
            ReaderEvent.ResetZoom -> displayDelegate.updateZoom(1f)
            ReaderEvent.ZoomToWidth -> displayDelegate.updateZoom(1.5f)
            ReaderEvent.ZoomToHeight -> displayDelegate.updateZoom(1.2f)
        }
    }

    private fun handleDisplay(event: ReaderEvent.DisplayControl) {
        when (event) {
            is ReaderEvent.OnModeChange -> displayDelegate.changeReaderMode(event.mode)
            is ReaderEvent.OnOrientationChange -> displayDelegate.changeOrientation(event.orientation)
            is ReaderEvent.OnDirectionChange -> displayDelegate.updateReadingDirection(event.direction)
            ReaderEvent.RotateCW -> displayDelegate.cyclePageRotation()
            ReaderEvent.ResetRotation -> displayDelegate.resetRotation()
        }
    }

    private fun handleOverlay(event: ReaderEvent.OverlayControl) {
        when (event) {
            ReaderEvent.ToggleMenu -> displayDelegate.toggleMenu()
            ReaderEvent.ToggleGallery -> displayDelegate.toggleGallery()
            is ReaderEvent.SetGalleryColumns -> displayDelegate.setGalleryColumns(event.columns)
            ReaderEvent.ToggleFullscreen -> displayDelegate.toggleFullscreen()
            ReaderEvent.ToggleSettingsOverlay -> displayDelegate.toggleSettingsOverlay()
            ReaderEvent.ToggleChapterListOverlay -> displayDelegate.toggleChapterListOverlay()
            ReaderEvent.ToggleCommentsOverlay -> displayDelegate.toggleCommentsOverlay()
        }
    }

    private fun handleBrightness(event: ReaderEvent.BrightnessControl) {
        when (event) {
            is ReaderEvent.OnBrightnessChange -> displayDelegate.updateBrightness(event.brightness)
            ReaderEvent.BrightnessUp -> displayDelegate.updateBrightness(_state.value.brightness + BRIGHTNESS_INCREMENT)
            ReaderEvent.BrightnessDown -> displayDelegate.updateBrightness(_state.value.brightness - BRIGHTNESS_INCREMENT)
        }
    }

    private fun handleAutoScroll(event: ReaderEvent.AutoScrollControl) {
        when (event) {
            ReaderEvent.ToggleAutoScroll -> displayDelegate.toggleAutoScroll()
            is ReaderEvent.OnAutoScrollSpeedChange -> displayDelegate.updateAutoScrollSpeed(event.speed)
            ReaderEvent.AutoScrollSpeedUp ->
                displayDelegate.updateAutoScrollSpeed(_state.value.autoScrollSpeed + AUTO_SCROLL_INCREMENT)
            ReaderEvent.AutoScrollSpeedDown ->
                displayDelegate.updateAutoScrollSpeed(_state.value.autoScrollSpeed - AUTO_SCROLL_INCREMENT)
        }
    }

    private fun handleSettings(event: ReaderEvent.SettingsControl) {
        when (event) {
            is ReaderEvent.ToggleSetting -> displayDelegate.toggleSetting(event.setting)
            is ReaderEvent.UpdateTapZones -> displayDelegate.updateTapZones(event.config)
            is ReaderEvent.SetBackgroundColor -> displayDelegate.updateBackgroundColor(event.color)
            is ReaderEvent.SetReaderScale -> displayDelegate.updateReaderScale(event.scale)
            is ReaderEvent.SetWebtoonSidePadding -> displayDelegate.updateWebtoonSidePadding(event.padding)
            is ReaderEvent.SetNavigationModePager -> displayDelegate.updateNavigationModePager(event.mode)
            is ReaderEvent.SetNavigationModeWebtoon -> displayDelegate.updateNavigationModeWebtoon(event.mode)
            is ReaderEvent.SetTapInvertModePager -> displayDelegate.updateTapInvertModePager(event.mode)
            is ReaderEvent.SetTapInvertModeWebtoon -> displayDelegate.updateTapInvertModeWebtoon(event.mode)
            is ReaderEvent.SetWebtoonScaleType -> displayDelegate.updateWebtoonScaleType(event.scaleType)
            is ReaderEvent.SetLandscapeZoomScaleType -> displayDelegate.updateLandscapeZoomScaleType(event.scaleType)
            is ReaderEvent.SetPageLayout -> displayDelegate.updatePageLayout(event.layout)
        }
    }

    private fun handleColorFilter(event: ReaderEvent.ColorFilterControl) {
        when (event) {
            is ReaderEvent.SetColorFilterMode -> displayDelegate.updateColorFilterMode(event.mode)
            is ReaderEvent.SetCustomTintColor -> displayDelegate.updateCustomTintColor(event.color)
            is ReaderEvent.SetReaderBackgroundColor -> {
                displayDelegate.updateReaderBackgroundColor(event.color)
                currentManga?.let { manga ->
                    val updated = manga.copy(readerBackgroundColor = event.color)
                    currentManga = updated
                    viewModelScope.launch { mangaRepository.updateManga(updated) }
                }
            }
        }
    }

    private fun handleAction(event: ReaderEvent.ActionEvent) {
        when (event) {
            ReaderEvent.ToggleBookmark -> toggleBookmark()
            ReaderEvent.SharePage -> sharePage()
            ReaderEvent.DismissError -> dismissError()
            ReaderEvent.Retry -> loadChapter()
            is ReaderEvent.ApplyPreset -> viewModelScope.launch {
                readerPreferences.applyPreset(event.preset)
                loadSettings()
            }
            is ReaderEvent.OnPageLongPress -> _state.update {
                it.copy(isPageContextMenuVisible = true, contextMenuPageUrl = event.pageUrl)
            }
            ReaderEvent.DismissPageContextMenu -> _state.update {
                it.copy(isPageContextMenuVisible = false, contextMenuPageUrl = null)
            }
            ReaderEvent.SavePageImage -> savePageImage()
            ReaderEvent.SetPageAsCover -> setPageAsCover()
            ReaderEvent.DownloadCurrentChapter -> downloadCurrentChapter()
        }
    }

    private fun downloadCurrentChapter() {
        val manga = currentManga ?: return
        val chapter = currentChapter ?: return
        viewModelScope.launch {
            val enqueued = try {
                downloadAheadDelegate.enqueueCurrentChapter(manga, chapter)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (enqueued) {
                _effect.send(ReaderEffect.ShowSnackbar(messageResId = R.string.reader_chapter_download_queued))
            }
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

            // Update Discord presence with current page
            val manga = currentManga
            val chapter = currentChapter
            if (manga != null && chapter != null) {
                discordDelegate.updatePresence(manga.title, chapter.name, pages.size, validPage + 1)
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

    private fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun loadChapterById(chapterId: Long) {
        this.chapterId = chapterId
        _state.update { it.copy(currentChapterId = chapterId) }
        loadChapter()
    }

    private fun toggleBookmark() {
        val state = _state.value
        val currentPage = state.currentPage
        viewModelScope.launch {
            if (state.isCurrentPageBookmarked) {
                pageBookmarkRepository.removeBookmark(chapterId, currentPage)
                _effect.send(ReaderEffect.ShowSnackbar("Page bookmark removed"))
            } else {
                pageBookmarkRepository.addBookmark(
                    PageBookmark(
                        mangaId = mangaId,
                        chapterId = chapterId,
                        pageIndex = currentPage
                    )
                )
                _effect.send(ReaderEffect.ShowSnackbar("Page bookmarked"))
            }
        }
    }

    /**
     * Schedule auto-save of reading progress with debouncing to prevent excessive database writes.
     * Multiple rapid page changes will only trigger one save after the delay period.
     */
    private fun sharePage() {
        val pageUrl = _state.value.contextMenuPageUrl ?: return
        _state.update { it.copy(isPageContextMenuVisible = false, contextMenuPageUrl = null) }
        viewModelScope.launch {
            _effect.send(ReaderEffect.SharePage(pageUrl))
        }
    }

    private fun savePageImage() {
        val pageUrl = _state.value.contextMenuPageUrl ?: return
        _state.update { it.copy(isPageContextMenuVisible = false, contextMenuPageUrl = null) }
        viewModelScope.launch {
            try {
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context).data(pageUrl).build()
                )
                val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: run {
                    _effect.send(ReaderEffect.ShowSnackbar(messageResId = R.string.reader_page_save_failed))
                    return@launch
                }
                val fileName = "otaku_reader_${System.currentTimeMillis()}.jpg"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OtakuReader")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri == null) {
                    _effect.send(ReaderEffect.ShowSnackbar(messageResId = R.string.reader_page_save_failed))
                    return@launch
                }
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                _effect.send(ReaderEffect.ShowSnackbar(messageResId = R.string.reader_page_saved))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _effect.send(ReaderEffect.ShowSnackbar(messageResId = R.string.reader_page_save_failed))
            }
        }
    }

    private fun setPageAsCover() {
        val pageUrl = _state.value.contextMenuPageUrl ?: return
        _state.update { it.copy(isPageContextMenuVisible = false, contextMenuPageUrl = null) }
        viewModelScope.launch {
            try {
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context).data(pageUrl).build()
                )
                val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: run {
                    _effect.send(ReaderEffect.ShowSnackbar(messageResId = R.string.reader_page_cover_failed))
                    return@launch
                }
                val tempFile = java.io.File(context.cacheDir, "cover_${System.currentTimeMillis()}.jpg")
                tempFile.outputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                }
                mangaRepository.setCustomCover(mangaId, android.net.Uri.fromFile(tempFile).toString())
                _effect.send(ReaderEffect.ShowSnackbar(messageResId = R.string.reader_page_cover_set))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _effect.send(ReaderEffect.ShowSnackbar(messageResId = R.string.reader_page_cover_failed))
            }
        }
    }

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

            if (currentState.isLastPage) {
                deleteAfterReadDelegate.maybeDeleteAfterRead(mangaId = mangaId, chapterId = chapterId)
            }
        }
    }

    private fun navigatePreviousChapter() = navigateToAdjacentChapter(forward = false)

    private fun navigateNextChapter() = navigateToAdjacentChapter(forward = true)

    /**
     * Loads the chapter immediately before or after the current one, in reading order
     * (ascending chapter number). Previously both directions were stubbed — "next" only showed
     * an end-of-chapters toast and "previous" simply exited the reader — so chapter-to-chapter
     * navigation never worked. Reuses the same [loadChapterById] path the chapter-list overlay
     * uses, and falls back to a boundary snackbar when there is no adjacent chapter.
     */
    private fun navigateToAdjacentChapter(forward: Boolean) {
        val ordered = chapters.value.sortedBy { it.chapterNumber }
        val currentIndex = ordered.indexOfFirst { it.id == _state.value.currentChapterId }
        // Resolve the target only when the current chapter is found; an unresolved current
        // chapter (empty/not-yet-loaded list) is treated the same as hitting a boundary so the
        // control always gives feedback instead of becoming a silent no-op — this matters for the
        // end-of-chapter overlay's "Next" button, which isn't gated by the slider's enabled state.
        val target = if (currentIndex >= 0) {
            ordered.getOrNull(if (forward) currentIndex + 1 else currentIndex - 1)
        } else {
            null
        }
        if (target == null) {
            viewModelScope.launch {
                _effect.send(
                    ReaderEffect.ShowSnackbar(
                        messageResId = if (forward) {
                            R.string.reader_end_of_chapters
                        } else {
                            R.string.reader_no_previous_chapter
                        }
                    )
                )
            }
            return
        }
        loadChapterById(target.id)
    }

    /**
     * Handle tap zones for navigation
     */
    fun onTapZone(zone: TapZone) {
        displayDelegate.handlePageTap(
            zone = zone,
            getState = { _state.value },
            onNavigate = { delta -> navigatePage(delta) },
            onToggleMenu = { displayDelegate.toggleMenu() }
        )
    }

    /**
     * Reset zoom to default
     */
    fun resetZoom() {
        displayDelegate.updateZoom(1f)
    }

    /**
     * Jump to specific page (for gallery/thumbnail navigation)
     */
    fun jumpToPage(page: Int) {
        changePage(page)
        _state.update { it.copy(isGalleryOpen = false) }
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
        prefetchDelegate.clearCache()

        if (currentState.isLastPage) {
            val chapter = currentChapter
            val manga = currentManga
            if (chapter != null && manga != null) {
                viewModelScope.launch {
                    withContext(NonCancellable) {
                        try {
                            trackerSyncRepository.getSyncStateForManga(mangaId).first()
                                .forEach { syncState ->
                                    trackerSyncRepository.recordLocalChange(
                                        mangaId = mangaId,
                                        trackerId = syncState.trackerId,
                                        chapterRead = chapter.chapterNumber,
                                        status = manga.status
                                    )
                                }
                        } catch (e: Exception) {
                            // Reading still works without tracker sync, but losing the
                            // progress push silently would be confusing — leave a trace.
                            android.util.Log.w("ReaderViewModel", "Tracker sync on exit failed", e)
                        }
                    }
                }
            }
        }
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
        if (currentState.isLastPage) {
            val chapter = currentChapter
            val manga = currentManga
            if (chapter != null && manga != null) {
                try {
                    trackerSyncRepository.getSyncStateForManga(mangaId).first()
                        .forEach { syncState ->
                            trackerSyncRepository.recordLocalChange(
                                mangaId = mangaId,
                                trackerId = syncState.trackerId,
                                chapterRead = chapter.chapterNumber,
                                status = manga.status
                            )
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // non-fatal: tracker sync failure must not interrupt reader exit
                }
            }
            deleteAfterReadDelegate.maybeDeleteAfterRead(mangaId = mangaId, chapterId = chapterId)
        }
    }


    companion object {
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 5f
        private const val PROGRESS_SAVE_DELAY = 3000L // 3 seconds
        const val ZOOM_INCREMENT = 0.25f
        const val BRIGHTNESS_INCREMENT = 0.1f
        const val AUTO_SCROLL_INCREMENT = 50f
    }

    private fun observeVisualEffects() {
        generalPreferences.visualEffectsEnabled
            .onEach { enabled -> _state.update { it.copy(visualEffectsEnabled = enabled) } }
            .launchIn(viewModelScope)
    }

    private fun observeContentType(manga: Manga) {
        val genreText = manga.genre.joinToString(" ").lowercase()
        val isManhwaByGenre = "manhwa" in genreText || "webtoon" in genreText || "korean" in genreText
        generalPreferences.getMangaContentType(mangaId)
            .onEach { userSetsManhwa ->
                val isWebtoon = userSetsManhwa || isManhwaByGenre
                var switchedToWebtoon = false
                _state.update { current ->
                    switchedToWebtoon = isWebtoon && current.mode == ReaderMode.SINGLE_PAGE
                    current.copy(
                        isManhwaContent = isWebtoon,
                        mode = if (switchedToWebtoon) ReaderMode.WEBTOON else current.mode
                    )
                }
                if (switchedToWebtoon) {
                    viewModelScope.launch { settingsRepository.setReaderMode(ReaderMode.WEBTOON) }
                }
            }
            .launchIn(viewModelScope)
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
