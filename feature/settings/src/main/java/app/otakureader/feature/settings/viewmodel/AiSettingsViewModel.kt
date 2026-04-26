package app.otakureader.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.feature.settings.SettingsEffect
import app.otakureader.feature.settings.SettingsEvent
import app.otakureader.feature.settings.SettingsState
import app.otakureader.feature.settings.delegate.AiSettingsDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Focused ViewModel for the AI settings section (Gemini API key, AI feature toggles,
 * service tier, token usage).
 *
 * Owns only the [AiSettingsDelegate] so the AI preferences screen can be unit-tested and
 * navigated to independently of the rest of the settings surface. Performs the same one-time
 * legacy API-key migration that the umbrella `SettingsViewModel` performs on startup.
 *
 * Part of the work to split the previously monolithic `SettingsViewModel` into per-section
 * ViewModels aligned with the settings sections.
 */
@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val aiDelegate: AiSettingsDelegate,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _effect = Channel<SettingsEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    init {
        viewModelScope.launch {
            try {
                aiDelegate.initAiPrefs()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _effect.send(
                    SettingsEffect.ShowSnackbar(
                        "Failed to load AI settings. You may need to re-enter your API key."
                    )
                )
            }
        }
        aiDelegate.startObserving(viewModelScope) { reducer -> _state.update(reducer) }
    }

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            aiDelegate.handleEvent(event) { _effect.send(it) }
        }
    }
}
