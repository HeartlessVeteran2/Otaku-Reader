package app.otakureader.feature.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val chapterRepository: ChapterRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadsState())
    val state: StateFlow<DownloadsState> = _state.asStateFlow()

    init {
        downloadRepository.observeDownloads()
            .onEach { downloads ->
                _state.update { it.copy(items = downloads) }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: DownloadsEvent) {
        when (event) {
            is DownloadsEvent.OnItemClick -> onItemClick(event.id)
            is DownloadsEvent.OnItemLongClick -> toggleSelection(event.id)
            is DownloadsEvent.Pause -> viewModelScope.launch {
                downloadRepository.pauseDownload(event.id)
            }

            is DownloadsEvent.Resume -> viewModelScope.launch {
                downloadRepository.resumeDownload(event.id)
            }

            is DownloadsEvent.Cancel -> viewModelScope.launch {
                downloadRepository.cancelDownload(event.id)
            }

            is DownloadsEvent.Prioritize -> viewModelScope.launch {
                downloadRepository.prioritizeDownload(event.id)
            }

            DownloadsEvent.ClearAll -> viewModelScope.launch {
                downloadRepository.clearAll()
            }

            DownloadsEvent.PauseAll -> pauseAll()
            DownloadsEvent.ResumeAll -> resumeAll()
            DownloadsEvent.RetryAllFailed -> retryAllFailed()

            DownloadsEvent.ClearSelection -> clearSelection()
            DownloadsEvent.SelectAll -> selectAll()
            DownloadsEvent.PauseSelected -> pauseSelected()
            DownloadsEvent.ResumeSelected -> resumeSelected()
            DownloadsEvent.CancelSelected -> cancelSelected()
            DownloadsEvent.PrioritizeSelected -> prioritizeSelected()

            is DownloadsEvent.SortByUploadDate ->
                sortQueue(descending = event.newestFirst) { it.dateUpload }
            is DownloadsEvent.SortByChapterNumber ->
                sortQueue(descending = !event.ascending) { it.chapterNumber }
        }
    }

    private fun onItemClick(id: Long) {
        if (_state.value.selectedItems.isNotEmpty()) {
            toggleSelection(id)
        }
    }

    private fun toggleSelection(id: Long) {
        _state.update { state ->
            val currentSelection = state.selectedItems
            val newSelection = if (currentSelection.contains(id)) {
                currentSelection - id
            } else {
                currentSelection + id
            }
            state.copy(selectedItems = newSelection)
        }
    }

    private fun clearSelection() {
        _state.update { it.copy(selectedItems = emptySet()) }
    }

    private fun selectAll() {
        _state.update { state ->
            val allIds = state.items.map { it.id }.toSet()
            state.copy(selectedItems = allIds)
        }
    }

    private fun pauseSelected() {
        viewModelScope.launch {
            val selectedIds = _state.value.selectedItems
            selectedIds.forEach { id ->
                downloadRepository.pauseDownload(id)
            }
            clearSelection()
        }
    }

    private fun resumeSelected() {
        viewModelScope.launch {
            val selectedIds = _state.value.selectedItems
            selectedIds.forEach { id ->
                downloadRepository.resumeDownload(id)
            }
            clearSelection()
        }
    }

    private fun cancelSelected() {
        viewModelScope.launch {
            val selectedIds = _state.value.selectedItems
            selectedIds.forEach { id ->
                downloadRepository.cancelDownload(id)
            }
            clearSelection()
        }
    }

    private fun pauseAll() {
        viewModelScope.launch {
            _state.value.items
                .filter {
                    it.status == app.otakureader.domain.model.DownloadStatus.QUEUED ||
                        it.status == app.otakureader.domain.model.DownloadStatus.DOWNLOADING
                }
                .forEach { downloadRepository.pauseDownload(it.id) }
        }
    }

    private fun resumeAll() {
        viewModelScope.launch {
            _state.value.items
                .filter { it.status == app.otakureader.domain.model.DownloadStatus.PAUSED }
                .forEach { downloadRepository.resumeDownload(it.id) }
        }
    }

    private fun retryAllFailed() {
        viewModelScope.launch {
            _state.value.items
                .filter { it.status == app.otakureader.domain.model.DownloadStatus.FAILED }
                .forEach { downloadRepository.retryDownload(it.id) }
        }
    }

    /**
     * Reorders the entire queue by a comparable chapter field (upload date / chapter number),
     * matching Komikku's download-queue "Sort" menu. Chapter metadata isn't stored on
     * [DownloadItem] itself, so each queued chapter is looked up to read the field, then the
     * whole queue is reassigned sequential priorities in the new order via the existing
     * single-item [DownloadRepository.reorderDownload] call (same pattern as
     * [pauseSelected]/[resumeSelected]).
     *
     * Items whose chapter lookup returns null (e.g. the chapter row was deleted while still
     * queued) can't be placed by [selector], so they're appended after the sorted items in
     * their original queue order instead of being silently dropped — every queued item still
     * gets an explicit, consistent priority.
     */
    private fun <R : Comparable<R>> sortQueue(descending: Boolean, selector: (app.otakureader.domain.model.Chapter) -> R) {
        viewModelScope.launch {
            val items = _state.value.items
            val resolved = items
                .map { item -> async { item.chapterId to chapterRepository.getChapterById(item.chapterId) } }
                .awaitAll()
            val withChapter = resolved.mapNotNull { (chapterId, chapter) -> chapter?.let { chapterId to it } }
            val withoutChapter = resolved.filter { (_, chapter) -> chapter == null }

            val comparator = compareBy<Pair<Long, app.otakureader.domain.model.Chapter>> { selector(it.second) }
            val sortable = if (descending) withChapter.sortedWith(comparator.reversed()) else withChapter.sortedWith(comparator)

            (sortable + withoutChapter).forEachIndexed { index, (chapterId, _) ->
                downloadRepository.reorderDownload(chapterId, index)
            }
        }
    }

    private fun prioritizeSelected() {
        viewModelScope.launch {
            val state = _state.value
            val selectedIds = state.items
                .asSequence()
                .filter { item ->
                    item.id in state.selectedItems &&
                        (item.status == app.otakureader.domain.model.DownloadStatus.QUEUED ||
                            item.status == app.otakureader.domain.model.DownloadStatus.DOWNLOADING ||
                            item.status == app.otakureader.domain.model.DownloadStatus.PAUSED)
                }
                .map { it.id }
                .toList()
            if (selectedIds.isNotEmpty()) {
                downloadRepository.prioritizeDownloads(selectedIds)
            }
            clearSelection()
        }
    }
}
