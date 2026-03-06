package app.komikku.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.komikku.core.preferences.AppPreferences
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
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _effect = Channel<SettingsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        observePreferences()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            appPreferences.themeMode.collect { mode ->
                _state.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            appPreferences.useDynamicColor.collect { value ->
                _state.update { it.copy(useDynamicColor = value) }
            }
        }
        viewModelScope.launch {
            appPreferences.readerMode.collect { mode ->
                _state.update { it.copy(readerMode = mode) }
            }
        }
        viewModelScope.launch {
            appPreferences.notificationsEnabled.collect { value ->
                _state.update { it.copy(notificationsEnabled = value) }
            }
        }
        viewModelScope.launch {
            appPreferences.updateCheckInterval.collect { value ->
                _state.update { it.copy(updateCheckInterval = value) }
            }
        }
    }

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            when (event) {
                is SettingsEvent.SetThemeMode -> appPreferences.setThemeMode(event.mode)
                is SettingsEvent.SetDynamicColor -> appPreferences.setUseDynamicColor(event.enabled)
                is SettingsEvent.SetReaderMode -> appPreferences.setReaderMode(event.mode)
                is SettingsEvent.SetUpdateInterval -> appPreferences.setUpdateCheckInterval(event.hours)
                is SettingsEvent.SetNotificationsEnabled -> appPreferences.setNotificationsEnabled(event.enabled)
            }
        }
    }
}
