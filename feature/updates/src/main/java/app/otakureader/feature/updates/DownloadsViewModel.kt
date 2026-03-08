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
            is DownloadsEvent.Pause -> viewModelScope.launch {
                downloadRepository.pauseDownload(event.id)
            }

            is DownloadsEvent.Resume -> viewModelScope.launch {
                downloadRepository.resumeDownload(event.id)
            }

            is DownloadsEvent.Cancel -> viewModelScope.launch {
                downloadRepository.cancelDownload(event.id)
            }

            DownloadsEvent.ClearAll -> viewModelScope.launch {
                downloadRepository.clearAll()
            }
        }
    }
}
