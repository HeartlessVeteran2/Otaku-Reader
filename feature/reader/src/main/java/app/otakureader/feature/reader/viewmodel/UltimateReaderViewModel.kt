package app.otakureader.feature.reader.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.data.loader.PageLoader
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.feature.reader.model.ReaderMode
import app.otakureader.feature.reader.model.ReaderPage
import app.otakureader.feature.reader.model.ReadingDirection
import app.otakureader.feature.reader.repository.ReaderSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

/**
 * Ultimate ViewModel for the Reader feature.
 * Manages all reader modes, page preloading, progress saving, and settings.
 * Integrates with existing Otaku Reader domain repositories.
 */
@HiltViewModel
class UltimateReaderViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val settingsRepository: ReaderSettingsRepository,
    private val pageLoader: PageLoader,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val mangaId: Long = checkNotNull(savedStateHandle["mangaId"])
    private val chapterId: Long = checkNotNull(savedStateHandle["chapterId"])

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _effect = Channel<ReaderEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var autoSaveJob: Job? = null
    private var preloadJob: Job? = null

    private val sessionStartMs = System.currentTimeMillis()

    /** Independent scope used for cleanup work that must survive viewModelScope cancellation. */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        loadSettings()
        loadChapter()
    }

    private fun recordHistoryOpen() {
        viewModelScope.launch {
            runCatching {
                chapterRepository.recordHistory(
                    chapterId = chapterId,
                    readAt = sessionStartMs,
                    readDurationMs = 0L
                )
            }
        }
    }

    /**
     * Load saved reader settings
     */
    private fun loadSettings() {
        viewModelScope.launch {
            // Load all settings concurrently
            val mode = settingsRepository.readerMode.first()
            val brightness = settingsRepository.brightness.first()
            val keepScreenOn = settingsRepository.keepScreenOn.first()
            val showPageNumber = settingsRepository.showPageNumber.first()
            val direction = settingsRepository.readingDirection.first()
            val volumeKeyNav = settingsRepository.volumeKeyNavigation.first()
            val fullscreen = settingsRepository.fullscreen.first()

            _state.update {
                it.copy(
                    mode = mode,
                    brightness = brightness,
                    keepScreenOn = keepScreenOn,
                    showPageNumber = showPageNumber,
                    readingDirection = direction,
                    volumeKeyNavigation = volumeKeyNav,
                    isFullscreen = fullscreen
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
                        chapterTitle = chapter.name
                    )
                }

                // Record history now that the chapter is confirmed to exist.
                recordHistoryOpen()

                // Start preloading adjacent pages
                if (pages.isNotEmpty()) {
                    preloadPages(_state.value.currentPage)
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
        // TODO: Integrate with SourceManager to fetch actual page URLs.
        // val source = sourceManager.get(manga.sourceId)
        // val remotePages = source.fetchPageList(chapter.toSourceChapter())
        //
        // Once remotePages are available, resolve each URL through PageLoader:
        // return remotePages.mapIndexed { index, page ->
        //     ReaderPage(
        //         index = index,
        //         imageUrl = pageLoader.resolveUrl(page.imageUrl, sourceName, mangaTitle, chapterName, index),
        //         chapterName = chapterName
        //     )
        // }
        return emptyList()
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
            ReaderEvent.AutoScrollSpeedUp -> updateAutoScrollSpeed(_state.value.autoScrollSpeed + AUTO_SCROLL_INCREMENT)
            ReaderEvent.AutoScrollSpeedDown -> updateAutoScrollSpeed(_state.value.autoScrollSpeed - AUTO_SCROLL_INCREMENT)
            ReaderEvent.FirstPage -> changePage(0)
            ReaderEvent.LastPage -> changePage((_state.value.pages.size - 1).coerceAtLeast(0))
            ReaderEvent.FirstPanel -> changePanel(0)
            ReaderEvent.LastPanel -> {
                val currentPage = _state.value.pages.getOrNull(_state.value.currentPage)
                changePanel((currentPage?.panels?.size ?: 1) - 1)
            }
        }
    }

    private fun changePage(page: Int) {
        val validPage = page.coerceIn(0, (_state.value.pages.size - 1).coerceAtLeast(0))
        if (validPage != _state.value.currentPage) {
            _state.update { it.copy(currentPage = validPage) }
            preloadPages(validPage)
            scheduleProgressSave()
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

    private fun updateReadingDirection(direction: ReadingDirection) {
        _state.update { it.copy(readingDirection = direction) }
        viewModelScope.launch {
            settingsRepository.setReadingDirection(direction)
        }
    }

    private fun changeReaderMode(mode: ReaderMode) {
        _state.update { it.copy(mode = mode) }
        
        // Adjust current page for dual page mode
        if (mode == ReaderMode.DUAL_PAGE && _state.value.currentPage % 2 != 0) {
            _state.update { it.copy(currentPage = it.currentPage - 1) }
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
                val newValue = !_state.value.volumeKeyNavigation
                _state.update { it.copy(volumeKeyNavigation = newValue) }
                viewModelScope.launch { settingsRepository.setVolumeKeyNavigation(newValue) }
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
     * Preload pages ahead and behind current page for smooth scrolling
     */
    private fun preloadPages(currentPage: Int) {
        preloadJob?.cancel()
        preloadJob = viewModelScope.launch {
            val pages = _state.value.pages
            val preloadRange = (currentPage - PRELOAD_BUFFER)..(currentPage + PRELOAD_BUFFER)

            preloadRange.forEach { index ->
                if (index in pages.indices && index != currentPage) {
                    // Preload logic here - could use Coil's prefetch or custom loader
                }
            }
        }
    }

    /**
     * Schedule auto-save of reading progress
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

    override fun onCleared() {
        super.onCleared()
        val durationMs = System.currentTimeMillis() - sessionStartMs
        // Use cleanupScope (not viewModelScope) so the coroutine is not cancelled along with the ViewModel.
        cleanupScope.launch {
            runCatching {
                chapterRepository.recordHistory(
                    chapterId = chapterId,
                    readAt = sessionStartMs,
                    readDurationMs = durationMs
                )
            }
        }
        saveCurrentProgress()
        autoSaveJob?.cancel()
        preloadJob?.cancel()
    }

    companion object {
        private const val MIN_ZOOM = 0.5f
        private const val MAX_ZOOM = 5f
        private const val PRELOAD_BUFFER = 3
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
