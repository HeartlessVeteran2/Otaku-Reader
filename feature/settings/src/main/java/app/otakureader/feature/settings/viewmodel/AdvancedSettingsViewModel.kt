package app.otakureader.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.network.NetworkSettings
import app.otakureader.core.network.cookie.WebViewCookieJar
import app.otakureader.core.preferences.NetworkPreferences
import app.otakureader.feature.settings.AdvancedSettingsEffect
import app.otakureader.feature.settings.AdvancedSettingsEvent
import app.otakureader.feature.settings.AdvancedSettingsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdvancedSettingsViewModel @Inject constructor(
    private val preferences: NetworkPreferences,
    private val cookieJar: WebViewCookieJar,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AdvancedSettingsState(defaultUserAgent = NetworkSettings.DEFAULT_USER_AGENT),
    )
    val state: StateFlow<AdvancedSettingsState> = _state.asStateFlow()

    private val _effect = Channel<AdvancedSettingsEffect>(Channel.BUFFERED)
    val effect: Flow<AdvancedSettingsEffect> = _effect.receiveAsFlow()

    init {
        viewModelScope.launch {
            preferences.userAgent.collect { value -> _state.update { it.copy(userAgent = value) } }
        }
        viewModelScope.launch {
            preferences.dohProvider.collect { value -> _state.update { it.copy(dohProvider = value) } }
        }
        viewModelScope.launch {
            preferences.verboseLogging.collect { value ->
                _state.update { it.copy(verboseLogging = value) }
            }
        }
    }

    fun onEvent(event: AdvancedSettingsEvent) {
        when (event) {
            is AdvancedSettingsEvent.SetUserAgent -> viewModelScope.launch {
                preferences.setUserAgent(event.value)
            }
            // Writing blank rather than removing the key: NetworkPreferences treats a blank value
            // and an absent one identically, so this is the same state the user started from.
            AdvancedSettingsEvent.ResetUserAgent -> viewModelScope.launch {
                preferences.setUserAgent("")
            }
            is AdvancedSettingsEvent.SetDohProvider -> viewModelScope.launch {
                preferences.setDohProvider(event.provider)
            }
            is AdvancedSettingsEvent.SetVerboseLogging -> viewModelScope.launch {
                preferences.setVerboseLogging(event.enabled)
            }
            AdvancedSettingsEvent.ClearCookies -> viewModelScope.launch {
                val cleared = cookieJar.clear()
                _effect.send(
                    if (cleared) {
                        AdvancedSettingsEffect.CookiesCleared
                    } else {
                        AdvancedSettingsEffect.CookieClearFailed
                    },
                )
            }
        }
    }
}
