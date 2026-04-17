package app.otakureader.feature.updates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.data.worker.LibraryUpdateWorker
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.usecase.GetLibraryMangaUseCase
import app.otakureader.domain.usecase.GetRecentUpdatesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val getRecentUpdatesUseCase: GetRecentUpdatesUseCase,
    private val getLibraryMangaUseCase: GetLibraryMangaUseCase,
    private val generalPreferences: GeneralPreferences,
    private val downloadRepository: DownloadRepository,
    private val chapterRepository: ChapterRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(UpdatesState())
    val state: StateFlow<UpdatesState> = _state.asStateFlow()

    private val _effect = Channel<UpdatesEffect>()
    val effect = _effect.receiveAsFlow()

    init {
        loadUpdates()
        markUpdatesViewed()
    }

    fun onEvent(event: UpdatesEvent) {
        when (event) {
            UpdatesEvent.Refresh -> loadUpdates()
            is UpdatesEvent.OnChapterClick -> handleChapterClick(event.mangaId, event.chapterId)
            is UpdatesEvent.OnChapterLongClick -> toggleSelection(event.chapterId)
            is UpdatesEvent.OnDownloadChapter -> downloadChapter(event.mangaId, event.chapterId)
            UpdatesEvent.ClearSelection -> _state.update { it.copy(selectedItems = emptySet()) }
            UpdatesEvent.SelectAll -> selectAll()
            UpdatesEvent.DownloadSelected -> downloadSelected()
            UpdatesEvent.MarkSelectedAsRead -> markSelectedAsRead()

            // Update Error Screen events
            UpdatesEvent.ShowUpdateErrors -> _state.update { it.copy(showUpdateErrors = true) }
            UpdatesEvent.HideUpdateErrors -> _state.update { it.copy(showUpdateErrors = false) }
            is UpdatesEvent.ClearUpdateError -> _state.update { state ->
                state.copy(updateErrors = state.updateErrors.filter { it.mangaId != event.mangaId })
            }
            UpdatesEvent.ClearAllUpdateErrors -> _state.update { it.copy(updateErrors = emptyList()) }

            // To-Be-Updated Screen events
            UpdatesEvent.ShowPendingUpdates -> {
                _state.update { it.copy(showPendingUpdates = true) }
                loadPendingUpdates()
            }
            UpdatesEvent.HidePendingUpdates -> _state.update { it.copy(showPendingUpdates = false) }
            UpdatesEvent.StartLibraryUpdate -> startLibraryUpdate()
        }
    }

    private fun handleChapterClick(mangaId: Long, chapterId: Long) {
        if (_state.value.selectedItems.isNotEmpty()) {
            toggleSelection(chapterId)
        } else {
            viewModelScope.launch {
                _effect.send(UpdatesEffect.NavigateToReader(mangaId, chapterId))
            }
        }
    }

    private fun toggleSelection(chapterId: Long) {
        _state.update { state ->
            val sel = state.selectedItems
            state.copy(
                selectedItems = if (chapterId in sel) sel - chapterId else sel + chapterId
            )
        }
    }

    private fun selectAll() {
        _state.update { state ->
            state.copy(selectedItems = state.updates.map { it.chapter.id }.toSet())
        }
    }

    private fun downloadChapter(mangaId: Long, chapterId: Long) {
        val update = _state.value.updates.find { it.chapter.id == chapterId } ?: return
        viewModelScope.launch {
            runCatching {
                downloadRepository.enqueueChapter(
                    mangaId = mangaId,
                    chapterId = chapterId,
                    mangaTitle = update.manga.title,
                    chapterTitle = update.chapter.name,
                    sourceName = update.manga.sourceId.toString()
                )
            }.onSuccess {
                _effect.send(UpdatesEffect.ShowSnackbar(
                    context.getString(R.string.updates_download_queued, update.chapter.name)
                ))
            }.onFailure {
                _effect.send(UpdatesEffect.ShowSnackbar(
                    context.getString(R.string.updates_download_failed, update.chapter.name)
                ))
            }
        }
    }

    private fun downloadSelected() {
        val selected = _state.value.selectedItems
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val updates = _state.value.updates.filter { it.chapter.id in selected }
            var successCount = 0
            var failCount = 0
            updates.forEach { update ->
                runCatching {
                    downloadRepository.enqueueChapter(
                        mangaId = update.manga.id,
                        chapterId = update.chapter.id,
                        mangaTitle = update.manga.title,
                        chapterTitle = update.chapter.name,
                        sourceName = update.manga.sourceId.toString()
                    )
                }.onSuccess { successCount++ }.onFailure { failCount++ }
            }
            _state.update { it.copy(selectedItems = emptySet()) }
            val message = if (failCount == 0) {
                context.getString(R.string.updates_bulk_download_queued, successCount)
            } else {
                context.getString(R.string.updates_bulk_download_partial, successCount, failCount)
            }
            _effect.send(UpdatesEffect.ShowSnackbar(message))
        }
    }

    private fun markSelectedAsRead() {
        val selected = _state.value.selectedItems
        if (selected.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                chapterRepository.updateChapterProgress(selected, read = true, lastPageRead = 0)
            }
            _state.update { it.copy(selectedItems = emptySet()) }
        }
    }

    private fun loadUpdates() {
        _state.update { it.copy(isLoading = true, error = null) }
        getRecentUpdatesUseCase()
            .onEach { updates ->
                _state.update { it.copy(isLoading = false, updates = updates) }
            }
            .catch { error ->
                _state.update { it.copy(isLoading = false, error = error.message) }
            }
            .launchIn(viewModelScope)
    }

    /** Record the current time so the Library badge counter resets to zero. */
    private fun markUpdatesViewed() {
        viewModelScope.launch {
            generalPreferences.setLastUpdatesViewedAt(System.currentTimeMillis())
        }
    }

    /** Load manga that will be checked during the next library update. */
    private fun loadPendingUpdates() {
        viewModelScope.launch {
            try {
                val libraryManga = getLibraryMangaUseCase().first()
                val pendingManga = libraryManga.map { manga ->
                    PendingUpdateManga(
                        mangaId = manga.id,
                        title = manga.title,
                        thumbnailUrl = manga.thumbnailUrl,
                        sourceName = manga.sourceId.toString(),
                        lastChecked = 0L
                    )
                }
                _state.update { state -> state.copy(pendingUpdates = pendingManga) }
            } catch (e: Exception) {
                _state.update { state -> state.copy(pendingUpdates = emptyList()) }
            }
        }
    }

    /** Start a manual library update. */
    private fun startLibraryUpdate() {
        viewModelScope.launch {
            LibraryUpdateWorker.enqueue(context)
            _state.update { it.copy(showPendingUpdates = false) }
            _effect.send(UpdatesEffect.ShowSnackbar(context.getString(R.string.updates_library_update_started)))
        }
    }
}
