package app.otakureader.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.feature.settings.SettingsEffect
import app.otakureader.feature.settings.SettingsEvent
import app.otakureader.feature.settings.SettingsState
import app.otakureader.feature.settings.delegate.AppearanceSettingsDelegate
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
 * Focused ViewModel for the Appearance settings section (theme, color scheme, locale,
 * notifications, Discord RPC).
 *
 * Owns only the [AppearanceSettingsDelegate] so the appearance preferences screen can be
 * unit-tested and navigated to independently of the rest of the settings surface. Reuses the
 * shared [SettingsState] / [SettingsEvent] / [SettingsEffect] types — only the
 * appearance-related fields/events are populated/handled here.
 *
 * Part of the work to split the previously monolithic `SettingsViewModel` (956 lines, 15+
 * dependencies) into per-section ViewModels aligned with the settings sections.
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val appearanceDelegate: AppearanceSettingsDelegate,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _effect = Channel<SettingsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        appearanceDelegate.startObserving(viewModelScope) { reducer -> _state.update(reducer) }
    }

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            appearanceDelegate.handleEvent(event) { _effect.send(it) }
        }
    }
}
