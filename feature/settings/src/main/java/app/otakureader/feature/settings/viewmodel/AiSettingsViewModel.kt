package app.otakureader.feature.settings.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.feature.settings.R
import app.otakureader.feature.settings.SettingsEffect
import app.otakureader.feature.settings.SettingsEvent
import app.otakureader.feature.settings.SettingsState
import app.otakureader.feature.settings.delegate.AiSettingsDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
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
                Log.e(TAG, "Failed to initialize AI preferences", e)
                _effect.send(
                    SettingsEffect.ShowSnackbar(
                        context.getString(R.string.settings_ai_load_failed)
                    )
                )
            }
        }
        aiDelegate.startObserving(viewModelScope) { reducer -> _state.update(reducer) }
    }

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            val handled = aiDelegate.handleEvent(event) { _effect.send(it) }
            if (!handled) {
                Log.w(TAG, "Unhandled event in AiSettingsViewModel: $event")
            }
        }
    }

    companion object {
        private const val TAG = "AiSettingsViewModel"
    }
}
