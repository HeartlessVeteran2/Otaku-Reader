package app.otakureader.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.feature.settings.SettingsEffect
import app.otakureader.feature.settings.SettingsEvent
import app.otakureader.feature.settings.SettingsState
import app.otakureader.feature.settings.delegate.DownloadSettingsDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Focused ViewModel for the Downloads settings section (download location, Wi-Fi-only,
 * concurrent downloads, save-as-CBZ, download-ahead).
 *
 * Owns only the [DownloadSettingsDelegate] so the downloads preferences screen can be
 * unit-tested and navigated to independently of the rest of the settings surface.
 *
 * Part of the work to split the previously monolithic `SettingsViewModel` into per-section
 * ViewModels aligned with the settings sections.
 */
@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadDelegate: DownloadSettingsDelegate,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _effect = Channel<SettingsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        downloadDelegate.startObserving(viewModelScope) { reducer -> _state.update(reducer) }
    }

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            downloadDelegate.handleEvent(event) { _effect.send(it) }
        }
    }
}
