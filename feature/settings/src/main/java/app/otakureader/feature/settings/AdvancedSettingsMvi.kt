package app.otakureader.feature.settings

import androidx.compose.runtime.Immutable
import app.otakureader.core.preferences.DohProvider

/**
 * State, events and effects for the Advanced settings screen (#1208).
 *
 * Its own MVI triple rather than more cases on [SettingsState]: that class already backs eight
 * screens, and nothing here — a User-Agent string, a resolver choice — is of any use to them.
 */
@Immutable
data class AdvancedSettingsState(
    /**
     * The override the user typed, or blank for the app's built-in identity.
     *
     * Held here as the *saved* value. The text field keeps its own in-progress text, because
     * writing on every keystroke would send a request under a half-typed User-Agent.
     */
    val userAgent: String = "",
    /** The built-in identity, shown as the field's placeholder so "blank" has a visible meaning. */
    val defaultUserAgent: String = "",
    val dohProvider: DohProvider = DohProvider.OFF,
    val verboseLogging: Boolean = false,
)

sealed interface AdvancedSettingsEvent {
    data class SetUserAgent(val value: String) : AdvancedSettingsEvent
    data object ResetUserAgent : AdvancedSettingsEvent
    data class SetDohProvider(val provider: DohProvider) : AdvancedSettingsEvent
    data class SetVerboseLogging(val enabled: Boolean) : AdvancedSettingsEvent
    data object ClearCookies : AdvancedSettingsEvent
}

sealed interface AdvancedSettingsEffect {
    /** Cookies cleared — reported rather than silent, since nothing visible changes otherwise. */
    data object CookiesCleared : AdvancedSettingsEffect

    /**
     * The clearance did not happen — no WebView provider, or the removal failed.
     *
     * Distinct from [CookiesCleared] rather than folded into it, because the two send the user
     * somewhere different: one says the Cloudflare loop should be gone, the other says it will
     * not be and to stop retrying the source over it.
     */
    data object CookieClearFailed : AdvancedSettingsEffect
}
