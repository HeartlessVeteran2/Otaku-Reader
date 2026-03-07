package app.otakureader.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.MangaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Manga Details Screen following MVI pattern
 */
@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository
) : ViewModel() {

    private val mangaId: Long = savedStateHandle.get<Long>(MANGA_ID_ARG) 
        ?: throw IllegalArgumentException("Manga ID is required")

    private val _state = MutableStateFlow(DetailsContract.State())
    val state: StateFlow<DetailsContract.State> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<DetailsContract.Effect>()
    val effect: SharedFlow<DetailsContract.Effect> = _effect.asSharedFlow()

    init {
        loadMangaDetails()
        loadChapters()
        observeFavoriteStatus()
        loadNextUnreadChapter()
    }

    fun onEvent(event: DetailsContract.Event) {
        when (event) {
            is DetailsContract.Event.Refresh -> refreshData()
            is DetailsContract.Event.ToggleFavorite -> toggleFavorite()
            is DetailsContract.Event.ToggleDescription -> toggleDescription()
            is DetailsContract.Event.ToggleSortOrder -> toggleSortOrder()
            is DetailsContract.Event.StartReading -> startReading()
            is DetailsContract.Event.ContinueReading -> continueReading()
            is DetailsContract.Event.ChapterClick -> onChapterClick(event.chapterId)
            is DetailsContract.Event.ChapterLongClick -> onChapterLongClick(event.chapterId)
            is DetailsContract.Event.ToggleChapterRead -> toggleChapterRead(event.chapterId)
            is DetailsContract.Event.ToggleChapterBookmark -> toggleChapterBookmark(event.chapterId)
            is DetailsContract.Event.DownloadChapter -> downloadChapter(event.chapterId)
            is DetailsContract.Event.DeleteChapterDownload -> deleteChapterDownload(event.chapterId)
            is DetailsContract.Event.MarkPreviousAsRead -> markPreviousAsRead(event.chapterId)
        }
    }

    private fun loadMangaDetails() {
        mangaRepository.getMangaByIdFlow(mangaId)
            .onEach { manga ->
                _state.update { it.copy(manga = manga, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    private fun loadChapters() {
        chapterRepository.getChaptersByMangaId(mangaId)
            .onEach { chapters ->
                _state.update { state ->
                    state.copy(
                        chapters = chapters.map { it.toChapterItem() },
                        isLoading = false
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeFavoriteStatus() {
        mangaRepository.isFavorite(mangaId)
            .onEach { isFavorite ->
                _state.update { it.copy(isFavorite = isFavorite) }
            }
            .launchIn(viewModelScope)
    }

    private fun loadNextUnreadChapter() {
        viewModelScope.launch {
            val nextChapter = chapterRepository.getNextUnreadChapter(mangaId)
            _state.update { it.copy(nextUnreadChapter = nextChapter) }
        }
    }

    private fun refreshData() {
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            // In a real implementation, this would fetch fresh data from the source
            // For now, we just reload from local database
            kotlinx.coroutines.delay(500) // Simulate network delay
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    private fun toggleFavorite() {
        viewModelScope.launch {
            try {
                mangaRepository.toggleFavorite(mangaId)
                val message = if (_state.value.isFavorite) {
                    "Removed from library"
                } else {
                    "Added to library"
                }
                _effect.emit(DetailsContract.Effect.ShowSnackbar(message))
            } catch (e: Exception) {
                _effect.emit(DetailsContract.Effect.ShowError("Failed to update library: ${e.message}"))
            }
        }
    }

    private fun toggleDescription() {
        _state.update { it.copy(descriptionExpanded = !it.descriptionExpanded) }
    }

    private fun toggleSortOrder() {
        _state.update {
            val newOrder = when (it.chapterSortOrder) {
                DetailsContract.ChapterSortOrder.ASCENDING -> DetailsContract.ChapterSortOrder.DESCENDING
                DetailsContract.ChapterSortOrder.DESCENDING -> DetailsContract.ChapterSortOrder.ASCENDING
            }
            it.copy(chapterSortOrder = newOrder)
        }
    }

    private fun startReading() {
        viewModelScope.launch {
            val firstChapter = _state.value.chapters.lastOrNull()
                ?: _state.value.chapters.firstOrNull()
            
            if (firstChapter != null) {
                _effect.emit(
                    DetailsContract.Effect.NavigateToReader(mangaId, firstChapter.id)
                )
            } else {
                _effect.emit(DetailsContract.Effect.ShowError("No chapters available"))
            }
        }
    }

    private fun continueReading() {
        viewModelScope.launch {
            val nextChapter = _state.value.nextUnreadChapter
                ?: _state.value.chapters.firstOrNull { !it.read }
                ?: _state.value.chapters.lastOrNull()
            
            if (nextChapter != null) {
                _effect.emit(
                    DetailsContract.Effect.NavigateToReader(mangaId, nextChapter.id)
                )
            } else {
                _effect.emit(DetailsContract.Effect.ShowError("No chapters available"))
            }
        }
    }

    private fun onChapterClick(chapterId: Long) {
        viewModelScope.launch {
            _effect.emit(DetailsContract.Effect.NavigateToReader(mangaId, chapterId))
        }
    }

    private fun onChapterLongClick(chapterId: Long) {
        // Show chapter options menu (could be implemented with a bottom sheet)
        viewModelScope.launch {
            _effect.emit(DetailsContract.Effect.ShowSnackbar("Chapter options coming soon"))
        }
    }

    private fun toggleChapterRead(chapterId: Long) {
        viewModelScope.launch {
            try {
                val chapter = _state.value.chapters.find { it.id == chapterId }
                chapter?.let {
                    chapterRepository.updateChapterProgress(
                        chapterId = chapterId,
                        read = !it.read,
                        lastPageRead = if (!it.read) 0 else it.lastPageRead
                    )
                }
            } catch (e: Exception) {
                _effect.emit(DetailsContract.Effect.ShowError("Failed to update chapter: ${e.message}"))
            }
        }
    }

    private fun toggleChapterBookmark(chapterId: Long) {
        viewModelScope.launch {
            try {
                val chapter = _state.value.chapters.find { it.id == chapterId }
                chapter?.let {
                    chapterRepository.updateBookmark(chapterId, !it.bookmark)
                }
            } catch (e: Exception) {
                _effect.emit(DetailsContract.Effect.ShowError("Failed to update bookmark: ${e.message}"))
            }
        }
    }

    private fun downloadChapter(chapterId: Long) {
        viewModelScope.launch {
            // TODO: Implement actual download logic
            _effect.emit(DetailsContract.Effect.ShowSnackbar("Download started"))
        }
    }

    private fun deleteChapterDownload(chapterId: Long) {
        viewModelScope.launch {
            // TODO: Implement actual delete logic
            _effect.emit(DetailsContract.Effect.ShowSnackbar("Download deleted"))
        }
    }

    private fun markPreviousAsRead(chapterId: Long) {
        viewModelScope.launch {
            try {
                val chapters = _state.value.chapters
                val targetChapter = chapters.find { it.id == chapterId }
                targetChapter?.let { target ->
                    chapters
                        .filter { it.chapterNumber < target.chapterNumber }
                        .forEach { chapter ->
                            if (!chapter.read) {
                                chapterRepository.updateChapterProgress(
                                    chapterId = chapter.id,
                                    read = true,
                                    lastPageRead = 0
                                )
                            }
                        }
                }
                _effect.emit(DetailsContract.Effect.ShowSnackbar("Marked previous chapters as read"))
            } catch (e: Exception) {
                _effect.emit(DetailsContract.Effect.ShowError("Failed to mark chapters: ${e.message}"))
            }
        }
    }

    companion object {
        const val MANGA_ID_ARG = "mangaId"
    }
}
