package app.otakureader.feature.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.PendingOAuthStore
import app.otakureader.domain.tracking.TrackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [TrackerOAuthScreen].
 *
 * Exchanges the OAuth authorization code for an access token via [TrackManager].
 * Loads the PKCE code verifier from [PendingOAuthStore] to satisfy PKCE requirements
 * and validates the CSRF state token when provided by the authorization server.
 */
@HiltViewModel
class TrackerOAuthViewModel @Inject constructor(
    private val trackManager: TrackManager,
    private val pendingOAuthStore: PendingOAuthStore,
) : ViewModel() {

    private val _state = MutableStateFlow(TrackerOAuthState())
    val state: StateFlow<TrackerOAuthState> = _state

    /**
     * Exchange the OAuth [code] for an access token for the given [tracker].
     *
     * @param tracker Tracker ID (e.g., "anilist", "mal", "kitsu", "shikimori").
     * @param code Authorization code from the OAuth redirect.
     * @param callbackState CSRF state token returned by the provider, or null if omitted.
     */
    fun exchangeCode(tracker: String, code: String, callbackState: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, success = false) }

            val session = pendingOAuthStore.get()

            // A stored session always carries a state (PendingOAuthSession.state is non-null), and
            // every authorization URL now sends it, so a callback that comes back *without* one is
            // not "a provider that omits state" — it is a callback this app did not start. The old
            // condition began with `callbackState != null`, which turned exactly that case into a
            // pass: an attacker-supplied redirect with no state skipped the comparison entirely,
            // which is the one shape the check exists to catch.
            if (session != null && callbackState != session.state) {
                pendingOAuthStore.clear()
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "OAuth state mismatch — possible CSRF attack. Please try logging in again."
                    )
                }
                return@launch
            }

            if (session == null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "OAuth session expired or not found. Please try logging in again."
                    )
                }
                return@launch
            }

            try {
                val result = trackManager.login(tracker, code, session.codeVerifier)
                if (result) {
                    // Done with the one-time session.
                    pendingOAuthStore.clear()
                    _state.update { it.copy(isLoading = false, success = true) }
                } else {
                    // The provider rejected the code. OAuth authorization codes are single-use,
                    // so re-exchanging the same code can never succeed — clear the session and
                    // require a fresh login (the Retry button would otherwise loop on a dead code).
                    pendingOAuthStore.clear()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Authentication failed. The authorization code may have expired or been reused."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Transient failure (e.g. network). Keep the PKCE session so Retry can re-attempt
                // the exchange — previously this cleared the session, so every retry failed with
                // "session expired" even when the code was still valid.
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "An unexpected error occurred during authentication."
                    )
                }
            }
        }
    }
}

/**
 * UI state for [TrackerOAuthScreen].
 */
data class TrackerOAuthState(
    val isLoading: Boolean = false,
    val success: Boolean = false,
    val error: String? = null,
)
