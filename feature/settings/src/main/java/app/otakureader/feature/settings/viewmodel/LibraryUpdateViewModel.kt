package app.otakureader.feature.settings.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.feature.settings.SettingsEffect
import app.otakureader.feature.settings.SettingsEvent
import app.otakureader.feature.settings.SettingsState
import app.otakureader.feature.settings.delegate.LibrarySettingsDelegate
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
 * Focused ViewModel for the Library & Update settings section (grid size, badges, library
 * update interval, Wi-Fi-only updates, auto-refresh on start).
 *
 * Owns only the [LibrarySettingsDelegate] so the library preferences screen can be unit-tested
 * and navigated to independently of the rest of the settings surface.
 *
 * Part of the work to split the previously monolithic `SettingsViewModel` into per-section
 * ViewModels aligned with the settings sections.
 */
@HiltViewModel
class LibraryUpdateViewModel @Inject constructor(
    private val libraryDelegate: LibrarySettingsDelegate,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _effect = Channel<SettingsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        libraryDelegate.startObserving(viewModelScope) { reducer -> _state.update(reducer) }
    }

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            val handled = libraryDelegate.handleEvent(event) { _effect.send(it) }
            if (!handled) {
                Log.w(TAG, "Unhandled event in LibraryUpdateViewModel: $event")
            }
        }
    }

    companion object {
        private const val TAG = "LibraryUpdateViewModel"
    }
}
