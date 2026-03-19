package app.otakureader.feature.tracking

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.common.mvi.UiEffect
import app.otakureader.core.common.mvi.UiEvent
import app.otakureader.core.common.mvi.UiState
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackStatus
import app.otakureader.domain.model.TrackerType
import app.otakureader.domain.tracking.TrackRepository
import app.otakureader.domain.tracking.Tracker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackingState(
    val trackers: List<TrackerUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTracker: Int? = null,
    val searchQuery: String = "",
    val searchResults: List<TrackEntry> = emptyList(),
    val isSearching: Boolean = false,
    val mangaId: Long = 0L,
    val mangaTitle: String = "",
    val currentEntry: TrackEntry? = null,
    /** Tracker ID for which the credential login dialog should be shown. */
    val loginDialogTrackerId: Int? = null
) : UiState

data class TrackerUiModel(
    val id: Int,
    val name: String,
    /** Brand color (ARGB) used to render the tracker badge locally. */
    val brandColor: Long,
    val isLoggedIn: Boolean,
    val entry: TrackEntry? = null
)

sealed interface TrackingEvent : UiEvent {
    data class LoadTrackers(val mangaId: Long, val mangaTitle: String) : TrackingEvent
    /** Initiates the appropriate login flow (credential dialog or OAuth). */
    data class InitiateLogin(val trackerId: Int) : TrackingEvent
    /** Submits credentials collected from the login dialog. */
    data class Login(val trackerId: Int, val username: String, val password: String) : TrackingEvent
    data object DismissLoginDialog : TrackingEvent
    data class Logout(val trackerId: Int) : TrackingEvent
    /** Opens the search dialog for a tracker without triggering a search yet. */
    data class OpenSearchDialog(val trackerId: Int) : TrackingEvent
    data class Search(val trackerId: Int, val query: String) : TrackingEvent
    data class LinkManga(val trackerId: Int, val remoteId: Long) : TrackingEvent
    data class UnlinkManga(val trackerId: Int) : TrackingEvent
    data class UpdateStatus(val trackerId: Int, val status: TrackStatus) : TrackingEvent
    data class UpdateProgress(val trackerId: Int, val chapter: Float) : TrackingEvent
    data class UpdateScore(val trackerId: Int, val score: Float) : TrackingEvent
    data class OnSearchQueryChange(val query: String) : TrackingEvent
    data object ClearSearch : TrackingEvent
}

sealed interface TrackingEffect : UiEffect {
    data class ShowMessage(val message: String) : TrackingEffect
    data class ShowError(val message: String) : TrackingEffect
    data class OpenOAuth(val trackerId: Int, val url: String) : TrackingEffect
}

