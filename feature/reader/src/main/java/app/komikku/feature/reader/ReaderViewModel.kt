package app.komikku.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.komikku.domain.repository.ChapterRepository
import app.komikku.domain.repository.MangaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
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

    init {
        loadChapter()
    }

    private fun loadChapter() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val manga = mangaRepository.observeManga(mangaId)
                val chapter = chapterRepository.getChapter(chapterId)
                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        chapter = chapter,
                        currentPage = chapter?.lastPageRead ?: 0
                    )
                }
                manga.collect { m ->
                    _state.update { it.copy(manga = m) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
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
                chapterRepository.setRead(
                    id = chapter.id,
                    read = page >= (_state.value.pages.size - 1),
                    lastPageRead = page
                )
            }
        }
    }

    private fun navigatePreviousChapter() {
        viewModelScope.launch { _effect.send(ReaderEffect.NavigateBack) }
    }

    private fun navigateNextChapter() {
        viewModelScope.launch { _effect.send(ReaderEffect.ShowSnackbar("End of available chapters")) }
    }
}
