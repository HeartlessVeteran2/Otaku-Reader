package app.otakureader.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.model.DownloadStatus
import app.otakureader.domain.model.Chapter
import app.otakureader.domain.model.Manga
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.core.preferences.DeleteAfterReadMode
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.domain.usecase.UpdateMangaNoteUseCase
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
    private val chapterRepository: ChapterRepository,
    private val downloadRepository: DownloadRepository,
    private val downloadPreferences: DownloadPreferences,
    private val updateMangaNote: UpdateMangaNoteUseCase
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
        observeDownloads()
        observeDeleteAfterReadSetting()
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
            is DetailsContract.Event.ExportChapterAsCbz -> exportChapterAsCbz(event.chapterId)
            is DetailsContract.Event.MarkPreviousAsRead -> markPreviousAsRead(event.chapterId)
            is DetailsContract.Event.ShareManga -> shareManga()
            is DetailsContract.Event.SetDeleteAfterReadOverride -> setDeleteAfterReadOverride(event.mode)
            is DetailsContract.Event.ShowNoteEditor -> showNoteEditor()
            is DetailsContract.Event.HideNoteEditor -> hideNoteEditor()
            is DetailsContract.Event.UpdateNoteText -> updateNoteText(event.text)
            is DetailsContract.Event.SaveNote -> saveNote()
            is DetailsContract.Event.ClearChapterSelection -> clearChapterSelection()
            is DetailsContract.Event.SelectAllChapters -> selectAllChapters()
            is DetailsContract.Event.DownloadSelectedChapters -> downloadSelectedChapters()
            is DetailsContract.Event.DeleteSelectedChapters -> deleteSelectedChapters()
            is DetailsContract.Event.MarkSelectedAsRead -> markSelectedAsRead()
            is DetailsContract.Event.MarkSelectedAsUnread -> markSelectedAsUnread()
            is DetailsContract.Event.BookmarkSelectedChapters -> bookmarkSelectedChapters()
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

    private fun observeDownloads() {
        downloadRepository.observeDownloads()
            .onEach { downloads ->
                _state.update { state ->
                    val updatedChapters = state.chapters.map { chapter ->
                        val matchingDownload = downloads.firstOrNull { it.chapterId == chapter.id }
                        when (matchingDownload?.status) {
                            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED, DownloadStatus.PAUSED -> {
                                chapter.copy(downloadStatus = DetailsContract.DownloadStatus.DOWNLOADING)
                            }
                            DownloadStatus.COMPLETED -> {
                                chapter.copy(downloadStatus = DetailsContract.DownloadStatus.DOWNLOADED)
                            }
                            else -> chapter.copy(downloadStatus = DetailsContract.DownloadStatus.NOT_DOWNLOADED)
                        }
                    }
                    state.copy(chapters = updatedChapters)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeDeleteAfterReadSetting() {
        // Observe delete-after-read preference and keep state in sync
        combine(
            downloadPreferences.deleteAfterReading,
            downloadPreferences.perMangaOverrides
        ) { global, overrides ->
            Pair(global, overrides[mangaId] ?: DeleteAfterReadMode.INHERIT)
        }
            .onEach { (global, override) ->
                _state.update { state ->
                    state.copy(
                        globalDeleteAfterRead = global,
                        deleteAfterReadOverride = override
                    )
                }
            }
            .launchIn(viewModelScope)
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
            val firstChapter = _state.value.sortedChapters.firstOrNull()
            
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
            val nextUnread = _state.value.nextUnreadChapter
            val chapterId = if (nextUnread != null) {
                nextUnread.id
            } else {
                (_state.value.chapters.firstOrNull { !it.read }
                    ?: _state.value.chapters.lastOrNull())?.id
            }

            if (chapterId != null) {
                _effect.emit(DetailsContract.Effect.NavigateToReader(mangaId, chapterId))
            } else {
                _effect.emit(DetailsContract.Effect.ShowError("No chapters available"))
            }
        }
    }

    private fun onChapterClick(chapterId: Long) {
        if (_state.value.selectedChapters.isNotEmpty()) {
            toggleChapterSelection(chapterId)
        } else {
            viewModelScope.launch {
                _effect.emit(DetailsContract.Effect.NavigateToReader(mangaId, chapterId))
            }
        }
    }

    private fun onChapterLongClick(chapterId: Long) {
        toggleChapterSelection(chapterId)
    }

    private fun toggleChapterSelection(chapterId: Long) {
        _state.update { state ->
            val currentSelection = state.selectedChapters
            val newSelection = if (currentSelection.contains(chapterId)) {
                currentSelection - chapterId
            } else {
                currentSelection + chapterId
            }
            state.copy(selectedChapters = newSelection)
        }
    }

    private fun clearChapterSelection() {
        _state.update { it.copy(selectedChapters = emptySet()) }
    }

    private fun selectAllChapters() {
        _state.update { state ->
            val allIds = state.chapters.map { it.id }.toSet()
            state.copy(selectedChapters = allIds)
        }
    }

    private fun downloadSelectedChapters() {
        viewModelScope.launch {
            val selectedIds = _state.value.selectedChapters
            val manga = _state.value.manga
            val chapters = _state.value.chapters.filter { selectedIds.contains(it.id) }
            val mangaTitle = manga?.title ?: "Manga"
            val sourceName = manga?.sourceId?.toString() ?: ""

            chapters.forEach { chapter ->
                downloadRepository.enqueueChapter(
                    mangaId = chapter.mangaId,
                    chapterId = chapter.id,
                    sourceName = sourceName,
                    mangaTitle = mangaTitle,
                    chapterTitle = chapter.name
                )
            }
            clearChapterSelection()
            _effect.emit(DetailsContract.Effect.ShowSnackbar("${chapters.size} chapter(s) added to download queue"))
        }
    }

    private fun deleteSelectedChapters() {
        viewModelScope.launch {
            val selectedIds = _state.value.selectedChapters
            val manga = _state.value.manga
            val chapters = _state.value.chapters.filter { selectedIds.contains(it.id) }

            if (manga != null) {
                chapters.forEach { chapter ->
                    downloadRepository.deleteChapterDownload(
                        chapterId = chapter.id,
                        sourceName = manga.sourceId.toString(),
                        mangaTitle = manga.title,
                        chapterTitle = chapter.name
                    )
                }
            }
            clearChapterSelection()
            _effect.emit(DetailsContract.Effect.ShowSnackbar("Deleted ${chapters.size} download(s)"))
        }
    }

    private fun markSelectedAsRead() {
        viewModelScope.launch {
            try {
                val selectedIds = _state.value.selectedChapters.toList()
                if (selectedIds.isNotEmpty()) {
                    chapterRepository.updateChapterProgress(
                        chapterIds = selectedIds,
                        read = true,
                        lastPageRead = 0
                    )
                    clearChapterSelection()
                    _effect.emit(DetailsContract.Effect.ShowSnackbar("Marked ${selectedIds.size} chapter(s) as read"))
                }
            } catch (e: Exception) {
                _effect.emit(DetailsContract.Effect.ShowError("Failed to mark chapters as read: ${e.message}"))
            }
        }
    }

    private fun markSelectedAsUnread() {
        viewModelScope.launch {
            try {
                val selectedIds = _state.value.selectedChapters.toList()
                if (selectedIds.isNotEmpty()) {
                    chapterRepository.updateChapterProgress(
                        chapterIds = selectedIds,
                        read = false,
                        lastPageRead = 0
                    )
                    clearChapterSelection()
                    _effect.emit(DetailsContract.Effect.ShowSnackbar("Marked ${selectedIds.size} chapter(s) as unread"))
                }
            } catch (e: Exception) {
                _effect.emit(DetailsContract.Effect.ShowError("Failed to mark chapters as unread: ${e.message}"))
            }
        }
    }

    private fun bookmarkSelectedChapters() {
        viewModelScope.launch {
            try {
                val selectedIds = _state.value.selectedChapters
                if (selectedIds.isNotEmpty()) {
                    selectedIds.forEach { chapterId ->
                        chapterRepository.updateBookmark(chapterId, true)
                    }
                    clearChapterSelection()
                    _effect.emit(DetailsContract.Effect.ShowSnackbar("Bookmarked ${selectedIds.size} chapter(s)"))
                }
            } catch (e: Exception) {
                _effect.emit(DetailsContract.Effect.ShowError("Failed to bookmark chapters: ${e.message}"))
            }
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
            val chapter = _state.value.chapters.firstOrNull { it.id == chapterId }
            val manga = _state.value.manga
            val mangaTitle = manga?.title ?: "Manga"
            // Use sourceId as a stable directory key. Once a SourceManager is available
            // this can be replaced with the source's display name.
            val sourceName = manga?.sourceId?.toString() ?: ""

            if (chapter != null) {
                downloadRepository.enqueueChapter(
                    mangaId = chapter.mangaId,
                    chapterId = chapter.id,
                    sourceName = sourceName,
                    mangaTitle = mangaTitle,
                    chapterTitle = chapter.name
                )
                _effect.emit(DetailsContract.Effect.ShowSnackbar("Download added to queue"))
            }
        }
    }

    private fun deleteChapterDownload(chapterId: Long) {
        viewModelScope.launch {
            val chapter = _state.value.chapters.firstOrNull { it.id == chapterId }
            val manga = _state.value.manga
            if (chapter != null && manga != null) {
                downloadRepository.deleteChapterDownload(
                    chapterId = chapterId,
                    sourceName = manga.sourceId.toString(),
                    mangaTitle = manga.title,
                    chapterTitle = chapter.name
                )
                _effect.emit(DetailsContract.Effect.ShowSnackbar("Download removed"))
            } else {
                downloadRepository.cancelDownload(chapterId)
                _effect.emit(DetailsContract.Effect.ShowSnackbar("Download removed"))
            }
        }
    }

    private fun exportChapterAsCbz(chapterId: Long) {
        viewModelScope.launch {
            val chapter = _state.value.chapters.firstOrNull { it.id == chapterId }
            val manga = _state.value.manga
            if (chapter == null || manga == null) {
                _effect.emit(DetailsContract.Effect.ShowError("Chapter not found"))
                return@launch
            }
            downloadRepository.exportChapterAsCbz(
                sourceName = manga.sourceId.toString(),
                mangaTitle = manga.title,
                chapterTitle = chapter.name
            ).fold(
                onSuccess = { _effect.emit(DetailsContract.Effect.ShowSnackbar("Exported as CBZ")) },
                onFailure = {
                    val reason = it.message ?: "Unknown error"
                    _effect.emit(DetailsContract.Effect.ShowError("Export failed: $reason"))
                }
            )
        }
    }

    private fun markPreviousAsRead(chapterId: Long) {
        viewModelScope.launch {
            try {
                val chapters = _state.value.chapters
                val targetChapter = chapters.find { it.id == chapterId }
                targetChapter?.let { target ->
                    val chapterIdsToUpdate = chapters
                        .filter { it.chapterNumber < target.chapterNumber && !it.read }
                        .map { it.id }

                    if (chapterIdsToUpdate.isNotEmpty()) {
                        chapterRepository.updateChapterProgress(
                            chapterIds = chapterIdsToUpdate,
                            read = true,
                            lastPageRead = 0
                        )
                    }
                }
                _effect.emit(DetailsContract.Effect.ShowSnackbar("Marked previous chapters as read"))
            } catch (e: Exception) {
                _effect.emit(DetailsContract.Effect.ShowError("Failed to mark chapters: ${e.message}"))
            }
        }
    }

    /**
     * Returns a fully-qualified shareable URL, or null if [manga.url] is a source-relative path
     * (i.e., does not start with "http://" or "https://").
     */
    private fun buildShareUrl(manga: Manga): String? {
        val url = manga.url
        return if (url.startsWith("http://") || url.startsWith("https://")) url else null
    }

    private fun shareManga() {
        viewModelScope.launch {
            val manga = _state.value.manga
            if (manga != null) {
                _effect.emit(
                    DetailsContract.Effect.ShareManga(
                        title = manga.title,
                        url = buildShareUrl(manga) ?: ""
                    )
                )
            }
        }
    }

    private fun setDeleteAfterReadOverride(mode: DeleteAfterReadMode) {
        // Delete-after-reading feature has been removed.
        // Provide explicit feedback so the user is aware this action is no longer supported.
        viewModelScope.launch {
            _effect.emit(
                DetailsContract.Effect.ShowSnackbar(
                    "Delete-after-read is no longer supported."
                )
            )
        }
    }

    private fun showNoteEditor() {
        val currentNote = _state.value.manga?.notes ?: ""
        _state.update { it.copy(noteEditorVisible = true, noteEditorText = currentNote) }
    }

    private fun hideNoteEditor() {
        _state.update { it.copy(noteEditorVisible = false) }
    }

    private fun updateNoteText(text: String) {
        _state.update { it.copy(noteEditorText = text) }
    }

    private fun saveNote() {
        viewModelScope.launch {
            val text = _state.value.noteEditorText.trim().ifEmpty { null }
            try {
                updateMangaNote(mangaId, text)
                _state.update { it.copy(noteEditorVisible = false) }
                _effect.emit(DetailsContract.Effect.ShowSnackbar("Note saved"))
            } catch (e: Exception) {
                val errorMessage = buildString {
                    append("Failed to save note")
                    val detail = e.message
                    if (!detail.isNullOrBlank()) {
                        append(": ")
                        append(detail)
                    }
                }
                _effect.emit(DetailsContract.Effect.ShowError(errorMessage))
            }
        }
    }

    companion object {
        const val MANGA_ID_ARG = "mangaId"
    }
}
