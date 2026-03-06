package app.komikku.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.komikku.core.preferences.PreferencesDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PreferencesDataSource,
) : ViewModel() {

    val state = combine(
        preferences.theme,
        preferences.dynamicColors,
        preferences.gridSize,
        preferences.autoUpdateEnabled,
    ) { theme, dynamicColors, gridSize, autoUpdate ->
        SettingsState(
            theme = theme,
            dynamicColors = dynamicColors,
            gridSize = gridSize,
            autoUpdate = autoUpdate,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsState(),
    )

    private val _effect = Channel<SettingsEffect>()
    val effect = _effect.receiveAsFlow()

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            when (event) {
                is SettingsEvent.OnThemeChange -> preferences.setTheme(event.theme)
                is SettingsEvent.OnDynamicColorsChange -> preferences.setDynamicColors(event.enabled)
                is SettingsEvent.OnGridSizeChange -> preferences.setGridSize(event.size)
                is SettingsEvent.OnAutoUpdateChange -> preferences.setAutoUpdateEnabled(event.enabled)
            }
        }
    }
}
