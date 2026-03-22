package app.otakureader.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.core.discord.DiscordRpcService
import app.otakureader.core.discord.ReadingStatus
import app.otakureader.core.preferences.GeneralPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val discordRpcService: DiscordRpcService,
    private val generalPreferences: GeneralPreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val mangaId: Long = checkNotNull(savedStateHandle["mangaId"])
    private val chapterId: Long = checkNotNull(savedStateHandle["chapterId"])

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _effect = Channel<ReaderEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private val sessionStartMs = System.currentTimeMillis()

    /** Cached Discord RPC enabled state, loaded once to avoid DataStore reads on every page change. */
    private var cachedDiscordRpcEnabled: Boolean = false

    /** Independent scope used for cleanup work that must survive viewModelScope cancellation. */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        loadChapter()
        cacheDiscordPreference()
    }

    /**
     * Loads the chapter and manga data for the current reader session.
     *
     * **H-12 — Silent page fetch failure:** Previously, failures in this function
     * would leave the reader in a blank state with no error message. The error is now
     * propagated to [ReaderState.error] so the UI can display an informative error state
     * (e.g., "Failed to load chapter. Please try again.") instead of a blank reader.
     */
    private fun loadChapter() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val manga = mangaRepository.getMangaByIdFlow(mangaId)
                val chapter = chapterRepository.getChapterById(chapterId)

                if (chapter == null) {
                    // H-12: Explicitly surface a missing chapter as an error rather than
                    // silently showing a blank reader.
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Chapter not found. It may have been deleted or is unavailable."
                        )
                    }
                    return@launch
                }

                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        chapter = chapter,
                        currentPage = chapter.lastPageRead
                    )
                }
                recordHistoryOpen()
                manga.collect { m ->
                    _state.update { it.copy(manga = m) }
                    // Update Discord Rich Presence when manga data is available
                    if (m != null) {
                        updateDiscordPresence(m.title, chapter.name)
                    }
                }
            } catch (e: Exception) {
                // H-12: Propagate the error message to the UI state.
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load chapter. Please go back and try again."
                    )
                }
            }
        }
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

    fun onEvent(event: ReaderEvent) {
        when (event) {
            is ReaderEvent.OnPageChange -> onPageChange(event.page)
            is ReaderEvent.ToggleMenu -> _state.update { it.copy(isMenuVisible = !it.isMenuVisible) }
            is ReaderEvent.NavigateToPreviousChapter -> navigatePreviousChapter()
            is ReaderEvent.NavigateToNextChapter -> navigateNextChapter()
            is ReaderEvent.SetReadingMode -> _state.update { it.copy(readingMode = event.mode) }
            is ReaderEvent.SetScaleType -> _state.update { it.copy(scaleType = event.type) }
        }
    }

    private fun onPageChange(page: Int) {
        _state.update { it.copy(currentPage = page) }
        // Persist reading progress
        viewModelScope.launch {
            _state.value.chapter?.let { chapter ->
                chapterRepository.updateChapterProgress(
                    chapterId = chapter.id,
                    read = page >= (_state.value.pages.size - 1),
                    lastPageRead = page
                )
            }
        }
        // Update Discord presence with page info
        val manga = _state.value.manga
        val chapter = _state.value.chapter
        if (manga != null && chapter != null) {
            updateDiscordPresence(manga.title, chapter.name, _state.value.pages.size, page + 1)
        }
    }

    private fun navigatePreviousChapter() {
        viewModelScope.launch { _effect.send(ReaderEffect.NavigateBack) }
    }

    private fun navigateNextChapter() {
        viewModelScope.launch { _effect.send(ReaderEffect.ShowSnackbar("End of available chapters")) }
    }

    /**
     * Saves reading history when the ViewModel is cleared.
     *
     * **H-5 — Unsafe coroutine scope for critical cleanup:**
     * [cleanupScope] survives [viewModelScope] cancellation, which means the history
     * write will complete as long as the *process* stays alive. However, if the OS
     * kills the process (e.g., low-memory kill while the app is in the background),
     * the coroutine will not complete and the reading duration will be lost.
     *
     * TODO(H-5): Replace this coroutine with a [androidx.work.WorkManager] one-shot
     * task so that the history write is guaranteed to complete even after a process
     * death. The WorkManager task should be enqueued here and the coroutine removed.
     */
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
        // Clear Discord Rich Presence when reader closes
        discordRpcService.clearReadingPresence(showBrowsing = false)
    }

    /**
     * Update Discord Rich Presence if the feature is enabled.
     * Uses the cached preference value to avoid DataStore reads on every call.
     */
    private fun updateDiscordPresence(
        mangaTitle: String,
        chapterName: String,
        totalPages: Int? = null,
        currentPage: Int? = null
    ) {
        if (!cachedDiscordRpcEnabled) return
        discordRpcService.updateReadingPresence(
            mangaTitle = mangaTitle,
            chapterName = chapterName,
            status = ReadingStatus.READING,
            page = currentPage,
            totalPages = totalPages
        )
    }

    /** Continuously observe Discord RPC preference to react to runtime changes. */
    private fun cacheDiscordPreference() {
        viewModelScope.launch {
            generalPreferences.discordRpcEnabled
                .catch { emit(false) }
                .collectLatest { enabled ->
                    cachedDiscordRpcEnabled = enabled

                    if (!enabled) {
                        discordRpcService.clearReadingPresence(showBrowsing = false)
                        return@collectLatest
                    }

                    val currentState = _state.value
                    val manga = currentState.manga
                    val chapter = currentState.chapter
                    if (manga != null && chapter != null) {
                        updateDiscordPresence(manga.title, chapter.name)
                    }
                }
        }
    }
}
