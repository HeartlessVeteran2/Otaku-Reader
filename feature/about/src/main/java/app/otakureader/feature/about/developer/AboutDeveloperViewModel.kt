package app.otakureader.feature.about.developer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.common.developer.DeveloperUnlock
import app.otakureader.core.preferences.DeveloperPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AboutDeveloperError { WrongPassphrase, NotConfigured }

data class AboutDeveloperState(
    val isPromptVisible: Boolean = false,
    val error: AboutDeveloperError? = null,
)

sealed interface AboutDeveloperEvent {
    /** One tap on the version line. The counter lives in the ViewModel, not the composable. */
    data object VersionTapped : AboutDeveloperEvent

    data class SubmitPassphrase(val input: String) : AboutDeveloperEvent

    data object DismissPrompt : AboutDeveloperEvent
}

sealed interface AboutDeveloperEffect {
    data object NavigateToDeveloper : AboutDeveloperEffect
}

/**
 * The reveal gesture and passphrase prompt on the About screen.
 *
 * The tap counter is held here rather than in a `remember` because a configuration change midway
 * through the gesture would otherwise silently reset it, leaving the sequence apparently broken
 * for no visible reason.
 *
 * See `DeveloperUnlock` for what the passphrase gate is and is not. In short: it keeps the screen
 * out of casual reach and is not a security boundary, which is why nothing here does more than
 * flip a preference flag.
 */
@HiltViewModel
class AboutDeveloperViewModel @Inject constructor(
    private val developerPreferences: DeveloperPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(AboutDeveloperState())
    val state: StateFlow<AboutDeveloperState> = _state.asStateFlow()

    private val _effect = Channel<AboutDeveloperEffect>(Channel.BUFFERED)
    val effect: Flow<AboutDeveloperEffect> = _effect.receiveAsFlow()

    private var tapCount = 0

    fun onEvent(event: AboutDeveloperEvent) {
        when (event) {
            is AboutDeveloperEvent.VersionTapped -> onVersionTapped()
            is AboutDeveloperEvent.SubmitPassphrase -> submit(event.input)
            is AboutDeveloperEvent.DismissPrompt -> {
                tapCount = 0
                _state.update { it.copy(isPromptVisible = false, error = null) }
            }
        }
    }

    private fun onVersionTapped() {
        tapCount++
        if (tapCount < DeveloperUnlock.REVEAL_TAP_COUNT) return
        tapCount = 0

        // An unconfigured build opens the prompt already reporting *why* no passphrase will work.
        // `DeveloperUnlock.matches` refuses every input in that state, so without this the dialog
        // would reject each attempt one at a time and read as a forgotten passphrase rather than
        // as a build that never had one.
        _state.update {
            it.copy(
                isPromptVisible = true,
                error = AboutDeveloperError.NotConfigured.takeUnless { _ -> DeveloperUnlock.isConfigured },
            )
        }
    }

    private fun submit(input: String) {
        if (!DeveloperUnlock.matches(input)) {
            _state.update { it.copy(error = AboutDeveloperError.WrongPassphrase) }
            return
        }
        viewModelScope.launch {
            developerPreferences.setUnlocked(true)
            _state.update { it.copy(isPromptVisible = false, error = null) }
            _effect.send(AboutDeveloperEffect.NavigateToDeveloper)
        }
    }
}
