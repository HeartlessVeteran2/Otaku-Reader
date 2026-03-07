package app.otakureader.feature.reader.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.feature.reader.model.ReaderMode
import app.otakureader.feature.reader.model.ReaderPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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

    init {
        loadChapter()
    }

    /**
     * Load chapter pages and initialize reader state
     */
    private fun loadChapter() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val manga = mangaRepository.observeManga(mangaId)
                val chapter = chapterRepository.getChapter(chapterId)

                // Convert page URLs to ReaderPage objects
                val pages = chapter?.pageUrls?.mapIndexed { index, url ->
                    ReaderPage(
                        index = index,
                        imageUrl = url,
                        pageNumber = index + 1,
                        id = "page_${chapterId}_$index",
                        chapterName = chapter.name
                    )
                } ?: emptyList()

                _state.update {
                    it.copy(
                        pages = pages,
                        currentPage = chapter?.lastPageRead?.coerceIn(0, pages.size - 1) ?: 0,
                        isLoading = false,
                        chapterTitle = chapter?.name ?: "",
                        totalPages = pages.size
                    )
                }

                // Start preloading adjacent pages
                preloadPages(_state.value.currentPage)

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
     * Handle all reader events
     */
    fun onEvent(event: ReaderEvent) {
        when (event) {
            is ReaderEvent.OnPageChange -> changePage(event.page)
            is ReaderEvent.OnPanelChange -> changePanel(event.panel)
            is ReaderEvent.OnZoomChange -> updateZoom(event.zoom)
            is ReaderEvent.OnModeChange -> changeReaderMode(event.mode)
            is ReaderEvent.OnBrightnessChange -> updateBrightness(event.brightness)
            ReaderEvent.ToggleMenu -> toggleMenu()
            ReaderEvent.ToggleGallery -> toggleGallery()
            ReaderEvent.NextPage -> navigatePage(1)
            ReaderEvent.PrevPage -> navigatePage(-1)
            ReaderEvent.NextPanel -> navigatePanel(1)
            ReaderEvent.PrevPanel -> navigatePanel(-1)
            ReaderEvent.ZoomIn -> updateZoom(_state.value.zoomLevel + ReaderEvent.ZOOM_INCREMENT)
            ReaderEvent.ZoomOut -> updateZoom(_state.value.zoomLevel - ReaderEvent.ZOOM_INCREMENT)
            ReaderEvent.ResetZoom -> updateZoom(1f)
            ReaderEvent.ToggleFullscreen -> toggleFullscreen()
            ReaderEvent.NextChapter -> navigateNextChapter()
            ReaderEvent.PrevChapter -> navigatePreviousChapter()
            ReaderEvent.DismissError -> dismissError()
            ReaderEvent.Retry -> loadChapter()
            else -> { /* Handle other events as needed */ }
        }
    }

    private fun changePage(page: Int) {
        val validPage = page.coerceIn(0, _state.value.pages.size - 1)
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
        val validPanel = panel.coerceIn(0, maxPanels - 1)
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
        _state.update { it.copy(brightness = brightness.coerceIn(0.1f, 1.5f)) }
    }

    private fun changeReaderMode(mode: ReaderMode) {
        _state.update { it.copy(mode = mode) }
        // Adjust current page for dual page mode
        if (mode == ReaderMode.DUAL_PAGE && _state.value.currentPage % 2 != 0) {
            _state.update { it.copy(currentPage = it.currentPage - 1) }
        }
    }

    private fun toggleMenu() {
        _state.update { it.copy(isMenuVisible = !it.isMenuVisible) }
    }

    private fun toggleGallery() {
        _state.update { it.copy(isGalleryOpen = !it.isGalleryOpen) }
    }

    private fun toggleFullscreen() {
        _state.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    private fun dismissError() {
        _state.update { it.copy(error = null) }
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
            chapterRepository.setRead(
                id = chapterId,
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
            TapZone.LEFT -> when (_state.value.mode) {
                ReaderMode.WEBTOON -> navigatePage(-1)
                else -> navigatePage(-1)
            }
            TapZone.RIGHT -> when (_state.value.mode) {
                ReaderMode.WEBTOON -> navigatePage(1)
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
        saveCurrentProgress()
        autoSaveJob?.cancel()
        preloadJob?.cancel()
    }

    companion object {
        private const val MIN_ZOOM = 0.5f
        private const val MAX_ZOOM = 5f
        private const val PRELOAD_BUFFER = 3
        private const val PROGRESS_SAVE_DELAY = 3000L // 3 seconds
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