@HiltViewModel
class TrackingViewModel @Inject constructor(
    trackers: Set<@JvmSuppressWildcards Tracker>,
    private val trackRepository: TrackRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val trackerMap: Map<Int, Tracker> = trackers.associateBy { it.id }

    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TrackingState()
        )

    private val _effect = Channel<TrackingEffect>(Channel.BUFFERED)
    val effect: Flow<TrackingEffect> = _effect.receiveAsFlow()

    /** Tracks the current entry-observation job so it can be cancelled on re-entry. */
    private var observeEntriesJob: Job? = null

    fun onEvent(event: TrackingEvent) {
        when (event) {
            is TrackingEvent.LoadTrackers -> loadTrackers(event.mangaId, event.mangaTitle)
            is TrackingEvent.InitiateLogin -> initiateLogin(event.trackerId)
            is TrackingEvent.Login -> login(event.trackerId, event.username, event.password)
            TrackingEvent.DismissLoginDialog -> _state.update { it.copy(loginDialogTrackerId = null) }
            is TrackingEvent.Logout -> logout(event.trackerId)
            is TrackingEvent.OpenSearchDialog -> _state.update {
                it.copy(selectedTracker = event.trackerId, searchQuery = "", searchResults = emptyList())
            }
            is TrackingEvent.Search -> search(event.trackerId, event.query)
            is TrackingEvent.LinkManga -> linkManga(event.trackerId, event.remoteId)
            is TrackingEvent.UnlinkManga -> unlinkManga(event.trackerId)
            is TrackingEvent.UpdateStatus -> updateStatus(event.trackerId, event.status)
            is TrackingEvent.UpdateProgress -> updateProgress(event.trackerId, event.chapter)
            is TrackingEvent.UpdateScore -> updateScore(event.trackerId, event.score)
            is TrackingEvent.OnSearchQueryChange -> _state.update { it.copy(searchQuery = event.query) }
            TrackingEvent.ClearSearch -> _state.update {
                it.copy(searchQuery = "", searchResults = emptyList(), selectedTracker = null)
            }
        }
    }

    private fun loadTrackers(mangaId: Long, mangaTitle: String) {
        _state.update { it.copy(mangaId = mangaId, mangaTitle = mangaTitle, isLoading = true) }

        // Cancel any previous observation to avoid leaking collectors when mangaId changes.
        observeEntriesJob?.cancel()
        observeEntriesJob = viewModelScope.launch {
            trackRepository.observeEntriesForManga(mangaId).collect { entries ->
                val entryMap = entries.associateBy { it.trackerId }
                val trackerModels = trackerMap.values
                    .sortedBy { it.id }
                    .map { tracker ->
                        TrackerUiModel(
                            id = tracker.id,
                            name = tracker.name,
                            brandColor = getTrackerBrandColor(tracker.id),
                            isLoggedIn = tracker.isLoggedIn,
                            entry = entryMap[tracker.id]
                        )
                    }
                _state.update { it.copy(trackers = trackerModels, isLoading = false) }
            }
        }
    }

    /**
     * Determines the correct login flow:
     * - Credential-based trackers (Kitsu, MangaUpdates) show a username/password dialog.
     * - OAuth-based trackers (MAL, AniList, Shikimori) open the provider's authorization URL
     *   with PKCE parameters for security.
     */
    private fun initiateLogin(trackerId: Int) {
        if (isOAuthTracker(trackerId)) {
            val tracker = trackerMap[trackerId]
            val codeVerifier = generateCodeVerifier()
            val oauthUrl = tracker?.authorizationUrl(codeVerifier)
                ?: getOAuthUrl(trackerId) // Fallback to base URL for trackers that haven't
                                          // implemented authorizationUrl() yet
            _effect.trySend(TrackingEffect.OpenOAuth(trackerId, oauthUrl))
        } else {
            _state.update { it.copy(loginDialogTrackerId = trackerId) }
        }
    }

    /**
     * Generates a random PKCE code verifier (43-128 characters, URL-safe).
     */
    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
    }

    private fun login(trackerId: Int, username: String, password: String) {
        val tracker = trackerMap[trackerId] ?: return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loginDialogTrackerId = null) }

            try {
                val success = tracker.login(username, password)
                if (success) {
                    refreshTracker(trackerId)
                    _effect.trySend(TrackingEffect.ShowMessage(
                        context.getString(R.string.tracking_login_success, tracker.name)
                    ))
                } else {
                    _effect.trySend(TrackingEffect.ShowError(
                        context.getString(R.string.tracking_login_failed, tracker.name)
                    ))
                }
            } catch (e: Exception) {
                _effect.trySend(TrackingEffect.ShowError(
                    context.getString(R.string.tracking_login_error, e.message ?: "")
                ))
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun logout(trackerId: Int) {
        val tracker = trackerMap[trackerId] ?: return
        tracker.logout()
        viewModelScope.launch { refreshTracker(trackerId) }
    }

    private fun search(trackerId: Int, query: String) {
        val tracker = trackerMap[trackerId] ?: return

        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, selectedTracker = trackerId) }

            try {
                val results = tracker.search(query)
                _state.update { it.copy(searchResults = results, isSearching = false) }
            } catch (e: Exception) {
                _effect.trySend(TrackingEffect.ShowError(
                    context.getString(R.string.tracking_search_error, e.message ?: "")
                ))
                _state.update { it.copy(isSearching = false) }
            }
        }
    }

    private fun linkManga(trackerId: Int, remoteId: Long) {
        val tracker = trackerMap[trackerId] ?: return
        val mangaId = _state.value.mangaId

        viewModelScope.launch {
            try {
                val entry = tracker.find(remoteId)
                if (entry != null) {
                    val linkedEntry = entry.copy(
                        mangaId = mangaId,
                        trackerId = trackerId
                    )
                    trackRepository.upsertEntry(linkedEntry)
                    // Clear the search dialog state entirely so it dismisses automatically.
                    _state.update { it.copy(searchResults = emptyList(), searchQuery = "", selectedTracker = null) }
                    _effect.trySend(TrackingEffect.ShowMessage(
                        context.getString(R.string.tracking_link_success, tracker.name)
                    ))
                }
            } catch (e: Exception) {
                _effect.trySend(TrackingEffect.ShowError(
                    context.getString(R.string.tracking_link_error, e.message ?: "")
                ))
            }
        }
    }

    private fun unlinkManga(trackerId: Int) {
        val tracker = trackerMap[trackerId] ?: return
        val entry = _state.value.trackers.find { it.id == trackerId }?.entry ?: return

        viewModelScope.launch {
            try {
                trackRepository.deleteEntry(trackerId, entry.remoteId)
                _effect.trySend(TrackingEffect.ShowMessage(
                    context.getString(R.string.tracking_unlink_success, tracker.name)
                ))
            } catch (e: Exception) {
                _effect.trySend(TrackingEffect.ShowError(
                    context.getString(R.string.tracking_unlink_error, e.message ?: "")
                ))
            }
        }
    }

    private fun updateStatus(trackerId: Int, status: TrackStatus) {
        val currentEntry = _state.value.trackers.find { it.id == trackerId }?.entry ?: return
        val tracker = trackerMap[trackerId] ?: return

        viewModelScope.launch {
            try {
                val updated = tracker.update(currentEntry.copy(status = status))
                // Only persist on confirmed success (update() must throw on failure)
                trackRepository.upsertEntry(updated)
            } catch (e: Exception) {
                _effect.trySend(TrackingEffect.ShowError(
                    context.getString(R.string.tracking_update_error, e.message ?: "")
                ))
            }
        }
    }

    private fun updateProgress(trackerId: Int, chapter: Float) {
        val currentEntry = _state.value.trackers.find { it.id == trackerId }?.entry ?: return
        val tracker = trackerMap[trackerId] ?: return

        viewModelScope.launch {
            try {
                val updated = tracker.update(currentEntry.copy(lastChapterRead = chapter))
                trackRepository.upsertEntry(updated)
            } catch (e: Exception) {
                _effect.trySend(TrackingEffect.ShowError(
                    context.getString(R.string.tracking_update_error, e.message ?: "")
                ))
            }
        }
    }

    private fun updateScore(trackerId: Int, score: Float) {
        val currentEntry = _state.value.trackers.find { it.id == trackerId }?.entry ?: return
        val tracker = trackerMap[trackerId] ?: return

        viewModelScope.launch {
            try {
                val updated = tracker.update(currentEntry.copy(score = score))
                trackRepository.upsertEntry(updated)
            } catch (e: Exception) {
                _effect.trySend(TrackingEffect.ShowError(
                    context.getString(R.string.tracking_update_error, e.message ?: "")
                ))
            }
        }
    }

    private suspend fun refreshTracker(trackerId: Int) {
        val tracker = trackerMap[trackerId] ?: return
        // The flow collector in loadTrackers will automatically update entries;
        // just update the login status for this tracker immediately.
        _state.update { state ->
            val updatedList = state.trackers.map { model ->
                if (model.id == trackerId) model.copy(isLoggedIn = tracker.isLoggedIn) else model
            }
            state.copy(trackers = updatedList)
        }
    }

    private fun isOAuthTracker(trackerId: Int): Boolean = trackerId in setOf(
        TrackerType.MY_ANIME_LIST,
        TrackerType.ANILIST,
        TrackerType.SHIKIMORI
    )

    /**
     * Returns the base authorization endpoint for OAuth-based trackers.
     *
     * This is used as a fallback when a [Tracker] implementation has not yet
     * overridden [Tracker.authorizationUrl] to build a fully-parameterized URL.
     * Individual tracker implementations should override [Tracker.authorizationUrl]
     * to include client_id, redirect_uri, response_type, state, and PKCE parameters.
     */
    private fun getOAuthUrl(trackerId: Int): String = when (trackerId) {
        TrackerType.MY_ANIME_LIST -> "https://myanimelist.net/v1/oauth2/authorize"
        TrackerType.ANILIST -> "https://anilist.co/api/v2/oauth/authorize"
        TrackerType.SHIKIMORI -> "https://shikimori.one/oauth/authorize"
        else -> ""
    }

    private fun getTrackerBrandColor(trackerId: Int): Long = when (trackerId) {
        TrackerType.MY_ANIME_LIST -> 0xFF2E51A2L
        TrackerType.ANILIST -> 0xFF02A9FFL
        TrackerType.KITSU -> 0xFFE95D21L
        TrackerType.MANGA_UPDATES -> 0xFF00868BL
        TrackerType.SHIKIMORI -> 0xFF3CC680L
        else -> 0xFF9E9E9EL
    }
}