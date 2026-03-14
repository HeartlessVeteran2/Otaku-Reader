package app.otakureader.feature.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
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

            DownloadsEvent.ClearSelection -> clearSelection()
            DownloadsEvent.SelectAll -> selectAll()
            DownloadsEvent.PauseSelected -> pauseSelected()
            DownloadsEvent.ResumeSelected -> resumeSelected()
            DownloadsEvent.CancelSelected -> cancelSelected()
            DownloadsEvent.PrioritizeSelected -> prioritizeSelected()
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
                .toSet()
            if (selectedIds.isNotEmpty()) {
                downloadRepository.prioritizeDownloads(selectedIds)
            }
            clearSelection()
        }
    }
}
