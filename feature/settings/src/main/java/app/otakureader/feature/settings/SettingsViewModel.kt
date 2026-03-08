package app.otakureader.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.core.preferences.ReaderPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val generalPreferences: GeneralPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val readerPreferences: ReaderPreferences
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
            combine(
                generalPreferences.themeMode,
                generalPreferences.useDynamicColor,
                generalPreferences.locale,
                generalPreferences.notificationsEnabled,
                generalPreferences.updateCheckInterval
            ) { themeMode, dynamicColor, locale, notificationsEnabled, updateInterval ->
                SettingsState(
                    themeMode = themeMode,
                    useDynamicColor = dynamicColor,
                    locale = locale,
                    notificationsEnabled = notificationsEnabled,
                    updateCheckInterval = updateInterval
                )
            }.combine(libraryPreferences.gridSize) { state, gridSize ->
                state.copy(libraryGridSize = gridSize)
            }.combine(libraryPreferences.showBadges) { state, showBadges ->
                state.copy(showBadges = showBadges)
            }.combine(readerPreferences.readerMode) { state, readerMode ->
                state.copy(readerMode = readerMode)
            }.combine(readerPreferences.keepScreenOn) { state, keepScreenOn ->
                state.copy(keepScreenOn = keepScreenOn)
            }.collect { newState ->
                _state.update { newState }
            }
        }
    }

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            when (event) {
                is SettingsEvent.SetThemeMode -> generalPreferences.setThemeMode(event.mode)
                is SettingsEvent.SetDynamicColor -> generalPreferences.setUseDynamicColor(event.enabled)
                is SettingsEvent.SetLocale -> generalPreferences.setLocale(event.locale)
                is SettingsEvent.SetNotificationsEnabled -> generalPreferences.setNotificationsEnabled(event.enabled)
                is SettingsEvent.SetUpdateInterval -> generalPreferences.setUpdateCheckInterval(event.hours)
                is SettingsEvent.SetLibraryGridSize -> libraryPreferences.setGridSize(event.size)
                is SettingsEvent.SetShowBadges -> libraryPreferences.setShowBadges(event.enabled)
                is SettingsEvent.SetReaderMode -> readerPreferences.setReaderMode(event.mode)
                is SettingsEvent.SetKeepScreenOn -> readerPreferences.setKeepScreenOn(event.enabled)
            }
        }
    }
}
