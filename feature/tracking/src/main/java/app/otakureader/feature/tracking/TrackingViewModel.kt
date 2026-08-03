package app.otakureader.feature.tracking

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.PendingOAuthStore
import app.otakureader.domain.model.SyncStatus
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackStatus
import app.otakureader.domain.model.TrackerType
import app.otakureader.domain.repository.TrackerSyncRepository
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
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

@HiltViewModel
class TrackingViewModel @Inject constructor(
    trackers: Set<@JvmSuppressWildcards Tracker>,
    private val trackRepository: TrackRepository,
    private val trackerSyncRepository: TrackerSyncRepository,
    private val pendingOAuthStore: PendingOAuthStore,
    @param:ApplicationContext private val context: Context
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

    @Suppress("CyclomaticComplexMethod")
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
            is TrackingEvent.SyncTracker -> syncTracker(event.trackerId)
            is TrackingEvent.PushToTracker -> pushToTracker(event.trackerId)
            is TrackingEvent.PullFromTracker -> pullFromTracker(event.trackerId)
            is TrackingEvent.ResolveConflict -> resolveConflict(event.trackerId, event.useLocal)
            TrackingEvent.DismissConflict -> _state.update { it.copy(conflictState = null) }
            is TrackingEvent.RetryFailedUpdates -> retryFailedUpdates(event.trackerId)
        }
    }

    private fun loadTrackers(mangaId: Long, mangaTitle: String) {
        _state.update { it.copy(mangaId = mangaId, mangaTitle = mangaTitle, isLoading = true) }

        // Cancel any previous observation to avoid leaking collectors when mangaId changes.
        observeEntriesJob?.cancel()
        observeEntriesJob = viewModelScope.launch {
            // Combine track entries and sync states into a single update
            trackRepository.observeEntriesForManga(mangaId).collect { entries ->
                val entryMap = entries.associateBy { it.trackerId }
                val currentSyncStates = _state.value.syncStates
                val trackerModels = trackerMap.values
                    .sortedBy { it.id }
                    .map { tracker ->
                        TrackerUiModel(
                            id = tracker.id,
                            name = tracker.name,
                            brandColor = getTrackerBrandColor(tracker.id),
                            isLoggedIn = tracker.isLoggedIn,
                            entry = entryMap[tracker.id],
                            syncStatus = currentSyncStates[tracker.id]?.syncStatus
                        )
                    }
                _state.update { it.copy(trackers = trackerModels, isLoading = false) }
            }
        }

        // Observe sync states separately and merge into tracker models
        viewModelScope.launch {
            trackerSyncRepository.getSyncStateForManga(mangaId).collect { syncStateList ->
                val syncMap = syncStateList.associateBy { it.trackerId }
                _state.update { state ->
                    val updatedTrackers = state.trackers.map { model ->
                        val syncState = syncMap[model.id]
                        model.copy(
                            syncStatus = syncState?.syncStatus,
                            // Convert the Instant to epoch-ms for the UI; null means never synced.
                            lastSyncAt = syncState?.lastSuccessfulSync?.toEpochMilli(),
                            // A PENDING status means there is at least one change waiting to sync.
                            pendingUpdates = if (syncState?.syncStatus == SyncStatus.PENDING) 1 else 0,
                            // An ERROR status means the last sync attempt failed.
                            failedUpdates = if (syncState?.syncStatus == SyncStatus.ERROR) 1 else 0,
                        )
                    }
                    state.copy(trackers = updatedTrackers, syncStates = syncMap)
                }
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
            // No entry means the tracker isn't in the injected set — there is nothing to log in to.
            // Previously this still fell through to a hardcoded endpoint and opened a browser for
            // a tracker the app does not have.
            val tracker = trackerMap[trackerId] ?: return
            val codeVerifier = generateCodeVerifier()
            val state = UUID.randomUUID().toString()
            // `state` goes to the tracker, not just to the store: an authorization URL without it
            // means the provider has nothing to echo back, and a callback with no state cannot be
            // tied to the login this app started.
            //
            // A null URL now ends the attempt instead of falling back to a bare authorization
            // endpoint. That fallback was `getOAuthUrl(trackerId)`, which returned the provider's
            // endpoint with no client_id, redirect_uri or response_type — a URL no OAuth provider
            // can act on, since without a client_id it cannot even resolve where to redirect. So
            // the "safety net" only ever opened a browser on a page guaranteed to error, and it
            // silently defeated AniList's own unconfigured-build guard: returning null there was
            // meant to keep the tracker unconfigured, and this turned it straight back into the
            // doomed URL it was avoiding.
            val oauthUrl = tracker.authorizationUrl(codeVerifier, state)
            if (oauthUrl == null) {
                _effect.trySend(
                    TrackingEffect.ShowError(
                        context.getString(R.string.tracking_oauth_not_configured, tracker.name)
                    )
                )
                return
            }

            // Persist {trackerId, codeVerifier, state} before opening the browser so they
            // survive the process boundary crossing during the OAuth redirect.
            viewModelScope.launch {
                pendingOAuthStore.save(trackerId, codeVerifier, state)
                _effect.send(TrackingEffect.OpenOAuth(trackerId, oauthUrl))
            }
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
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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
                trackRepository.deleteEntry(_state.value.mangaId, trackerId)
                _effect.trySend(TrackingEffect.ShowMessage(
                    context.getString(R.string.tracking_unlink_success, tracker.name)
                ))
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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

    private fun syncTracker(trackerId: Int) {
        val mangaId = _state.value.mangaId
        viewModelScope.launch {
            _state.update { state ->
                state.copy(trackers = state.trackers.map { model ->
                    if (model.id == trackerId) model.copy(syncStatus = SyncStatus.SYNCING) else model
                })
            }
            val result = trackerSyncRepository.syncManga(mangaId, trackerId)
            when {
                result.hasConflict -> {
                    val syncState = _state.value.syncStates[trackerId]
                    val trackerName = _state.value.trackers.find { it.id == trackerId }?.name ?: ""
                    _state.update { state ->
                        state.copy(
                            conflictState = ConflictUiState(
                                trackerId = trackerId,
                                trackerName = trackerName,
                                localChapter = syncState?.localLastChapterRead ?: 0f,
                                remoteChapter = syncState?.remoteLastChapterRead ?: 0f,
                                message = result.message
                            )
                        )
                    }
                }
                result.success ->
                    _effect.trySend(TrackingEffect.ShowMessage(
                        context.getString(R.string.tracking_sync_success)
                    ))
                else ->
                    _effect.trySend(TrackingEffect.ShowError(
                        context.getString(R.string.tracking_sync_error, result.message)
                    ))
            }
        }
    }

    private fun pushToTracker(trackerId: Int) {
        val mangaId = _state.value.mangaId
        viewModelScope.launch {
            val result = trackerSyncRepository.pushToTracker(mangaId, trackerId)
            if (result.success) {
                _effect.trySend(TrackingEffect.ShowMessage(
                    context.getString(R.string.tracking_push_success)
                ))
            } else {
                _effect.trySend(TrackingEffect.ShowError(
                    context.getString(R.string.tracking_sync_error, result.message)
                ))
            }
        }
    }

    private fun pullFromTracker(trackerId: Int) {
        val mangaId = _state.value.mangaId
        viewModelScope.launch {
            val result = trackerSyncRepository.pullFromTracker(mangaId, trackerId)
            if (result.success) {
                _effect.trySend(TrackingEffect.ShowMessage(
                    context.getString(R.string.tracking_pull_success)
                ))
            } else {
                _effect.trySend(TrackingEffect.ShowError(
                    context.getString(R.string.tracking_sync_error, result.message)
                ))
            }
        }
    }

    private fun resolveConflict(trackerId: Int, useLocal: Boolean) {
        val mangaId = _state.value.mangaId
        _state.update { it.copy(conflictState = null) }
        viewModelScope.launch {
            trackerSyncRepository.resolveConflict(mangaId, trackerId, useLocal)
            _effect.trySend(TrackingEffect.ShowMessage(
                context.getString(R.string.tracking_conflict_resolved)
            ))
        }
    }

    /**
     * Handles the [TrackingEvent.RetryFailedUpdates] event.
     *
     * Full retry logic is a larger feature — for now this emits a snackbar to acknowledge the
     * request and surfaces the event path so the infrastructure is in place.
     */
    @Suppress("UnusedParameter")
    private fun retryFailedUpdates(trackerId: Int) {
        viewModelScope.launch {
            _effect.trySend(
                TrackingEffect.ShowMessage(
                    context.getString(R.string.tracking_retrying_failed_updates)
                )
            )
        }
    }

    private fun isOAuthTracker(trackerId: Int): Boolean = trackerId in setOf(
        TrackerType.MY_ANIME_LIST,
        TrackerType.ANILIST,
        TrackerType.SHIKIMORI
    )

    private fun getTrackerBrandColor(trackerId: Int): Long = when (trackerId) {
        TrackerType.MY_ANIME_LIST -> 0xFF2E51A2L
        TrackerType.ANILIST -> 0xFF02A9FFL
        TrackerType.KITSU -> 0xFFE95D21L
        TrackerType.MANGA_UPDATES -> 0xFF00868BL
        TrackerType.SHIKIMORI -> 0xFF3CC680L
        else -> 0xFF9E9E9EL
    }
}
