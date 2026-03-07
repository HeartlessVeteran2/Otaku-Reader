package app.komikku.feature.updates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.komikku.feature.updates.worker.UpdateWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(UpdatesState())
    val state = _state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UpdatesState(),
    )

    private val _effect = Channel<UpdatesEffect>()
    val effect = _effect.receiveAsFlow()

    fun onEvent(event: UpdatesEvent) {
        when (event) {
            UpdatesEvent.Refresh -> checkForUpdates()
            is UpdatesEvent.OnChapterClick -> {
                viewModelScope.launch {
                    _effect.send(UpdatesEffect.NavigateToReader(event.mangaId, event.chapterId))
                }
            }
        }
    }

    private fun checkForUpdates() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            val workRequest = OneTimeWorkRequestBuilder<UpdateWorker>().build()
            val workManager = WorkManager.getInstance(context)
            workManager.enqueue(workRequest)

            workManager.getWorkInfoByIdFlow(workRequest.id)
                .filterNotNull()
                .first { it.state.isFinished }

            _state.value = _state.value.copy(isLoading = false)
        }
    }
}
